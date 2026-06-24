/**
 * 文件速览：
 * 1. 文件职责：提供求职者端看板、投递记录、在线简历与附件简历接口。
 * 2. 关键升级：求职洞察接口现补齐“仅求职者可访问”的角色校验。
 * 3. 关键入口：/seeker/dashboard-stats、/seeker/insight-stats、/seeker/resume。
 * 4. 阅读建议：先看看板/洞察鉴权，再看简历与附件简历读写逻辑。
 */
package com.example.backend.controller;

import com.example.backend.common.Result;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.backend.service.JobDeliveryService;
import com.example.backend.dto.ResumeSaveDTO;
import com.example.backend.service.ApplicantInfoService;
import com.example.backend.service.SeekerService;
import com.example.backend.utils.ControllerAccessUtils;
import com.example.backend.utils.PageQueryUtils;
import com.example.backend.utils.SecurityUtils;
import com.example.backend.utils.UploadFileUtils;
import com.example.backend.vo.DeliveryVO;
import com.example.backend.vo.ResumeVO;
import com.example.backend.vo.ResumeAttachmentVO;
import com.example.backend.vo.SeekerDashboardVO;
import com.example.backend.entity.ApplicantInfo;
import com.example.backend.entity.SysUser;
import com.example.backend.mapper.SysUserMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@RestController
@Validated
@RequestMapping("/seeker")
public class SeekerController {

    @Autowired
    private SeekerService seekerService;

    @Autowired
    private ApplicantInfoService applicantInfoService;

    @Autowired
    private JobDeliveryService jobDeliveryService;

    @Autowired
    private SysUserMapper sysUserMapper;

    private Long requireApplicant(String forbiddenMessage) {
        return ControllerAccessUtils.requireApplicant(forbiddenMessage);
    }

    /**
     * 获取求职者看板统计数据
     * 包含：简历完善度、投递状态统计
     */
    @GetMapping("/dashboard-stats")
    public Result<SeekerDashboardVO> getDashboardStats() {
        Long userId = requireApplicant("仅求职者可访问看板");
        SeekerDashboardVO vo = seekerService.getDashboardStats(userId);
        return Result.success(vo);
    }

    /**
     * 获取求职洞察统计数据（柱状图数据）
     */
    @GetMapping("/insight-stats")
    public Result<java.util.Map<String, Object>> getInsightStats() {
        Long userId = requireApplicant("仅求职者可访问求职洞察");
        return Result.success(seekerService.getInsightStats(userId));
    }

    /**
     * 获取求职者投递记录（分页/搜索/排序）
     */
    @GetMapping("/applications")
    public Result<IPage<DeliveryVO>> getMyApplications(
            @RequestParam(required = false) @Positive(message = "页码必须为正数") Integer current,
            @RequestParam(required = false) @Positive(message = "页码必须为正数") Integer page,
            @RequestParam(required = false) @Positive(message = "每页条数必须为正数") @Max(value = 50, message = "每页条数不能超过50") Integer size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String timeOrder
    ) {
        Long userId = requireApplicant("仅求职者可查看投递记录");
        Page<DeliveryVO> mpPage = PageQueryUtils.buildPage(current, page, size, 10);
        IPage<DeliveryVO> result = jobDeliveryService.getMyDeliveriesPage(mpPage, userId, status, keyword, timeOrder);
        return Result.success(result);
    }

    /**
     * 获取在线简历
     */
    @GetMapping("/resume")
    public Result<ResumeVO> getResume() {
        Long userId = requireApplicant("仅求职者可访问在线简历");
        ResumeVO vo = applicantInfoService.getResume(userId);
        return Result.success(vo);
    }

    /**
     * 保存在线简历
     */
    @PostMapping("/resume")
    public Result<Boolean> saveResume(@RequestBody @Valid ResumeSaveDTO dto) {
        Long userId = requireApplicant("仅求职者可保存在线简历");
        // 账号联系方式只允许从个人信息维护，这里强制剥离避免误写
        if (dto != null && dto.getBasicInfo() != null) {
            dto.getBasicInfo().setPhone(null);
            dto.getBasicInfo().setEmail(null);
        }
        boolean ok = applicantInfoService.saveOrUpdateResume(userId, dto);
        return Result.success(ok);
    }

