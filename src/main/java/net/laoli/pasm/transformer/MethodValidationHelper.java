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

}