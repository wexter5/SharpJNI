package org.wexter.csharp;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;

public class CSharpGenerator {
    private final StringBuilder methodsCode = new StringBuilder();
    private final Map<Integer, List<RegisteredMethod>> classMethods = new LinkedHashMap<>();
    private int methodCounter = 0;

    public record RegisteredMethod(String internalName, String javaName, String desc, Type returnType, Type[] argTypes) {}

    public void addMethod(int classId, String className, MethodNode methodNode, boolean isStatic) {
        Type methodType = Type.getMethodType(methodNode.desc);
        Type returnType = methodType.getReturnType();
        Type[] argumentTypes = methodType.getArgumentTypes();

        String internalFuncName = "Method_" + (methodCounter++);
        String csharpReturnType = TypeMapper.toCSharpType(returnType);

        StringBuilder args = new StringBuilder("JNIEnv* env, IntPtr thiz");
        for (int i = 0; i < argumentTypes.length; i++) {
            args.append(", ")
                    .append(TypeMapper.toCSharpType(argumentTypes[i]))
                    .append(" arg").append(i);
        }

        methodsCode.append("    [UnmanagedCallersOnly(CallConvs = new[] { typeof(System.Runtime.CompilerServices.CallConvCdecl) })]\n");
        methodsCode.append("    private static ").append(csharpReturnType).append(" ").append(internalFuncName)
                .append("(").append(args).append(")\n");
        methodsCode.append("    {\n");

        // Передаем returnType для правильного приведения типов
        String body = BytecodeTranslator.translateMethod(methodNode, className, returnType);
        methodsCode.append(body);

        // Предохранитель от CS0161 (не все пути возвращают значение)
        if (returnType.getSort() != Type.VOID) {
            methodsCode.append("        return default;\n");
        }

        methodsCode.append("    }\n\n");

        classMethods.computeIfAbsent(classId, k -> new ArrayList<>())
                .add(new RegisteredMethod(internalFuncName, methodNode.name, methodNode.desc, returnType, argumentTypes));
    }

    public int getMethodCount() {
        return methodCounter;
    }

    public String build() {
        StringBuilder registerSwitch = new StringBuilder();

        for (var entry : classMethods.entrySet()) {
            int classId = entry.getKey();
            List<RegisteredMethod> list = entry.getValue();

            registerSwitch.append("            case ").append(classId).append(":\n");
            registerSwitch.append("            {\n");
            registerSwitch.append("                JNINativeMethod* methods = stackalloc JNINativeMethod[").append(list.size()).append("];\n");

            for (int i = 0; i < list.size(); i++) {
                RegisteredMethod m = list.get(i);
                registerSwitch.append("                byte[] name_").append(i).append(" = Encoding.UTF8.GetBytes(\"").append(m.javaName).append("\\0\");\n");
                registerSwitch.append("                byte[] sig_").append(i).append(" = Encoding.UTF8.GetBytes(\"").append(m.desc).append("\\0\");\n");
                registerSwitch.append("                fixed (byte* pName = name_").append(i).append(")\n");
                registerSwitch.append("                fixed (byte* pSig = sig_").append(i).append(")\n");
                registerSwitch.append("                {\n");
                registerSwitch.append("                    methods[").append(i).append("].Name = pName;\n");
                registerSwitch.append("                    methods[").append(i).append("].Signature = pSig;\n");
                registerSwitch.append("                    delegate* unmanaged[Cdecl]<")
                        .append(buildDelegateSignature(m))
                        .append("> fn_").append(i).append(" = &").append(m.internalName).append(";\n");
                registerSwitch.append("                    methods[").append(i).append("].FnPtr = (void*)fn_").append(i).append(";\n");
                registerSwitch.append("                }\n");
            }

            registerSwitch.append("                return JNIEnv.RegisterNatives(env, new JClass(targetClass), methods, ").append(list.size()).append(");\n");
            registerSwitch.append("            }\n");
        }

        return """
            using System;
            using System.Runtime.InteropServices;
            using System.Text;
            using DotAot.Runtime;

            namespace DotAot.Generated;

            public static unsafe class ExportedMethods
            {
            """ + methodsCode + """
                [UnmanagedCallersOnly(EntryPoint = "Java_org_wexter_asm_SharpJNILoader_registerNativesForClass")]
                public static int RegisterClassNatives(JNIEnv* env, IntPtr thiz, int classId, IntPtr targetClass)
                {
                    switch (classId)
                    {
            """ + registerSwitch + """
                        default:
                            return -1;
                    }
                }
            }
            """;
    }

    private String buildDelegateSignature(RegisteredMethod m) {
        StringBuilder sb = new StringBuilder("JNIEnv*, IntPtr");
        for (Type arg : m.argTypes) {
            sb.append(", ").append(TypeMapper.toCSharpType(arg));
        }
        sb.append(", ").append(TypeMapper.toCSharpType(m.returnType));
        return sb.toString();
    }
}