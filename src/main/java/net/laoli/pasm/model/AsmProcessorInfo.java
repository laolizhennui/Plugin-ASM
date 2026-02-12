package net.laoli.pasm.model;

public class AsmProcessorInfo {
    private final String className;      // 全限定类名
    private final int priority;          // 优先级，越小越优先

    public AsmProcessorInfo(String className, int priority) {
        this.className = className;
        this.priority = priority;
    }

    public String getClassName() { return className; }
    public int getPriority() { return priority; }

    @Override
    public String toString() {
        return "AsmProcessorInfo{" + "class='" + className + '\'' + ", priority=" + priority + '}';
    }
}