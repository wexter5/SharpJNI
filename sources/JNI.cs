using System;
using System.Runtime.InteropServices;
using System.Text;

namespace DotAot.Runtime;

public readonly record struct JObject(IntPtr Handle)
{
    public static implicit operator JObject(long val) => new JObject((IntPtr)val);
    public static implicit operator JObject(IntPtr val) => new JObject(val);
}

public readonly record struct JClass(IntPtr Handle)
{
    public static implicit operator JClass(long val) => new JClass((IntPtr)val);
    public static implicit operator JClass(IntPtr val) => new JClass(val);
}

public readonly record struct JString(IntPtr Handle)
{
    public static implicit operator JString(long val) => new JString((IntPtr)val);
    public static implicit operator JString(IntPtr val) => new JString(val);
}

public readonly record struct JMethodID(IntPtr Handle);
public readonly record struct JFieldID(IntPtr Handle);

[StructLayout(LayoutKind.Explicit)]
public struct JValue
{
    [FieldOffset(0)] public byte z;   // boolean
    [FieldOffset(0)] public sbyte b;  // byte
    [FieldOffset(0)] public char c;   // char
    [FieldOffset(0)] public short s;  // short
    [FieldOffset(0)] public int i;    // int
    [FieldOffset(0)] public long j;   // long
    [FieldOffset(0)] public float f;  // float
    [FieldOffset(0)] public double d; // double
    [FieldOffset(0)] public IntPtr l; // jobject / JString / JClass
}

[StructLayout(LayoutKind.Sequential)]
public unsafe struct JNINativeMethod
{
    public byte* Name;
    public byte* Signature;
    public void* FnPtr;
}

public unsafe struct JNIEnv
{
    // Таблица функций JVM (VTable)
    public void** Functions;

    // Глобальная ссылка на Fabric KnotClassLoader
    public static IntPtr CachedClassLoader = IntPtr.Zero;
    private static IntPtr loadClassMethodId = IntPtr.Zero;

    // Индекс 17: ExceptionClear
    public static void ExceptionClear(JNIEnv* env)
    {
        var fn = (delegate* unmanaged[Cdecl]<JNIEnv*, void>)env->Functions[17];
        fn(env);
    }

