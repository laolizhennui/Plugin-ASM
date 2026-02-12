package net.laoli.pasm.annotation;

public enum InjectionType {
    BEFORE,      // 方法开始处注入
    AFTER,       // 方法返回前注入
    REPLACE,     // 替换整个方法
    AROUND,      // 环绕方法（替换+调用原方法）
    HEAD,        // 在方法头部注入（位于参数之后，第一条指令之前）
    TAIL         // 在方法尾部注入（所有return之前）
}
