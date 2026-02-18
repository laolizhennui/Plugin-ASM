package net.laoli.pasm.transformer;

import net.laoli.pasm.processor.InjectionProcessor;
import net.laoli.pasm.utils.PrintUtils;

import java.lang.instrument.ClassFileTransformer;
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

    @Override
    public byte[] transform(ClassLoader loader,
                            String internalClassName,
                            Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain,
                            byte[] originalClassfileBuffer) {

        // 基本检查...
        if (internalClassName == null || internalClassName.isEmpty() || loader == null) {
            return null;
        }

        // 排除系统类...
        if (internalClassName.startsWith("java/") ||
                internalClassName.startsWith("javax/") ||
                internalClassName.startsWith("sun/") ||
                internalClassName.startsWith("com/sun/") ||
                internalClassName.startsWith("net/laoli/pasm/") ||
                internalClassName.startsWith("[") ||
                internalClassName.length() == 1) {
            return null;
        }

        // 检查是否有注入信息...
        if (!injectionProcessor.hasInjectionsForClass(internalClassName)) {
            return null;
        }

        PrintUtils.debug("转换类: " + internalClassName);

        try {
            // 获取注入信息
            List<net.laoli.pasm.model.InjectionInfo> injections =
                    injectionProcessor.getInjectionsForClass(internalClassName);

            if (injections == null || injections.isEmpty()) {
                return null;
            }

            // 使用MixinTransformer进行转换
            return MixinTransformer.transformClass(
                    originalClassfileBuffer,
                    internalClassName,
                    injections,
                    loader
            );

        } catch (Exception e) {
            PrintUtils.error("转换失败: " + internalClassName + " - " + e.getMessage());
            e.printStackTrace();
            return originalClassfileBuffer;
        } catch (Throwable t) {
            // 捕获所有异常，包括Error，确保不会导致JVM崩溃
            PrintUtils.error("转换发生严重错误: " + internalClassName + " - " + t.getMessage());
            t.printStackTrace();
            return originalClassfileBuffer;
        }
    }
}