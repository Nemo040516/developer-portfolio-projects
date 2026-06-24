/*
 * 文件速览：
 * 1. 文件职责：承载管理员账号管理列表的展示字段。
 * 2. 对外入口：AdminService#getUserList 返回的分页记录。
 * 3. 关键结构：账号基础信息、封禁状态、最近密码重置记录。
 * 4. 阅读建议：先看基础账号字段，再看 latestPasswordReset* 相关扩展字段。
 */
package com.example.backend.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员账号列表展示 VO
 */
@Data
public class AdminUserVO {
    private Long id;
    private String username;
    private String nickname;
    private String role;
    private Integer status;
    private Integer banStatus;
    private String banReason;
    private LocalDateTime banUntil;
    private String phone;
    private String email;
    private LocalDateTime createTime;
    private LocalDateTime latestPasswordResetTime;
    private Long latestPasswordResetOperatorId;
    private String latestPasswordResetOperatorName;
    private String latestPasswordResetDetail;
}
