/**
 * 文件速览：
 * 1. 文件职责：处理举报提交与证据上传，覆盖求职者举报职位/企业、商家举报求职者。
 * 2. 关键升级：商家举报求职者时现要求绑定本人的职位，且候选人对该职位存在真实投递关系。
 * 3. 关键入口：/report/submit、/report/evidence。
 * 4. 阅读建议：先看 submit 的角色与归属校验，再看证据归一化与对象快照逻辑。
 */
package com.example.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.backend.common.Result;
import com.example.backend.dto.ReportSubmitDTO;
import com.example.backend.entity.JobDelivery;
import com.example.backend.entity.JobInfo;
import com.example.backend.entity.MerchantInfo;
import com.example.backend.entity.ReportEvidence;
import com.example.backend.entity.ReportInfo;
import com.example.backend.entity.SysUser;
import com.example.backend.service.JobDeliveryService;
import com.example.backend.service.JobInfoService;
import com.example.backend.service.MerchantInfoService;
import com.example.backend.service.AuditLogService;
import com.example.backend.service.ChatService;
import com.example.backend.service.ReportEvidenceService;
import com.example.backend.service.ReportInfoService;
import com.example.backend.service.SysUserService;
import com.example.backend.utils.SecurityUtils;
import com.example.backend.utils.UploadFileUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 举报提交接口
 */
@RestController
@RequestMapping("/report")
public class ReportController {

    @Autowired
    private ReportInfoService reportInfoService;

    @Autowired
    private ReportEvidenceService reportEvidenceService;

    @Autowired
    private SysUserService sysUserService;

    @Autowired
    private JobInfoService jobInfoService;

    @Autowired
    private MerchantInfoService merchantInfoService;

    @Autowired
    private JobDeliveryService jobDeliveryService;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private ChatService chatService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 提交举报（商家可举报求职者；求职者可举报职位/企业）
     */
    @PostMapping("/submit")
    @Transactional(rollbackFor = Exception.class)
    public Result<?> submit(@RequestBody @Valid ReportSubmitDTO dto) {
        Long reporterId = SecurityUtils.getUserId();
        if (reporterId == null) {
            return Result.error(401, "未登录");
        }
        String role = SecurityUtils.getRole();
        String type = dto.getType() == null ? "" : dto.getType().trim().toUpperCase();
        if (!StringUtils.hasText(type)) {
            return Result.error(400, "举报类型不能为空");
        }
        if (dto.getTargetId() == null) {
            return Result.error(400, "举报对象不能为空");
        }
        if (reporterId.equals(dto.getTargetId())) {
            return Result.error(400, "不能举报自己");
        }

        // 角色与类型匹配校验
        if ("USER".equals(type)) {
            if (!SecurityUtils.isMerchantRole(role)) {
                return Result.error(403, "仅商家可举报求职者账号");
            }
        } else if ("JOB".equals(type) || "MERCHANT".equals(type)) {
            if (!SecurityUtils.isApplicantRole(role)) {
                return Result.error(403, "仅求职者可举报职位或企业");
            }
        } else {
            return Result.error(400, "举报类型不合法");
        }

        // 商家举报求职者账号时，证据必须提供（截图/附件）
        if ("USER".equals(type) && !hasValidEvidence(dto.getEvidenceList())) {
            return Result.error(400, "举报求职者账号时请至少上传1份证据截图");
        }

        Long targetId = dto.getTargetId();
        Long notifyUserId = null;
        SysUser targetUser = null;
        JobInfo targetJob = null;
        MerchantInfo targetMerchant = null;
        SysUser targetMerchantUser = null;
        // 目标存在性校验
        if ("USER".equals(type)) {
            targetUser = sysUserService.getById(targetId);
            if (targetUser == null) {
                return Result.error(404, "被举报账号不存在");
            }
            if (!SecurityUtils.isApplicantRole(targetUser.getRole())) {
                return Result.error(403, "仅可举报求职者账号");
            }
            Result<?> relationValidation = validateMerchantUserReportOwnership(dto, reporterId);
            if (relationValidation != null) {
                return relationValidation;
            }
            notifyUserId = targetId;
        }
        if ("JOB".equals(type)) {
            targetJob = jobInfoService.getById(targetId);
            if (targetJob == null) {
                return Result.error(404, "被举报职位不存在");
            }
            notifyUserId = targetJob.getMerchantId();
            if (targetJob.getMerchantId() != null) {
                targetMerchant = merchantInfoService.getByUserId(targetJob.getMerchantId());
                if (targetMerchant == null) {
                    targetMerchant = merchantInfoService.getById(targetJob.getMerchantId());
                }
                targetMerchantUser = sysUserService.getById(targetJob.getMerchantId());
            }
        }
        if ("MERCHANT".equals(type)) {
            targetMerchant = merchantInfoService.getByUserId(targetId);
            if (targetMerchant == null) {
                targetMerchant = merchantInfoService.getById(targetId);
            }
            if (targetMerchant == null) {
                return Result.error(404, "被举报企业不存在");
            }
            // 统一存 userId，确保管理员举报列表能够正确关联
            targetId = targetMerchant.getUserId();
            targetMerchantUser = sysUserService.getById(targetId);
            notifyUserId = targetId;
        }

        List<String> normalizedEvidenceList = normalizeEvidenceList(dto.getEvidenceList());
        String evidence = joinEvidenceList(normalizedEvidenceList);
        String targetSnapshot = buildTargetSnapshot(
                type, targetId, targetJob, targetMerchant, targetMerchantUser, targetUser
        );

        ReportInfo report = new ReportInfo();
        report.setType(type);
        report.setTargetId(targetId);
        report.setReporterId(reporterId);
        report.setReason(dto.getReason() == null ? "" : dto.getReason().trim());
        report.setStatus(0);
        report.setEvidence(evidence);
        report.setTargetSnapshot(targetSnapshot);
        report.setCreateTime(LocalDateTime.now());
        report.setUpdateTime(LocalDateTime.now());
        reportInfoService.save(report);
        saveReportEvidence(report.getId(), reporterId, normalizedEvidenceList, report.getCreateTime());

        // 记录审计日志（便于管理员查看举报提交时间线）
        auditLogService.record("REPORT", "SUBMIT", report.getId(),
                "提交举报:type=" + type + ", targetId=" + targetId);

        // 通知被举报人（系统消息）
        notifyTargetUser(report, dto, type, notifyUserId, reporterId);

        return Result.success();
    }

