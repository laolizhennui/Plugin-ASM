package net.laoli.pasm.scanner;

import net.laoli.pasm.model.InjectionInfo;
import net.laoli.pasm.utils.PrintUtils;
import org.objectweb.asm.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.laoli.pasm.scanner.PluginScanner.INJECT_ANNOTATION_DESC;
import static net.laoli.pasm.scanner.PluginScanner.PASM_ANNOTATION_DESC;

public class ClassScanner {
    /**
     * 使用ASM扫描单个类字节码中的@Pasm和@Inject注解
     */
    public static List<InjectionInfo> scanClass(byte[] classBytes) {
        List<InjectionInfo> injections = new ArrayList<>();
        final String[] targetClass = new String[1]; // 存储类级注解的目标类

        ClassReader cr = new ClassReader(classBytes);

        cr.accept(new ClassVisitor(Opcodes.ASM9) {
            private String currentClassName;

            @Override
            public void visit(int version, int access, String name,
                              String signature, String superName, String[] interfaces) {
                this.currentClassName = name;
                PrintUtils.debug("扫描类: " + name);
                super.visit(version, access, name, signature, superName, interfaces);
            }

            @Override
            public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                // 检查是否为@Pasm类级注解
                if (PASM_ANNOTATION_DESC.equals(desc)) {
                    return new AnnotationVisitor(Opcodes.ASM9) {
                        @Override
                        public void visit(String key, Object value) {
                            if ("value".equals(key)) {
                                // 获取目标类名，转换为内部名格式
                                String targetClassName = (String) value;
                                targetClass[0] = targetClassName.replace('.', '/');
                                PrintUtils.debug("  类级注解@Pasm: target=" + targetClass[0]);
                            }
                        }
                    };
                }
                return null;
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    private Map<String, Object> injectAnnotationValues = null;

                    @Override
                    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                        // 检查是否为@Inject方法级注解
                        if (INJECT_ANNOTATION_DESC.equals(desc)) {
                            injectAnnotationValues = new HashMap<>();
                            return new AnnotationVisitor(Opcodes.ASM9) {
                                @Override
                                public void visit(String key, Object value) {
                                    injectAnnotationValues.put(key, value);
                                }

                                @Override
                                public void visitEnum(String key, String descriptor, String value) {
                                    injectAnnotationValues.put(key, value);
                                }

                                @Override
                                public AnnotationVisitor visitArray(String name) {
                                    return this;
                                }
                            };
                        }
                        return null;
                    }

                    @Override
                    public void visitEnd() {
                        // 如果类有@Pasm注解且方法有@Inject注解
                        if (targetClass[0] != null &&
                                injectAnnotationValues != null &&
                                !injectAnnotationValues.isEmpty()) {

                            // 创建注入信息
                            InjectionInfo info = createInjectionInfoFromASM(
                                    currentClassName,
                                    name,
                                    descriptor,
                                    access,
                                    injectAnnotationValues,
                                    targetClass[0]
                            );

                            if (info != null && info.isValid()) {
                                injections.add(info);
                                PrintUtils.debug("  发现注入点: " + info);
                            } else if (info != null) {
                                PrintUtils.warn("  跳过无效的注入点: " + info.getInjectionId());
                            }
                        }
                        super.visitEnd();
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        PrintUtils.debug("  类扫描完成，找到 " + injections.size() + " 个注入点");
        return injections;
    }


    /**
     * 从ASM解析的数据创建注入信息（新版本，包含targetClass参数）
     */
    private static InjectionInfo createInjectionInfoFromASM(
            String sourceClassName,
            String sourceMethodName,
            String sourceMethodDesc,
            int methodAccess,
            Map<String, Object> annotationValues,
            String targetClassFromPasm) {

        try {
            // 验证源方法必须是静态方法
            if ((methodAccess & Opcodes.ACC_STATIC) == 0) {
                PrintUtils.warn("注入方法必须为静态方法 - " +
                        sourceClassName + "." + sourceMethodName);
                return null;
            }

            // 获取注解值（现在只从@Inject注解获取方法级信息）
            String targetMethodName = (String) annotationValues.get("name");
            String targetMethodDesc = (String) annotationValues.get("desc");
            int priority = (Integer) annotationValues.getOrDefault("priority", 1000);
            String typeStr = (String) annotationValues.getOrDefault("type", "REPLACE");

            // 注入类型
            net.laoli.pasm.annotation.InjectionType injectionType =
                    net.laoli.pasm.annotation.InjectionType.valueOf(typeStr);

            // 创建注入信息（目标类来自类级@Pasm注解）
            return new InjectionInfo(
                    targetClassFromPasm,           // 来自@Pasm注解
                    targetMethodName,              // 来自@Inject注解
                    targetMethodDesc,              // 来自@Inject注解
                    sourceClassName,               // 源类
                    sourceMethodName,              // 源方法
                    sourceMethodDesc,              // 源方法描述符
                    injectionType,                 // 注入类型
                    priority                       // 优先级
            );

        } catch (Exception e) {
            PrintUtils.error("从ASM数据创建注入信息失败: " +
                    sourceClassName + "." + sourceMethodName + " - " + e.getMessage());
            return null;
        }
    }
}
