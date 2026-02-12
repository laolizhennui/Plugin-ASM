package net.laoli.pasm.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 方法级注解 - 指定注入细节
 * @author laoli
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Inject {
    /**
     * 目标方法名
     */
    String name();

    /**
     * 目标方法描述符
     */
    String desc();

    /**
     * 注入类型
     */
    InjectionType type() default InjectionType.REPLACE;

    /**
     * 优先级（数值越小优先级越高）
     */
    int priority() default 1000;
}