    /**
     * 校验“商家举报求职者”必须绑定当前商家自己的职位，且双方存在真实投递关系。
     */
    private Result<?> validateMerchantUserReportOwnership(ReportSubmitDTO dto, Long reporterId) {
        if (dto == null || reporterId == null) {
            return Result.error(400, "举报参数不完整");
        }
        if (dto.getJobId() == null) {
            return Result.error(403, "举报求职者账号时必须关联职位");
        }
        JobInfo jobInfo = jobInfoService.getById(dto.getJobId());
        if (jobInfo == null) {
            return Result.error(404, "关联职位不存在");
        }
        if (jobInfo.getMerchantId() == null || !jobInfo.getMerchantId().equals(reporterId)) {
            return Result.error(403, "仅可举报自己职位的候选人");
        }
        long deliveryCount = jobDeliveryService.count(new LambdaQueryWrapper<JobDelivery>()
                .eq(JobDelivery::getJobId, dto.getJobId())
                .eq(JobDelivery::getApplicantId, dto.getTargetId()));
        if (deliveryCount == 0) {
            return Result.error(403, "该候选人与该职位无投递关系");
        }
        return null;
    }

    /**
     * 上传举报证据（图片/PDF）
     */
    @PostMapping("/evidence")
    public Result<String> uploadEvidence(@RequestParam("file") MultipartFile file) {
        Long userId = SecurityUtils.getUserId();
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        if (file == null || file.isEmpty()) {
            return Result.error(400, "文件不能为空");
        }
        long maxSize = 5 * 1024 * 1024;
        if (file.getSize() > maxSize) {
            return Result.error(400, "证据文件不能超过5MB");
        }
        String extension = UploadFileUtils.extractExtension(file.getOriginalFilename());
        if (!UploadFileUtils.isAllowedExtension(extension, "jpg", "jpeg", "png", "pdf")) {
            return Result.error(400, "仅支持 JPG/PNG/PDF 格式");
        }
        try {
            String fileUrl = UploadFileUtils.storeUnderUploads(file, userId, "evidence", "reports", String.valueOf(userId));
            return Result.success(fileUrl);
        } catch (Exception e) {
            return Result.error(500, "上传失败");
        }
    }

