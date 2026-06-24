/**
 * 文件速览：
 * 1. 文件职责：覆盖商家资料与工作台接口的参数校验边界。
 * 2. 关键入口：/merchant/dashboard-stats、/merchant/detail/{userId}。
 * 3. 关键升级：验证统计范围和商家详情 ID 在控制器层被提前拦截。
 * 4. 阅读建议：先看 rangeDays，再看商家详情参数校验。
 */
package com.example.backend.api;

import com.example.backend.service.ChatService;
import com.example.backend.service.InterviewScheduleService;
import com.example.backend.service.JobDeliveryService;
import com.example.backend.service.JobInfoService;
import com.example.backend.service.JobViewLogService;
import com.example.backend.service.MerchantInfoService;
import com.example.backend.support.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MerchantApiTest extends ApiTestBase {

    @MockBean
    private MerchantInfoService merchantInfoService;

    @MockBean
    private JobInfoService jobInfoService;

    @MockBean
    private JobDeliveryService jobDeliveryService;

    @MockBean
    private InterviewScheduleService interviewScheduleService;

    @MockBean
    private ChatService chatService;

    @MockBean
    private JobViewLogService jobViewLogService;

    @Test
    void shouldRejectInvalidDashboardRangeDays() throws Exception {
        mockMvc.perform(get("/merchant/dashboard-stats")
                        .param("rangeDays", "0")
                        .with(authorizedAs(8L, "MERCHANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("统计天数必须为正数"));

        verifyNoInteractions(jobInfoService, jobDeliveryService, interviewScheduleService, chatService, jobViewLogService);
    }

    @Test
    void shouldRejectInvalidMerchantDetailUserId() throws Exception {
        mockMvc.perform(get("/merchant/detail/0")
                        .with(authorizedAs(8L, "MERCHANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("商家用户ID必须为正数"));

        verifyNoInteractions(merchantInfoService);
    }
}
