package org.wexter.asm;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class SharpJNILoader {
    private static boolean loaded = false;

    // Единственный нативный экспорт в DLL
    public static native int registerNativesForClass(int classId, Class<?> clazz);

    public static synchronized void load() {
        if (loaded) return;

        String dllName = "WexterCore.dll";
        try {
            InputStream in = SharpJNILoader.class.getResourceAsStream("/" + dllName);
            if (in == null) {
                in = SharpJNILoader.class.getClassLoader().getResourceAsStream(dllName);
            }

            if (in == null) {
                throw new RuntimeException("Не найдена библиотека " + dllName + " внутри JAR!");
            }

            File tempDll = File.createTempFile("wexter_", ".dll");
            tempDll.deleteOnExit();

            Files.copy(in, tempDll.toPath(), StandardCopyOption.REPLACE_EXISTING);

            System.load(tempDll.getAbsolutePath());
            loaded = true;

        } catch (Exception e) {
            throw new RuntimeException("Ошибка инициализации нативного ядра: " + e.getMessage(), e);
        }
    }
}