    /**
     * 获取附件简历信息
     */
    @GetMapping("/resume-attachment")
    public Result<ResumeAttachmentVO> getResumeAttachment() {
        Long userId = requireApplicant("仅求职者可访问附件简历");
        ResumeAttachmentVO vo = new ResumeAttachmentVO();
        String resumeUrl = applicantInfoService.getResumeUrl(userId);
        if (StringUtils.hasText(resumeUrl)) {
            vo.setFileUrl(resumeUrl);
            String fileName = resumeUrl.substring(resumeUrl.lastIndexOf('/') + 1);
            String extension = UploadFileUtils.extractExtension(fileName);
            vo.setFileName(buildResumeDisplayName(userId, extension));
        }
        return Result.success(vo);
    }

    /**
     * 上传/替换附件简历
     */
    @PostMapping("/resume-attachment")
    public Result<ResumeAttachmentVO> uploadResumeAttachment(@RequestParam("file") MultipartFile file) {
        Long userId = requireApplicant("仅求职者可上传附件简历");
        if (file == null || file.isEmpty()) {
            return Result.error(400, "文件不能为空");
        }
        // 基础校验：仅支持 PDF/DOC/DOCX，大小不超过 10MB
        long maxSize = 10 * 1024 * 1024;
        if (file.getSize() > maxSize) {
            return Result.error(400, "附件简历大小不能超过10MB");
        }

        String extension = UploadFileUtils.extractExtension(file.getOriginalFilename());
        if (!UploadFileUtils.isAllowedExtension(extension, "pdf", "doc", "docx")) {
            return Result.error(400, "仅支持 PDF/DOC/DOCX 格式");
        }
        String displayName = buildResumeDisplayName(userId, extension);
        String fileName = sanitizeFileName(displayName);
        Path uploadRoot = Paths.get(System.getProperty("user.dir"), "uploads").toAbsolutePath().normalize();
        Path targetPath = null;
        Path backupPath = null;
        Path previousPath = null;

        try {
            Path uploadDir = uploadRoot.resolve(Paths.get("resumes", String.valueOf(userId))).normalize();
            Files.createDirectories(uploadDir);
            targetPath = uploadDir.resolve(fileName).normalize();
            String previousResumeUrl = applicantInfoService.getResumeUrl(userId);
            previousPath = resolveUploadPath(uploadRoot, previousResumeUrl);

            // 同路径覆盖时先备份旧文件，避免数据库更新失败后无法恢复原文件内容。
            if (previousPath != null && previousPath.equals(targetPath) && Files.exists(previousPath)) {
                backupPath = Files.createTempFile(uploadDir, "resume-backup-", ".tmp");
                Files.move(previousPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            }

            file.transferTo(targetPath.toFile());

            String fileUrl = "/uploads/resumes/" + userId + "/" + fileName;
            boolean updated = applicantInfoService.updateResumeUrl(userId, fileUrl);
            if (!updated) {
                throw new IllegalStateException("resume url persist failed");
            }

            cleanupPreviousResumeAfterSuccess(previousPath, targetPath, backupPath);

            ResumeAttachmentVO vo = new ResumeAttachmentVO();
            vo.setFileUrl(fileUrl);
            vo.setFileName(displayName);
            return Result.success(vo);
        } catch (Exception e) {
            rollbackUploadedResume(targetPath, previousPath, backupPath);
            return Result.error(500, "上传失败");
        }
    }

    /**
     * 删除附件简历
     */
    @DeleteMapping("/resume-attachment")
    public Result<Boolean> deleteResumeAttachment() {
        Long userId = requireApplicant("仅求职者可删除附件简历");
        String resumeUrl = applicantInfoService.getResumeUrl(userId);
        if (StringUtils.hasText(resumeUrl)) {
            try {
                Path uploadRoot = Paths.get(System.getProperty("user.dir"), "uploads");
                // 优先按 resumeUrl 解析真实存储路径
                Path resolvedPath = resolveUploadPath(uploadRoot, resumeUrl);
                if (resolvedPath != null) {
                    Files.deleteIfExists(resolvedPath);
                } else {
                    // 兼容历史路径：uploads/resumes/{userId}/{fileName} 或 uploads/resumes/{fileName}
                    String fileName = resumeUrl.substring(resumeUrl.lastIndexOf('/') + 1);
                    Path newPath = uploadRoot.resolve(Paths.get("resumes", String.valueOf(userId), fileName));
                    Path legacyPath = uploadRoot.resolve(Paths.get("resumes", fileName));
                    Files.deleteIfExists(newPath);
                    Files.deleteIfExists(legacyPath);
                }
            } catch (Exception ignored) {
                // 删除失败不影响数据库清理
            }
        }
        applicantInfoService.updateResumeUrl(userId, null);
        return Result.success(true);
    }

    /**
     * 根据 resumeUrl 推导本地 uploads 目录下的真实路径
     * 兼容以下形式：
     * 1) /uploads/resumes/{userId}/{fileName}
     * 2) uploads/resumes/{userId}/{fileName}
     * 3) http(s)://host/uploads/resumes/{userId}/{fileName}
     */
    private Path resolveUploadPath(Path uploadRoot, String resumeUrl) {
        if (uploadRoot == null || !StringUtils.hasText(resumeUrl)) {
            return null;
        }
        Path normalizedRoot = uploadRoot.toAbsolutePath().normalize();
        String normalized = resumeUrl.trim();
        int marker = normalized.indexOf("/uploads/");
        if (marker >= 0) {
            normalized = normalized.substring(marker + "/uploads/".length());
        } else if (normalized.startsWith("uploads/")) {
            normalized = normalized.substring("uploads/".length());
        }
        normalized = normalized.replaceFirst("^/+", "");
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        Path resolved = normalizedRoot.resolve(normalized).normalize();
        return resolved.startsWith(normalizedRoot) ? resolved : null;
    }

    /**
     * 上传成功后，清理旧文件或过渡备份，避免跨扩展名替换留下残留文件。
     */
    private void cleanupPreviousResumeAfterSuccess(Path previousPath, Path targetPath, Path backupPath) {
        if (previousPath != null && !previousPath.equals(targetPath)) {
            deleteFileQuietly(previousPath);
        }
        deleteFileQuietly(backupPath);
    }

    /**
     * 上传链路失败时回滚新文件；若是同路径覆盖，则恢复旧文件备份。
     */
    private void rollbackUploadedResume(Path targetPath, Path previousPath, Path backupPath) {
        deleteFileQuietly(targetPath);
        if (backupPath != null && previousPath != null) {
            try {
                Files.createDirectories(previousPath.getParent());
                Files.move(backupPath, previousPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 文件系统清理只作为副作用收口，不额外放大上传主流程的失败面。
     */
    private void deleteFileQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
        }
    }

    /**
     * 构建附件简历展示名：{姓名}的简历(.pdf/.doc/.docx)
     */
    private String buildResumeDisplayName(Long userId, String extension) {
        String realName = resolveApplicantDisplayName(userId);
        String suffix = StringUtils.hasText(extension) ? "." + extension : "";
        return realName + "的简历" + suffix;
    }

    /**
     * 求职者姓名优先级：简历姓名 > 昵称 > 用户名 > 求职者
     */
    private String resolveApplicantDisplayName(Long userId) {
        ApplicantInfo info = applicantInfoService.getByUserId(userId);
        if (info != null && StringUtils.hasText(info.getRealName())) {
            return info.getRealName();
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user != null) {
            if (StringUtils.hasText(user.getNickname())) {
                return user.getNickname();
            }
            if (StringUtils.hasText(user.getUsername())) {
                return user.getUsername();
            }
        }
        return "求职者";
    }

    /**
     * 清理文件名非法字符，避免路径异常
     */
    private String sanitizeFileName(String name) {
        if (!StringUtils.hasText(name)) {
            return "求职者的简历";
        }
        String cleaned = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return cleaned.isEmpty() ? "求职者的简历" : cleaned;
    }

}
