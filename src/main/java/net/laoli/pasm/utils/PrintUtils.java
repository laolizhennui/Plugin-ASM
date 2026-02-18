package net.laoli.pasm.utils;

/**
 * 日志工具类 - 支持不同日志级别
 *
 * @author laoli
 */
public class PrintUtils {
    private PrintUtils() {
    }

    // 日志级别
    public enum Level {
        DEBUG,   // 调试信息
        INFO,    // 一般信息
        WARN,    // 警告信息
        ERROR    // 错误信息
    }

    // 当前日志级别（默认为INFO）
    private static Level currentLevel = Level.INFO;
    // 是否启用调试模式
    private static boolean debugEnabled = false;

    /**
     * 设置调试模式
     */
    public static void setDebugEnabled(boolean enabled) {
        debugEnabled = enabled;
        currentLevel = enabled ? Level.DEBUG : Level.INFO;
    }

    /**
     * 是否启用了调试模式
     */
    public static boolean isDebugEnabled() {
        return debugEnabled;
    }

    /**
     * 调试日志（只在调试模式下输出）
     */
    public static void debug(Object o) {
        if (debugEnabled) {
            System.out.println("[PASM DEBUG] " + o);
        }
    }

    /**
     * 一般信息日志（默认输出）
     */
    public static void info(Object o) {
        if (currentLevel.ordinal() <= Level.INFO.ordinal()) {
            System.out.println("[PASM] " + o);
        }
    }

    /**
     * 警告日志
     */
    public static void warn(Object o) {
        if (currentLevel.ordinal() <= Level.WARN.ordinal()) {
            System.out.println("[PASM WARN] " + o);
        }
    }

    /**
     * 错误日志
     */
    public static void error(Object o) {
        if (currentLevel.ordinal() <= Level.ERROR.ordinal()) {
            System.err.println("[PASM ERROR] " + o);
        }
    }

    /**
     * 始终输出的日志（用于关键信息）
     */
    public static void always(Object o) {
        System.out.println("[PASM] " + o);
    }

    /**
     * 打印分隔线
     */
    public static void separator() {
        if (currentLevel.ordinal() <= Level.INFO.ordinal()) {
            System.out.println("========================================");
        }
    }

    /**
     * 打印带标题的分隔线
     */
    public static void separator(String title) {
        if (currentLevel.ordinal() <= Level.INFO.ordinal()) {
            System.out.println("\n=== " + title + " ===");
        }
    }
}