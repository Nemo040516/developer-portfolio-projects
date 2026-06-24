/*
 * 文件速览：
 * 1. 文件职责：验证候选人库在返回详情前，会按隐私设置脱敏联系方式与结构化简历字段。
 * 2. 关键入口：TalentPoolServiceImpl#getTalentPage。
 * 3. 关键结构：分页结果映射、商家维度隐私判断、脱敏后字段断言。
 * 4. 阅读建议：直接看 shouldMaskTalentCandidateWhenPrivacyDenied。
 */
package com.example.backend.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.backend.entity.ApplicantInfo;
import com.example.backend.entity.SysUser;
import com.example.backend.mapper.ApplicantInfoMapper;
import com.example.backend.mapper.JobDeliveryMapper;
import com.example.backend.mapper.SysUserMapper;
import com.example.backend.support.ApplicantPrivacyGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TalentPoolServiceImplTest {

    @Mock
    private ApplicantInfoMapper applicantInfoMapper;

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private JobDeliveryMapper jobDeliveryMapper;

    @Mock
    private com.example.backend.service.UserPrivacySettingService userPrivacySettingService;

    @Mock
    private com.example.backend.service.ResumeAttachmentPermissionService permissionService;

    private TalentPoolServiceImpl talentPoolService;

    @BeforeEach
    void setUp() {
        talentPoolService = new TalentPoolServiceImpl();
        ReflectionTestUtils.setField(talentPoolService, "applicantInfoMapper", applicantInfoMapper);
        ReflectionTestUtils.setField(talentPoolService, "sysUserMapper", sysUserMapper);
        ReflectionTestUtils.setField(talentPoolService, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(talentPoolService, "applicantPrivacyGuard",
                new ApplicantPrivacyGuard(userPrivacySettingService, jobDeliveryMapper, permissionService));
    }

    @Test
    void shouldMaskTalentCandidateWhenPrivacyDenied() {
        ApplicantInfo info = new ApplicantInfo();
        info.setUserId(9L);
        info.setRealName("候选人A");
        info.setPhone("13800000000");
        info.setEmail("app@example.com");
        info.setExpectJob("前端开发");
        info.setEducationJson("[{\"school\":\"A\"}]");

        SysUser user = new SysUser();
        user.setId(9L);
        user.setNickname("候选人A");

        Page<ApplicantInfo> applicantPage = new Page<>(1, 12, 1);
        applicantPage.setRecords(List.of(info));
        when(applicantInfoMapper.selectPage(any(Page.class), any())).thenReturn(applicantPage);
        when(sysUserMapper.selectBatchIds(any())).thenReturn(List.of(user));
        when(jobDeliveryMapper.countMerchantApplicantRelation(2L, 9L)).thenReturn(0L);

        var result = talentPoolService.getTalentPage(new Page<>(1, 12), 2L, null, null, null);
        var record = result.getRecords().get(0);

        assertEquals("前端开发", record.getExpectJob());
        assertNull(record.getPhone());
        assertNull(record.getEmail());
        assertNull(record.getEducationJson());
    }
}
