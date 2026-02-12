package net.laoli.pasm.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 类级注解 - 指定PASM注入的目标类
 * @author laoli
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Pasm {
    /**
     * 目标类名（全限定名）
     */
    String value();

    /**
     * 目标类名（内部名），可选
     */
    String internalName() default "";
}