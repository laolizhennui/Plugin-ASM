package net.laoli.pasm;

import com.google.common.base.Splitter;
import com.google.common.collect.Maps;
import net.laoli.pasm.agent.AgentManager;
import net.laoli.pasm.utils.PrintUtils;
import java.lang.instrument.Instrumentation;
import java.util.Map;

/**
 * PASM (Plugin ASM) - Java Agent 主入口
 * @author laoli
 */
public class PasmAgent {

    private static volatile AgentManager agentManager;

    /**
     * Java Agent 标准入口 (JVM启动时)
     */
    public static void premain(String agentArgs, Instrumentation inst) {
        try {
            // 解析Agent参数
            Map<String, String> args = parseAgentArgs(agentArgs);

            // 设置调试模式
            boolean debug = Boolean.parseBoolean(args.getOrDefault("debug", "false"));
            PrintUtils.setDebugEnabled(debug);

            if (debug) {
                PrintUtils.always("PASM Agent 启动中 (调试模式)...");
            } else {
                PrintUtils.always("PASM Agent 启动中...");
            }

            // 初始化Agent管理器（双重检查锁定模式确保线程安全）
            if (agentManager == null) {
                synchronized (PasmAgent.class) {
                    if (agentManager == null) {
                        agentManager = new AgentManager();
                        if (agentManager == null) {
                            throw new IllegalStateException("AgentManager初始化失败");
                        }
                    }
                }
            }
            if (agentManager == null) {
                throw new IllegalStateException("AgentManager未初始化");
            }
            agentManager.initialize(inst);

            PrintUtils.always("PASM Agent 启动完成");

        } catch (Throwable t) {
            PrintUtils.error("PASM Agent 启动失败: " + t.getMessage());
            t.printStackTrace();
        }
    }

    /**
     * Java Agent 动态加载入口 (JVM运行时)
     */
    public static void agentmain(String agentArgs, Instrumentation inst) {
        PrintUtils.always("PASM Agent 动态加载中...");
        premain(agentArgs, inst);
    }

    /**
     * 解析Agent参数
     * 格式: key1=value1,key2=value2 或 debug=true 或 debug
     */
    private static Map<String, String> parseAgentArgs(String agentArgs) {
        if (agentArgs == null || agentArgs.trim().isEmpty()) {
            return Maps.newHashMap();
        }
        Map<String, String> result = Maps.newHashMap();
        // 按逗号分割参数
        for (String arg : Splitter.on(',').omitEmptyStrings().trimResults().split(agentArgs)) {
            if (arg.contains("=")) {
                // 处理 key=value 格式
                String[] parts = arg.split("=", 2);
                String key = parts[0].trim().toLowerCase();
                String value = parts[1].trim();
                result.put(key, value);
            } else {
                // 处理只有键没有值的情况，默认值为true
                String key = arg.trim().toLowerCase();
                result.put(key, "true");
            }
        }
        return result;
    }

    /**
     * 重新加载插件 (外部调用接口)
     */
    public static void reloadPlugins() {
        if (agentManager != null && agentManager.isInitialized()) {
            agentManager.reload();
        } else {
            PrintUtils.warn("Agent未初始化，无法重新加载插件");
        }
    }

    /**
     * 获取Agent管理器 (用于测试和监控)
     */
    public static AgentManager getAgentManager() {
        return agentManager;
    }
}