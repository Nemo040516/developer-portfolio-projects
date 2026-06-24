/*
 * 文件速览：
 * 1. 文件职责：承接管理员后台的审核、举报、账号风控、密码重置与安全设置接口。
 * 2. 对外入口：/admin 下的职位、商家、举报、用户管理相关接口。
 * 3. 关键结构：统一管理员鉴权、分页参数归一化、批量处理、账号封禁、密码重置、安全策略开关与日志。
 * 4. 阅读建议：先看顶部鉴权调用，再按 stats / security / users / reports / jobs / merchants 分段阅读。
 */
package com.example.backend.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.backend.common.Result;
import com.example.backend.dto.AdminAuditDTO;
import com.example.backend.dto.AdminAuditRevokeDTO;
import com.example.backend.dto.AdminBatchAuditDTO;
import com.example.backend.dto.AdminBatchReportHandleDTO;
import com.example.backend.dto.AdminForcePasswordChangeSettingDTO;
import com.example.backend.dto.AdminReportHandleDTO;
import com.example.backend.dto.AdminMerchantStatusDTO;
import com.example.backend.dto.AdminResetPasswordDTO;
import com.example.backend.dto.AdminUserBanDTO;
import com.example.backend.service.AdminService;
import com.example.backend.utils.ControllerAccessUtils;
import com.example.backend.utils.PageQueryUtils;
import com.example.backend.vo.AdminJobAuditVO;
import com.example.backend.vo.AdminJobAuditCountVO;
import com.example.backend.vo.AdminMerchantAuditVO;
import com.example.backend.vo.AdminReportVO;
import com.example.backend.vo.AdminAuthSecuritySettingsVO;
import com.example.backend.vo.AdminSecuritySettingLogVO;
import com.example.backend.vo.AdminStatsVO;
import com.example.backend.vo.AdminUserVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

/**
 * 管理员后台接口
 */
