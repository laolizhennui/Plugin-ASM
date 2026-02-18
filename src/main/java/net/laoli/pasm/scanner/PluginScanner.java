package net.laoli.pasm.scanner;

import com.google.common.io.ByteStreams;
import net.laoli.pasm.loader.InjectionClassLoader;
import net.laoli.pasm.model.AsmProcessorInfo;
import net.laoli.pasm.model.InjectionInfo;
import net.laoli.pasm.transformer.MethodCopyHelper.Pair;
import net.laoli.pasm.utils.PrintUtils;
import com.google.gson.*;

import java.io.File;
import java.io.InputStream;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static net.laoli.pasm.scanner.ClassScanner.scanClass;

/**
 * 插件扫描器 - 扫描plugins目录下的jar包，读取pasm.json配置
 * 使用ASM进行无加载解析，避免反射加载类
 */
public class PluginScanner {
    private static InjectionClassLoader injectionClassLoader;
    private static String PLUGINS_DIR = "./plugins"; // 默认值
    private static final String CONFIG_FILE = "pasm.json";
    private static final String AGENT_CONFIG_FILE = "pasm.json";
    static final String PASM_ANNOTATION_DESC = "Lnet/laoli/pasm/annotation/Pasm;";
    static final String INJECT_ANNOTATION_DESC = "Lnet/laoli/pasm/annotation/Inject;";

    // 静态初始化块，读取JavaAgent同级目录下的pasm.json配置文件
    static {
        try {
            // 获取当前类的位置，即JavaAgent的位置
            String agentPath = PluginScanner.class.getProtectionDomain().getCodeSource().getLocation().getPath();
            File agentFile = new File(agentPath);
            File agentDir = agentFile.getParentFile();
            
            // 读取JavaAgent同级目录下的pasm.json配置文件
            File agentConfigFile = new File(agentDir, AGENT_CONFIG_FILE);
            if (agentConfigFile.exists() && agentConfigFile.isFile()) {
                PrintUtils.debug("读取JavaAgent配置文件: " + agentConfigFile.getAbsolutePath());
                
                // 读取配置文件内容
                String configContent = new String(java.nio.file.Files.readAllBytes(agentConfigFile.toPath()));
                JsonElement jsonElement = JsonParser.parseString(configContent);
                
                if (jsonElement.isJsonObject()) {
                    JsonObject config = jsonElement.getAsJsonObject();
                    // 从配置文件中读取插件目录
                    if (config.has("pluginsDir")) {
                        JsonElement pluginsDirElement = config.get("pluginsDir");
                        if (pluginsDirElement.isJsonPrimitive() && pluginsDirElement.getAsJsonPrimitive().isString()) {
                            String pluginsDir = pluginsDirElement.getAsString().trim();
                            if (!pluginsDir.isEmpty()) {
                                PLUGINS_DIR = pluginsDir;
                                PrintUtils.debug("从配置文件读取插件目录: " + PLUGINS_DIR);
                            }
                        }
                    }
                }
            } else {
                PrintUtils.debug("未找到JavaAgent配置文件: " + agentConfigFile.getAbsolutePath());
                // 自动生成配置文件
                generateDefaultConfigFile(agentConfigFile);
            }
        } catch (Exception e) {
            PrintUtils.warn("读取JavaAgent配置文件失败: " + e.getMessage());
        }

    }

    /**
     * 生成默认的配置文件
     */
    private static void generateDefaultConfigFile(File configFile) {
        try {
            // 创建默认配置对象
            JsonObject defaultConfig = new JsonObject();
            defaultConfig.addProperty("pluginsDir", "./plugins");
            
            // 生成格式化的JSON字符串
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String jsonContent = gson.toJson(defaultConfig);
            
            // 写入配置文件
            java.nio.file.Files.write(configFile.toPath(), jsonContent.getBytes());
            
            PrintUtils.info("自动生成默认配置文件: " + configFile.getAbsolutePath());
            PrintUtils.info("默认插件目录设置为: ./plugins");
        } catch (Exception e) {
            PrintUtils.error("生成默认配置文件失败: " + e.getMessage());
        }
    }

    /**
     * 按目标类分组注入信息
     */
    public static Map<String, List<InjectionInfo>> groupByTargetClass(List<InjectionInfo> injections) {
        Map<String, List<InjectionInfo>> grouped = new HashMap<>();

        for (InjectionInfo info : injections) {
            if (!info.isValid()) {
                continue;
            }

            String targetClass = info.getTargetClass();
            grouped.computeIfAbsent(targetClass, k -> new ArrayList<>())
                    .add(info);
        }

        // 按注入类型和优先级排序每个组内的注入信息
        for (List<InjectionInfo> list : grouped.values()) {
            // 先按注入类型排序
            // 再按优先级排序（数值越小优先级越高）
            list.sort(Comparator.comparing(InjectionInfo::getType).thenComparingInt(InjectionInfo::getPriority));
        }

        return grouped;
    }