    /**
     * 证据列表归一化（最多 5 条）
     */
    private List<String> normalizeEvidenceList(List<String> evidenceList) {
        if (evidenceList == null || evidenceList.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> cleaned = new ArrayList<>();
        for (String item : evidenceList) {
            if (!StringUtils.hasText(item)) {
                continue;
            }
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                cleaned.add(trimmed);
            }
            if (cleaned.size() >= 5) {
                break;
            }
        }
        if (cleaned.isEmpty()) {
            return cleaned;
        }
        return cleaned;
    }

    /**
     * 将证据列表拼接为兼容历史字段的字符串
     */
    private String joinEvidenceList(List<String> evidenceList) {
        if (evidenceList == null || evidenceList.isEmpty()) {
            return null;
        }
        return String.join(",", evidenceList);
    }

    /**
     * 判断举报证据是否至少包含 1 条有效地址
     */
    private boolean hasValidEvidence(List<String> evidenceList) {
        return !normalizeEvidenceList(evidenceList).isEmpty();
    }

    /**
     * 保存举报证据子表（规范化存储）
     */
    private void saveReportEvidence(Long reportId, Long uploaderId, List<String> evidenceList, LocalDateTime createTime) {
        if (reportId == null || evidenceList == null || evidenceList.isEmpty()) {
            return;
        }
        LocalDateTime safeCreateTime = createTime == null ? LocalDateTime.now() : createTime;
        List<ReportEvidence> rows = new ArrayList<>();
        int sortOrder = 1;
        for (String fileUrl : evidenceList) {
            ReportEvidence evidence = new ReportEvidence();
            evidence.setReportId(reportId);
            evidence.setFileUrl(fileUrl);
            evidence.setFileType(resolveEvidenceType(fileUrl));
            evidence.setSortOrder(sortOrder++);
            evidence.setUploaderId(uploaderId);
            evidence.setCreateTime(safeCreateTime);
            rows.add(evidence);
        }
        if (!rows.isEmpty()) {
            reportEvidenceService.saveBatch(rows);
        }
    }

