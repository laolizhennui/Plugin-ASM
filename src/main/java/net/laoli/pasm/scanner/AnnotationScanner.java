package net.laoli.pasm.scanner;

import net.laoli.pasm.model.InjectionInfo;
import net.laoli.pasm.utils.PrintUtils;

import java.util.*;

/**
 * 注解扫描器 - 专门处理@Inject注解的验证和分组
 * 注意：现在扫描逻辑已迁移到PluginScanner中，此类主要用于验证和工具方法
 */
public class AnnotationScanner {

    /**
     * 验证注入信息之间的冲突
     */
    public static void validateInjections(List<InjectionInfo> injections) {
        // 检查重复注入
        for (int i = 0; i < injections.size(); i++) {
            for (int j = i + 1; j < injections.size(); j++) {
                InjectionInfo info1 = injections.get(i);
                InjectionInfo info2 = injections.get(j);

                // 检查是否是同一个注入点（完全相同的源和目标）
                if (info1.getInjectionId().equals(info2.getInjectionId())) {
                    PrintUtils.warn("发现重复注入 - " + info1.getInjectionId());
                }
            }
        }
    }
}