    /**
     * 按注入点分组注入信息
     */
    public static Map<String, List<InjectionInfo>> groupByInjectionPoint(List<InjectionInfo> injections) {
        Map<String, List<InjectionInfo>> grouped = new HashMap<>();

        for (InjectionInfo info : injections) {
            if (!info.isValid()) {
                continue;
            }

            String groupKey = getInjectionGroupKey(info);
            grouped.computeIfAbsent(groupKey, k -> new ArrayList<>())
                    .add(info);
        }

        // 按注入类型和优先级排序每个组内的注入信息
        for (List<InjectionInfo> list : grouped.values()) {
            list.sort(Comparator.comparing(InjectionInfo::getType).thenComparingInt(InjectionInfo::getPriority));
        }

        return grouped;
    }

    /**
     * 获取注入点的分组键
     */
    private static String getInjectionGroupKey(InjectionInfo info) {
        return String.format("%s.%s%s",
                info.getTargetClass(),
                info.getTargetMethod(),
                info.getTargetDesc());
    }

    public static void initializeClassLoader() {
        if (injectionClassLoader == null) {
            injectionClassLoader = new InjectionClassLoader(
                    Thread.currentThread().getContextClassLoader()
            );

            PrintUtils.debug("初始化统一类加载器完成");
        }
    }

    /**
     * 获取统一类加载器
     */
    public static InjectionClassLoader getInjectionClassLoader() {
        if (injectionClassLoader == null) {
            initializeClassLoader();
        }
        return injectionClassLoader;
    }

    /**
     * 扫描所有插件，返回所有注入信息和所有ASM处理器
     */
    public static Pair<List<InjectionInfo>, List<AsmProcessorInfo>> scanAllPlugins() {
        List<InjectionInfo> allInjections = new ArrayList<>();
        List<AsmProcessorInfo> allAsmProcessors = new ArrayList<>();
        File pluginsDir = new File(PLUGINS_DIR);

        if (!pluginsDir.exists() || !pluginsDir.isDirectory()) {
            PrintUtils.warn("插件目录不存在: " + PLUGINS_DIR);
            return Pair.create(allInjections, allAsmProcessors);
        }

        File[] jarFiles = pluginsDir.listFiles((dir, name) ->
                name.toLowerCase().endsWith(".jar"));

        if (jarFiles == null || jarFiles.length == 0) {
            PrintUtils.info("未找到插件jar文件");
            return Pair.create(allInjections, allAsmProcessors);
        }

        PrintUtils.debug("开始扫描 " + jarFiles.length + " 个插件...");

        for (File jarFile : jarFiles) {
            try {
                Pair<List<InjectionInfo>, List<AsmProcessorInfo>> pluginConfig = scanPlugin(jarFile);
                allInjections.addAll(pluginConfig.getLeft());
                allAsmProcessors.addAll(pluginConfig.getRight());

                if (!pluginConfig.getLeft().isEmpty() || !pluginConfig.getRight().isEmpty()) {
                    PrintUtils.info("插件扫描完成: " + jarFile.getName() +
                            "，找到 " + pluginConfig.getLeft().size() + " 个注入点, " +
                            pluginConfig.getRight().size() + " 个ASM处理器");
                } else {
                    PrintUtils.debug("插件扫描完成: " + jarFile.getName() + "，未找到任何配置");
                }
            } catch (Exception e) {
                PrintUtils.error("扫描插件失败: " + jarFile.getName() + " - " + e.getMessage());
            }
        }

        // 验证所有注入信息
        AnnotationScanner.validateInjections(allInjections);

        // 按优先级排序全局 ASM 处理器
        allAsmProcessors.sort(Comparator.comparingInt(AsmProcessorInfo::getPriority)
                .thenComparing(AsmProcessorInfo::getClassName));

        if (!allInjections.isEmpty()) {
            PrintUtils.always("总共发现 " + allInjections.size() + " 个注入点");
        }
        if (!allAsmProcessors.isEmpty()) {
            PrintUtils.always("总共发现 " + allAsmProcessors.size() + " 个ASM处理器");
        }

        return Pair.create(allInjections, allAsmProcessors);
    }

