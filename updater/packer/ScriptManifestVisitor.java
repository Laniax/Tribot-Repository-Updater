package updater.packer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jdk.internal.org.objectweb.asm.AnnotationVisitor;
import jdk.internal.org.objectweb.asm.ClassVisitor;

class ScriptManifestVisitor extends ClassVisitor {
    private final Map<String, Object> attributes = new HashMap();

    protected ScriptManifestVisitor() {
        super(327680);
    }

    public Map<String, Object> getAttributes() {
        return this.attributes;
    }

    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        return !desc.equals("Lorg/tribot/script/ScriptManifest;")?null:new AnnotationVisitor(327680) {
            public void visit(String name, Object value) {
                ScriptManifestVisitor.this.attributes.put(name, value);
                super.visit(name, value);
            }

            public AnnotationVisitor visitArray(final String atrName) {
                ScriptManifestVisitor.this.attributes.put(atrName, new ArrayList());
                return new AnnotationVisitor(327680) {
                    public void visit(String name, Object value) {
                        ((List)ScriptManifestVisitor.this.attributes.get(atrName)).add(value);
                    }
                };
            }
        };
    }
}
