package org.wexter;

import org.wexter.asm.LoaderInjector;
import org.wexter.asm.NativeTransformer;
import org.wexter.csharp.CSharpGenerator;
import org.wexter.csharp.DotNetCompiler;
import org.wexter.util.JarUtils;
import org.wexter.util.Logger;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

public class Main {
    public static final String VERSION = "1.0.0";
    public static final String LIB_NAME = "WexterCore";

    public static void main(String[] args) {
        Logger.banner(VERSION);

        Scanner scanner = new Scanner(System.in);
        String inputPath;
        String outputPath;
        String filter = "";

        if (args.length >= 2) {
            inputPath = cleanPath(args[0]);
            outputPath = cleanPath(args[1]);
            if (args.length > 2) filter = args[2];
        } else {
            System.out.print(Logger.CYAN + "Введите путь до исходного файла: " + Logger.RESET);
            inputPath = cleanPath(scanner.nextLine());

            while (inputPath.isEmpty() || !new File(inputPath).exists()) {
                Logger.error("Файл не найден! Попробуйте снова.");
                System.out.print(Logger.CYAN + "Введите путь до исходного файла: " + Logger.RESET);
                inputPath = cleanPath(scanner.nextLine());
            }

            System.out.print(Logger.CYAN + "Введите путь для сохранения [Enter для <имя>-protected.jar]: " + Logger.RESET);
            String outInput = cleanPath(scanner.nextLine());
            if (outInput.isEmpty()) {
                outputPath = inputPath.replace(".jar", "-protected.jar");
            } else {
                outputPath = outInput;
            }

            System.out.print(Logger.CYAN + "Фильтр пакета (например 'org/wexter') [Enter для всех]: " + Logger.RESET);
            filter = scanner.nextLine().trim();
        }

        File inputFile = new File(inputPath);
        File outputFile = new File(outputPath);

        System.out.println();
        Logger.info("Исходный файл: " + inputFile.getAbsolutePath());
        Logger.info("Выходной файл:  " + outputFile.getAbsolutePath());
        if (!filter.isEmpty()) Logger.info("Фильтр пакета:  " + filter);
        System.out.println();

        try {
            long startTime = System.currentTimeMillis();

            Path workDir = Files.createTempDirectory("wexter_build_");
            Path csharpDir = workDir.resolve("csharp_src");
            Files.createDirectories(csharpDir);

            Logger.step(1, 5, "Извлечение рантайма C# Native AOT...");
            JarUtils.extractResourceDirectory("sources", csharpDir);

            Logger.step(2, 5, "Парсинг байткода и генерация C# методов...");
            CSharpGenerator csharpGen = new CSharpGenerator();
            NativeTransformer transformer = new NativeTransformer(filter, csharpGen);

            var classMap = JarUtils.readJarClasses(inputFile);
            var modifiedClasses = transformer.transform(classMap);

            Files.writeString(csharpDir.resolve("ExportedMethods.cs"), csharpGen.build());
            Logger.success("Сгенерировано нативных методов: " + csharpGen.getMethodCount());

            Logger.step(3, 5, "Компиляция C# Native AOT в нативную DLL...");
            DotNetCompiler compiler = new DotNetCompiler(csharpDir);
            Path compiledDll = compiler.compile(LIB_NAME);
            Logger.success("Библиотека собрана: " + compiledDll.getFileName());

            Logger.step(4, 5, "Внедрение автоматического загрузчика DLL...");
            LoaderInjector.inject(modifiedClasses, transformer.getClassIdMap());

            Logger.step(5, 5, "Сборка защищённого JAR...");
            JarUtils.writeJar(outputFile, modifiedClasses, inputFile, compiledDll, LIB_NAME + ".dll");

            JarUtils.deleteDirectory(workDir);

            long elapsed = System.currentTimeMillis() - startTime;
            Logger.finish(outputFile.getAbsolutePath(), elapsed);

        } catch (Exception e) {
            Logger.error("Критическая ошибка обфускации: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String cleanPath(String path) {
        if (path == null) return "";
        path = path.trim();
        if (path.startsWith("\"") && path.endsWith("\"") && path.length() >= 2) {
            path = path.substring(1, path.length() - 1);
        }
        return path.trim();
    }
}