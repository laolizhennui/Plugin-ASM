package net.laoli.pasm.agent;

import net.laoli.pasm.api.PasmAsmProcessor;
import net.laoli.pasm.model.AsmProcessorInfo;
import net.laoli.pasm.processor.InjectionProcessor;
import net.laoli.pasm.scanner.PluginScanner;
import net.laoli.pasm.transformer.ClassTransformer;
import net.laoli.pasm.utils.PrintUtils;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * 极简Agent管理器
 */
public class AgentManager {

    private final InjectionProcessor injectionProcessor;
    private final ClassTransformer classTransformer;
    private boolean initialized;

    public AgentManager() {
        this.injectionProcessor = InjectionProcessor.getInstance();
        this.classTransformer = new ClassTransformer(injectionProcessor);
        this.initialized = false;
    }

    /**
     * 初始化Agent
     */
    public void initialize(Instrumentation inst, Map<String, String> args) {
        if (initialized) {
            PrintUtils.debug("Agent已经初始化，跳过...");
            return;
        }

        PrintUtils.separator("初始化PASM Agent");

        // 应用配置参数
        applyConfiguration(args);

        // 初始化注解处理器
        PrintUtils.info("扫描插件...");
        injectionProcessor.initialize();

        // 获取全局ASM处理器列表（已按优先级排序）
        List<AsmProcessorInfo> asmProcessors = injectionProcessor.getGlobalAsmProcessors();

        // 执行 beforeInject
        invokeAsmProcessors(asmProcessors, inst, true);

        // 注册PASM转换器
        inst.addTransformer(classTransformer, true);

        // 执行 afterInject
        invokeAsmProcessors(asmProcessors, inst, false);

        initialized = true;

        PrintUtils.info("PASM Agent初始化完成");

        // 打印统计信息
        printStats();
    }

    /**
     * 应用配置参数
     */
    private void applyConfiguration(Map<String, String> args) {
        // 调试模式已经在RedefineAgent中设置
        PrintUtils.debug("应用配置参数: " + args);

        // 这里可以添加更多配置参数的处理
        for (Map.Entry<String, String> entry : args.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            switch (key) {
                case "exclude":
                    classTransformer.addExcludedClass(value.replace('.', '/'));
                    PrintUtils.debug("添加排除类: " + value);
                    break;
                case "cache":
                    classTransformer.setEnableCaching(Boolean.parseBoolean(value));
                    PrintUtils.debug("设置缓存: " + value);
                    break;
                // 可以添加更多配置参数
            }
        }
    }

    /**
     * 重新加载插件
     */
    public void reload() {
        if (!initialized) {
            PrintUtils.warn("Agent未初始化，无法重新加载");
            return;
        }

        PrintUtils.separator("重新加载插件");
        injectionProcessor.reload();
        PrintUtils.info("插件重新加载完成");
    }

    /**
     * 打印统计信息
     */
    private void printStats() {
        var allInjections = injectionProcessor.getAllInjectionsByTarget();
        int totalInjections = allInjections.values().stream()
                .mapToInt(java.util.List::size)
                .sum();

        PrintUtils.separator("注入统计");
        PrintUtils.always("目标类数量: " + allInjections.size());
        PrintUtils.always("注入点总数: " + totalInjections);
        PrintUtils.separator();

        // 调试模式下打印详细信息
        if (PrintUtils.isDebugEnabled()) {
            PrintUtils.debug("详细注入信息:");
            for (Map.Entry<String, java.util.List<net.laoli.pasm.model.InjectionInfo>> entry :
                    allInjections.entrySet()) {
                PrintUtils.debug("  目标类: " + entry.getKey());
                for (net.laoli.pasm.model.InjectionInfo info : entry.getValue()) {
                    PrintUtils.debug("    -> " + info.getSourceClass() + "." +
                            info.getSourceMethod() + " (类型: " + info.getType() +
                            ", 优先级: " + info.getPriority() + ")");
                }
            }
        }
    }

    public boolean isInitialized() {
        return initialized;
    }

    private void invokeAsmProcessors(List<AsmProcessorInfo> processors,
                                     Instrumentation inst,
                                     boolean isBefore) {
        for (AsmProcessorInfo info : processors) {
            String className = info.getClassName();
            try {
                Class<?> clazz = Class.forName(className, true,
                        PluginScanner.getInjectionClassLoader());
                // 必须有无参构造器
                PasmAsmProcessor processor = (PasmAsmProcessor) clazz.getDeclaredConstructor().newInstance();
                if (isBefore) {
                    processor.beforeInject(inst);
                } else {
                    processor.afterInject(inst);
                }
                PrintUtils.debug("Invoked " + (isBefore ? "beforeInject" : "afterInject") +
                        " on " + className);
            } catch (Exception e) {
                PrintUtils.error("Failed to invoke ASM processor " + className + ": " + e.getMessage());
            }
        }
    }
}