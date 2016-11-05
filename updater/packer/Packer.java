package updater.packer;

import jdk.internal.org.objectweb.asm.ClassReader;
import org.tribot.script.Script;
import org.tribot.script.ScriptManifest;

import javax.swing.*;
import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
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
    private static HashMap<String, Set<String>> scriptDependencyMap = new HashMap<>();
    private static HashMap<Class, String> scriptNameMap = new HashMap<>();
    private static HashMap<String, File> zips = new HashMap<>();

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
            if(!zipFile.getParentFile().exists()) {
                zipFile.mkdirs();
            }

            FileOutputStream e = new FileOutputStream(zipFile);
            ZipOutputStream zos = new ZipOutputStream(e);
            Iterator var4 = files.iterator();

            while(var4.hasNext()) {
                File file = (File)var4.next();
                FileInputStream fis = new FileInputStream(file);
                ZipEntry zipEntry = new ZipEntry(file.getAbsolutePath().substring(file.getAbsolutePath().lastIndexOf(SRC_DIR.getName()) + 4));
                zos.putNextEntry(zipEntry);
                byte[] bytes = new byte[1024];

                int length;
                while((length = fis.read(bytes)) >= 0) {
                    zos.write(bytes, 0, length);
                }

                zos.closeEntry();
                fis.close();
            }

            zos.close();
            e.close();
            return true;
        } catch (IOException var10) {
            var10.printStackTrace();
            return false;
        }
    }

    public static boolean hasPacked() {
        return zips.size() > 0;
    }

    public static void load(HashMap<String, List<String>> scriptPaths) throws IOException, ClassNotFoundException {
        scriptDependencyMap.clear();
        scriptNameMap.clear();

        List classFiles = getDirFiles(BIN_DIR, (file) -> file.getName().endsWith(".class"));
        Iterator scriptSelector = classFiles.iterator();
        while (scriptSelector.hasNext()) {
            File scriptName = (File)scriptSelector.next();
            ClassReader fullScriptName = new ClassReader(new FileInputStream(scriptName));
            if (fullScriptName.getSuperName().endsWith("org/tribot/script/Script")) {
                DependencyVisitor dependencies = new DependencyVisitor();
                ScriptManifestVisitor zipFile = new ScriptManifestVisitor();
                fullScriptName.accept(zipFile, 0);
                fullScriptName.accept(dependencies, 0);
                findAllSubDependencies(dependencies);
                String scriptName1;
                if (zipFile.getAttributes().size() > 0) {
                    Object version = zipFile.getAttributes().get("version");
                    scriptName1 = zipFile.getAttributes().get("name") + " V" + (version == null?"1.0":version);
                } else {
                    scriptName1 = scriptName.getName().substring(0, scriptName.getName().length() - 6);
                }

                ClassLoader loader = new URLClassLoader(new URL[] {BIN_DIR.toURL()});
                Class clazz = loader.loadClass(fullScriptName.getClassName().replaceAll("/", "."));

                scriptNameMap.put(clazz, scriptName1);
                scriptDependencyMap.put(fullScriptName.getClassName(), dependencies.getDependencyClasses());
            }
        }

        for (Class scriptClass : scriptNameMap.keySet()) {
            for (String scriptName : scriptPaths.keySet()) {
                if (scriptNameMap.get(scriptClass).contains(scriptName)) {
                    System.out.println("Found script: " + scriptName);
                    if (scriptPaths.get(scriptName).size() == 0) return;

                    List<File> sourceDirs = scriptPaths.get(scriptName).stream().map(s -> new File(s)).collect(Collectors.toList());
                    File zip = pack(scriptClass, sourceDirs);
                    if (zip != null) zips.put(scriptName, zip);
                }
            }
        }

        System.out.println("Loaded " + scriptNameMap.size() + " scripts and " + scriptDependencyMap.size() + " dependencies.");
    }

    public static File getZip(String name) {
        return zips.containsKey(name) ? zips.get(name) : null;
    }

    public static File pack(Class<? extends Script> script, List<File> dirs) {
        try {
            List<File> sources = new ArrayList<>();
            File e = new File(JAR_FILE.getParentFile(), "zips");
            if (!e.exists()) e.mkdir();
            for (File dir : dirs) {
                Collections.addAll(sources, getDirFiles(dir, (f) -> f.getName().endsWith(".java")).toArray(new File[0]));
            }

            System.out.println("Loaded " + sources.size() + " source files.");
            String scriptName;
            if(script.isAnnotationPresent(ScriptManifest.class)) {
                ScriptManifest codeSrc = script.getAnnotation(ScriptManifest.class);
                scriptName = codeSrc.name() + " V" + codeSrc.version();
            } else {
                Logger.getLogger("Logger").log(Level.WARNING, "No script manifest found!");
                scriptName = script.getSimpleName();
            }


            File codeSrc1 = new File(script.getProtectionDomain().getCodeSource().getLocation().toURI());
            File scriptFile = new File(codeSrc1, script.getName().replace('.', File.separatorChar) + ".class");
            ClassReader cr = new ClassReader(new FileInputStream(scriptFile));
            DependencyVisitor dv = new DependencyVisitor();
            cr.accept(dv, 0);
            findAllSubDependencies(dv);
            System.out.printf("Found %d dependencies for the script: %s\n", new Object[]{Integer.valueOf(dv.getDependencyClasses().size()), scriptName});
            Set var10000 = dv.getDependencyClasses();
            PrintStream var10001 = System.out;
            System.out.getClass();
            var10000.forEach(var10001::println);
            System.out.println();
            File zipFile = getZipFile(e, scriptName);
            packZip(getScriptFiles(dirs, sources, dv.getDependencyClasses()), zipFile);
            System.out.println("Successfully packed: " + zipFile.getName());
            return zipFile;
        } catch (URISyntaxException | IOException var9) {
            var9.printStackTrace();
        }

        return null;
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
            String d = (String)var2.next();
            if(d.startsWith("scripts/")) {
                if (!checkedClasses.contains(d)) {
                    ClassReader cr = new ClassReader(new FileInputStream(new File(BIN_DIR, d + ".class")));
                    cr.accept(dv, 0);
                    checkedClasses.add(d);
                    //System.out.println(d);
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