    // Индекс 6: FindClass (с поддержкой Fabric / Minecraft ClassLoader)
    public static JClass FindClass(JNIEnv* env, string name)
    {
        // 1. Пробуем стандартный JNI поиск
        var fn = (delegate* unmanaged[Cdecl]<JNIEnv*, byte*, JClass>)env->Functions[6];
        JClass res;
        fixed (byte* namePtr = Encoding.UTF8.GetBytes(name + '\0'))
        {
            res = fn(env, namePtr);
        }

        if (res.Handle != IntPtr.Zero) return res;

        // Если не найден — сбрасываем ошибку ClassNotFoundException
        ExceptionClear(env);

        // 2. Ищем через сохранённый KnotClassLoader мода
        if (CachedClassLoader != IntPtr.Zero)
        {
            string dotName = name.Replace('/', '.');
            JString jName = NewStringUTF(env, dotName);

            if (loadClassMethodId == IntPtr.Zero)
            {
                JClass clClass = FindClass(env, "java/lang/ClassLoader");
                loadClassMethodId = GetMethodID(env, clClass.Handle, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
            }

            // Индекс 34: CallObjectMethod(env, cl, mid, arg)
            var callObjFn = (delegate* unmanaged[Cdecl]<JNIEnv*, IntPtr, IntPtr, IntPtr, IntPtr>)env->Functions[34];
            IntPtr loadedCls = callObjFn(env, CachedClassLoader, loadClassMethodId, jName.Handle);

            if (loadedCls != IntPtr.Zero)
            {
                return new JClass(loadedCls);
            }
            ExceptionClear(env);
        }

        return new JClass(IntPtr.Zero);
    }

    // Индекс 14: ThrowNew
    public static void ThrowNew(JNIEnv* env, string className, string message)
    {
        var findClassFn = (delegate* unmanaged[Cdecl]<JNIEnv*, byte*, JClass>)env->Functions[6];
        var throwNewFn = (delegate* unmanaged[Cdecl]<JNIEnv*, JClass, byte*, int>)env->Functions[14];

        fixed (byte* namePtr = Encoding.UTF8.GetBytes(className + '\0'))
        fixed (byte* msgPtr = Encoding.UTF8.GetBytes(message + '\0'))
        {
            JClass clazz = findClassFn(env, namePtr);
            throwNewFn(env, clazz, msgPtr);
        }
    }

    // Индекс 33: GetMethodID
    public static IntPtr GetMethodID(JNIEnv* env, IntPtr clazz, string name, string sig)
    {
        if (clazz == IntPtr.Zero) return IntPtr.Zero;
        var fn = (delegate* unmanaged[Cdecl]<JNIEnv*, IntPtr, byte*, byte*, IntPtr>)env->Functions[33];
        fixed (byte* pName = Encoding.UTF8.GetBytes(name + '\0'))
        fixed (byte* pSig = Encoding.UTF8.GetBytes(sig + '\0'))
        {
            return fn(env, clazz, pName, pSig);
        }
    }

    // Индекс 94: GetFieldID
    public static IntPtr GetFieldID(JNIEnv* env, IntPtr clazz, string name, string sig)
    {
        if (clazz == IntPtr.Zero) return IntPtr.Zero;
        var fn = (delegate* unmanaged[Cdecl]<JNIEnv*, IntPtr, byte*, byte*, IntPtr>)env->Functions[94];
        fixed (byte* pName = Encoding.UTF8.GetBytes(name + '\0'))
        fixed (byte* pSig = Encoding.UTF8.GetBytes(sig + '\0'))
        {
            return fn(env, clazz, pName, pSig);
        }
    }

    // Индекс 95: GetObjectField
    public static IntPtr GetObjectField(JNIEnv* env, IntPtr obj, IntPtr fieldId)
    {
        if (obj == IntPtr.Zero || fieldId == IntPtr.Zero) return IntPtr.Zero;
        var fn = (delegate* unmanaged[Cdecl]<JNIEnv*, IntPtr, IntPtr, IntPtr>)env->Functions[95];
        return fn(env, obj, fieldId);
    }

    // Индекс 104: SetObjectField (с защитой кучи GC от порчи фейковыми указателями!)
    public static void SetObjectField(JNIEnv* env, IntPtr obj, IntPtr fieldId, IntPtr val)
    {
        if (obj == IntPtr.Zero || fieldId == IntPtr.Zero || val == (IntPtr)1) return;
        var fn = (delegate* unmanaged[Cdecl]<JNIEnv*, IntPtr, IntPtr, IntPtr, void>)env->Functions[104];
        fn(env, obj, fieldId, val);
    }

    // Индекс 144: GetStaticFieldID
    public static IntPtr GetStaticFieldID(JNIEnv* env, IntPtr clazz, string name, string sig)
    {
        if (clazz == IntPtr.Zero) return IntPtr.Zero;
        var fn = (delegate* unmanaged[Cdecl]<JNIEnv*, IntPtr, byte*, byte*, IntPtr>)env->Functions[144];
        fixed (byte* pName = Encoding.UTF8.GetBytes(name + '\0'))
        fixed (byte* pSig = Encoding.UTF8.GetBytes(sig + '\0'))
        {
            return fn(env, clazz, pName, pSig);
        }
    }

    // Индекс 145: GetStaticObjectField
    public static IntPtr GetStaticObjectField(JNIEnv* env, IntPtr clazz, IntPtr fieldId)
    {
        if (clazz == IntPtr.Zero || fieldId == IntPtr.Zero) return IntPtr.Zero;
        var fn = (delegate* unmanaged[Cdecl]<JNIEnv*, IntPtr, IntPtr, IntPtr>)env->Functions[145];
        return fn(env, clazz, fieldId);
    }

    // Индекс 154: SetStaticObjectField (с защитой кучи GC)
    public static void SetStaticObjectField(JNIEnv* env, IntPtr clazz, IntPtr fieldId, IntPtr val)
    {
        if (clazz == IntPtr.Zero || fieldId == IntPtr.Zero || val == (IntPtr)1) return;
        var fn = (delegate* unmanaged[Cdecl]<JNIEnv*, IntPtr, IntPtr, IntPtr, void>)env->Functions[154];
        fn(env, clazz, fieldId, val);
    }

    // Индекс 167: NewStringUTF
    public static JString NewStringUTF(JNIEnv* env, string value)
    {
        var fn = (delegate* unmanaged[Cdecl]<JNIEnv*, byte*, JString>)env->Functions[167];
        fixed (byte* strPtr = Encoding.UTF8.GetBytes(value + '\0'))
        {
            return fn(env, strPtr);
        }
    }

    // Индексы 169 и 170: GetStringUTFChars / ReleaseStringUTFChars
    public static string? GetStringUTF(JNIEnv* env, JString jstr)
    {
        if (jstr.Handle == IntPtr.Zero) return null;
        var getFn = (delegate* unmanaged[Cdecl]<JNIEnv*, JString, byte*, byte*>)env->Functions[169];
        var relFn = (delegate* unmanaged[Cdecl]<JNIEnv*, JString, byte*, void>)env->Functions[170];

        byte isCopy;
        byte* ptr = getFn(env, jstr, &isCopy);
        if (ptr == null) return null;
        string res = Marshal.PtrToStringUTF8((IntPtr)ptr) ?? string.Empty;
        relFn(env, jstr, ptr);
        return res;
    }

    // Индекс 215: RegisterNatives (с перехватом Fabric KnotClassLoader)
    public static int RegisterNatives(JNIEnv* env, JClass clazz, JNINativeMethod* methods, int nMethods)
    {
        if (CachedClassLoader == IntPtr.Zero && clazz.Handle != IntPtr.Zero)
        {
            var getMethodIdFn = (delegate* unmanaged[Cdecl]<JNIEnv*, IntPtr, byte*, byte*, IntPtr>)env->Functions[33];
            var callObjectMethodFn = (delegate* unmanaged[Cdecl]<JNIEnv*, IntPtr, IntPtr, IntPtr>)env->Functions[34];
            var newGlobalRefFn = (delegate* unmanaged[Cdecl]<JNIEnv*, IntPtr, IntPtr>)env->Functions[21];

            byte[] mName = Encoding.UTF8.GetBytes("getClassLoader\0");
            byte[] mSig = Encoding.UTF8.GetBytes("()Ljava/lang/ClassLoader;\0");

            fixed (byte* pName = mName)
            fixed (byte* pSig = mSig)
            {
                JClass classClass = FindClass(env, "java/lang/Class");
                IntPtr mid = getMethodIdFn(env, classClass.Handle, pName, pSig);
                if (mid != IntPtr.Zero)
                {
                    IntPtr cl = callObjectMethodFn(env, clazz.Handle, mid);
                    if (cl != IntPtr.Zero)
                    {
                        CachedClassLoader = newGlobalRefFn(env, cl);
                    }
                }
            }
            ExceptionClear(env);
        }

        var fn = (delegate* unmanaged[Cdecl]<JNIEnv*, JClass, JNINativeMethod*, int, int>)env->Functions[215];
        return fn(env, clazz, methods, nMethods);
    }
}