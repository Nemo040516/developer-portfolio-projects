/*
 * 文件速览：
 * 1. 文件职责：承载管理员查看账号安全策略时的运行时配置快照。
 * 2. 对外入口：AdminService#getAuthSecuritySettings、AdminService#updateForcePasswordChangeEnabled。
 * 3. 关键字段：forcePasswordChangeEnabled、defaultForcePasswordChangeEnabled、runtimeOverrideActive。
 * 4. 阅读建议：先看当前生效值，再看默认值与是否偏离默认配置。
 */
package com.example.backend.vo;

import lombok.Data;

/**
 * 管理员账号安全设置 VO
 */
@Data
public class AdminAuthSecuritySettingsVO {
    private Boolean forcePasswordChangeEnabled;
    private Boolean defaultForcePasswordChangeEnabled;
    private Boolean runtimeOverrideActive;
}
