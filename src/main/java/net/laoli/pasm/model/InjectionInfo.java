package net.laoli.pasm.model;

import com.google.common.base.Joiner;
import net.laoli.pasm.annotation.InjectionType;

/**
 * @author laoli
 */
public class InjectionInfo {
    private final String targetClass;      // 目标类名（内部名）
    private final String targetMethod;     // 目标方法名
    private final String targetDesc;       // 目标方法描述符
    private final String sourceClass;      // 源类名（内部名）
    private final String sourceMethod;     // 源方法名
    private final String sourceDesc;       // 源方法描述符
    private final InjectionType type;      // 注入类型
    private final int priority;            // 优先级（数值越小优先级越高）

    public InjectionInfo(String targetClass, String targetMethod, String targetDesc,
                         String sourceClass, String sourceMethod, String sourceDesc,
                         InjectionType type, int priority) {
        this.targetClass = targetClass;
        this.targetMethod = targetMethod;
        this.targetDesc = targetDesc;
        this.sourceClass = sourceClass;
        this.sourceMethod = sourceMethod;
        this.sourceDesc = sourceDesc;
        this.type = type;
        this.priority = priority;
    }

    // Getters
    public String getTargetClass() {
        return targetClass;
    }

    public String getTargetMethod() {
        return targetMethod;
    }

    public String getTargetDesc() {
        return targetDesc;
    }

    public String getSourceClass() {
        return sourceClass;
    }

    public String getSourceMethod() {
        return sourceMethod;
    }

    public String getSourceDesc() {
        return sourceDesc;
    }

    public InjectionType getType() {
        return type;
    }

    public int getPriority() {
        return priority;
    }

    /**
     * 验证注入信息是否有效
     */
    public boolean isValid() {
        return targetClass != null && !targetClass.isEmpty() &&
                targetMethod != null && !targetMethod.isEmpty() &&
                targetDesc != null && !targetDesc.isEmpty() &&
                sourceClass != null && !sourceClass.isEmpty() &&
                sourceMethod != null && !sourceMethod.isEmpty() &&
                sourceDesc != null && !sourceDesc.isEmpty() &&
                type != null;
    }

    /**
     * 获取注入点的唯一标识
     */
    public String getInjectionId() {
        return Joiner.on("->").join(
                targetClass + "." + targetMethod + targetDesc,
                sourceClass + "." + sourceMethod + sourceDesc);
    }

    @Override
    public String toString() {
        return String.format("InjectionInfo{id=%s, type=%s, priority=%d}",
                getInjectionId(), type, priority);
    }
}