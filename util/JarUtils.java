package org.wexter.util;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.wexter.asm.SharpJNILoader;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

public class JarUtils {

    public static Map<String, ClassNode> readJarClasses(File jarFile) throws IOException {
        Map<String, ClassNode> classes = new HashMap<>();
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".class")) {
                    try (InputStream is = jar.getInputStream(entry)) {
                        ClassReader reader = new ClassReader(is);
                        ClassNode node = new ClassNode();
                        reader.accept(node, 0);
                        classes.put(node.name, node);
                    }
                }
            }
        }
        return classes;
    }

    public static void writeJar(File outFile, Map<String, ClassNode> modifiedClasses,
                                File originalJar, Path dllPath, String dllEntryName) throws IOException {
        try (JarOutputStream zos = new JarOutputStream(new FileOutputStream(outFile));
             JarFile orig = new JarFile(originalJar)) {

            for (ClassNode node : modifiedClasses.values()) {
                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                node.accept(writer);
                zos.putNextEntry(new JarEntry(node.name + ".class"));
                zos.write(writer.toByteArray());
                zos.closeEntry();
            }

            // Вшиваем сам SharpJNILoader.class
            String loaderPath = "org/wexter/asm/SharpJNILoader.class";
            try (InputStream is = SharpJNILoader.class.getClassLoader().getResourceAsStream(loaderPath)) {
                if (is != null) {
                    zos.putNextEntry(new JarEntry(loaderPath));
                    is.transferTo(zos);
                    zos.closeEntry();
                }
            }

            // Переносим ресурсы (манифест и т.д.)
            Enumeration<JarEntry> entries = orig.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) {
                    zos.putNextEntry(new JarEntry(entry.getName()));
                    try (InputStream is = orig.getInputStream(entry)) {
                        is.transferTo(zos);
                    }
                    zos.closeEntry();
                }
            }

            // Вшиваем скомпилированную DLL
            zos.putNextEntry(new JarEntry(dllEntryName));
            Files.copy(dllPath, zos);
            zos.closeEntry();
        }
    }

    public static void extractResourceDirectory(String resourceDir, Path targetDir) throws IOException {
        ClassLoader cl = JarUtils.class.getClassLoader();
        String[] files = {"NativeAot.csproj", "JNI.cs", "StringPool.cs"};

        for (String file : files) {
            String path = resourceDir + "/" + file;
            try (InputStream is = cl.getResourceAsStream(path)) {
                if (is != null) {
                    Files.copy(is, targetDir.resolve(file), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    public static void deleteDirectory(Path path) throws IOException {
        try (var walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }
}