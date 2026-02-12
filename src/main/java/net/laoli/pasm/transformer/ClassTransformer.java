package net.laoli.pasm.transformer;

import net.laoli.pasm.processor.InjectionProcessor;
import net.laoli.pasm.utils.PrintUtils;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.*;

/**
 * 简化的类转换器 - 实现ClassFileTransformer接口
 *
 * @author laoli
 */
public class ClassTransformer implements ClassFileTransformer {

    private final InjectionProcessor injectionProcessor;

    public ClassTransformer(InjectionProcessor processor) {
        this.injectionProcessor = processor;
    }

    /**
     * 检查是否是被排除的类
     */
    private boolean isExcludedClass(String className) {
        // 排除Java标准库
        return className.startsWith("java/") ||
                className.startsWith("javax/") ||
                className.startsWith("sun/") ||
                className.startsWith("com/sun/") ||
                className.startsWith("net/laoli/pasm/") ||
                className.startsWith("[") ||
                className.length() == 1;
    }

    /**
     * 添加排除的类前缀
     */
    public void addExcludedClass(String classPrefix) {
        PrintUtils.debug("添加排除类前缀: " + classPrefix);
        // 注意：这里简化的实现中没有保存排除列表
        // 实际实现应该保存到一个集合中
    }

    /**
     * 设置是否启用缓存
     */
    public void setEnableCaching(boolean enable) {
        PrintUtils.debug("设置缓存: " + enable);
        // 这里简化的实现中没有缓存机制
        // 实际实现可以添加缓存逻辑
    }

    @Override
    public byte[] transform(ClassLoader loader,
                            String internalClassName,
                            Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain,
                            byte[] originalClassfileBuffer) throws IllegalClassFormatException {

        // 基本检查...
        if (internalClassName == null || internalClassName.isEmpty() || loader == null) {
            return null;
        }

        // 排除系统类...
        if (isExcludedClass(internalClassName)) {
            return null;
        }

        // 检查是否有注入信息...
        if (!injectionProcessor.hasInjectionsForClass(internalClassName)) {
            return null;
        }

        PrintUtils.debug("Mixin转换类: " + internalClassName);

        try {
            // 获取注入信息
            List<net.laoli.pasm.model.InjectionInfo> injections =
                    injectionProcessor.getInjectionsForClass(internalClassName);

            if (injections == null || injections.isEmpty()) {
                return null;
            }

            // 使用MixinTransformer进行转换
            byte[] transformedBytes = MixinTransformer.transformClass(
                    originalClassfileBuffer,
                    internalClassName,
                    injections,
                    loader
            );

            return transformedBytes;

        } catch (Exception e) {
            PrintUtils.error("Mixin转换失败: " + internalClassName + " - " + e.getMessage());
            return originalClassfileBuffer;
        }
    }
}