/*
 * 文件速览：
 * 1. 文件职责：声明管理员后台的核心业务能力，包括系统安全设置读写。
 * 2. 对外入口：供 AdminController 调用。
 * 3. 关键结构：审核、举报、账号风控、密码重置、统计查询、安全策略配置与日志。
 * 4. 阅读建议：先看账号与安全设置相关方法，再看各业务模块审核方法。
 */
package com.example.backend.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.backend.entity.AuditLog;
import com.example.backend.vo.AdminJobAuditVO;
import com.example.backend.vo.AdminMerchantAuditVO;
import com.example.backend.vo.AdminReportVO;
import com.example.backend.vo.AdminAuthSecuritySettingsVO;
import com.example.backend.vo.AdminSecuritySettingLogVO;
import com.example.backend.vo.AdminStatsVO;

import java.util.List;

public interface AdminService {
    // 获取职位审核列表
    IPage<AdminJobAuditVO> getJobAuditList(Page<?> page, String keyword, Integer status, String sortField, String sortOrder, String timeOrder);

    // 审核职位（通过/驳回）
    void auditJob(Long id, Integer status, String reason);

    // 批量审核职位
    void auditJobBatch(java.util.List<Long> ids, Integer status, String reason);

    // 撤回职位审核（恢复为待审核）
    void revokeJobAudit(Long id, String reason);

    // 获取职位审核操作记录
    List<AuditLog> getJobAuditLogs(Long jobId);

    // 获取职位审核统计（用于 Tab 数量）
    com.example.backend.vo.AdminJobAuditCountVO getJobAuditCounts();

    // 获取商家审核列表
    IPage<AdminMerchantAuditVO> getMerchantAuditList(Page<?> page, String keyword, Integer status);

    // 审核商家（通过/驳回）
    void auditMerchant(Long id, Integer status, String reason);

    // 批量审核商家
    void auditMerchantBatch(java.util.List<Long> ids, Integer status, String reason);

    // 更新商家发布状态（限制/解除/封禁）
    void updateMerchantPublishStatus(Long id, Integer status, String reason);

    // 获取商家审核操作记录
    List<AuditLog> getMerchantAuditLogs(Long merchantId);

    // 获取举报列表
    IPage<AdminReportVO> getReportList(Page<?> page, String type, Integer status);

    // 处理举报（可附带动作）
    void handleReport(Long id, Integer status, String action, String result);

    // 批量处理举报
    void handleReportBatch(java.util.List<Long> ids, Integer status, String action, String result);

    // 获取举报操作记录
    List<AuditLog> getReportLogs(Long reportId);

    // 获取账号列表（封禁/黑名单管理）
    IPage<com.example.backend.vo.AdminUserVO> getUserList(Page<?> page, String keyword, String role, Integer status, Integer banStatus);

    // 更新账号封禁状态
    void updateUserBan(Long userId, Integer banStatus, java.time.LocalDateTime banUntil, String banReason);

    // 管理员重置账号密码
    void resetUserPassword(Long userId, String newPassword, String reason);

    // 获取管理员可见的账号安全设置
    AdminAuthSecuritySettingsVO getAuthSecuritySettings();

    // 更新“临时密码登录后强制修改密码”开关
    AdminAuthSecuritySettingsVO updateForcePasswordChangeEnabled(Boolean enabled);

    // 查询账号安全设置最近变更日志
    List<AdminSecuritySettingLogVO> getSecuritySettingLogs();

    // 管理员看板统计
    AdminStatsVO getStats();
}