@RestController
@Validated
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private AdminService adminService;

    @GetMapping("/stats")
    public Result<AdminStatsVO> getStats() {
        ControllerAccessUtils.requireAdmin("仅管理员可访问");
        return Result.success(adminService.getStats());
    }

    @GetMapping("/security-settings")
    public Result<AdminAuthSecuritySettingsVO> getAuthSecuritySettings() {
        ControllerAccessUtils.requireAdmin("仅管理员可访问");
        return Result.success(adminService.getAuthSecuritySettings());
    }

    @PutMapping("/security-settings/password-force-change")
    public Result<AdminAuthSecuritySettingsVO> updateForcePasswordChangeSetting(
            @RequestBody @Valid AdminForcePasswordChangeSettingDTO dto) {
        ControllerAccessUtils.requireAdmin("仅管理员可操作");
        return Result.success(adminService.updateForcePasswordChangeEnabled(dto.getEnabled()));
    }

    @GetMapping("/security-settings/logs")
    public Result<java.util.List<AdminSecuritySettingLogVO>> getSecuritySettingLogs() {
        ControllerAccessUtils.requireAdmin("仅管理员可访问");
        return Result.success(adminService.getSecuritySettingLogs());
    }

    @GetMapping("/jobs")
    public Result<IPage<AdminJobAuditVO>> getJobAuditList(
            @RequestParam(required = false) Integer current,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder,
            @RequestParam(required = false) String timeOrder
    ) {
        ControllerAccessUtils.requireAdmin("仅管理员可访问");
        Page<AdminJobAuditVO> mpPage = PageQueryUtils.buildPage(current, page, size, 20);
        IPage<AdminJobAuditVO> result = adminService.getJobAuditList(mpPage, keyword, status, sortField, sortOrder, timeOrder);
        return Result.success(result);
    }

    @GetMapping("/jobs/counts")
    public Result<AdminJobAuditCountVO> getJobAuditCounts() {
        ControllerAccessUtils.requireAdmin("仅管理员可访问");
        return Result.success(adminService.getJobAuditCounts());
    }

    @PutMapping("/jobs/{id}/audit")
    public Result<?> auditJob(@PathVariable @Positive(message = "职位ID必须为正数") Long id, @RequestBody @Valid AdminAuditDTO dto) {
        ControllerAccessUtils.requireAdmin("仅管理员可操作");
        if (dto.getStatus() == 2 && (dto.getReason() == null || dto.getReason().trim().isEmpty())) {
            return Result.error(400, "驳回原因不能为空");
        }
        adminService.auditJob(id, dto.getStatus(), dto.getReason());
        return Result.success("操作成功");
    }

    @PutMapping("/jobs/batch/audit")
    public Result<?> auditJobBatch(@RequestBody @Valid AdminBatchAuditDTO dto) {
        ControllerAccessUtils.requireAdmin("仅管理员可操作");
        if (dto.getStatus() == 2 && (dto.getReason() == null || dto.getReason().trim().isEmpty())) {
            return Result.error(400, "驳回原因不能为空");
        }
        adminService.auditJobBatch(dto.getIds(), dto.getStatus(), dto.getReason());
        return Result.success("批量操作成功");
    }

    @GetMapping("/jobs/{id}/logs")
    public Result<java.util.List<com.example.backend.entity.AuditLog>> getJobAuditLogs(@PathVariable @Positive(message = "职位ID必须为正数") Long id) {
        ControllerAccessUtils.requireAdmin("仅管理员可访问");
        return Result.success(adminService.getJobAuditLogs(id));
    }

    @PutMapping("/jobs/{id}/revoke")
    public Result<?> revokeJobAudit(@PathVariable @Positive(message = "职位ID必须为正数") Long id, @RequestBody @Valid AdminAuditRevokeDTO dto) {
        ControllerAccessUtils.requireAdmin("仅管理员可操作");
        if (dto.getReason() == null || dto.getReason().trim().isEmpty()) {
            return Result.error(400, "撤回原因不能为空");
        }
        adminService.revokeJobAudit(id, dto.getReason());
        return Result.success("已撤回审核");
    }

    @GetMapping("/merchants")
    public Result<IPage<AdminMerchantAuditVO>> getMerchantAuditList(
            @RequestParam(required = false) Integer current,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status
    ) {
        ControllerAccessUtils.requireAdmin("仅管理员可访问");
        Page<AdminMerchantAuditVO> mpPage = PageQueryUtils.buildPage(current, page, size, 20);
        IPage<AdminMerchantAuditVO> result = adminService.getMerchantAuditList(mpPage, keyword, status);
        return Result.success(result);
    }

    @PutMapping("/merchants/{id}/audit")
    public Result<?> auditMerchant(@PathVariable @Positive(message = "商家ID必须为正数") Long id, @RequestBody @Valid AdminAuditDTO dto) {
        ControllerAccessUtils.requireAdmin("仅管理员可操作");
        if (dto.getStatus() == 2 && (dto.getReason() == null || dto.getReason().trim().isEmpty())) {
            return Result.error(400, "驳回原因不能为空");
        }
        adminService.auditMerchant(id, dto.getStatus(), dto.getReason());
        return Result.success("操作成功");
    }

    @PutMapping("/merchants/batch/audit")
    public Result<?> auditMerchantBatch(@RequestBody @Valid AdminBatchAuditDTO dto) {
        ControllerAccessUtils.requireAdmin("仅管理员可操作");
        if (dto.getStatus() == 2 && (dto.getReason() == null || dto.getReason().trim().isEmpty())) {
            return Result.error(400, "驳回原因不能为空");
        }
        adminService.auditMerchantBatch(dto.getIds(), dto.getStatus(), dto.getReason());
        return Result.success("批量操作成功");
    }

    @GetMapping("/merchants/{id}/logs")
    public Result<java.util.List<com.example.backend.entity.AuditLog>> getMerchantAuditLogs(@PathVariable @Positive(message = "商家ID必须为正数") Long id) {
        ControllerAccessUtils.requireAdmin("仅管理员可访问");
        return Result.success(adminService.getMerchantAuditLogs(id));
    }

    @PutMapping("/merchants/{id}/status")
    public Result<?> updateMerchantStatus(@PathVariable @Positive(message = "商家ID必须为正数") Long id, @RequestBody @Valid AdminMerchantStatusDTO dto) {
        ControllerAccessUtils.requireAdmin("仅管理员可操作");
        if (dto.getStatus() == null || (dto.getStatus() != 0 && dto.getStatus() != 1 && dto.getStatus() != 2)) {
            return Result.error(400, "发布状态不合法");
        }
        adminService.updateMerchantPublishStatus(id, dto.getStatus(), dto.getReason());
        return Result.success("操作成功");
    }

    @GetMapping("/reports")
    public Result<IPage<AdminReportVO>> getReportList(
            @RequestParam(required = false) Integer current,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer status
    ) {
        ControllerAccessUtils.requireAdmin("仅管理员可访问");
        Page<AdminReportVO> mpPage = PageQueryUtils.buildPage(current, page, size, 20);
        IPage<AdminReportVO> result = adminService.getReportList(mpPage, type, status);
        return Result.success(result);
    }

    @PutMapping("/reports/{id}/handle")
    public Result<?> handleReport(@PathVariable @Positive(message = "举报ID必须为正数") Long id, @RequestBody @Valid AdminReportHandleDTO dto) {
        ControllerAccessUtils.requireAdmin("仅管理员可操作");
        if (dto.getStatus() == 2 && (dto.getResult() == null || dto.getResult().trim().isEmpty())) {
            dto.setResult("驳回举报");
        }
        adminService.handleReport(id, dto.getStatus(), dto.getAction(), dto.getResult());
        return Result.success("操作成功");
    }

    @PutMapping("/reports/batch/handle")
    public Result<?> handleReportBatch(@RequestBody @Valid AdminBatchReportHandleDTO dto) {
        ControllerAccessUtils.requireAdmin("仅管理员可操作");
        if (dto.getStatus() == 2 && (dto.getResult() == null || dto.getResult().trim().isEmpty())) {
            dto.setResult("驳回举报");
        }
        adminService.handleReportBatch(dto.getIds(), dto.getStatus(), dto.getAction(), dto.getResult());
        return Result.success("批量操作成功");
    }

    @GetMapping("/reports/{id}/logs")
    public Result<java.util.List<com.example.backend.entity.AuditLog>> getReportLogs(@PathVariable @Positive(message = "举报ID必须为正数") Long id) {
        ControllerAccessUtils.requireAdmin("仅管理员可访问");
        return Result.success(adminService.getReportLogs(id));
    }

    @GetMapping("/users")
    public Result<IPage<AdminUserVO>> getUserList(
            @RequestParam(required = false) Integer current,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer banStatus
    ) {
        ControllerAccessUtils.requireAdmin("仅管理员可访问");
        Page<AdminUserVO> mpPage = PageQueryUtils.buildPage(current, page, size, 20);
        IPage<AdminUserVO> result = adminService.getUserList(mpPage, keyword, role, status, banStatus);
        return Result.success(result);
    }

    @PutMapping("/users/{id}/ban")
    public Result<?> updateUserBan(@PathVariable @Positive(message = "账号ID必须为正数") Long id, @RequestBody @Valid AdminUserBanDTO dto) {
        ControllerAccessUtils.requireAdmin("仅管理员可操作");
        if (dto.getBanStatus() == null || (dto.getBanStatus() != 0 && dto.getBanStatus() != 1 && dto.getBanStatus() != 2)) {
            return Result.error(400, "封禁状态不合法");
        }
        adminService.updateUserBan(id, dto.getBanStatus(), dto.getBanUntil(), dto.getBanReason());
        return Result.success("操作成功");
    }

    @PutMapping("/users/{id}/password/reset")
    public Result<?> resetUserPassword(@PathVariable @Positive(message = "账号ID必须为正数") Long id, @RequestBody @Valid AdminResetPasswordDTO dto) {
        ControllerAccessUtils.requireAdmin("仅管理员可操作");
        if (!Objects.equals(dto.getNewPassword(), dto.getConfirmPassword())) {
            return Result.error(400, "两次输入的密码不一致");
        }
        adminService.resetUserPassword(id, dto.getNewPassword(), dto.getReason());
        return Result.success("密码已重置");
    }
}
