package net.laoli.pasm.transformer;

import net.laoli.pasm.injector.MethodInjector;
import net.laoli.pasm.model.InjectionInfo;
import net.laoli.pasm.utils.PrintUtils;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.util.*;

public class MixinTransformer {

    public static byte[] transformClass(byte[] originalBytes,
                                        String className,
                                        List<InjectionInfo> injections,
                                        ClassLoader loader) {

        try {
            if (injections == null || injections.isEmpty()) {
                return originalBytes;
            }

            PrintUtils.debug("开始转换类: " + className + "，注入点数量: " + injections.size());

            // 按方法分组注入信息
            Map<String, List<InjectionInfo>> injectionsByMethod = groupInjectionsByMethod(injections);

            if (injectionsByMethod.isEmpty()) {
                return originalBytes;
            }

            // 解析类 - 跳过帧，因为我们会重新计算
            ClassReader cr = new ClassReader(originalBytes);
            ClassNode classNode = new ClassNode(Opcodes.ASM9);
            cr.accept(classNode, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

            // 处理每个方法的注入
            boolean injected = false;
            for (MethodNode method : classNode.methods) {
                String methodKey = method.name + method.desc;
                List<InjectionInfo> methodInjections = injectionsByMethod.get(methodKey);

                if (methodInjections != null && !methodInjections.isEmpty()) {
                    PrintUtils.debug("处理方法: " + method.name + method.desc);

                    // 按优先级排序
                    methodInjections.sort(Comparator.comparingInt(InjectionInfo::getPriority));

                    // 批量注入
                    int successCount = MethodInjector.injectMultiple(method, methodInjections);

                    if (successCount > 0) {
                        injected = true;
                        PrintUtils.debug("  成功注入 " + successCount + " 个点");
                    }
                }
            }

            if (injected) {
                // 关键修复：先不使用 COMPUTE_FRAMES，用简单的 COMPUTE_MAXS
                // 这样可以避免栈映射帧计算的复杂性
                ClassWriter cw = new SafeClassWriter(loader, ClassWriter.COMPUTE_FRAMES);

                // 遍历所有方法，清理可能的问题
                for (MethodNode method : classNode.methods) {
                    // 确保方法指令不为空
                    if (method.instructions.size() == 0 && !isAbstractOrNative(method)) {
                        PrintUtils.warn("方法体为空: " + method.name);
                        // 添加默认返回指令
                        addDefaultReturnInstruction(method);
                    }
                }

                classNode.accept(cw);
                byte[] transformedBytes = cw.toByteArray();

                PrintUtils.debug("转换完成: " + className +
                        " (原始: " + originalBytes.length +
                        "字节, 转换后: " + transformedBytes.length + "字节)");

                // 验证生成的字节码
                if (PrintUtils.isDebugEnabled()) {
                    verifyBytecode(transformedBytes, className);
                }

                return transformedBytes;
            }

        } catch (Exception e) {
            PrintUtils.error("转换失败: " + className + " - " + e.getMessage());
            e.printStackTrace();
        }

        return originalBytes;
    }

    private static boolean isAbstractOrNative(MethodNode method) {
        return (method.access & Opcodes.ACC_ABSTRACT) != 0 ||
                (method.access & Opcodes.ACC_NATIVE) != 0;
    }

    private static void addDefaultReturnInstruction(MethodNode method) {
        Type returnType = Type.getReturnType(method.desc);
        switch (returnType.getSort()) {
            case Type.VOID:
                method.instructions.add(new InsnNode(Opcodes.RETURN));
                break;
            case Type.BOOLEAN:
            case Type.CHAR:
            case Type.BYTE:
            case Type.SHORT:
            case Type.INT:
                method.instructions.add(new InsnNode(Opcodes.ICONST_0));
                method.instructions.add(new InsnNode(Opcodes.IRETURN));
                break;
            case Type.FLOAT:
                method.instructions.add(new InsnNode(Opcodes.FCONST_0));
                method.instructions.add(new InsnNode(Opcodes.FRETURN));
                break;
            case Type.LONG:
                method.instructions.add(new InsnNode(Opcodes.LCONST_0));
                method.instructions.add(new InsnNode(Opcodes.LRETURN));
                break;
            case Type.DOUBLE:
                method.instructions.add(new InsnNode(Opcodes.DCONST_0));
                method.instructions.add(new InsnNode(Opcodes.DRETURN));
                break;
            case Type.OBJECT:
            case Type.ARRAY:
                method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
                method.instructions.add(new InsnNode(Opcodes.ARETURN));
                break;
        }
    }

    private static class SafeClassWriter extends ClassWriter {
        private final ClassLoader classLoader;

        public SafeClassWriter(ClassLoader classLoader, int flags) {
            super(flags);
            this.classLoader = classLoader != null ? classLoader :
                    Thread.currentThread().getContextClassLoader();
        }


        @Override
        protected String getCommonSuperClass(String type1, String type2) {
            try {
                String type1Name = type1.replace('/', '.');
                String type2Name = type2.replace('/', '.');

                Class<?> c1, c2;
                try {
                    c1 = Class.forName(type1Name, false, classLoader);
                    c2 = Class.forName(type2Name, false, classLoader);
                } catch (ClassNotFoundException e) {
                    // 回退到系统类加载器
                    c1 = Class.forName(type1Name, false, ClassLoader.getSystemClassLoader());
                    c2 = Class.forName(type2Name, false, ClassLoader.getSystemClassLoader());
                }

                if (c1.isAssignableFrom(c2)) {
                    return type1;
                }
                if (c2.isAssignableFrom(c1)) {
                    return type2;
                }
                if (c1.isInterface() || c2.isInterface()) {
                    return "java/lang/Object";
                } else {
                    Class<?> c = c1;
                    while (!c.isAssignableFrom(c2)) {
                        c = c.getSuperclass();
                    }
                    return c.getName().replace('.', '/');
                }
            } catch (Exception e) {
                // 关键修复：抛出运行时异常，明确告知无法计算共同超类
                throw new RuntimeException("无法计算共同超类: " + type1 + ", " + type2, e);
            }
        }
    }

    private static void verifyBytecode(byte[] bytes, String className) {
        try {
            ClassReader cr = new ClassReader(bytes);
            cr.accept(new ClassVisitor(Opcodes.ASM9) {}, 0);
            PrintUtils.debug("字节码验证通过: " + className);
        } catch (Exception e) {
            PrintUtils.error("字节码验证失败: " + className + " - " + e.getMessage());
        }
    }

    private static Map<String, List<InjectionInfo>> groupInjectionsByMethod(List<InjectionInfo> injections) {
        Map<String, List<InjectionInfo>> grouped = new HashMap<>();
        for (InjectionInfo info : injections) {
            if (!info.isValid()) continue;
            String methodKey = info.getTargetMethod() + info.getTargetDesc();
            grouped.computeIfAbsent(methodKey, k -> new ArrayList<>()).add(info);
        }
        return grouped;
    }
}