    /**
     * 扫描单个插件jar包
     */
    private static Pair<List<InjectionInfo>, List<AsmProcessorInfo>> scanPlugin(File jarFile) throws Exception {
        List<InjectionInfo> injections = new ArrayList<>();
        List<AsmProcessorInfo> asmProcessors = new ArrayList<>();

        try (JarFile jar = new JarFile(jarFile)) {
            // 将插件JAR添加到类加载器
            getInjectionClassLoader().addPluginJar(jarFile);

            // 读取配置
            JarEntry configEntry = jar.getJarEntry(CONFIG_FILE);

            if (configEntry == null) {
                return Pair.create(injections, asmProcessors);
            }

            // 读取配置
            try (InputStream inputStream = jar.getInputStream(configEntry)) {
                String configContent = new String(ByteStreams.toByteArray(inputStream));
                JsonElement jsonElement = JsonParser.parseString(configContent);
                
                if (!jsonElement.isJsonObject()) {
                    PrintUtils.warn("配置文件格式错误: 根元素必须是JSON对象");
                    return Pair.create(injections, asmProcessors);
                }
                
                JsonObject config = jsonElement.getAsJsonObject();

                if (config.has("pasms")) {
                    JsonElement pasmsElement = config.get("pasms");
                    if (pasmsElement.isJsonArray()) {
                        JsonArray pasmsArray = pasmsElement.getAsJsonArray();
                        List<String> classesToScan = new ArrayList<>();

                        // 解析类名
                        for (JsonElement element : pasmsArray) {
                            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                                String[] classNames = element.getAsString().split(",\\s*");
                                for (String className : classNames) {
                                    if (!className.trim().isEmpty()) {
                                        classesToScan.add(className.trim());
                                    }
                                }
                            } else {
                                PrintUtils.warn("pasms数组元素必须是字符串");
                            }
                        }

                        for (String className : classesToScan) {
                            String classFilePath = className.replace('.', '/') + ".class";
                            JarEntry classEntry = jar.getJarEntry(classFilePath);
                            if (classEntry == null) {
                                PrintUtils.warn("类文件不存在: " + classFilePath);
                                continue;
                            }
                            try (InputStream classInputStream = jar.getInputStream(classEntry)) {
                                byte[] classBytes = ByteStreams.toByteArray(classInputStream);
                                List<InjectionInfo> classInjections = scanClass(classBytes);
                                injections.addAll(classInjections);
                            } catch (Exception e) {
                                PrintUtils.warn("扫描类失败: " + className + " - " + e.getMessage());
                            }
                        }
                    } else {
                        PrintUtils.warn("pasms必须是JSON数组");
                    }
                }

                if (config.has("asms")) {
                    JsonElement asmsElement = config.get("asms");
                    if (asmsElement.isJsonArray()) {
                        JsonArray asmsArray = asmsElement.getAsJsonArray();
                        for (JsonElement elem : asmsArray) {
                            if (elem.isJsonPrimitive() && elem.getAsJsonPrimitive().isString()) {
                                // 格式1：纯字符串 -> 默认优先级 1000
                                String className = elem.getAsString().trim();
                                if (!className.isEmpty()) {
                                    asmProcessors.add(new AsmProcessorInfo(className, 1000));
                                }
                            } else if (elem.isJsonObject()) {
                                // 格式2：对象
                                JsonObject obj = elem.getAsJsonObject();
                                if (obj.has("class")) {
                                    JsonElement classElement = obj.get("class");
                                    if (classElement.isJsonPrimitive() && classElement.getAsJsonPrimitive().isString()) {
                                        String className = classElement.getAsString().trim();
                                        int priority = 1000;
                                        if (obj.has("priority")) {
                                            JsonElement priorityElement = obj.get("priority");
                                            if (priorityElement.isJsonPrimitive() && priorityElement.getAsJsonPrimitive().isNumber()) {
                                                priority = priorityElement.getAsInt();
                                            } else {
                                                PrintUtils.warn("priority必须是数字");
                                            }
                                        }
                                        if (!className.isEmpty()) {
                                            asmProcessors.add(new AsmProcessorInfo(className, priority));
                                        }
                                    } else {
                                        PrintUtils.warn("class必须是字符串");
                                    }
                                } else {
                                    PrintUtils.warn("ASM处理器配置缺少class字段");
                                }
                            } else {
                                PrintUtils.warn("asms数组元素必须是字符串或对象");
                            }
                        }
                    } else {
                        PrintUtils.warn("asms必须是JSON数组");
                    }
                }
            } catch (JsonSyntaxException e) {
                PrintUtils.warn("配置文件语法错误: " + e.getMessage());
            }
        }

        return Pair.create(injections, asmProcessors);
    }
}