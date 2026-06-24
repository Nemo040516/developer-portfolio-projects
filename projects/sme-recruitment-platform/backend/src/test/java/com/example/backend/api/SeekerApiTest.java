/**
 * 文件速览：
 * 1. 文件职责：覆盖求职者看板类接口的基础鉴权与角色隔离。
 * 2. 关键升级：验证求职洞察接口仅允许 APPLICANT 访问。
 * 3. 关键入口：/seeker/insight-stats。
 * 4. 阅读建议：先看角色拒绝场景，再看成功透传场景。
 */
package com.example.backend.api;

import com.example.backend.dto.ResumeSaveDTO;
import com.example.backend.service.ApplicantInfoService;
import com.example.backend.service.SeekerService;
import com.example.backend.support.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Map;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeekerApiTest extends ApiTestBase {

    @MockBean
    private SeekerService seekerService;

    @MockBean
    private ApplicantInfoService applicantInfoService;

    @Test
    void shouldRejectMerchantReadingInsightStats() throws Exception {
        mockMvc.perform(get("/seeker/insight-stats")
                        .with(authorizedAs(7L, "MERCHANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("仅求职者可访问求职洞察"));
    }

    @Test
    void shouldReturnInsightStatsForApplicant() throws Exception {
        when(seekerService.getInsightStats(5L)).thenReturn(Map.of("appliedCount", 3, "interviewCount", 1));

        mockMvc.perform(get("/seeker/insight-stats")
                        .with(authorizedAs(5L, "APPLICANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.appliedCount").value(3))
                .andExpect(jsonPath("$.data.interviewCount").value(1));

        verify(seekerService).getInsightStats(5L);
    }

    @Test
    void shouldRejectInvalidApplicationsPageSize() throws Exception {
        mockMvc.perform(get("/seeker/applications")
                        .param("size", "0")
                        .with(authorizedAs(5L, "APPLICANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("每页条数必须为正数"));
    }

    @Test
    void shouldRejectResumeSaveWhenSalaryRangeInvalid() throws Exception {
        String body = """
                {
                  "basicInfo": {
                    "expectSalaryMin": 25,
                    "expectSalaryMax": 15
                  }
                }
                """;

        mockMvc.perform(post("/seeker/resume")
                        .contentType(json())
                        .content(body)
                        .with(authorizedAs(5L, "APPLICANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("期望薪资最低值不能大于最高值"));

        verify(applicantInfoService, never()).saveOrUpdateResume(eq(5L), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectResumeSaveWhenEducationTimeRangeInvalid() throws Exception {
        String body = """
                {
                  "education": [
                    {
                      "school": "测试大学",
                      "timeRange": ["2025-09"]
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/seeker/resume")
                        .contentType(json())
                        .content(body)
                        .with(authorizedAs(5L, "APPLICANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("教育经历时间范围必须为两个YYYY-MM值，且开始时间不能晚于结束时间"));

        verify(applicantInfoService, never()).saveOrUpdateResume(eq(5L), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldStripPhoneAndEmailBeforeSavingResume() throws Exception {
        when(applicantInfoService.saveOrUpdateResume(eq(5L), org.mockito.ArgumentMatchers.any())).thenReturn(true);
        String body = """
                {
                  "basicInfo": {
                    "name": "张三",
                    "phone": "13800138000",
                    "email": "demo@example.com",
                    "expectSalaryMin": 8,
                    "expectSalaryMax": 12
                  }
                }
                """;

        mockMvc.perform(post("/seeker/resume")
                        .contentType(json())
                        .content(body)
                        .with(authorizedAs(5L, "APPLICANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(true));

        verify(applicantInfoService).saveOrUpdateResume(eq(5L), argThat((ResumeSaveDTO dto) ->
                dto != null
                        && dto.getBasicInfo() != null
                        && dto.getBasicInfo().getPhone() == null
                        && dto.getBasicInfo().getEmail() == null
                        && Integer.valueOf(8).equals(dto.getBasicInfo().getExpectSalaryMin())
                        && Integer.valueOf(12).equals(dto.getBasicInfo().getExpectSalaryMax())
        ));
    }
}
