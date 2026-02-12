package net.laoli.pasm;

import net.laoli.pasm.agent.AgentManager;
import net.laoli.pasm.utils.PrintUtils;
import java.lang.instrument.Instrumentation;
import java.util.HashMap;
import java.util.Map;

/**
 * PASM (Plugin ASM) - Java Agent 主入口
 */
public class RedefineAgent {

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

            // 初始化Agent管理器
            agentManager = new AgentManager();
            agentManager.initialize(inst, args);

            PrintUtils.always("PASM Agent 启动完成");

        } catch (Throwable t) {
            PrintUtils.error("PASM Agent 启动失败: " + t.getMessage());
            t.printStackTrace();
            // 不抛出异常，让应用正常启动
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
     * 格式: key1=value1,key2=value2 或 debug=true
     */
    private static Map<String, String> parseAgentArgs(String agentArgs) {
        Map<String, String> args = new HashMap<>();

        if (agentArgs == null || agentArgs.trim().isEmpty()) {
            return args;
        }

        PrintUtils.debug("原始Agent参数: " + agentArgs);

        // 处理简单格式: "debug" 或 "debug=true"
        if (agentArgs.contains("=")) {
            // 格式: key=value,key2=value2
            String[] pairs = agentArgs.split(",");
            for (String pair : pairs) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    args.put(kv[0].trim().toLowerCase(), kv[1].trim());
                } else if (kv.length == 1) {
                    // 单个值，认为是debug标志
                    args.put("debug", Boolean.toString(Boolean.parseBoolean(kv[0].trim())));
                }
            }
        } else {
            // 简单标志: "debug" 或 "true"
            boolean debug = Boolean.parseBoolean(agentArgs) ||
                    "debug".equalsIgnoreCase(agentArgs);
            args.put("debug", Boolean.toString(debug));
        }

        PrintUtils.debug("解析后的参数: " + args);
        return args;
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