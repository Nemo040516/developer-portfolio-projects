/*
 * 文件速览：
 * 1. 文件职责：统一接管 /uploads/** 文件访问，对公开 Logo 与敏感附件执行差异化鉴权。
 * 2. 对外入口：GET /uploads/**。
 * 3. 关键结构：路径归一化、目录级权限判断、uploads 双根目录兼容读取。
 * 4. 阅读建议：先看 readUpload 主流程，再看各类 canAccessXxx 辅助判断。
 */
package com.example.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.backend.common.Result;
import com.example.backend.entity.ApplicantInfo;
import com.example.backend.entity.MerchantInfo;
import com.example.backend.entity.ResumeAttachmentPermission;
import com.example.backend.service.ApplicantInfoService;
import com.example.backend.service.JobDeliveryService;
import com.example.backend.service.MerchantInfoService;
import com.example.backend.service.ResumeAttachmentPermissionService;
import com.example.backend.utils.JwtUtils;
import com.example.backend.utils.SecurityUtils;
import com.example.backend.utils.UploadFileUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * uploads 受控访问控制器。
 */
@RestController
public class UploadAccessController {

    private static final Set<String> PUBLIC_DIRS = Set.of("logo", "logos");
    private static final Set<String> MERCHANT_MATERIAL_DIRS = Set.of("license", "licenses", "qualification", "qualifications");
    private static final String RESUME_DIR = "resumes";
    private static final String REPORT_DIR = "reports";

    private final ApplicantInfoService applicantInfoService;
    private final MerchantInfoService merchantInfoService;
    private final JobDeliveryService jobDeliveryService;
    private final ResumeAttachmentPermissionService permissionService;
    private final JwtUtils jwtUtils;

    public UploadAccessController(ApplicantInfoService applicantInfoService,
                                  MerchantInfoService merchantInfoService,
                                  JobDeliveryService jobDeliveryService,
                                  ResumeAttachmentPermissionService permissionService,
                                  JwtUtils jwtUtils) {
        this.applicantInfoService = applicantInfoService;
        this.merchantInfoService = merchantInfoService;
        this.jobDeliveryService = jobDeliveryService;
        this.permissionService = permissionService;
        this.jwtUtils = jwtUtils;
    }

