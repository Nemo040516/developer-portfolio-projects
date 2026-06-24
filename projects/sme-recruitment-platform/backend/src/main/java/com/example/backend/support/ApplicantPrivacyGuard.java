/*
 * 文件速览：
 * 1. 文件职责：统一解释求职者联系方式隐私规则，并提供商家侧数据脱敏辅助方法。
 * 2. 对外入口：canMerchantViewContact、hasActiveAttachmentAuthorization、maskTalentCandidate、maskApplicantContactFields。
 * 3. 关键结构：PUBLIC/DELIVERY/AUTH 三档规则、附件授权有效期判断、候选人简历字段脱敏。
 * 4. 阅读建议：先看 canMerchantViewContact，再看两个 mask 方法。
 */
package com.example.backend.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.backend.entity.ResumeAttachmentPermission;
import com.example.backend.entity.UserPrivacySetting;
import com.example.backend.mapper.JobDeliveryMapper;
import com.example.backend.service.ResumeAttachmentPermissionService;
import com.example.backend.service.UserPrivacySettingService;
import com.example.backend.vo.ApplicantSimpleVO;
import com.example.backend.vo.TalentCandidateVO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 求职者隐私规则守卫。
 */
@Component
public class ApplicantPrivacyGuard {

    private static final String VISIBILITY_PUBLIC = "PUBLIC";
    private static final String VISIBILITY_DELIVERY = "DELIVERY";
    private static final String VISIBILITY_AUTH = "AUTH";

    private final UserPrivacySettingService userPrivacySettingService;
    private final JobDeliveryMapper jobDeliveryMapper;
    private final ResumeAttachmentPermissionService permissionService;

    public ApplicantPrivacyGuard(UserPrivacySettingService userPrivacySettingService,
                                 JobDeliveryMapper jobDeliveryMapper,
                                 ResumeAttachmentPermissionService permissionService) {
        this.userPrivacySettingService = userPrivacySettingService;
        this.jobDeliveryMapper = jobDeliveryMapper;
        this.permissionService = permissionService;
    }

    /**
     * 判断当前商家是否可查看求职者联系方式。
     */
    public boolean canMerchantViewContact(Long merchantId, Long applicantId) {
        if (merchantId == null || applicantId == null) {
            return false;
        }
        String visibility = resolveVisibility(applicantId);
        if (VISIBILITY_PUBLIC.equals(visibility)) {
            return true;
        }
        if (VISIBILITY_AUTH.equals(visibility)) {
            return hasActiveAttachmentAuthorization(applicantId, merchantId);
        }
        return jobDeliveryMapper.countMerchantApplicantRelation(merchantId, applicantId) > 0;
    }

    /**
     * 判断附件授权是否仍然有效。
     */
    public boolean hasActiveAttachmentAuthorization(Long applicantId, Long merchantId) {
        if (applicantId == null || merchantId == null) {
            return false;
        }
        ResumeAttachmentPermission permission = permissionService.getPermission(applicantId, merchantId);
        return permission != null
                && Integer.valueOf(1).equals(permission.getStatus())
                && !isExpired(permission.getExpireTime());
    }

    /**
     * 候选人库脱敏：收口联系方式与完整结构化简历，避免越权批量查看。
     */
    public void maskTalentCandidate(TalentCandidateVO candidate) {
        if (candidate == null) {
            return;
        }
        candidate.setPhone(null);
        candidate.setEmail(null);
        candidate.setEducationJson(null);
        candidate.setExperienceJson(null);
        candidate.setProjectJson(null);
        candidate.setCertificateJson(null);
        candidate.setAwardJson(null);
        candidate.setLanguageJson(null);
    }

    /**
     * 投递详情脱敏：隐藏联系方式与完整结构化简历 JSON。
     */
    public void maskApplicantContactFields(ApplicantSimpleVO applicant) {
        if (applicant == null) {
            return;
        }
        applicant.setPhone(null);
        applicant.setEmail(null);
        applicant.setEducationJson(null);
        applicant.setExperienceJson(null);
        applicant.setProjectJson(null);
        applicant.setCertificateJson(null);
        applicant.setAwardJson(null);
        applicant.setLanguageJson(null);
    }

    private String resolveVisibility(Long applicantId) {
        if (applicantId == null) {
            return VISIBILITY_DELIVERY;
        }
        UserPrivacySetting setting = userPrivacySettingService.getOne(new LambdaQueryWrapper<UserPrivacySetting>()
                .eq(UserPrivacySetting::getUserId, applicantId)
                .last("LIMIT 1"), false);
        String visibility = setting == null ? null : setting.getContactVisibility();
        String normalized = StringUtils.hasText(visibility) ? visibility.trim().toUpperCase() : VISIBILITY_DELIVERY;
        if (VISIBILITY_PUBLIC.equals(normalized) || VISIBILITY_AUTH.equals(normalized)) {
            return normalized;
        }
        return VISIBILITY_DELIVERY;
    }

    private boolean isExpired(LocalDateTime expireTime) {
        return expireTime != null && expireTime.isBefore(LocalDateTime.now());
    }
}
