package updater.packer;

import jdk.internal.org.objectweb.asm.ClassReader;

import javax.swing.*;
import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Created by Spencer on 11/3/2016.
 */
public class Packer {

    private static File SRC_DIR;
    private static File BIN_DIR;
    private static File JAR_FILE;
    private static File TRIBOT_DIR;

    private static HashMap<String, File> zips = new HashMap<>();
    private static HashMap<String, String> versions = new HashMap<>();
    private static List<String> superScripts = new ArrayList<>();

    static {
        try {
            JAR_FILE = new File(Packer.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
        } catch (URISyntaxException var1) {
            var1.printStackTrace();
            throw new RuntimeException();
        }

        TRIBOT_DIR = new File(System.getProperty("os.name").startsWith("Win")?System.getenv("APPDATA"):System.getProperty("user.home"), ".tribot");
        BIN_DIR = new File(TRIBOT_DIR, "bin");
        SRC_DIR = new File(JAR_FILE.getParent(), "src");
    }

    private static boolean packZip(List<File> files, File zipFile) {
        try {
            if (!zipFile.getParentFile().exists())
                zipFile.mkdirs();

            FileOutputStream e = new FileOutputStream(zipFile);
            ZipOutputStream zos = new ZipOutputStream(e);
            Iterator fileIterator = files.iterator();

            while(fileIterator.hasNext()) {
                File file = (File) fileIterator.next();
                FileInputStream fis = new FileInputStream(file);
                ZipEntry zipEntry = new ZipEntry(file.getAbsolutePath().substring(file.getAbsolutePath().lastIndexOf(SRC_DIR.getName()) + 4));
                zos.putNextEntry(zipEntry);
                byte[] bytes = new byte[1024];
                int length;
                while((length = fis.read(bytes)) >= 0)
                    zos.write(bytes, 0, length);

                zos.closeEntry();
                fis.close();
            }
            zos.close();
            e.close();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean hasPacked() {
        return zips.size() > 0;
    }

    public static int findSubScripts(String superScript) throws IOException {
        int subScriptCount = 0;
        List<File> classFiles = getDirFiles(BIN_DIR, (file) -> file.getName().endsWith(".class"));
        for (File classFile : classFiles) {
            ClassReader fullClassName = new ClassReader(new FileInputStream(classFile));
            if (fullClassName.getSuperName().endsWith(superScript)) {
                subScriptCount++;
            }
        }

        return subScriptCount;
    }

    public static void findSuperScript() throws IOException {
        List<File> classFiles = getDirFiles(BIN_DIR, (file) -> file.getName().endsWith(".class"));
        for (File classFile : classFiles) {
            ClassReader fullClassName = new ClassReader(new FileInputStream(classFile));
            if (fullClassName.getSuperName().endsWith("org/tribot/script/Script")) {
                if (superScripts.contains(fullClassName.getClassName())) continue;
                if (findSubScripts(fullClassName.getClassName()) > 0) {
                    System.out.println("Found super script: " + fullClassName.getClassName());
                    if (!superScripts.contains(fullClassName.getClassName())) {
                        superScripts.add(fullClassName.getClassName());
                    }
                }
            }
        }
    }

    public static void loadScript(String scriptName, List<String> scriptPaths) throws IOException, ClassNotFoundException {
        findSuperScript();

        List<File> classFiles = getDirFiles(BIN_DIR, (file) -> file.getName().endsWith(".class"));
        for (File classFile : classFiles) {
            ClassReader fullClassName = new ClassReader(new FileInputStream(classFile));
            if (fullClassName.getSuperName().endsWith("org/tribot/script/Script") || superScripts.contains(fullClassName.getSuperName())) {
                DependencyVisitor dependencies = new DependencyVisitor();
                ScriptManifestVisitor manifest = new ScriptManifestVisitor();
                fullClassName.accept(manifest, 0);
                fullClassName.accept(dependencies, 0);

                String zipName;
                if (manifest.getAttributes().size() > 0) {
                    Object version = manifest.getAttributes().get("version");
                    zipName = manifest.getAttributes().get("name") + " V" + (version == null ? "1.0" : version);
                    if (!zipName.contains(scriptName)) {
                        //System.out.println(zipName + " does not contain " + scriptName + " do not process.");
                        continue;
                    }

                    versions.put(scriptName, (version == null ? "1.0" : version) + "");
                } else {
                    //System.out.println(fullClassName.getClassName() + " does not contain a script manifest.");
                    continue; //Do not process without manifests
                }

                findAllSubDependencies(dependencies);
                Set dependencyClasses = dependencies.getDependencyClasses();
                File zipFolder = new File(JAR_FILE.getParentFile(), "zips");
                File zipFile = getZipFile(zipFolder, zipName);

                List<File> dirs = new ArrayList<>();
                Collections.addAll(dirs, scriptPaths.stream().map(f -> new File(f)).collect(Collectors.toList()).toArray(new File[0]));
                List<File> sources = new ArrayList<>();
                for (File dir : dirs) {
                    Collections.addAll(sources, getDirFiles(dir, (f) -> f.getName().endsWith(".java")).toArray(new File[0]));
                }

                System.out.println("--- PACKING " + scriptName + " ---");
                packZip(getScriptFiles(dirs, sources, dependencyClasses), zipFile);
                System.out.println("--- END PACKING " + scriptName + " ---");
                if (zipFile != null) {
                    zips.put(scriptName, zipFile);
                }
            }
        }
    }

    public static void load(HashMap<String, List<String>> scriptPaths) throws IOException, ClassNotFoundException {
        for (String scriptName : scriptPaths.keySet()) {
            if (scriptPaths.get(scriptName).size() > 0)
                loadScript(scriptName, scriptPaths.get(scriptName));
        }
    }

    public static File getZip(String name) {
        return zips.containsKey(name) ? zips.get(name) : null;
    }

    public static String getVersion(String name) {
        return versions.containsKey(name) ? versions.get(name) : "1.0";
    }

    private static File getZipFile(File scrDir, String scriptName) {
        File zipFile = new File(scrDir, scriptName + ".zip");

        for(int version = 1; zipFile.exists(); ++version) {
            zipFile = new File(zipFile.getParent(), scriptName + String.format("(%d).zip", new Object[]{Integer.valueOf(version)}));
        }

        return zipFile;
    }

    private static void findAllSubDependencies(DependencyVisitor dv) throws IOException {
        findAllSubDependencies(dv, new HashSet());
    }

    private static void findAllSubDependencies(DependencyVisitor dv, Set<String> checkedClasses) throws IOException {
        Iterator var2 = (new HashSet(dv.getDependencyClasses())).iterator();
        while(var2.hasNext()) {
            String d = (String) var2.next();
            if (d.startsWith("scripts/")) {
                if (!checkedClasses.contains(d)) {
                    ClassReader cr = new ClassReader(new FileInputStream(new File(BIN_DIR, d + ".class")));
                    cr.accept(dv, 0);
                    checkedClasses.add(d);
                    findAllSubDependencies(dv, checkedClasses);
                }
            }
        }
    }

    private static List<File> getScriptFiles(List<File> sourceDirs, List<File> sourceFiles, Set<String> dependencies) {
        ArrayList sourceFilesToPack = new ArrayList();
        ArrayList missingFiles = new ArrayList();
        dependencies.stream().filter((d) ->  d.startsWith("scripts/") && d.indexOf(36) == -1).map((d) -> d.replace('/', File.separatorChar)).forEach((d) -> {
            for (File dir : sourceDirs) {
                File file = new File(dir, d + ".java");
                if (sourceFiles.stream().anyMatch((f) -> f.equals(file))) {
                    sourceFilesToPack.add(file);
                    return;
                }
            }

            System.out.println("Missing file: " + d);
            System.exit(0);
        });

        if(missingFiles.size() > 0) {
            StringBuilder sb = new StringBuilder();
            missingFiles.forEach((m) -> {
                sb.append(m);
                sb.append("\n");
            });
            JOptionPane.showMessageDialog(null, "Can\'t find the following source files:\n" + sb.toString(), "Missing files", 0);
            System.exit(0);
        }

        return sourceFilesToPack;
    }

    private static List<File> getDirFiles(File dir, final Predicate<File> filter) {
        final ArrayList files = new ArrayList();

        try {
            Files.walkFileTree(dir.toPath(), new SimpleFileVisitor<Path>() {
                public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                    File file = path.toFile();
                    if(filter.test(file) && !files.contains(file)) {
                        files.add(file);
                    }

                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException var4) {
            var4.printStackTrace();
        }

        return files;
    }
}
