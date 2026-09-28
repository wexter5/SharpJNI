package org.wexter.csharp;

import org.wexter.util.Logger;

import java.nio.file.Files;
import java.nio.file.Path;

public class DotNetCompiler {
    private final Path projectDir;

    public DotNetCompiler(Path projectDir) {
        this.projectDir = projectDir;
    }

    public Path compile(String libraryName) throws Exception {
        Path outDir = projectDir.resolve("bin_out");

        System.out.println(Logger.CYAN + "--- [НАЧАЛО ВЫВОДА .NET SDK] ---" + Logger.RESET);

        ProcessBuilder pb = new ProcessBuilder(
                "dotnet", "publish",
                "-c", "Release",
                "-r", "win-x64",
                "-o", outDir.toAbsolutePath().toString(),
                "/p:PublishAot=true"
        );

        pb.directory(projectDir.toFile());
        pb.inheritIO();

        Process process = pb.start();
        int exitCode = process.waitFor();

        System.out.println(Logger.CYAN + "--- [КОНЕЦ ВЫВОДА .NET SDK] ---\n" + Logger.RESET);

        if (exitCode != 0) {
            throw new RuntimeException(".NET сборка завершилась с ошибкой! Код: " + exitCode);
        }

        Path expectedDll = outDir.resolve("NativeAot.dll");
        if (!Files.exists(expectedDll)) {
            throw new RuntimeException("Скомпилированная DLL не найдена: " + expectedDll);
        }

        return expectedDll;
    }
}