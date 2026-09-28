package org.wexter.csharp;

import org.objectweb.asm.Type;

public class TypeMapper {

    public static String toCSharpType(Type type) {
        return switch (type.getSort()) {
            case Type.VOID -> "void";
            case Type.BOOLEAN -> "byte";
            case Type.BYTE -> "sbyte";
            case Type.CHAR -> "char";
            case Type.SHORT -> "short";
            case Type.INT -> "int";
            case Type.LONG -> "long";
            case Type.FLOAT -> "float";
            case Type.DOUBLE -> "double";
            case Type.OBJECT -> {
                if (type.getInternalName().equals("java/lang/String")) {
                    yield "JString";
                }
                yield "JObject";
            }
            case Type.ARRAY -> "JObject";
            default -> "IntPtr";
        };
    }
}