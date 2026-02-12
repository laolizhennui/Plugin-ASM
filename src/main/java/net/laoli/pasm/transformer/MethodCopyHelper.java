package net.laoli.pasm.transformer;

import net.laoli.pasm.model.AsmProcessorInfo;
import net.laoli.pasm.model.InjectionInfo;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.*;

/**
 * 字节码复制辅助类 - 处理指令复制、索引偏移、标签映射
 */
public class MethodCopyHelper {

    public static Pair<InsnList, Map<LabelNode, LabelNode>> copyMethodBodyWithOffsetAndMapping(
            MethodNode sourceMethod, MethodNode targetMethod) {

        // ----- 1. 计算源方法的局部变量布局 -----
        boolean sourceIsStatic = (sourceMethod.access & Opcodes.ACC_STATIC) != 0;
        int sourceParamStart = sourceIsStatic ? 0 : 1;          // 源方法参数起始索引
        Type[] argTypes = Type.getArgumentTypes(sourceMethod.desc);
        int[] paramSlots = new int[argTypes.length];           // 每个参数占槽数 (1或2)
        int[] sourceParamIndices = new int[argTypes.length];   // 源方法每个参数的起始索引
        int curSlot = sourceParamStart;
        for (int i = 0; i < argTypes.length; i++) {
            sourceParamIndices[i] = curSlot;
            int size = argTypes[i].getSize();
            paramSlots[i] = size;
            curSlot += size;
        }
        int sourceParamEnd = curSlot;                           // 源方法参数区结束索引（不包含）
        int sourceLocalStart = sourceParamEnd;                 // 源方法局部变量起始索引

        // ----- 2. 计算目标方法的局部变量布局 -----
        boolean targetIsStatic = (targetMethod.access & Opcodes.ACC_STATIC) != 0;
        int targetParamStart = targetIsStatic ? 0 : 1;
        int[] targetParamIndices = new int[argTypes.length];
        curSlot = targetParamStart;
        for (int i = 0; i < argTypes.length; i++) {
            targetParamIndices[i] = curSlot;
            curSlot += paramSlots[i];
        }
        int targetParamEnd = curSlot;
        int targetLocalStart = targetParamEnd;

        // ----- 3. 构建局部变量索引映射表 -----
        // 注意：我们只需要为源方法中出现的局部变量索引建立映射，
        // 但这里无法预知所有索引，因此采用“运行时计算”策略，
        // 在克隆每条 VarInsnNode / IincInsnNode 时动态计算目标索引。
        // 偏移量定义：
        int paramOffset = targetParamStart - sourceParamStart;      // 参数区整体偏移
        int localOffset = targetLocalStart - sourceLocalStart;      // 非参数局部变量区整体偏移

        // ----- 4. 克隆指令并应用索引映射 -----
        InsnList result = new InsnList();
        Map<LabelNode, LabelNode> labelMap = new HashMap<>();

        // 第一遍：为所有原始 LabelNode 创建新 LabelNode
        for (AbstractInsnNode insn : sourceMethod.instructions) {
            if (insn instanceof LabelNode) {
                labelMap.put((LabelNode) insn, new LabelNode());
            }
        }

        for (AbstractInsnNode insn : sourceMethod.instructions) {
            AbstractInsnNode clone;

            if (insn instanceof LabelNode) {
                clone = labelMap.get(insn);
            }
            else if (insn instanceof VarInsnNode) {
                VarInsnNode var = (VarInsnNode) insn;
                int newIndex = mapLocalIndex(var.var,
                        sourceParamStart, sourceParamEnd, sourceLocalStart,
                        targetParamStart, targetParamEnd, targetLocalStart,
                        paramOffset, localOffset);
                clone = new VarInsnNode(var.getOpcode(), newIndex);
            }
            else if (insn instanceof IincInsnNode) {
                IincInsnNode iinc = (IincInsnNode) insn;
                int newIndex = mapLocalIndex(iinc.var,
                        sourceParamStart, sourceParamEnd, sourceLocalStart,
                        targetParamStart, targetParamEnd, targetLocalStart,
                        paramOffset, localOffset);
                clone = new IincInsnNode(newIndex, iinc.incr);
            }
            else {
                clone = insn.clone(labelMap);
            }

            if (clone != null) result.add(clone);
        }

        return new Pair<>(result, labelMap);
    }

    /**
     * 将源局部变量索引映射到目标局部变量索引
     */
    private static int mapLocalIndex(int sourceIndex,
                                     int sourceParamStart, int sourceParamEnd, int sourceLocalStart,
                                     int targetParamStart, int targetParamEnd, int targetLocalStart,
                                     int paramOffset, int localOffset) {
        if (sourceIndex >= sourceParamStart && sourceIndex < sourceParamEnd) {
            // 参数区：直接整体偏移（因为参数顺序、类型完全一致）
            return sourceIndex + paramOffset;
        } else if (sourceIndex >= sourceLocalStart) {
            // 非参数局部变量区：整体偏移
            return sourceIndex + localOffset;
        } else {
            // 理论上不会走到这里（比如 this 指针），但保留原值
            return sourceIndex;
        }
    }

    public static InsnList copyMethodBodyWithoutReturn(MethodNode sourceMethod, MethodNode targetMethod) {
        Pair<InsnList, Map<LabelNode, LabelNode>> copyResult =
                copyMethodBodyWithOffsetAndMapping(sourceMethod, targetMethod);
        InsnList all = copyResult.getLeft();
        // 遍历并移除所有返回指令
        for (AbstractInsnNode insn = all.getFirst(); insn != null; ) {
            AbstractInsnNode next = insn.getNext();
            int opcode = insn.getOpcode();
            if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
                all.remove(insn);
            }
            insn = next;
        }
        return all;
    }

    public static InsnList cloneInstructionList(InsnList original) {
        InsnList cloned = new InsnList();
        Map<LabelNode, LabelNode> labelMap = new HashMap<>();

        // 第一遍：收集原始 LabelNode，创建对应的新 LabelNode
        for (AbstractInsnNode insn = original.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof LabelNode) {
                labelMap.put((LabelNode) insn, new LabelNode());
            }
        }

        // 第二遍：克隆每条指令，使用 labelMap 替换内部引用的 LabelNode
        for (AbstractInsnNode insn = original.getFirst(); insn != null; insn = insn.getNext()) {
            cloned.add(insn.clone(labelMap));
        }
        return cloned;
    }

    // 简单的Pair容器
    public static class Pair<L, R> {
        private final L left;
        private final R right;
        private Pair(L left, R right) { this.left = left; this.right = right; }

        public static <L1, R1> Pair<L1, R1> create(L1 l1, R1 r1) {
            return new Pair<>(l1, r1);
        }

        public static <L1, R1> Pair<L1, R1> of(L1 l1, R1 r1) {
            return new Pair<>(l1, r1);
        }

        public L getLeft() { return left; }
        public R getRight() { return right; }
    }
}