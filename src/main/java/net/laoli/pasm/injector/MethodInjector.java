package net.laoli.pasm.injector;

import com.google.common.io.ByteStreams;
import net.laoli.pasm.annotation.InjectionType;
import net.laoli.pasm.model.InjectionInfo;
import net.laoli.pasm.scanner.PluginScanner;
import net.laoli.pasm.transformer.BytecodeMerger;
import net.laoli.pasm.utils.PrintUtils;
import net.laoli.pasm.loader.InjectionClassLoader;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.InputStream;
import java.util.*;

/**
 * 方法注入器 - Mixin风格，使用方法体复制
 * @author laoli
 */
public class MethodInjector {

    private static final InjectionClassLoader CLASS_LOADER =
            PluginScanner.getInjectionClassLoader();

    /**
     * 根据注入信息注入字节码
     */
    public static boolean injectMethod(MethodNode methodNode,
                                       InjectionInfo injectionInfo) {

        try {
            // 参数验证
            if (methodNode == null) {
                PrintUtils.warn("目标方法节点为null");
                return false;
            }
            if (injectionInfo == null) {
                PrintUtils.warn("注入信息为null");
                return false;
            }
            if (!injectionInfo.isValid()) {
                PrintUtils.warn("注入信息无效: " + injectionInfo.getInjectionId());
                return false;
            }

            PrintUtils.debug("开始Mixin注入: " + injectionInfo.getInjectionId());

            // 1. 加载源类字节码
            byte[] sourceClassBytes = loadClassBytes(injectionInfo.getSourceClass());
            if (sourceClassBytes == null) {
                PrintUtils.warn("无法加载源类字节码: " + injectionInfo.getSourceClass());
                return false;
            }

            // 2. 解析源类
            ClassReader sourceCr = new ClassReader(sourceClassBytes);
            ClassNode sourceClassNode = new ClassNode(Opcodes.ASM9);
            sourceCr.accept(sourceClassNode, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

            // 3. 查找源方法
            MethodNode sourceMethod = null;
            for (MethodNode m : sourceClassNode.methods) {
                if (m.name.equals(injectionInfo.getSourceMethod()) &&
                        m.desc.equals(injectionInfo.getSourceDesc())) {
                    sourceMethod = m;
                    break;
                }
            }

            if (sourceMethod == null) {
                PrintUtils.warn("找不到源方法: " +
                        injectionInfo.getSourceMethod() + injectionInfo.getSourceDesc());
                return false;
            }

            // 4. 使用方法体复制进行注入
            return BytecodeMerger.mergeMethodBody(methodNode, sourceMethod, injectionInfo);

        } catch (Exception e) {
            String injectionId = injectionInfo != null ? injectionInfo.getInjectionId() : "未知";
            PrintUtils.error("Mixin注入失败: " + injectionId + " - " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 加载类字节码
     */
    private static byte[] loadClassBytes(String className) {
        try {
            // 使用统一类加载器获取资源
            String resourcePath = className + ".class";
            InputStream is = CLASS_LOADER.getResourceAsStream(resourcePath);

            if (is != null) {
                byte[] bytes = ByteStreams.toByteArray(is);
                is.close();
                return bytes;
            }

            PrintUtils.warn("无法找到类资源: " + resourcePath);
            return null;

        } catch (Exception e) {
            PrintUtils.error("加载类字节码失败: " + className + " - " + e.getMessage());
            return null;
        }
    }

    public static int injectMultiple(MethodNode methodNode,
                                     List<InjectionInfo> injections) {
        // 按优先级排序（数值小优先级高）
        injections.sort(Comparator.comparingInt(InjectionInfo::getPriority));

        // 查找是否存在 REPLACE 注入
        Optional<InjectionInfo> replaceOpt = injections.stream()
                .filter(info -> info.getType() == InjectionType.REPLACE)
                .findFirst();

        if (replaceOpt.isPresent()) {
            InjectionInfo replace = replaceOpt.get();
            long replaceCount = injections.stream()
                    .filter(info -> info.getType() == InjectionType.REPLACE)
                    .count();
            if (replaceCount > 1) {
                PrintUtils.warn("方法 " + methodNode.name + " 存在多个 REPLACE 注入，仅执行优先级最高的: "
                        + replace.getSourceMethod());
            } else {
                PrintUtils.debug("检测到 REPLACE 注入，跳过其他 " + (injections.size() - 1) + " 个注入点");
            }
            // ⚠️ 关键：仅执行这一个 REPLACE，直接返回
            return injectMethod(methodNode, replace) ? 1 : 0;
        }

        // 无 REPLACE：执行所有注入
        int success = 0;
        for (InjectionInfo info : injections) {
            if (injectMethod(methodNode, info)) success++;
        }
        return success;
    }
}