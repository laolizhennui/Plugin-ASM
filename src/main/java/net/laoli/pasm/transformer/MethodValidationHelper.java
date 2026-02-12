package net.laoli.pasm.transformer;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 方法验证辅助类 - 专门处理指令查找和验证
 */
public class MethodValidationHelper {

    /**
     * 查找所有return指令
     */
    public static List<AbstractInsnNode> findReturnNodes(MethodNode method) {
        List<AbstractInsnNode> returnNodes = new ArrayList<>();

        for (AbstractInsnNode insn = method.instructions.getFirst();
             insn != null; insn = insn.getNext()) {
            int opcode = insn.getOpcode();
            if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
                returnNodes.add(insn);
            }
        }

        return returnNodes;
    }

    /**
     * 查找第一条非参数加载指令
     */
    public static AbstractInsnNode findFirstNonParameterInstruction(MethodNode method) {
        for (AbstractInsnNode insn = method.instructions.getFirst();
             insn != null; insn = insn.getNext()) {
            int opcode = insn.getOpcode();
            if (!(opcode >= Opcodes.ILOAD && opcode <= Opcodes.ALOAD) &&
                    !(insn instanceof LabelNode) &&
                    !(insn instanceof LineNumberNode)) {
                return insn;
            }
        }
        return null;
    }

    /**
     * 查找super()或this()调用指令
     */
    public static AbstractInsnNode findSuperOrThisCall(MethodNode methodNode) {
        for (AbstractInsnNode insn = methodNode.instructions.getFirst();
             insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() == Opcodes.INVOKESPECIAL) {
                MethodInsnNode methodInsn = (MethodInsnNode) insn;
                if ("<init>".equals(methodInsn.name)) {
                    return insn;
                }
            }
        }
        return null;
    }

    /**
     * 检查方法是否为抽象方法或本地方法
     */
    public static boolean isAbstractOrNative(MethodNode method) {
        return (method.access & Opcodes.ACC_ABSTRACT) != 0 ||
                (method.access & Opcodes.ACC_NATIVE) != 0;
    }

    /**
     * 验证源方法是否有效
     */
    public static boolean validateSourceMethod(MethodNode sourceMethod) {
        if (sourceMethod == null) {
            return false;
        }

        // 检查是否为静态方法
        if ((sourceMethod.access & Opcodes.ACC_STATIC) == 0) {
            return false;
        }

        // 检查指令是否为空
        if (sourceMethod.instructions.size() == 0) {
            return false;
        }

        return true;
    }

    /**
     * 验证目标方法是否可注入
     */
    public static boolean validateTargetMethod(MethodNode targetMethod) {
        if (targetMethod == null) {
            return false;
        }

        // 检查是否为抽象方法或本地方法
        if (isAbstractOrNative(targetMethod)) {
            return false;
        }

        // 检查是否为final方法
        if ((targetMethod.access & Opcodes.ACC_FINAL) != 0) {
            return false;
        }

        return true;
    }

    /**
     * 统计方法中的指令类型
     */
    public static InstructionStats countInstructionTypes(MethodNode method) {
        InstructionStats stats = new InstructionStats();

        for (AbstractInsnNode insn = method.instructions.getFirst();
             insn != null; insn = insn.getNext()) {

            stats.totalInstructions++;

            if (insn instanceof VarInsnNode) {
                stats.varInstructions++;
            } else if (insn instanceof JumpInsnNode) {
                stats.jumpInstructions++;
            } else if (insn instanceof LabelNode) {
                stats.labels++;
            } else if (insn instanceof MethodInsnNode) {
                stats.methodCalls++;
            } else if (insn instanceof FieldInsnNode) {
                stats.fieldAccesses++;
            } else if (insn.getOpcode() >= Opcodes.IRETURN && insn.getOpcode() <= Opcodes.RETURN) {
                stats.returnInstructions++;
            }
        }

        return stats;
    }

    /**
     * 指令统计类
     */
    public static class InstructionStats {
        public int totalInstructions = 0;
        public int varInstructions = 0;
        public int jumpInstructions = 0;
        public int labels = 0;
        public int methodCalls = 0;
        public int fieldAccesses = 0;
        public int returnInstructions = 0;

        @Override
        public String toString() {
            return String.format("指令统计: 总数=%d, 变量=%d, 跳转=%d, 标签=%d, 方法调用=%d, 字段访问=%d, 返回=%d",
                    totalInstructions, varInstructions, jumpInstructions, labels,
                    methodCalls, fieldAccesses, returnInstructions);
        }
    }
}