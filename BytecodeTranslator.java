package org.wexter.csharp;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.*;

public class BytecodeTranslator {

    public static String translateMethod(MethodNode method, String className, Type returnType) {
        StringBuilder code = new StringBuilder();

        int maxLocals = Math.max(method.maxLocals * 2, 64);
        int maxStack = Math.max(method.maxStack * 4, 512);

        code.append("        long[] loc = new long[").append(maxLocals).append("];\n");
        code.append("        long[] stk = new long[").append(maxStack).append("];\n");
        code.append("        int sp = 0;\n\n");

        code.append("        loc[0] = (long)thiz;\n");

        Map<LabelNode, String> labelNames = new HashMap<>();
        int labelCounter = 0;
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof LabelNode ln) {
                labelNames.put(ln, "L_" + (labelCounter++));
            }
        }

        for (AbstractInsnNode insn : method.instructions) {
            int opcode = insn.getOpcode();

            if (insn instanceof LabelNode ln) {
                code.append("    ").append(labelNames.get(ln)).append(":;\n");
                continue;
            }

            if (insn instanceof LineNumberNode) {
                continue;
            }

            switch (opcode) {
                case Opcodes.ACONST_NULL -> code.append("        stk[sp++] = 0;\n");

                case Opcodes.ICONST_M1, Opcodes.ICONST_0, Opcodes.ICONST_1,
                     Opcodes.ICONST_2, Opcodes.ICONST_3, Opcodes.ICONST_4, Opcodes.ICONST_5 -> {
                    int val = opcode - Opcodes.ICONST_0;
                    code.append("        stk[sp++] = ").append(val).append(";\n");
                }
                case Opcodes.BIPUSH, Opcodes.SIPUSH -> {
                    int val = ((IntInsnNode) insn).operand;
                    code.append("        stk[sp++] = ").append(val).append(";\n");
                }
                case Opcodes.LDC -> {
                    Object cst = ((LdcInsnNode) insn).cst;
                    if (cst instanceof Integer || cst instanceof Short || cst instanceof Byte) {
                        code.append("        stk[sp++] = ").append(cst).append(";\n");
                    } else if (cst instanceof Long l) {
                        code.append("        stk[sp++] = ").append(l).append("L;\n");
                    } else if (cst instanceof Float f) {
                        int bits = Float.floatToIntBits(f);
                        code.append("        stk[sp++] = ").append(bits).append(";\n");
                    } else if (cst instanceof Double d) {
                        long bits = Double.doubleToLongBits(d);
                        code.append("        stk[sp++] = ").append(bits).append("L;\n");
                    } else if (cst instanceof String str) {
                        code.append("        stk[sp++] = (long)JNIEnv.NewStringUTF(env, \"")
                                .append(escapeString(str)).append("\").Handle;\n");
                    } else {
                        code.append("        stk[sp++] = 0;\n");
                    }
                }

                case Opcodes.ILOAD, Opcodes.ALOAD, Opcodes.LLOAD, Opcodes.FLOAD, Opcodes.DLOAD -> {
                    int var = ((VarInsnNode) insn).var;
                    code.append("        stk[sp++] = (").append(var).append(" < loc.Length) ? loc[").append(var).append("] : 0;\n");
                }
                case Opcodes.ISTORE, Opcodes.ASTORE, Opcodes.LSTORE, Opcodes.FSTORE, Opcodes.DSTORE -> {
                    int var = ((VarInsnNode) insn).var;
                    code.append("        if (sp > 0 && ").append(var).append(" < loc.Length) loc[").append(var).append("] = stk[--sp];\n");
                }
                case Opcodes.IINC -> {
                    IincInsnNode iinc = (IincInsnNode) insn;
                    code.append("        if (").append(iinc.var).append(" < loc.Length) loc[").append(iinc.var).append("] += ").append(iinc.incr).append(";\n");
                }

                case Opcodes.IADD -> code.append("        if (sp >= 2) { long b = stk[--sp]; stk[sp - 1] = (int)stk[sp - 1] + (int)b; }\n");
                case Opcodes.ISUB -> code.append("        if (sp >= 2) { long b = stk[--sp]; stk[sp - 1] = (int)stk[sp - 1] - (int)b; }\n");
                case Opcodes.IMUL -> code.append("        if (sp >= 2) { long b = stk[--sp]; stk[sp - 1] = (int)stk[sp - 1] * (int)b; }\n");
                case Opcodes.IDIV -> code.append("        if (sp >= 2) { long b = stk[--sp]; if (b != 0) stk[sp - 1] = (int)stk[sp - 1] / (int)b; }\n");

                case Opcodes.POP -> code.append("        if (sp > 0) sp--;\n");
                case Opcodes.POP2 -> code.append("        sp = Math.Max(0, sp - 2);\n");
                case Opcodes.DUP -> code.append("        if (sp > 0 && sp < stk.Length) { stk[sp] = stk[sp - 1]; sp++; }\n");
                case Opcodes.ANEWARRAY, Opcodes.NEWARRAY -> code.append("        // anewarray\n");
                case Opcodes.ATHROW -> {
                    if (returnType.getSort() == Type.VOID) code.append("        return; // athrow\n");
                    else code.append("        return default; // athrow\n");
                }

                // НАСТОЯЩИЙ GETFIELD ЧЕРЕЗ JNI
                case Opcodes.GETFIELD -> {
                    FieldInsnNode finsn = (FieldInsnNode) insn;
                    code.append("        if (sp > 0) {\n");
                    code.append("            IntPtr obj = (IntPtr)stk[sp - 1];\n");
                    code.append("            IntPtr cls = JNIEnv.FindClass(env, \"").append(finsn.owner).append("\").Handle;\n");
                    code.append("            IntPtr fid = JNIEnv.GetFieldID(env, cls, \"").append(finsn.name).append("\", \"").append(finsn.desc).append("\");\n");
                    code.append("            stk[sp - 1] = (long)JNIEnv.GetObjectField(env, obj, fid);\n");
                    code.append("        }\n");
                }

                // НАСТОЯЩИЙ PUTFIELD ЧЕРЕЗ JNI
                case Opcodes.PUTFIELD -> {
                    FieldInsnNode finsn = (FieldInsnNode) insn;
                    code.append("        if (sp >= 2) {\n");
                    code.append("            IntPtr val = (IntPtr)stk[--sp];\n");
                    code.append("            IntPtr obj = (IntPtr)stk[--sp];\n");
                    code.append("            IntPtr cls = JNIEnv.FindClass(env, \"").append(finsn.owner).append("\").Handle;\n");
                    code.append("            IntPtr fid = JNIEnv.GetFieldID(env, cls, \"").append(finsn.name).append("\", \"").append(finsn.desc).append("\");\n");
                    code.append("            JNIEnv.SetObjectField(env, obj, fid, val);\n");
                    code.append("        }\n");
                }

                // НАСТОЯЩИЙ GETSTATIC ЧЕРЕЗ JNI
                case Opcodes.GETSTATIC -> {
                    FieldInsnNode finsn = (FieldInsnNode) insn;
                    code.append("        {\n");
                    code.append("            IntPtr cls = JNIEnv.FindClass(env, \"").append(finsn.owner).append("\").Handle;\n");
                    code.append("            IntPtr fid = JNIEnv.GetStaticFieldID(env, cls, \"").append(finsn.name).append("\", \"").append(finsn.desc).append("\");\n");
                    code.append("            stk[sp++] = (long)JNIEnv.GetStaticObjectField(env, cls, fid);\n");
                    code.append("        }\n");
                }

                // НАСТОЯЩИЙ PUTSTATIC ЧЕРЕЗ JNI
                case Opcodes.PUTSTATIC -> {
                    FieldInsnNode finsn = (FieldInsnNode) insn;
                    code.append("        if (sp > 0) {\n");
                    code.append("            IntPtr val = (IntPtr)stk[--sp];\n");
                    code.append("            IntPtr cls = JNIEnv.FindClass(env, \"").append(finsn.owner).append("\").Handle;\n");
                    code.append("            IntPtr fid = JNIEnv.GetStaticFieldID(env, cls, \"").append(finsn.name).append("\", \"").append(finsn.desc).append("\");\n");
                    code.append("            JNIEnv.SetStaticObjectField(env, cls, fid, val);\n");
                    code.append("        }\n");
                }

                case Opcodes.GOTO -> {
                    JumpInsnNode jump = (JumpInsnNode) insn;
                    code.append("        goto ").append(labelNames.get(jump.label)).append(";\n");
                }
                case Opcodes.IF_ICMPGT, Opcodes.IFGT -> {
                    JumpInsnNode jump = (JumpInsnNode) insn;
                    code.append("        if (sp >= 2) { long b = stk[--sp]; long a = stk[--sp]; if ((int)a > (int)b) goto ")
                            .append(labelNames.get(jump.label)).append("; }\n");
                }
                case Opcodes.IF_ICMPLE, Opcodes.IFLE -> {
                    JumpInsnNode jump = (JumpInsnNode) insn;
                    code.append("        if (sp >= 2) { long b = stk[--sp]; long a = stk[--sp]; if ((int)a <= (int)b) goto ")
                            .append(labelNames.get(jump.label)).append("; }\n");
                }
                case Opcodes.IF_ICMPGE, Opcodes.IFGE -> {
                    JumpInsnNode jump = (JumpInsnNode) insn;
                    code.append("        if (sp >= 2) { long b = stk[--sp]; long a = stk[--sp]; if ((int)a >= (int)b) goto ")
                            .append(labelNames.get(jump.label)).append("; }\n");
                }
                case Opcodes.IF_ICMPLT, Opcodes.IFLT -> {
                    JumpInsnNode jump = (JumpInsnNode) insn;
                    code.append("        if (sp >= 2) { long b = stk[--sp]; long a = stk[--sp]; if ((int)a < (int)b) goto ")
                            .append(labelNames.get(jump.label)).append("; }\n");
                }
                case Opcodes.IF_ICMPEQ, Opcodes.IFEQ, Opcodes.IFNULL -> {
                    JumpInsnNode jump = (JumpInsnNode) insn;
                    code.append("        if (sp > 0) { long b = stk[--sp]; if (b == 0) goto ")
                            .append(labelNames.get(jump.label)).append("; }\n");
                }
                case Opcodes.IF_ICMPNE, Opcodes.IFNE, Opcodes.IFNONNULL -> {
                    JumpInsnNode jump = (JumpInsnNode) insn;
                    code.append("        if (sp > 0) { long b = stk[--sp]; if (b != 0) goto ")
                            .append(labelNames.get(jump.label)).append("; }\n");
                }

                case Opcodes.INVOKEDYNAMIC -> {
                    InvokeDynamicInsnNode idInsn = (InvokeDynamicInsnNode) insn;
                    Type indyType = Type.getMethodType(idInsn.desc);
                    Type[] args = indyType.getArgumentTypes();

                    String recipe = "";
                    if (idInsn.bsmArgs.length > 0 && idInsn.bsmArgs[0] instanceof String s) {
                        recipe = s;
                    }

                    code.append("        {\n");
                    for (int a = args.length - 1; a >= 0; a--) {
                        code.append("            long indyArg").append(a).append(" = (sp > 0) ? stk[--sp] : 0;\n");
                    }
                    code.append("            string finalStr = \"").append(escapeString(recipe)).append("\";\n");
                    for (int a = 0; a < args.length; a++) {
                        code.append("            finalStr = finalStr.Replace(\"\\u0001\", indyArg").append(a).append(".ToString());\n");
                    }
                    code.append("            if (sp < stk.Length) stk[sp++] = (long)JNIEnv.NewStringUTF(env, finalStr).Handle;\n");
                    code.append("        }\n");
                }

                case Opcodes.INVOKEVIRTUAL, Opcodes.INVOKESTATIC, Opcodes.INVOKESPECIAL, Opcodes.INVOKEINTERFACE -> {
                    MethodInsnNode minsn = (MethodInsnNode) insn;
                    Type methodType = Type.getMethodType(minsn.desc);
                    int argCount = methodType.getArgumentTypes().length;
                    boolean isStatic = minsn.getOpcode() == Opcodes.INVOKESTATIC;
                    int totalPops = argCount + (isStatic ? 0 : 1);

                    code.append("        sp = Math.Max(0, sp - ").append(totalPops).append(");\n");

                    if (methodType.getReturnType().getSort() != Type.VOID) {
                        code.append("        if (sp < stk.Length) stk[sp++] = 1; // return of ").append(minsn.name).append("\n");
                    }
                }

                case Opcodes.RETURN -> code.append("        return;\n");
                case Opcodes.IRETURN -> {
                    switch (returnType.getSort()) {
                        case Type.BOOLEAN -> code.append("        return (byte)((sp > 0) ? stk[--sp] : 0);\n");
                        case Type.BYTE -> code.append("        return (sbyte)((sp > 0) ? stk[--sp] : 0);\n");
                        case Type.CHAR -> code.append("        return (char)((sp > 0) ? stk[--sp] : 0);\n");
                        case Type.SHORT -> code.append("        return (short)((sp > 0) ? stk[--sp] : 0);\n");
                        default -> code.append("        return (int)((sp > 0) ? stk[--sp] : 0);\n");
                    }
                }
                case Opcodes.LRETURN -> code.append("        return (sp > 0) ? stk[--sp] : 0;\n");
                case Opcodes.FRETURN -> code.append("        { long v = (sp > 0) ? stk[--sp] : 0; int vi = (int)v; return *(float*)&vi; }\n");
                case Opcodes.DRETURN -> code.append("        { long v = (sp > 0) ? stk[--sp] : 0; return *(double*)&v; }\n");
                case Opcodes.ARETURN -> {
                    if (returnType.getSort() == Type.OBJECT && returnType.getInternalName().equals("java/lang/String")) {
                        code.append("        return new JString((IntPtr)((sp > 0) ? stk[--sp] : 0));\n");
                    } else {
                        code.append("        return new JObject((IntPtr)((sp > 0) ? stk[--sp] : 0));\n");
                    }
                }
            }
        }

        return code.toString();
    }

    private static String escapeString(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}