    @GetMapping("/uploads/**")
    public ResponseEntity<?> readUpload(HttpServletRequest request) {
        String relativePath = UploadFileUtils.normalizeUploadRelativePath(request.getRequestURI());
        if (!StringUtils.hasText(relativePath)) {
            return buildError(HttpStatus.NOT_FOUND, "文件不存在");
        }

        String topLevelDir = firstSegment(relativePath);
        if (!StringUtils.hasText(topLevelDir)) {
            return buildError(HttpStatus.NOT_FOUND, "文件不存在");
        }

        AccessIdentity identity = resolveAccessIdentity(request);
        if (!isAllowedToRead(topLevelDir, relativePath, identity)) {
            return identity.userId() == null
                    ? buildError(HttpStatus.UNAUTHORIZED, "请先登录后再访问该文件")
                    : buildError(HttpStatus.FORBIDDEN, "无权访问该文件");
        }

        Path filePath = UploadFileUtils.resolveExistingUploadPath(relativePath);
        if (filePath == null || !Files.isRegularFile(filePath)) {
            return buildError(HttpStatus.NOT_FOUND, "文件不存在");
        }

        try {
            Resource resource = new UrlResource(filePath.toUri());
            String contentType = Files.probeContentType(filePath);
            MediaType mediaType = StringUtils.hasText(contentType)
                    ? MediaType.parseMediaType(contentType)
                    : MediaType.APPLICATION_OCTET_STREAM;
            String filename = filePath.getFileName() == null ? "file" : filePath.getFileName().toString();
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                            .filename(filename, StandardCharsets.UTF_8)
                            .build()
                            .toString())
                    .body(resource);
        } catch (Exception ex) {
            return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "文件读取失败");
        }
    }

    private boolean isAllowedToRead(String topLevelDir, String relativePath, AccessIdentity identity) {
        if (PUBLIC_DIRS.contains(topLevelDir)) {
            return true;
        }
        Long userId = identity.userId();
        String role = identity.role();
        if (userId == null || !StringUtils.hasText(role)) {
            return false;
        }
        if (SecurityUtils.isAdminRole(role)) {
            return true;
        }
        if (RESUME_DIR.equals(topLevelDir)) {
            return canAccessResume(relativePath, userId, role);
        }
        if (REPORT_DIR.equals(topLevelDir)) {
            return canAccessReport(relativePath, userId);
        }
        if (MERCHANT_MATERIAL_DIRS.contains(topLevelDir)) {
            return canAccessMerchantMaterial(relativePath, userId, role);
        }
        return false;
    }

    private AccessIdentity resolveAccessIdentity(HttpServletRequest request) {
        Long currentUserId = SecurityUtils.getUserId();
        String currentRole = SecurityUtils.getRole();
        if (currentUserId != null && StringUtils.hasText(currentRole)) {
            return new AccessIdentity(currentUserId, currentRole);
        }
        String token = resolveToken(request);
        if (!StringUtils.hasText(token)) {
            return new AccessIdentity(null, null);
        }
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        try {
            Claims claims = jwtUtils.getClaimsByToken(token);
            Long userId = Long.parseLong(claims.getSubject());
            String role = SecurityUtils.normalizeRole(claims.get("role", String.class));
            return new AccessIdentity(userId, role);
        } catch (Exception ex) {
            return new AccessIdentity(null, null);
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (StringUtils.hasText(token)) {
            return token;
        }
        return request.getParameter("token");
    }

    /**
     * 附件简历：求职者本人可读；商家需要同时满足真实投递关系 + 有效授权。
     */
    private boolean canAccessResume(String relativePath, Long currentUserId, String role) {
        Long applicantId = resolveApplicantIdFromResume(relativePath);
        if (applicantId == null) {
            return false;
        }
        if (SecurityUtils.isApplicantRole(role)) {
            return applicantId.equals(currentUserId);
        }
        if (!SecurityUtils.isMerchantRole(role)) {
            return false;
        }
        if (!jobDeliveryService.hasDeliveryRelation(currentUserId, applicantId)) {
            return false;
        }
        ResumeAttachmentPermission permission = permissionService.getPermission(applicantId, currentUserId);
        return permission != null
                && Integer.valueOf(1).equals(permission.getStatus())
                && !isPermissionExpired(permission.getExpireTime());
    }

    /**
     * 举报证据：仅上传者本人或管理员可读。
     */
    private boolean canAccessReport(String relativePath, Long currentUserId) {
        Long ownerId = parseNumericSegment(relativePath, 1);
        return ownerId != null && ownerId.equals(currentUserId);
    }

    /**
     * 企业执照/资质：仅商家本人或管理员可读。
     */
    private boolean canAccessMerchantMaterial(String relativePath, Long currentUserId, String role) {
        if (!SecurityUtils.isMerchantRole(role)) {
            return false;
        }
        Long ownerId = resolveMerchantOwnerId(relativePath);
        return ownerId != null && ownerId.equals(currentUserId);
    }

    private Long resolveApplicantIdFromResume(String relativePath) {
        Long applicantId = parseNumericSegment(relativePath, 1);
        if (applicantId != null) {
            return applicantId;
        }
        String uploadUrl = UploadFileUtils.buildRelativeUploadUrl(relativePath);
        if (!StringUtils.hasText(uploadUrl)) {
            return null;
        }
        ApplicantInfo info = applicantInfoService.getOne(new LambdaQueryWrapper<ApplicantInfo>()
                .and(wrapper -> wrapper.eq(ApplicantInfo::getResumeUrl, uploadUrl)
                        .or()
                        .like(ApplicantInfo::getResumeUrl, uploadUrl)), false);
        return info == null ? null : info.getUserId();
    }

    private Long resolveMerchantOwnerId(String relativePath) {
        Long ownerIdFromFileName = parseLeadingOwnerId(fileName(relativePath));
        if (ownerIdFromFileName != null) {
            return ownerIdFromFileName;
        }
        String uploadUrl = UploadFileUtils.buildRelativeUploadUrl(relativePath);
        if (!StringUtils.hasText(uploadUrl)) {
            return null;
        }
        MerchantInfo licenseOwner = merchantInfoService.getOne(new LambdaQueryWrapper<MerchantInfo>()
                .eq(MerchantInfo::getLicenseUrl, uploadUrl), false);
        if (licenseOwner != null) {
            return licenseOwner.getUserId();
        }
        MerchantInfo qualificationOwner = merchantInfoService.getOne(new LambdaQueryWrapper<MerchantInfo>()
                .like(MerchantInfo::getQualificationUrls, uploadUrl), false);
        return qualificationOwner == null ? null : qualificationOwner.getUserId();
    }

    private boolean isPermissionExpired(LocalDateTime expireTime) {
        return expireTime != null && expireTime.isBefore(LocalDateTime.now());
    }

    private String firstSegment(String relativePath) {
        if (!StringUtils.hasText(relativePath)) {
            return null;
        }
        String[] segments = relativePath.split("/");
        return segments.length == 0 ? null : segments[0];
    }

    private Long parseNumericSegment(String relativePath, int segmentIndex) {
        if (!StringUtils.hasText(relativePath)) {
            return null;
        }
        String[] segments = relativePath.split("/");
        if (segmentIndex < 0 || segmentIndex >= segments.length) {
            return null;
        }
        try {
            return Long.parseLong(segments[segmentIndex]);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String fileName(String relativePath) {
        if (!StringUtils.hasText(relativePath)) {
            return "";
        }
        int slashIndex = relativePath.lastIndexOf('/');
        return slashIndex >= 0 ? relativePath.substring(slashIndex + 1) : relativePath;
    }

    private Long parseLeadingOwnerId(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return null;
        }
        int underscoreIndex = fileName.indexOf('_');
        String maybeOwnerId = underscoreIndex > 0 ? fileName.substring(0, underscoreIndex) : null;
        if (!StringUtils.hasText(maybeOwnerId)) {
            return null;
        }
        try {
            return Long.parseLong(maybeOwnerId);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private ResponseEntity<Result<Void>> buildError(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Result.error(status.value(), message));
    }

    private record AccessIdentity(Long userId, String role) {
    }
}
