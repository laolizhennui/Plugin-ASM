package net.laoli.pasm.processor;

import net.laoli.pasm.model.AsmProcessorInfo;
import net.laoli.pasm.model.InjectionInfo;
import net.laoli.pasm.scanner.PluginScanner;
import net.laoli.pasm.utils.PrintUtils;
import net.laoli.pasm.transformer.MethodCopyHelper.Pair;

import java.util.*;

/**
 * 注解处理器 - 负责协调扫描和处理过程
 * @author laoli
 */
public class InjectionProcessor {
    private List<AsmProcessorInfo> globalAsmProcessors;
    private static InjectionProcessor instance;
    private Map<String, List<InjectionInfo>> injectionsByTarget;
    private Map<String, List<InjectionInfo>> injectionsByPoint;
    private boolean initialized = false;

    private InjectionProcessor() {
        this.injectionsByTarget = new HashMap<>();
        this.injectionsByPoint = new HashMap<>();
    }

    public static synchronized InjectionProcessor getInstance() {
        if (instance == null) {
            instance = new InjectionProcessor();
        }
        return instance;
    }

    public synchronized void initialize() {
        if (initialized) {
            PrintUtils.debug("注解处理器已初始化，跳过...");
            return;
        }

        PrintUtils.debug("开始初始化注解处理器...");

        // 扫描所有插件，同时获得注入信息和ASM处理器
        Pair<List<InjectionInfo>, List<AsmProcessorInfo>> scanResult = PluginScanner.scanAllPlugins();
        List<InjectionInfo> allInjections = scanResult.getLeft();
        List<AsmProcessorInfo> allAsmProcessors = scanResult.getRight();

        // 按目标类分组注入信息
        injectionsByTarget = PluginScanner.groupByTargetClass(allInjections);
        injectionsByPoint = PluginScanner.groupByInjectionPoint(allInjections);

        // 存储全局ASM处理器
        this.globalAsmProcessors = new ArrayList<>(allAsmProcessors);

        // 打印统计信息
        printStatistics();

        initialized = true;
        PrintUtils.debug("注解处理器初始化完成");
    }

    /**
     * 重新加载插件（热部署）
     */
    public synchronized void reload() {
        PrintUtils.debug("重新加载插件...");
        initialized = false;
        injectionsByTarget.clear();
        injectionsByPoint.clear();
        if (globalAsmProcessors != null) {
            globalAsmProcessors.clear();
        }
        initialize();
    }

    /**
     * 获取指定目标类的注入信息
     */
    public List<InjectionInfo> getInjectionsForClass(String targetClassName) {
        // 转换为内部名格式
        String internalName = targetClassName.replace('.', '/');
        return injectionsByTarget.getOrDefault(internalName, Collections.emptyList());
    }

    /**
     * 获取所有注入信息（按目标类分组）
     */
    public Map<String, List<InjectionInfo>> getAllInjectionsByTarget() {
        return Collections.unmodifiableMap(injectionsByTarget);
    }

    /**
     * 检查是否有指定目标类的注入信息
     */
    public boolean hasInjectionsForClass(String targetClassName) {
        String internalName = targetClassName.replace('.', '/');
        return injectionsByTarget.containsKey(internalName);
    }

    /**
     * 打印统计信息
     */
    private void printStatistics() {
        int totalInjections = injectionsByTarget.values().stream()
                .mapToInt(List::size)
                .sum();

        PrintUtils.debug("===== 注解处理器统计信息 =====");
        PrintUtils.debug("注入点总数: " + totalInjections);
        PrintUtils.debug("目标类数量: " + injectionsByTarget.size());
        PrintUtils.debug("独立注入点: " + injectionsByPoint.size());

        // 按注入类型统计
        Map<String, Integer> typeStats = new HashMap<>();
        injectionsByTarget.values().forEach(list ->
                list.forEach(info ->
                        typeStats.merge(info.getType().name(), 1, Integer::sum)
                )
        );

        PrintUtils.debug("注入类型分布:");
        typeStats.forEach((type, count) ->
                PrintUtils.debug("  " + type + ": " + count)
        );

        PrintUtils.debug("============================");
    }

    public List<AsmProcessorInfo> getGlobalAsmProcessors() {
        return globalAsmProcessors;
    }
}