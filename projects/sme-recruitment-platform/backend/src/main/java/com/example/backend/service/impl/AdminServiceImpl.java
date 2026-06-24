/*
 * 文件速览：
 * 1. 文件职责：实现管理员后台的审核、举报、账号风控、密码重置、安全设置与统计逻辑，并联动平台治理通知。
 * 2. 对外入口：AdminService 接口实现，由 AdminController 调用。
 * 3. 关键结构：职位审核、商家审核、举报处理、账号封禁、治理通知联动、密码重置、安全开关、安全日志、统计聚合。
 * 4. 阅读建议：先看审核与举报方法里的治理通知联动，再看账号与安全设置相关逻辑，最后看统计方法。
 */
package com.example.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.backend.config.RuntimeAuthSecuritySettings;
import com.example.backend.exception.ApiException;
import com.example.backend.entity.JobInfo;
import com.example.backend.entity.MerchantInfo;
import com.example.backend.entity.AuditLog;
import com.example.backend.entity.ReportInfo;
import com.example.backend.entity.SysUser;
import com.example.backend.mapper.JobInfoMapper;
import com.example.backend.mapper.MerchantInfoMapper;
import com.example.backend.mapper.ReportInfoMapper;
import com.example.backend.service.AdminService;
import com.example.backend.service.AuditLogService;
import com.example.backend.service.GovernanceNoticeService;
import com.example.backend.service.JobInfoService;
import com.example.backend.service.MerchantInfoService;
import com.example.backend.service.ReportInfoService;
import com.example.backend.service.SysUserService;
import com.example.backend.utils.SecurityUtils;
import com.example.backend.vo.AdminAuthSecuritySettingsVO;
import com.example.backend.vo.AdminJobAuditVO;
import com.example.backend.vo.AdminJobAuditCountVO;
import com.example.backend.vo.AdminMerchantAuditVO;
import com.example.backend.vo.AdminReportVO;
import com.example.backend.vo.AdminSecuritySettingLogVO;
import com.example.backend.vo.AdminStatsVO;
import com.example.backend.vo.AdminUserVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AdminServiceImpl implements AdminService {
    // 管理员后台核心业务实现（审核、举报、统计）

    @Autowired
    private JobInfoMapper jobInfoMapper;

    @Autowired
    private MerchantInfoMapper merchantInfoMapper;

    @Autowired
    private ReportInfoMapper reportInfoMapper;

    @Autowired
    private JobInfoService jobInfoService;

    @Autowired
    private MerchantInfoService merchantInfoService;

    @Autowired
    private ReportInfoService reportInfoService;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private SysUserService sysUserService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RuntimeAuthSecuritySettings runtimeAuthSecuritySettings;

    @Autowired
    private GovernanceNoticeService governanceNoticeService;

    @Override
    public IPage<AdminJobAuditVO> getJobAuditList(Page<?> page, String keyword, Integer status, String sortField, String sortOrder, String timeOrder) {
        return jobInfoMapper.selectAdminAuditList(page, keyword, status, sortField, sortOrder, timeOrder);
    }

    @Override
    public void auditJob(Long id, Integer status, String reason) {
        JobInfo jobInfo = getJobOrThrow(id);
        jobInfo.setAuditStatus(status);
        jobInfo.setAuditReason(status != null && status == 2 ? reason : null);
        jobInfo.setAuditTime(LocalDateTime.now());
        jobInfo.setUpdateTime(LocalDateTime.now());
        // 驳回时强制下架，避免误展示
        if (status != null && status == 2) {
            jobInfo.setStatus(0);
        }
        jobInfoService.updateById(jobInfo);

        Long adminUserId = SecurityUtils.getUserId();
        if (status != null && status == 2) {
            governanceNoticeService.syncJobRectifyNoticeOnReject(id, jobInfo.getMerchantId(), reason, adminUserId);
        } else if (status != null && status == 1) {
            governanceNoticeService.syncJobRectifyNoticeOnApprove(id, jobInfo.getMerchantId(), adminUserId);
        }

        String detail = "审核职位: " + (status != null && status == 1 ? "通过" : "驳回")
                + (reason != null ? ("，原因：" + reason) : "");
        auditLogService.record("JOB", "AUDIT", id, detail);
    }

    @Override
    public void auditJobBatch(List<Long> ids, Integer status, String reason) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (Long id : ids) {
            auditJob(id, status, reason);
        }
    }

    @Override
    public void revokeJobAudit(Long id, String reason) {
        JobInfo jobInfo = getJobOrThrow(id);
        Integer previousStatus = jobInfo.getAuditStatus();
        if (previousStatus == null || previousStatus == 0) {
            throw new ApiException(400, "当前状态无需撤回");
        }
        jobInfo.setAuditStatus(0);
        jobInfo.setAuditReason(null);
        jobInfo.setAuditTime(null);
        jobInfo.setUpdateTime(LocalDateTime.now());
        jobInfoService.updateById(jobInfo);

        String detail = "撤回审核: 原状态=" + previousStatus
                + "，原因=" + reason;
        auditLogService.record("JOB", "REVOKE", id, detail);
    }

    @Override
    public List<AuditLog> getJobAuditLogs(Long jobId) {
        return listModuleAuditLogs("JOB", jobId);
    }

    @Override
    public AdminJobAuditCountVO getJobAuditCounts() {
        AdminJobAuditCountVO vo = new AdminJobAuditCountVO();
        vo.setTotal(countAllJobs());
        vo.setPending(countJobsByAuditStatus(0));
        vo.setApproved(countJobsByAuditStatus(1));
        vo.setRejected(countJobsByAuditStatus(2));
        return vo;
    }

    @Override
    public IPage<AdminMerchantAuditVO> getMerchantAuditList(Page<?> page, String keyword, Integer status) {
        return merchantInfoMapper.selectAdminAuditList(page, keyword, status);
    }

    @Override
    public void auditMerchant(Long id, Integer status, String reason) {
        MerchantInfo merchantInfo = getMerchantOrThrow(id);
        merchantInfo.setAuditStatus(status);
        merchantInfo.setAuditReason(status != null && status == 2 ? reason : null);
        merchantInfo.setAuditTime(LocalDateTime.now());
        merchantInfo.setUpdateTime(LocalDateTime.now());
        merchantInfoService.updateById(merchantInfo);
        Long adminUserId = SecurityUtils.getUserId();
        if (status != null && status == 2) {
            governanceNoticeService.syncMerchantRectifyNoticeOnReject(id, merchantInfo.getUserId(), reason, adminUserId);
        } else if (status != null && status == 1) {
            governanceNoticeService.syncMerchantRectifyNoticeOnApprove(id, merchantInfo.getUserId(), adminUserId);
        }
        String detail = "审核商家: " + (status != null && status == 1 ? "通过" : "驳回")
                + (reason != null ? ("，原因：" + reason) : "");
        auditLogService.record("MERCHANT", "AUDIT", id, detail);
    }

    @Override
    public void auditMerchantBatch(List<Long> ids, Integer status, String reason) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (Long id : ids) {
            auditMerchant(id, status, reason);
        }
    }

    @Override
    public void updateMerchantPublishStatus(Long id, Integer status, String reason) {
        MerchantInfo merchantInfo = getMerchantOrThrow(id);
        merchantInfo.setPublishStatus(status);
        merchantInfo.setUpdateTime(LocalDateTime.now());
        merchantInfoService.updateById(merchantInfo);

        String detail = "更新发布状态=" + status + (reason != null ? ("，原因：" + reason) : "");
        auditLogService.record("MERCHANT", "STATUS", id, detail);
    }

    @Override
    public List<AuditLog> getMerchantAuditLogs(Long merchantId) {
        return listModuleAuditLogs("MERCHANT", merchantId);
    }

    @Override
    public IPage<AdminReportVO> getReportList(Page<?> page, String type, Integer status) {
        return reportInfoMapper.selectAdminReportList(page, type, status);
    }

    @Override
    public void handleReport(Long id, Integer status, String action, String result) {
        ReportInfo reportInfo = getReportOrThrow(id);
        LocalDateTime now = LocalDateTime.now();
        Long adminUserId = SecurityUtils.getUserId();
        String safeAction = action == null ? "" : action.trim();
        String actionCode = normalizeReportAction(safeAction);
        String actionCodeToSave = null;

        // 仅当“已处理”时尝试执行关联动作
        if (status != null && status == 1) {
            actionCodeToSave = applyHandledReportAction(reportInfo, actionCode, result, now);
        } else if (status != null && status == 2) {
            // 驳回动作统一落库为 REJECT，便于后续统计口径统一
            actionCodeToSave = "REJECT";
        }

        updateReportHandleResult(reportInfo, status, result, actionCodeToSave, adminUserId, now);

        if (status != null && (status == 1 || status == 2)) {
            governanceNoticeService.syncReportNoticesOnHandle(reportInfo, actionCodeToSave, adminUserId);
        }

        String detail = "处理举报: 状态=" + status
                + (StringUtils.hasText(actionCodeToSave) ? ("，动作=" + actionCodeToSave) : "")
                + (result != null ? ("，说明=" + result) : "");
        auditLogService.record("REPORT", "HANDLE", id, detail);
    }

    /**
     * 已处理状态下按举报对象类型执行关联动作，主流程只保留状态编排。
     */
    private String applyHandledReportAction(ReportInfo reportInfo, String actionCode, String result, LocalDateTime now) {
        if (!StringUtils.hasText(actionCode)) {
            throw new ApiException(400, "处理动作不能为空");
        }

        String reportType = reportInfo.getType() == null ? "" : reportInfo.getType().trim().toUpperCase();
        if ("JOB".equals(reportType)) {
            handleJobReportAction(reportInfo, actionCode, result, now);
        } else if ("MERCHANT".equals(reportType)) {
            handleMerchantReportAction(reportInfo, actionCode, now);
        } else if ("USER".equals(reportType)) {
            handleUserReportAction(reportInfo, actionCode, result, now);
        }
        return actionCode;
    }

    /**
     * 职位举报支持警告、下架，以及附带商家限制的重度动作。
     */
    private void handleJobReportAction(ReportInfo reportInfo, String actionCode, String result, LocalDateTime now) {
        assertAllowedReportAction(
                actionCode,
                "职位举报处理动作不合法",
                "JOB_WARN",
                "JOB_OFFLINE",
                "JOB_OFFLINE_LIMIT_MERCHANT",
                "JOB_OFFLINE_BAN_MERCHANT"
        );
        JobInfo jobInfo = jobInfoService.getById(reportInfo.getTargetId());
        if (jobInfo == null) {
            return;
        }

        if ("JOB_WARN".equals(actionCode)) {
            jobInfo.setAuditReason(result != null ? result : "举报处理：警告提醒");
            jobInfo.setUpdateTime(now);
            jobInfoService.updateById(jobInfo);
        } else {
            jobInfo.setStatus(0);
            jobInfo.setAuditStatus(2);
            jobInfo.setAuditReason(resolveJobReportAuditReason(actionCode, result));
            jobInfo.setAuditTime(now);
            jobInfo.setUpdateTime(now);
            jobInfoService.updateById(jobInfo);
        }

        if ("JOB_OFFLINE_LIMIT_MERCHANT".equals(actionCode) || "JOB_OFFLINE_BAN_MERCHANT".equals(actionCode)) {
            MerchantInfo merchantInfo = findMerchantByUserIdOrProfileId(jobInfo.getMerchantId());
            if (merchantInfo == null) {
                return;
            }
            merchantInfo.setPublishStatus("JOB_OFFLINE_LIMIT_MERCHANT".equals(actionCode) ? 0 : 2);
            merchantInfo.setUpdateTime(now);
            merchantInfoService.updateById(merchantInfo);
        }
    }

    /**
     * 商家举报支持警告、限制发布、封禁，警告场景仅刷新更新时间保留处理痕迹。
     */
    private void handleMerchantReportAction(ReportInfo reportInfo, String actionCode, LocalDateTime now) {
        assertAllowedReportAction(
                actionCode,
                "企业举报处理动作不合法",
                "MERCHANT_WARN",
                "MERCHANT_LIMIT",
                "MERCHANT_BAN"
        );
        MerchantInfo merchantInfo = findMerchantByUserIdOrProfileId(reportInfo.getTargetId());
        if (merchantInfo == null) {
            return;
        }

        if ("MERCHANT_LIMIT".equals(actionCode)) {
            merchantInfo.setPublishStatus(0);
        } else if ("MERCHANT_BAN".equals(actionCode)) {
            merchantInfo.setPublishStatus(2);
        }
        merchantInfo.setUpdateTime(now);
        merchantInfoService.updateById(merchantInfo);
    }

    /**
     * 账号举报支持警告、禁用、封禁与拉黑，警告场景同样只记录处理时间。
     */
    private void handleUserReportAction(ReportInfo reportInfo, String actionCode, String result, LocalDateTime now) {
        assertAllowedReportAction(
                actionCode,
                "账号举报处理动作不合法",
                "USER_WARN",
                "USER_DISABLE",
                "USER_BAN",
                "USER_BLACKLIST"
        );
        SysUser user = sysUserService.getById(reportInfo.getTargetId());
        if (user == null) {
            return;
        }

        if ("USER_DISABLE".equals(actionCode)) {
            user.setStatus(0);
        } else if ("USER_BAN".equals(actionCode)) {
            user.setBanStatus(1);
            user.setBanReason(result);
            user.setBanUntil(null);
        } else if ("USER_BLACKLIST".equals(actionCode)) {
            user.setBanStatus(2);
            user.setBanReason(result);
            user.setBanUntil(null);
        }
        user.setUpdateTime(now);
        sysUserService.updateById(user);
    }

    /**
     * 举报主记录的状态、处理人和动作码统一在这里收口，避免多处分支写同一组字段。
     */
    private void updateReportHandleResult(ReportInfo reportInfo,
                                          Integer status,
                                          String result,
                                          String actionCodeToSave,
                                          Long adminUserId,
                                          LocalDateTime now) {
        reportInfo.setStatus(status);
        reportInfo.setResult(result);
        if (status != null && (status == 1 || status == 2)) {
            reportInfo.setActionCode(actionCodeToSave);
            reportInfo.setHandledBy(adminUserId);
            reportInfo.setHandledTime(now);
        } else if (status != null && status == 0) {
            reportInfo.setActionCode(null);
            reportInfo.setHandledBy(null);
            reportInfo.setHandledTime(null);
        }
        reportInfo.setUpdateTime(now);
        reportInfoService.updateById(reportInfo);
    }

    /**
     * 统一校验举报动作码，避免三类举报对象各自维护长串条件判断。
     */
    private void assertAllowedReportAction(String actionCode, String errorMessage, String... allowedActions) {
        for (String allowedAction : allowedActions) {
            if (allowedAction.equals(actionCode)) {
                return;
            }
        }
        throw new ApiException(400, errorMessage);
    }

    /**
     * 下架类职位举报根据动作强度补齐默认审核原因，保持原有文案口径。
     */
    private String resolveJobReportAuditReason(String actionCode, String result) {
        if (result != null) {
            return result;
        }
        if ("JOB_OFFLINE_LIMIT_MERCHANT".equals(actionCode)) {
            return "举报处理：下架职位并限制企业发布";
        }
        if ("JOB_OFFLINE_BAN_MERCHANT".equals(actionCode)) {
            return "举报处理：下架职位并封禁企业";
        }
        return "举报处理：下架职位";
    }

    @Override
    public void handleReportBatch(List<Long> ids, Integer status, String action, String result) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (Long id : ids) {
            handleReport(id, status, action, result);
        }
    }

    @Override
    public List<AuditLog> getReportLogs(Long reportId) {
        return listModuleAuditLogs("REPORT", reportId);
    }

    @Override
    public IPage<AdminUserVO> getUserList(Page<?> page, String keyword, String role, Integer status, Integer banStatus) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(SysUser::getUsername, keyword)
                    .or()
                    .like(SysUser::getNickname, keyword)
                    .or()
                    .like(SysUser::getPhone, keyword)
                    .or()
                    .like(SysUser::getEmail, keyword));
        }
        if (StringUtils.hasText(role)) {
            wrapper.eq(SysUser::getRole, role.trim().toUpperCase());
        }
        if (status != null) {
            wrapper.eq(SysUser::getStatus, status);
        }
        if (banStatus != null) {
            wrapper.eq(SysUser::getBanStatus, banStatus);
        }
        wrapper.orderByDesc(SysUser::getCreateTime);

        Page<SysUser> mpPage = new Page<>(page.getCurrent(), page.getSize());
        IPage<SysUser> result = sysUserService.page(mpPage, wrapper);
        IPage<AdminUserVO> voPage = result.convert(this::toAdminUserVO);
        attachLatestPasswordResetInfo(voPage.getRecords());
        return voPage;
    }

    @Override
    public void updateUserBan(Long userId, Integer banStatus, LocalDateTime banUntil, String banReason) {
        SysUser user = getUserOrThrow(userId);
        if (banStatus == null || (banStatus != 0 && banStatus != 1 && banStatus != 2)) {
            throw new ApiException(400, "封禁状态不合法");
        }
        user.setBanStatus(banStatus);
        if (banStatus == 0) {
            user.setBanReason(null);
            user.setBanUntil(null);
        } else {
            user.setBanReason(StringUtils.hasText(banReason) ? banReason.trim() : null);
            user.setBanUntil(banUntil);
        }
        user.setUpdateTime(LocalDateTime.now());
        sysUserService.updateById(user);
        governanceNoticeService.syncUserBanNotice(userId, banStatus, user.getBanUntil(), user.getBanReason(), SecurityUtils.getUserId());

        String detail = "更新账号封禁状态=" + banStatus
                + (StringUtils.hasText(user.getBanReason()) ? ("，原因：" + user.getBanReason()) : "")
                + (user.getBanUntil() != null ? ("，截止：" + user.getBanUntil()) : "");
        auditLogService.record("AUTH", "BAN", userId, detail);
    }

    @Override
    public void resetUserPassword(Long userId, String newPassword, String reason) {
        SysUser user = getUserOrThrow(userId);
        if (!StringUtils.hasText(newPassword)) {
            throw new ApiException(400, "临时密码不能为空");
        }
        if (!StringUtils.hasText(reason)) {
            throw new ApiException(400, "重置原因不能为空");
        }
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new ApiException(400, "新密码不能与当前密码一致");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdateTime(LocalDateTime.now());
        sysUserService.updateById(user);

        String safeReason = reason.trim();
        String detail = "管理员重置密码，账号=" + user.getUsername()
                + "，原因=" + safeReason;
        auditLogService.record("AUTH", "RESET_PASSWORD", userId, detail);
    }

    @Override
    public AdminAuthSecuritySettingsVO getAuthSecuritySettings() {
        return buildAuthSecuritySettingsVO();
    }

    @Override
    public AdminAuthSecuritySettingsVO updateForcePasswordChangeEnabled(Boolean enabled) {
        if (enabled == null) {
            throw new ApiException(400, "开关状态不能为空");
        }
        runtimeAuthSecuritySettings.updateForcePasswordChangeEnabled(enabled);
        auditLogService.record(
                "AUTH",
                "UPDATE_FORCE_PASSWORD_CHANGE",
                0L,
                "更新临时密码登录后强制修改密码开关为" + (enabled ? "开启" : "关闭")
        );
        return buildAuthSecuritySettingsVO();
    }

    @Override
    public List<AdminSecuritySettingLogVO> getSecuritySettingLogs() {
        List<AuditLog> logs = listLatestAuthActionLogs("UPDATE_FORCE_PASSWORD_CHANGE", 10);
        if (logs == null || logs.isEmpty()) {
            return List.of();
        }

        Map<Long, String> operatorNameById = buildOperatorNameMap(logs);
        return logs.stream()
                .map(log -> toSecuritySettingLogVO(log, operatorNameById))
                .toList();
    }

    private AdminUserVO toAdminUserVO(SysUser user) {
        AdminUserVO vo = new AdminUserVO();
        if (user == null) {
            return vo;
        }
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        vo.setBanStatus(user.getBanStatus());
        vo.setBanReason(user.getBanReason());
        vo.setBanUntil(user.getBanUntil());
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }

    /**
     * 为管理员账号列表补充最近一次密码重置记录，便于后台直接查看重置痕迹。
     */
    private void attachLatestPasswordResetInfo(List<AdminUserVO> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Set<Long> userIds = new HashSet<>();
        for (AdminUserVO record : records) {
            if (record != null && record.getId() != null) {
                userIds.add(record.getId());
            }
        }
        if (userIds.isEmpty()) {
            return;
        }

        List<AuditLog> resetLogs = auditLogService.list(buildAuthActionLogQuery("RESET_PASSWORD")
                .in(AuditLog::getTargetId, userIds));
        if (resetLogs == null || resetLogs.isEmpty()) {
            return;
        }

        Map<Long, AuditLog> latestLogByUserId = new HashMap<>();
        Set<Long> operatorIds = new HashSet<>();
        for (AuditLog log : resetLogs) {
            if (log == null || log.getTargetId() == null || latestLogByUserId.containsKey(log.getTargetId())) {
                continue;
            }
            latestLogByUserId.put(log.getTargetId(), log);
            if (log.getOperatorId() != null) {
                operatorIds.add(log.getOperatorId());
            }
        }

        Map<Long, String> operatorNameById = buildUserDisplayNameMap(operatorIds);

        for (AdminUserVO record : records) {
            if (record == null || record.getId() == null) {
                continue;
            }
            AuditLog log = latestLogByUserId.get(record.getId());
            if (log == null) {
                continue;
            }
            record.setLatestPasswordResetTime(log.getCreateTime());
            record.setLatestPasswordResetOperatorId(log.getOperatorId());
            record.setLatestPasswordResetOperatorName(operatorNameById.get(log.getOperatorId()));
            record.setLatestPasswordResetDetail(log.getDetail());
        }
    }

    private String resolveDisplayName(SysUser user) {
        if (user == null) {
            return "";
        }
        if (StringUtils.hasText(user.getNickname())) {
            return user.getNickname().trim();
        }
        if (StringUtils.hasText(user.getUsername())) {
            return user.getUsername().trim();
        }
        return String.valueOf(user.getId());
    }

    /**
     * 统一构造用户展示名映射，供密码重置记录与安全日志复用。
     */
    private Map<Long, String> buildUserDisplayNameMap(Set<Long> userIds) {
        Map<Long, String> userNameById = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return userNameById;
        }
        List<SysUser> users = sysUserService.listByIds(userIds);
        for (SysUser user : users) {
            if (user == null || user.getId() == null) {
                continue;
            }
            userNameById.put(user.getId(), resolveDisplayName(user));
        }
        return userNameById;
    }

    /**
     * 将审计日志中的操作者补全为可读名称，便于看板直接展示。
     */
    private Map<Long, String> buildOperatorNameMap(List<AuditLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return new HashMap<>();
        }
        Set<Long> operatorIds = new HashSet<>();
        for (AuditLog log : logs) {
            if (log != null && log.getOperatorId() != null) {
                operatorIds.add(log.getOperatorId());
            }
        }
        return buildUserDisplayNameMap(operatorIds);
    }

    /**
     * 模块审计日志统一按时间倒序取最近 30 条，避免不同模块重复拼查询。
     */
    private List<AuditLog> listModuleAuditLogs(String module, Long targetId) {
        return auditLogService.list(new LambdaQueryWrapper<AuditLog>()
                .eq(AuditLog::getModule, module)
                .eq(AuditLog::getTargetId, targetId)
                .orderByDesc(AuditLog::getCreateTime)
                .last("LIMIT 30"));
    }

    /**
     * AUTH 模块的动作日志查询口径统一收口，便于安全设置与密码重置记录复用。
     */
    private LambdaQueryWrapper<AuditLog> buildAuthActionLogQuery(String action) {
        return new LambdaQueryWrapper<AuditLog>()
                .eq(AuditLog::getModule, "AUTH")
                .eq(AuditLog::getAction, action)
                .orderByDesc(AuditLog::getCreateTime)
                .orderByDesc(AuditLog::getId);
    }

    /**
     * 用于读取固定数量的最新 AUTH 动作日志，避免调用方各自维护 LIMIT 语句。
     */
    private List<AuditLog> listLatestAuthActionLogs(String action, int limit) {
        return auditLogService.list(buildAuthActionLogQuery(action)
                .last("LIMIT " + limit));
    }

    /**
     * 统一把统计结果转成 int，保持超大数据量时仍能显式暴露异常。
     */
    private int toIntCount(long count) {
        return Math.toIntExact(count);
    }

    private int countAllJobs() {
        return toIntCount(jobInfoService.count());
    }

    private int countJobsByAuditStatus(int auditStatus) {
        return toIntCount(jobInfoService.count(new LambdaQueryWrapper<JobInfo>()
                .eq(JobInfo::getAuditStatus, auditStatus)));
    }

    private int countJobsCreatedBetween(LocalDateTime start, LocalDateTime end) {
        return toIntCount(jobInfoService.count(new LambdaQueryWrapper<JobInfo>()
                .between(JobInfo::getCreateTime, start, end)));
    }

    private int countAllMerchants() {
        return toIntCount(merchantInfoService.count());
    }

    private int countMerchantsByAuditStatus(int auditStatus) {
        return toIntCount(merchantInfoService.count(new LambdaQueryWrapper<MerchantInfo>()
                .eq(MerchantInfo::getAuditStatus, auditStatus)));
    }

    private int countMerchantsUpdatedBetween(LocalDateTime start, LocalDateTime end) {
        return toIntCount(merchantInfoService.count(new LambdaQueryWrapper<MerchantInfo>()
                .between(MerchantInfo::getUpdateTime, start, end)));
    }

    private int countAllReports() {
        return toIntCount(reportInfoService.count());
    }

    private int countReportsByStatus(int status) {
        return toIntCount(reportInfoService.count(new LambdaQueryWrapper<ReportInfo>()
                .eq(ReportInfo::getStatus, status)));
    }

    private int countReportsCreatedBetween(LocalDateTime start, LocalDateTime end) {
        return toIntCount(reportInfoService.count(new LambdaQueryWrapper<ReportInfo>()
                .between(ReportInfo::getCreateTime, start, end)));
    }

    /**
     * 统一获取必须存在的职位，避免各业务分支重复判空。
     */
    private JobInfo getJobOrThrow(Long id) {
        JobInfo jobInfo = jobInfoService.getById(id);
        if (jobInfo == null) {
            throw new ApiException(404, "职位不存在");
        }
        return jobInfo;
    }

    /**
     * 统一获取必须存在的商家，避免相同异常文案散落多处。
     */
    private MerchantInfo getMerchantOrThrow(Long id) {
        MerchantInfo merchantInfo = merchantInfoService.getById(id);
        if (merchantInfo == null) {
            throw new ApiException(404, "商家不存在");
        }
        return merchantInfo;
    }

    /**
     * 统一获取必须存在的举报记录，保持后台异常口径一致。
     */
    private ReportInfo getReportOrThrow(Long id) {
        ReportInfo reportInfo = reportInfoService.getById(id);
        if (reportInfo == null) {
            throw new ApiException(404, "举报记录不存在");
        }
        return reportInfo;
    }

    /**
     * 统一获取必须存在的账号，减少账号风控与密码重置的重复判空。
     */
    private SysUser getUserOrThrow(Long userId) {
        SysUser user = sysUserService.getById(userId);
        if (user == null) {
            throw new ApiException(404, "账号不存在");
        }
        return user;
    }

    /**
     * 兼容 userId 与商家资料 id 两种来源，避免举报处理里重复兜底查询。
     */
    private MerchantInfo findMerchantByUserIdOrProfileId(Long merchantIdOrUserId) {
        if (merchantIdOrUserId == null) {
            return null;
        }
        MerchantInfo merchantInfo = merchantInfoService.getByUserId(merchantIdOrUserId);
        if (merchantInfo != null) {
            return merchantInfo;
        }
        return merchantInfoService.getById(merchantIdOrUserId);
    }

    /**
     * 将账号安全设置日志转换成前端看板需要的展示结构。
     */
    private AdminSecuritySettingLogVO toSecuritySettingLogVO(AuditLog log, Map<Long, String> operatorNameById) {
        AdminSecuritySettingLogVO vo = new AdminSecuritySettingLogVO();
        if (log == null) {
            return vo;
        }
        vo.setId(log.getId());
        vo.setOperatorId(log.getOperatorId());
        vo.setOperatorRole(log.getOperatorRole());
        vo.setOperatorName(operatorNameById.get(log.getOperatorId()));
        vo.setDetail(log.getDetail());
        vo.setEnabledValue(resolveForcePasswordChangeValue(log.getDetail()));
        vo.setCreateTime(log.getCreateTime());
        return vo;
    }

    /**
     * 从当前固定日志文案中解析开关状态，便于前端使用统一颜色展示。
     */
    private Boolean resolveForcePasswordChangeValue(String detail) {
        if (!StringUtils.hasText(detail)) {
            return null;
        }
        if (detail.contains("开启")) {
            return true;
        }
        if (detail.contains("关闭")) {
            return false;
        }
        return null;
    }

    /**
     * 构造管理员安全设置快照，供看板直接展示当前运行口径。
     */
    private AdminAuthSecuritySettingsVO buildAuthSecuritySettingsVO() {
        AdminAuthSecuritySettingsVO vo = new AdminAuthSecuritySettingsVO();
        boolean currentEnabled = runtimeAuthSecuritySettings.isForcePasswordChangeEnabled();
        boolean defaultEnabled = runtimeAuthSecuritySettings.getDefaultForcePasswordChangeEnabled();
        vo.setForcePasswordChangeEnabled(currentEnabled);
        vo.setDefaultForcePasswordChangeEnabled(defaultEnabled);
        vo.setRuntimeOverrideActive(currentEnabled != defaultEnabled);
        return vo;
    }

    /**
     * 兼容旧文本动作，统一成动作代码
     */
    private String normalizeReportAction(String action) {
        if (action == null) {
            return "";
        }
        String trimmed = action.trim();
        String upper = trimmed.toUpperCase();
        if (upper.startsWith("JOB_") || upper.startsWith("MERCHANT_") || upper.startsWith("USER_")) {
            return upper;
        }
        if ("REJECT".equals(upper) || trimmed.contains("驳回")) {
            return "REJECT";
        }
        if (trimmed.contains("账号") && trimmed.contains("禁用")) {
            return "USER_DISABLE";
        }
        if (trimmed.contains("账号") && trimmed.contains("拉黑")) {
            return "USER_BLACKLIST";
        }
        if (trimmed.contains("账号") && trimmed.contains("封禁")) {
            return "USER_BAN";
        }
        if (trimmed.contains("账号") && trimmed.contains("警告")) {
            return "USER_WARN";
        }
        if (trimmed.contains("下架") && trimmed.contains("限制发布")) {
            return "JOB_OFFLINE_LIMIT_MERCHANT";
        }
        if (trimmed.contains("下架") && trimmed.contains("封禁")) {
            return "JOB_OFFLINE_BAN_MERCHANT";
        }
        if (trimmed.contains("职位") && trimmed.contains("警告")) {
            return "JOB_WARN";
        }
        if (trimmed.contains("下架")) {
            return "JOB_OFFLINE";
        }
        if (trimmed.contains("限制发布")) {
            return "MERCHANT_LIMIT";
        }
        if (trimmed.contains("封禁")) {
            return "MERCHANT_BAN";
        }
        if (trimmed.contains("警告")) {
            return "MERCHANT_WARN";
        }
        return upper;
    }

    @Override
    public AdminStatsVO getStats() {
        AdminStatsVO stats = new AdminStatsVO();

        // 今日开始与结束时间
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = start.plusDays(1);

        // 待审核/待处理
        stats.setJobPending(countJobsByAuditStatus(0));
        stats.setMerchantPending(countMerchantsByAuditStatus(0));
        stats.setReportPending(countReportsByStatus(0));

        // 今日新增
        stats.setTodayJobs(countJobsCreatedBetween(start, end));
        stats.setTodayMerchants(countMerchantsUpdatedBetween(start, end));
        stats.setTodayReports(countReportsCreatedBetween(start, end));

        // 总量
        stats.setTotalJobs(countAllJobs());
        stats.setTotalMerchants(countAllMerchants());
        stats.setTotalReports(countAllReports());

        return stats;
    }
}
