/*
 * 文件速览：
 * 1. 文件职责：承载管理员看板中账号安全设置变更记录的展示字段。
 * 2. 对外入口：AdminService#getSecuritySettingLogs 返回的日志列表。
 * 3. 关键字段：enabledValue、operatorName、operatorRole、detail、createTime。
 * 4. 阅读建议：先看 enabledValue 与操作人字段，再看 detail 和时间字段。
 */
package com.example.backend.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员安全设置日志 VO
 */
@Data
public class AdminSecuritySettingLogVO {
    private Long id;
    private Long operatorId;
    private String operatorName;
    private String operatorRole;
    private String detail;
    private Boolean enabledValue;
    private LocalDateTime createTime;
}
