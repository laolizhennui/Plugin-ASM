package net.laoli.pasm.api;

import java.lang.instrument.Instrumentation;

public interface PasmAsmProcessor {
    default void beforeInject(Instrumentation instrumentation) {}
    default void afterInject(Instrumentation instrumentation) {}
}
