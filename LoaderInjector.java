package org.wexter.asm;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.Map;

public class LoaderInjector {

    public static void inject(Map<String, ClassNode> classes, Map<String, Integer> classIdMap) {
        for (ClassNode classNode : classes.values()) {
            if (!classIdMap.containsKey(classNode.name)) continue;

            int classId = classIdMap.get(classNode.name);

            MethodNode clinit = classNode.methods.stream()
                    .filter(m -> m.name.equals("<clinit>"))
                    .findFirst()
                    .orElseGet(() -> {
                        MethodNode m = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
                        m.instructions.add(new InsnNode(Opcodes.RETURN));
                        classNode.methods.add(m);
                        return m;
                    });

            InsnList list = new InsnList();

            // SharpJNILoader.load();
            list.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    "org/wexter/asm/SharpJNILoader",
                    "load",
                    "()V",
                    false
            ));

            // SharpJNILoader.registerNativesForClass(classId, CurrentClass.class);
            list.add(new LdcInsnNode(classId));
            list.add(new LdcInsnNode(Type.getObjectType(classNode.name)));
            list.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    "org/wexter/asm/SharpJNILoader",
                    "registerNativesForClass",
                    "(ILjava/lang/Class;)I",
                    false
            ));
            list.add(new InsnNode(Opcodes.POP));

            clinit.instructions.insert(list);
        }
    }
}