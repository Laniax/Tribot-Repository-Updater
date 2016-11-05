package updater.packer;

import java.util.HashSet;
import java.util.Set;
import jdk.internal.org.objectweb.asm.AnnotationVisitor;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.FieldVisitor;
import jdk.internal.org.objectweb.asm.Label;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.signature.SignatureReader;
import jdk.internal.org.objectweb.asm.signature.SignatureVisitor;

public class DependencyVisitor extends ClassVisitor {
    private final Set<String> dependencyClasses = new HashSet();

    public Set<String> getDependencyClasses() {
        return this.dependencyClasses;
    }

    protected DependencyVisitor() {
        super(327680);
    }

    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        if(signature == null) {
            this.addName(superName);
            this.addNames(interfaces);
        } else {
            this.addSignature(signature);
        }
    }

    public void visitInnerClass(String name, String outerName, String innerName, int access) {
        this.addName(name);
    }

    public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
        if(signature == null) {
            this.addDesc(desc);
        } else {
            this.addTypeSignature(signature);
        }

        if(value instanceof Type) {
            this.addType((Type)value);
        }

        return new DependencyVisitor.FieldDependencyVisitor();
    }

    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        if(signature == null) {
            this.addMethodDesc(desc);
        } else {
            this.addSignature(signature);
        }

        this.addNames(exceptions);
        return new DependencyVisitor.MethodDependencyVisitor();
    }

    private void addName(String name) {
        if(name != null) {
            if(name.startsWith("[L") && name.endsWith(";")) {
                name = name.substring(2, name.length() - 1);
            }

            int index = name.indexOf(36);
            if(index != -1) {
                this.dependencyClasses.add(name);
                name = name.substring(0, index);
            }

            this.dependencyClasses.add(name);
        }
    }

    private void addNames(String[] names) {
        if(names != null) {
            String[] var2 = names;
            int var3 = names.length;

            for(int var4 = 0; var4 < var3; ++var4) {
                String name = var2[var4];
                this.addName(name);
            }

        }
    }

    private void addDesc(String desc) {
        this.addType(Type.getType(desc));
    }

    private void addMethodDesc(String desc) {
        this.addType(Type.getReturnType(desc));
        Type[] types = Type.getArgumentTypes(desc);
        Type[] var3 = types;
        int var4 = types.length;

        for(int var5 = 0; var5 < var4; ++var5) {
            Type type = var3[var5];
            this.addType(type);
        }

    }

    private void addType(Type t) {
        switch(t.getSort()) {
            case 9:
                this.addType(t.getElementType());
                break;
            case 10:
                this.addName(t.getClassName().replace('.', '/'));
        }

    }

    private void addSignature(String signature) {
        (new SignatureReader(signature)).accept(new DependencyVisitor.SignatureDependencyVisitor());
    }

    private void addTypeSignature(String signature) {
        (new SignatureReader(signature)).acceptType(new DependencyVisitor.SignatureDependencyVisitor());
    }

    private class FieldDependencyVisitor extends FieldVisitor {
        public FieldDependencyVisitor() {
            super(327680);
        }

        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            DependencyVisitor.this.addDesc(desc);
            return DependencyVisitor.this.new AnnotationDependencyVisitor();
        }
    }

    private class MethodDependencyVisitor extends MethodVisitor {
        public MethodDependencyVisitor() {
            super(327680);
        }

        public void visitTypeInsn(int opcode, String desc) {
            if(desc.charAt(0) == 91) {
                DependencyVisitor.this.addDesc(desc);
            } else {
                DependencyVisitor.this.addName(desc);
            }
        }

        public void visitFieldInsn(int opcode, String owner, String name, String desc) {
            DependencyVisitor.this.addName(owner);
            DependencyVisitor.this.addDesc(desc);
        }

        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            DependencyVisitor.this.addName(owner);
            DependencyVisitor.this.addMethodDesc(desc);
        }

        public void visitLdcInsn(Object cst) {
            if(cst instanceof Type) {
                DependencyVisitor.this.addType((Type)cst);
            }

        }

        public void visitMultiANewArrayInsn(String desc, int dims) {
            DependencyVisitor.this.addDesc(desc);
        }

        public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
            DependencyVisitor.this.addName(type);
        }

        public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
            if(signature == null) {
                DependencyVisitor.this.addDesc(desc);
            } else {
                DependencyVisitor.this.addTypeSignature(signature);
            }
        }
    }

    private class AnnotationDependencyVisitor extends AnnotationVisitor {
        public AnnotationDependencyVisitor() {
            super(327680);
        }

        public void visit(String name, Object value) {
            if(value instanceof Type) {
                DependencyVisitor.this.addType((Type)value);
            }

        }

        public void visitEnum(String name, String desc, String value) {
            DependencyVisitor.this.addDesc(desc);
        }

        public AnnotationVisitor visitAnnotation(String name, String desc) {
            DependencyVisitor.this.addDesc(desc);
            return this;
        }
    }

    private class SignatureDependencyVisitor extends SignatureVisitor {
        public SignatureDependencyVisitor() {
            super(327680);
        }

        public void visitClassType(String name) {
            DependencyVisitor.this.addName(name);
        }

        public void visitInnerClassType(String name) {
            DependencyVisitor.this.addName(name);
        }
    }

    public interface InnerClassVisitor {
        void visitInnerClass(String var1, String var2, String var3, int var4);
    }
}