    /**
     * 根据文件地址推断证据类型
     */
    private String resolveEvidenceType(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            return "FILE";
        }
        String lower = fileUrl.trim().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")) {
            return "IMAGE";
        }
        if (lower.endsWith(".pdf")) {
            return "PDF";
        }
        return "FILE";
    }

    /**
     * 构建举报对象快照（用于对象被后续修改/删除时仍可追溯）
     */
    private String buildTargetSnapshot(String type,
                                       Long targetId,
                                       JobInfo targetJob,
                                       MerchantInfo targetMerchant,
                                       SysUser targetMerchantUser,
                                       SysUser targetUser) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("type", type);
        snapshot.put("targetId", targetId);
        snapshot.put("capturedAt", LocalDateTime.now().toString());

        if ("JOB".equals(type)) {
            snapshot.put("title", targetJob == null ? null : targetJob.getTitle());
            snapshot.put("workLocation", targetJob == null ? null : targetJob.getWorkLocation());
            snapshot.put("district", targetJob == null ? null : targetJob.getDistrict());
            snapshot.put("salary", buildSalaryText(targetJob));
            snapshot.put("experience", targetJob == null ? null : targetJob.getExperience());
            snapshot.put("degree", targetJob == null ? null : targetJob.getDegree());
            snapshot.put("headcount", targetJob == null ? null : targetJob.getHeadcount());
            snapshot.put("companyName", resolveCompanyName(targetMerchant, targetMerchantUser));
            snapshot.put("companyIndustry", targetMerchant == null ? null : targetMerchant.getIndustry());
            snapshot.put("companyScale", targetMerchant == null ? null : targetMerchant.getScale());
            snapshot.put("publishStatus", targetJob == null ? null : targetJob.getStatus());
            snapshot.put("auditStatus", targetJob == null ? null : targetJob.getAuditStatus());
        } else if ("MERCHANT".equals(type)) {
            snapshot.put("companyName", targetMerchant == null ? null : targetMerchant.getCompanyName());
            snapshot.put("industry", targetMerchant == null ? null : targetMerchant.getIndustry());
            snapshot.put("scale", targetMerchant == null ? null : targetMerchant.getScale());
            snapshot.put("financing", targetMerchant == null ? null : targetMerchant.getFinancing());
            snapshot.put("contactName", targetMerchant == null ? null : targetMerchant.getContactName());
            snapshot.put("contactPhone", targetMerchant == null ? null : targetMerchant.getContactPhone());
            snapshot.put("publishStatus", targetMerchant == null ? null : targetMerchant.getPublishStatus());
            snapshot.put("auditStatus", targetMerchant == null ? null : targetMerchant.getAuditStatus());
            snapshot.put("ownerName", resolveUserDisplayName(targetMerchantUser));
        } else if ("USER".equals(type)) {
            snapshot.put("nickname", targetUser == null ? null : targetUser.getNickname());
            snapshot.put("username", targetUser == null ? null : targetUser.getUsername());
            snapshot.put("role", targetUser == null ? null : targetUser.getRole());
            snapshot.put("phone", targetUser == null ? null : targetUser.getPhone());
            snapshot.put("email", targetUser == null ? null : targetUser.getEmail());
            snapshot.put("status", targetUser == null ? null : targetUser.getStatus());
            snapshot.put("banStatus", targetUser == null ? null : targetUser.getBanStatus());
        }

        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /**
     * 计算岗位薪资展示文本（用于快照）
     */
    private String buildSalaryText(JobInfo targetJob) {
        if (targetJob == null || targetJob.getMinSalary() == null || targetJob.getMaxSalary() == null) {
            return "面议";
        }
        return targetJob.getMinSalary() + "-" + targetJob.getMaxSalary() + "K";
    }

    /**
     * 解析企业展示名称（用于快照）
     */
    private String resolveCompanyName(MerchantInfo merchantInfo, SysUser merchantUser) {
        if (merchantInfo != null && StringUtils.hasText(merchantInfo.getCompanyName())) {
            return merchantInfo.getCompanyName();
        }
        return resolveUserDisplayName(merchantUser);
    }

    /**
     * 通知被举报人（系统消息）
     */
    private void notifyTargetUser(ReportInfo report,
                                  ReportSubmitDTO dto,
                                  String type,
                                  Long targetUserId,
                                  Long reporterId) {
        if (report == null || targetUserId == null) {
            return;
        }
        Long adminSenderId = resolveAdminSenderId();
        if (adminSenderId == null) {
            return;
        }
        String reporterName = resolveUserDisplayName(reporterId);
        String reason = report.getReason() == null ? "" : report.getReason().trim();
        String subject = "USER".equals(type) ? "账号" : ("MERCHANT".equals(type) ? "企业" : "职位");
        String jobHint = "";
        if ("JOB".equals(type) && dto != null && dto.getTargetId() != null) {
            JobInfo jobInfo = jobInfoService.getById(dto.getTargetId());
            if (jobInfo != null && StringUtils.hasText(jobInfo.getTitle())) {
                jobHint = "，职位：" + jobInfo.getTitle();
            }
        }
        String content = "系统通知：您的" + subject + jobHint
                + "收到举报，举报人：" + (StringUtils.hasText(reporterName) ? reporterName : "匿名")
                + "，原因：" + (StringUtils.hasText(reason) ? reason : "未说明")
                + "。平台将尽快核实处理。";
        chatService.sendMessageWithPush(adminSenderId, targetUserId, content);
    }

    /**
     * 获取管理员发送者ID（用于系统消息）
     */
    private Long resolveAdminSenderId() {
        List<SysUser> adminList = sysUserService.list(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getRole, "ADMIN")
                .orderByAsc(SysUser::getId)
                .last("LIMIT 1"));
        if (adminList == null || adminList.isEmpty()) {
            return null;
        }
        return adminList.get(0).getId();
    }

    /**
     * 获取用户展示名称
     */
    private String resolveUserDisplayName(Long userId) {
        if (userId == null) {
            return "";
        }
        SysUser user = sysUserService.getById(userId);
        if (user == null) {
            return "";
        }
        if (StringUtils.hasText(user.getNickname())) {
            return user.getNickname();
        }
        return StringUtils.hasText(user.getUsername()) ? user.getUsername() : "";
    }

    /**
     * 从用户对象中提取展示名称
     */
    private String resolveUserDisplayName(SysUser user) {
        if (user == null) {
            return "";
        }
        if (StringUtils.hasText(user.getNickname())) {
            return user.getNickname();
        }
        return StringUtils.hasText(user.getUsername()) ? user.getUsername() : "";
    }
}
