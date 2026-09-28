package org.wexter.util;

public class Logger {
    public static final String RESET = "\u001B[0m";
    public static final String GREEN = "\u001B[32m";
    public static final String CYAN  = "\u001B[36m";
    public static final String RED   = "\u001B[31m";
    public static final String YELLOW= "\u001B[33m";

    public static void banner(String ver) {
        System.out.println(CYAN + """
         __      __              __                 
        /  \\    /  \\____ ___  ___/  |_  ___________ 
        \\   \\/\\/   / __ \\\\  \\/  /\\   __\\/ __ \\_  __ \\
         \\        /  ___/ >    <  |  | \\  ___/|  | \\/
          \\__/\\  / \\___  >__/\\_ \\ |__|  \\___  >__|   
               \\/      \\/      \\/           \\/        v""" + ver + "\n" + RESET);
    }

    public static void step(int cur, int total, String msg) {
        System.out.printf(CYAN + "[%d/%d] " + RESET + "%s\n", cur, total, msg);
    }

    public static void success(String msg) {
        System.out.println(GREEN + "  ✔ " + msg + RESET);
    }

    public static void info(String msg) {
        System.out.println("  ℹ " + msg);
    }

    public static void debug(String msg) {
        System.out.println("\u001B[90m   " + msg + "\u001B[0m");
    }

    public static void error(String msg) {
        System.out.println(RED + "  ✖ [ERROR] " + msg + RESET);
    }

    public static void finish(String path, long ms) {
        System.out.println("\n" + GREEN + "════════════════════════════════════════════════" + RESET);
        System.out.println(GREEN + " [✔] Успешно завершено за " + ms + " ms!" + RESET);
        System.out.println(" Выходной файл: " + YELLOW + path + RESET);
        System.out.println(GREEN + "════════════════════════════════════════════════" + RESET);
    }
}