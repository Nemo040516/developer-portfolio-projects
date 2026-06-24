/*
 * 文件速览：
 * 1. 文件职责：验证商家查看投递列表时，会按隐私与附件授权规则收口返回字段。
 * 2. 关键入口：JobDeliveryServiceImpl#getMerchantDeliveries。
 * 3. 关键结构：联系方式脱敏、结构化简历脱敏、过期/失效授权回收 resumeUrl。
 * 4. 阅读建议：直接看 shouldMaskApplicantContactAndAttachmentWhenPrivacyDenied。
 */
package com.example.backend.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.backend.mapper.JobDeliveryMapper;
import com.example.backend.support.ApplicantPrivacyGuard;
import com.example.backend.service.ResumeAttachmentPermissionService;
import com.example.backend.service.UserPrivacySettingService;
import com.example.backend.vo.ApplicantSimpleVO;
import com.example.backend.vo.MerchantDeliveryVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobDeliveryServiceImplTest {

    @Mock
    private JobDeliveryMapper jobDeliveryMapper;

    @Mock
    private UserPrivacySettingService userPrivacySettingService;

    @Mock
    private ResumeAttachmentPermissionService permissionService;

    private JobDeliveryServiceImpl jobDeliveryService;

    @BeforeEach
    void setUp() {
        jobDeliveryService = new JobDeliveryServiceImpl();
        ReflectionTestUtils.setField(jobDeliveryService, "baseMapper", jobDeliveryMapper);
        ReflectionTestUtils.setField(jobDeliveryService, "applicantPrivacyGuard",
                new ApplicantPrivacyGuard(userPrivacySettingService, jobDeliveryMapper, permissionService));
    }

    @Test
    void shouldMaskApplicantContactAndAttachmentWhenPrivacyDenied() {
        ApplicantSimpleVO applicant = new ApplicantSimpleVO();
        applicant.setUserId(9L);
        applicant.setPhone("13800000000");
        applicant.setEmail("app@example.com");
        applicant.setEducationJson("[{\"school\":\"A\"}]");

        MerchantDeliveryVO item = new MerchantDeliveryVO();
        item.setApplicant(applicant);
        item.setResumeUrl("/uploads/resumes/9/resume.pdf");
        item.setAttachmentStatus(1);

        Page<MerchantDeliveryVO> page = new Page<>(1, 10, 1);
        page.setRecords(java.util.List.of(item));
        when(jobDeliveryMapper.selectMerchantDeliveryPage(any(), eq(2L), any(), any(), any())).thenReturn(page);
        when(jobDeliveryMapper.countMerchantApplicantRelation(2L, 9L)).thenReturn(0L);
        when(permissionService.getPermission(9L, 2L)).thenReturn(null);

        Page<?> requestPage = new Page<>(1, 10);
        var result = jobDeliveryService.getMerchantDeliveries(requestPage, 2L, null, null, null);
        MerchantDeliveryVO record = result.getRecords().get(0);

        assertNull(record.getApplicant().getPhone());
        assertNull(record.getApplicant().getEmail());
        assertNull(record.getApplicant().getEducationJson());
        assertNull(record.getResumeUrl());
        assertNull(record.getAttachmentStatus());
        assertEquals(1L, result.getTotal());
    }
}
