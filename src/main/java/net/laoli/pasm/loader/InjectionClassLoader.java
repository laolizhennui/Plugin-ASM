package net.laoli.pasm.loader;

import net.laoli.pasm.utils.PrintUtils;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * 统一的类加载器 - 既能加载插件类，也能看到目标类
 */
public class InjectionClassLoader extends URLClassLoader {

    private final List<String> allowedPackages = new ArrayList<>();
    private final List<String> excludedPackages = new ArrayList<>();

    public InjectionClassLoader(ClassLoader parent) {
        super(new URL[0], parent);

        // 默认允许的包
        allowedPackages.add("net.laoli.pasm.");
        allowedPackages.add("java.");
        allowedPackages.add("javax.");
    }

    /**
     * 添加插件JAR到类路径
     */
    public void addPluginJar(File jarFile) throws Exception {
        addURL(jarFile.toURI().toURL());
        PrintUtils.debug("添加插件到类路径: " + jarFile.getName());
    }

    /**
     * 添加目录到类路径
     */
    public void addDirectory(File directory) throws Exception {
        addURL(directory.toURI().toURL());
    }

    /**
     * 允许指定的包前缀
     */
    public void allowPackage(String packagePrefix) {
        allowedPackages.add(packagePrefix);
    }

    /**
     * 排除指定的包前缀
     */
    public void excludePackage(String packagePrefix) {
        excludedPackages.add(packagePrefix);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // 1. 系统类直接委托给父加载器，不尝试本地加载，不打印调试
        if (name.startsWith("java.") || name.startsWith("javax.") ||
                name.startsWith("sun.") || name.startsWith("com.sun.")) {
            return getParent().loadClass(name);
        }

        // 2. 检查是否已加载
        synchronized (getClassLoadingLock(name)) {
            Class<?> c = findLoadedClass(name);
            if (c != null) {
                if (resolve) resolveClass(c);
                return c;
            }

            // 3. 委托给父加载器（不再打印“父加载器无法加载”的调试信息）
            try {
                c = getParent().loadClass(name);
                if (resolve) resolveClass(c);
                return c;
            } catch (ClassNotFoundException ignored) {
                // 父加载器找不到，准备本地加载（不打印调试）
            }

            // 4. 本地加载
            try {
                c = findClass(name);
                if (resolve) resolveClass(c);
                return c;
            } catch (ClassNotFoundException e) {
                // 只在 DEBUG 模式下打印，且仅对非系统类打印
                if (PrintUtils.isDebugEnabled() &&
                        !name.startsWith("java.") && !name.startsWith("javax.")) {
                    PrintUtils.debug("类加载器无法找到类: " + name);
                }
                throw e;
            }
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        try {
            return super.findClass(name);
        } catch (ClassNotFoundException e) {
            PrintUtils.debug("类加载器无法找到类: " + name);
            throw e;
        }
    }
}