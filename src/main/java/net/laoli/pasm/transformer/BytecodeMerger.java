package net.laoli.pasm.transformer;

import net.laoli.pasm.model.InjectionInfo;
import net.laoli.pasm.utils.PrintUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.*;

/**
 * 字节码合并器主类 - 将源方法的方法体复制到目标方法中
 */
public class BytecodeMerger {

    /**
     * 合并方法体（主入口）
     */
    public static boolean mergeMethodBody(MethodNode targetMethod,
                                          MethodNode sourceMethod,
                                          InjectionInfo injectionInfo) {

        try {
            PrintUtils.debug("开始合并方法体: " + sourceMethod.name + " -> " + targetMethod.name);

            // 验证源方法必须是静态的
            if ((sourceMethod.access & Opcodes.ACC_STATIC) == 0) {
                PrintUtils.warn("源方法必须为静态方法: " + sourceMethod.name);
                return false;
            }

            // 验证方法签名兼容性
            if (!validateSignatureCompatibility(targetMethod, sourceMethod, injectionInfo)) {
                return false;
            }

            // 根据注入类型进行合并
            return switch (injectionInfo.getType()) {
                case BEFORE -> mergeBefore(targetMethod, sourceMethod);
                case AFTER -> mergeAfter(targetMethod, sourceMethod);
                case REPLACE -> mergeReplace(targetMethod, sourceMethod);
                case HEAD -> mergeHead(targetMethod, sourceMethod);
                case TAIL -> mergeTail(targetMethod, sourceMethod);
                case AROUND -> mergeAround(targetMethod, sourceMethod);
            };

        } catch (Exception e) {
            PrintUtils.error("合并方法体失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }


    /**
     * BEFORE合并 - 在方法开始处插入
     */
    private static boolean mergeBefore(MethodNode targetMethod,
                                       MethodNode sourceMethod) {
        InsnList sourceInstructions = MethodCopyHelper.copyMethodBodyWithoutReturn(sourceMethod, targetMethod);
        targetMethod.instructions.insert(sourceInstructions);
        return true;
    }

    /**
     * AFTER合并 - 在方法返回前插入，正确处理返回值
     */
    private static boolean mergeAfter(MethodNode targetMethod,
                                      MethodNode sourceMethod) {
        // 获取目标方法的返回类型
        Type returnType = Type.getReturnType(targetMethod.desc);
        boolean isVoid = returnType.getSort() == Type.VOID;

        // 复制源方法体（排除return指令，并应用偏移）
        InsnList sourceInstructions = MethodCopyHelper.copyMethodBodyWithoutReturn(sourceMethod, targetMethod);

        // 查找所有return指令
        List<AbstractInsnNode> returnNodes = MethodValidationHelper.findReturnNodes(targetMethod);
        if (returnNodes.isEmpty()) {
            PrintUtils.warn("目标方法没有return指令，无法执行AFTER注入");
            return false;
        }

        // 为每个return位置生成插入指令
        for (AbstractInsnNode returnNode : returnNodes) {
            InsnList injectBlock = new InsnList();

            if (!isVoid) {
                // 非void方法：需要保存返回值
                // 1. 将栈顶返回值存入临时局部变量
                int tmpVar = targetMethod.maxLocals; // 使用新的局部变量索引
                targetMethod.maxLocals += 1;          // 增加局部变量槽计数

                // 根据返回类型生成存储指令
                int storeOpcode;
                switch (returnType.getSort()) {
                    case Type.BOOLEAN:
                    case Type.CHAR:
                    case Type.BYTE:
                    case Type.SHORT:
                    case Type.INT:
                        storeOpcode = Opcodes.ISTORE;
                        break;
                    case Type.FLOAT:
                        storeOpcode = Opcodes.FSTORE;
                        break;
                    case Type.LONG:
                        storeOpcode = Opcodes.LSTORE;
                        tmpVar = targetMethod.maxLocals;
                        targetMethod.maxLocals += 2;
                        break;
                    case Type.DOUBLE:
                        storeOpcode = Opcodes.DSTORE;
                        tmpVar = targetMethod.maxLocals;
                        targetMethod.maxLocals += 2;
                        break;
                    case Type.OBJECT:
                    case Type.ARRAY:
                        storeOpcode = Opcodes.ASTORE;
                        break;
                    default:
                        storeOpcode = Opcodes.ASTORE;
                }
                injectBlock.add(new VarInsnNode(storeOpcode, tmpVar));

                // 2. 插入源方法指令
                injectBlock.add(MethodCopyHelper.cloneInstructionList(sourceInstructions));

                // 3. 恢复返回值并返回
                int loadOpcode = Opcodes.ALOAD;
                switch (returnType.getSort()) {
                    case Type.VOID: break;
                    case Type.BOOLEAN:
                    case Type.CHAR:
                    case Type.BYTE:
                    case Type.SHORT:
                    case Type.INT:
                        loadOpcode = Opcodes.ILOAD;
                        break;
                    case Type.FLOAT:
                        loadOpcode = Opcodes.FLOAD;
                        break;
                    case Type.LONG:
                        loadOpcode = Opcodes.LLOAD;
                        break;
                    case Type.DOUBLE:
                        loadOpcode = Opcodes.DLOAD;
                        break;
                }
                if (!isVoid) {
                    injectBlock.add(new VarInsnNode(loadOpcode, tmpVar));
                }
                // 保留原有的return指令，无需添加
            } else {
                // void方法：直接插入源方法指令
                injectBlock.add(MethodCopyHelper.cloneInstructionList(sourceInstructions));
                // 保留原有return指令
            }

            // 在return指令前插入整个块
            targetMethod.instructions.insertBefore(returnNode, injectBlock);
        }

        return true;
    }

    /**
     * REPLACE合并 - 替换整个方法
     */
    private static boolean mergeReplace(MethodNode targetMethod,
                                        MethodNode sourceMethod) {
        try {

            // 清空目标方法的所有内容
            targetMethod.instructions.clear();
            targetMethod.tryCatchBlocks.clear();
            targetMethod.localVariables = null;
            targetMethod.visibleAnnotations = null;
            targetMethod.invisibleAnnotations = null;
            targetMethod.visibleParameterAnnotations = null;
            targetMethod.invisibleParameterAnnotations = null;
            targetMethod.annotationDefault = null;
            targetMethod.visibleAnnotableParameterCount = 0;
            targetMethod.invisibleAnnotableParameterCount = 0;

            // 复制源方法指令并获取标签映射
            MethodCopyHelper.Pair<InsnList, Map<LabelNode, LabelNode>> copyResult =
                    MethodCopyHelper.copyMethodBodyWithOffsetAndMapping(sourceMethod, targetMethod);
            InsnList newInstructions = copyResult.getLeft();
            Map<LabelNode, LabelNode> labelMap = copyResult.getRight();

            targetMethod.instructions.add(newInstructions);

            // 复制异常处理表
            if (sourceMethod.tryCatchBlocks != null) {
                for (Object obj : sourceMethod.tryCatchBlocks) {
                    TryCatchBlockNode tcbn = (TryCatchBlockNode) obj;
                    LabelNode start = labelMap.get(tcbn.start);
                    LabelNode end = labelMap.get(tcbn.end);
                    LabelNode handler = labelMap.get(tcbn.handler);
                    if (start != null && end != null && handler != null) {
                        targetMethod.tryCatchBlocks.add(
                                new TryCatchBlockNode(start, end, handler, tcbn.type));
                    } else {
                        PrintUtils.warn("异常表标签映射丢失，跳过该异常块");
                    }
                }
            }

            // 确保方法有返回指令
            ensureReturnInstruction(targetMethod);

            PrintUtils.debug("REPLACE合并成功，指令数: " + targetMethod.instructions.size());
            return true;

        } catch (Exception e) {
            PrintUtils.error("REPLACE合并失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * HEAD合并 - 在方法头部注入（参数之后，第一条指令之前）
     */
    private static boolean mergeHead(MethodNode targetMethod,
                                     MethodNode sourceMethod) {
        InsnList sourceInstructions = MethodCopyHelper.copyMethodBodyWithoutReturn(sourceMethod, targetMethod);

        if ("<init>".equals(targetMethod.name)) {
            // 构造函数：必须在super/this调用之后插入
            AbstractInsnNode superCall = MethodValidationHelper.findSuperOrThisCall(targetMethod);
            if (superCall != null) {
                targetMethod.instructions.insert(superCall, sourceInstructions);
                PrintUtils.debug("HEAD合并完成（构造函数，在super调用后插入）");
            } else {
                // 没有super调用，则插入开头
                targetMethod.instructions.insert(sourceInstructions);
                PrintUtils.debug("HEAD合并完成（构造函数，无super调用）");
            }
        } else {
            // 普通方法：在第一条非参数指令前插入
            AbstractInsnNode firstInsn = MethodValidationHelper.findFirstNonParameterInstruction(targetMethod);
            if (firstInsn != null) {
                targetMethod.instructions.insertBefore(firstInsn, sourceInstructions);
            } else {
                targetMethod.instructions.insert(sourceInstructions);
            }
        }
        return true;
    }

    /**
     * TAIL合并 - 等价于AFTER
     */
    private static boolean mergeTail(MethodNode targetMethod,
                                     MethodNode sourceMethod) {
        return mergeAfter(targetMethod, sourceMethod);
    }

    /**
     * AROUND合并 - 暂不支持，降级为REPLACE
     */
    private static boolean mergeAround(MethodNode targetMethod,
                                       MethodNode sourceMethod) {
        PrintUtils.warn("AROUND注入暂不支持，降级为REPLACE");
        return mergeReplace(targetMethod, sourceMethod);
    }


    /**
     * 验证方法签名兼容性
     */
    private static boolean validateSignatureCompatibility(MethodNode targetMethod,
                                                          MethodNode sourceMethod,
                                                          InjectionInfo info) {
        String targetDesc = targetMethod.desc;
        String sourceDesc = sourceMethod.desc;

        Type[] targetArgs = Type.getArgumentTypes(targetDesc);
        Type[] sourceArgs = Type.getArgumentTypes(sourceDesc);

        Type targetReturn = Type.getReturnType(targetDesc);
        Type sourceReturn = Type.getReturnType(sourceDesc);

        switch (info.getType()) {
            case BEFORE:
            case HEAD:
            case TAIL:
                // 1. 参数数量必须 ≤ 目标方法参数数量
                if (sourceArgs.length > targetArgs.length) {
                    PrintUtils.warn("参数数量过多: 源方法参数 " + sourceArgs.length +
                            " > 目标方法参数 " + targetArgs.length);
                    return false;
                }
                // 2. 参数类型必须完全匹配（确保字节码安全）
                for (int i = 0; i < sourceArgs.length; i++) {
                    if (!sourceArgs[i].equals(targetArgs[i])) {
                        PrintUtils.warn("参数类型不兼容: 位置 " + i +
                                " 期望 " + targetArgs[i] + "，实际 " + sourceArgs[i]);
                        return false;
                    }
                }
                return true;
            case AFTER:
                if (sourceReturn.getSort() != Type.VOID) {
                    PrintUtils.warn("AFTER注入的源方法必须返回void");
                    return false;
                }
                return sourceArgs.length == targetArgs.length;
            case REPLACE:
            case AROUND:
                return Arrays.equals(targetArgs, sourceArgs) &&
                        sourceReturn.equals(targetReturn);
            default:
                return false;
        }
    }

    /**
     * 确保方法包含返回指令，如果没有则添加默认返回值
     * @param method 目标方法节点
     */
    private static void ensureReturnInstruction(MethodNode method) {
        // 检查是否已有返回指令
        for (AbstractInsnNode insn = method.instructions.getFirst();
             insn != null; insn = insn.getNext()) {
            int opcode = insn.getOpcode();
            if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
                return; // 已有返回指令，无需添加
            }
        }

        // 根据返回类型添加默认返回值
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
            default:
                // 未知类型，添加空返回（可能触发 VerifyError，但至少方法完整）
                method.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
                method.instructions.add(new InsnNode(Opcodes.ARETURN));
                break;
        }
    }
}