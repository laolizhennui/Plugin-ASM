package net.laoli.pasm.api;

import java.lang.instrument.Instrumentation;

/**
 * @author laoli
 */
public interface PasmAsmProcessor {
    default void onInit(Instrumentation instrumentation) {}

    /**
     * 在每次重新加载插件（reload）时调用。
     * 注意：此方法会在每次 reload() 时执行，包括首次初始化后的第一次 reload。
     */
    default void onReload(Instrumentation instrumentation) {}
}
