package net.laoli.pasm.agent;

import net.laoli.pasm.api.PasmAsmProcessor;
import net.laoli.pasm.model.AsmProcessorInfo;
import net.laoli.pasm.model.InjectionInfo;
import net.laoli.pasm.processor.InjectionProcessor;
import net.laoli.pasm.scanner.PluginScanner;
import net.laoli.pasm.transformer.ClassTransformer;
import net.laoli.pasm.utils.PrintUtils;

import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 极简Agent管理器
 *
 * @author laoli
 */
public class AgentManager {
    private final InjectionProcessor injectionProcessor;
    private final ClassTransformer classTransformer;
    private boolean initialized;
    private Instrumentation inst;

    public AgentManager() {
        this.injectionProcessor = InjectionProcessor.getInstance();
        this.classTransformer = new ClassTransformer(injectionProcessor);
        this.initialized = false;
    }

    /**
     * 初始化Agent
     */
    public void initialize(Instrumentation inst) {
        if (initialized) {
            PrintUtils.debug("Agent已经初始化，跳过...");
            return;
        }

        PrintUtils.separator("初始化PASM Agent");

        this.inst = inst;

        // 初始化注解处理器
        PrintUtils.info("扫描插件...");
        injectionProcessor.initialize();

        // 获取全局ASM处理器列表（已按优先级排序）
        List<AsmProcessorInfo> asmProcessors = injectionProcessor.getGlobalAsmProcessors();

        // 注册PASM转换器
        inst.addTransformer(classTransformer, true);

        invokeAsmProcessors(asmProcessors, inst);

        initialized = true;

        PrintUtils.info("PASM Agent初始化完成");

        // 打印统计信息
        printStats();
    }

    public void reload() {
        if (!initialized) {
            PrintUtils.warn("Agent未初始化，无法重新加载");
            return;
        }

        PrintUtils.separator("重新加载插件（完全热重载）");

        // 1. 重新扫描插件，更新注入信息和 ASM 处理器列表
        injectionProcessor.reload();

        // 获取最新的 ASM 处理器列表（已按优先级排序）
        List<AsmProcessorInfo> asmProcessors = injectionProcessor.getGlobalAsmProcessors();

        // 2. 找出所有受影响的类并执行重转换
        List<Class<?>> affectedClasses = findAffectedClasses();
        if (!affectedClasses.isEmpty()) {
            PrintUtils.info("发现 " + affectedClasses.size() + " 个需要重转换的类，正在执行...");
            try {
                inst.retransformClasses(affectedClasses.toArray(new Class[0]));
                PrintUtils.info("重转换完成");
            } catch (Exception e) {
                PrintUtils.error("重转换失败: " + e.getMessage());
            }
        } else {
            PrintUtils.info("没有需要重转换的类");
        }

        // 3. 执行 onReload
        invokeAsmProcessorsReload(asmProcessors, inst);

        PrintUtils.info("插件重新加载完成");
    }

    /**
     * 专门调用 reload 钩子的辅助方法（与初始化钩子分离）
     */
    private void invokeAsmProcessorsReload(List<AsmProcessorInfo> processors,
                                           Instrumentation inst) {
        for (AsmProcessorInfo info : processors) {
            String className = info.getClassName();
            try {
                Class<?> clazz = Class.forName(className, true,
                        PluginScanner.getInjectionClassLoader());
                PasmAsmProcessor processor = (PasmAsmProcessor) clazz.getDeclaredConstructor().newInstance();
                processor.onReload(inst);
                PrintUtils.debug("Invoked " +
                        "on " + className + " (priority=" + info.getPriority() + ")");
            } catch (Exception e) {
                PrintUtils.error("Failed to invoke ASM processor " + className + ": " + e.getMessage());
            }
        }
    }

    /**
     * 找出所有受插件更新影响的已加载类
     * （简单实现：遍历所有已加载类，检查是否有注入信息）
     */
    private List<Class<?>> findAffectedClasses() {
        List<Class<?>> affected = new ArrayList<>();
        if (inst == null) return affected;

        Class<?>[] allLoaded = inst.getAllLoadedClasses();
        for (Class<?> clazz : allLoaded) {
            String internalName = clazz.getName().replace('.', '/');
            if (injectionProcessor.hasInjectionsForClass(internalName)) {
                affected.add(clazz);
            }
        }
        return affected;
    }

    /**
     * 打印统计信息
     */
    private void printStats() {
        Map<String, List<InjectionInfo>> allInjections = injectionProcessor.getAllInjectionsByTarget();
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
                                     Instrumentation inst) {
        final ClassLoader classLoader = PluginScanner.getInjectionClassLoader();
        for (AsmProcessorInfo info : processors) {
            String className = info.getClassName();
            try {
                Class<?> clazz = Class.forName(className, true, classLoader);
                // 必须有无参构造器
                PasmAsmProcessor processor = (PasmAsmProcessor) clazz.getDeclaredConstructor().newInstance();
                processor.onInit(inst);
                PrintUtils.debug("Invoked " +
                        "on " + className);
            } catch (Exception e) {
                PrintUtils.error("Failed to invoke ASM processor " + className + ": " + e.getMessage());
            }
        }
    }
}