package org.wexter.asm;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.wexter.csharp.CSharpGenerator;
import org.wexter.util.Logger;

import java.util.HashMap;
import java.util.Map;

public class NativeTransformer {
    private final String filterPrefix;
    private final CSharpGenerator generator;
    private final Map<String, Integer> classIdMap = new HashMap<>();
    private int currentClassId = 0;

    public NativeTransformer(String filterPrefix, CSharpGenerator generator) {
        this.filterPrefix = filterPrefix;
        this.generator = generator;
    }

    public Map<String, ClassNode> transform(Map<String, ClassNode> classes) {
        for (ClassNode classNode : classes.values()) {
            if (!filterPrefix.isEmpty() && !classNode.name.startsWith(filterPrefix)) {
                continue;
            }

            if ((classNode.access & Opcodes.ACC_INTERFACE) != 0) {
                continue;
            }

            for (MethodNode method : classNode.methods) {
                // 1. Пропускаем конструкторы и статические инициализаторы
                if (method.name.equals("<init>") || method.name.equals("<clinit>")
                        || (method.access & Opcodes.ACC_ABSTRACT) != 0) {
                    continue;
                }

                // 2. ПРОПУСКАЕМ ТРИВИАЛЬНЫЕ ГЕТТЕРЫ И СЕТТЕРЫ (Главная защита от краша Fabric!)
                // Если метод просто читает поле (как getModules()) - не трогаем его!
                if (isTrivialGetterOrSetter(method)) {
                    continue;
                }

                if (!classIdMap.containsKey(classNode.name)) {
                    classIdMap.put(classNode.name, currentClassId++);
                }

                int classId = classIdMap.get(classNode.name);
                boolean isStatic = (method.access & Opcodes.ACC_STATIC) != 0;

                generator.addMethod(classId, classNode.name, method, isStatic);

                method.instructions.clear();
                if (method.tryCatchBlocks != null) method.tryCatchBlocks.clear();
                if (method.localVariables != null) method.localVariables.clear();

                method.access |= Opcodes.ACC_NATIVE;

                Logger.debug("Преобразован: " + classNode.name + "." + method.name + method.desc);
            }
        }
        return classes;
    }

    /**
     * Проверяет, является ли метод примитивным геттером (return this.field)
     * или сеттером (this.field = arg). Такие методы нельзя нативизировать в Minecraft!
     */
    private boolean isTrivialGetterOrSetter(MethodNode method) {
        // Если в методе всего 2-4 инструкции: aload_0, getfield, areturn
        if (method.instructions.size() <= 6) {
            boolean hasFieldAccess = false;
            for (int i = 0; i < method.instructions.size(); i++) {
                if (method.instructions.get(i) instanceof FieldInsnNode) {
                    hasFieldAccess = true;
                    break;
                }
            }
            if (hasFieldAccess) {
                return true; // Это простой геттер/сеттер полей, оставляем на Java!
            }
        }

        // Также не трогаем стандартные toString, hashCode, equals
        return method.name.equals("toString") || method.name.equals("hashCode") || method.name.equals("equals");
    }

    public Map<String, Integer> getClassIdMap() {
        return classIdMap;
    }
}