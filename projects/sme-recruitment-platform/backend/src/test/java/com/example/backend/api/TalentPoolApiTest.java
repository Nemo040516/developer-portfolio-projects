/**
 * 文件速览：
 * 1. 文件职责：覆盖商家候选人库接口的角色隔离与分页/详情参数校验。
 * 2. 关键入口：/merchant/talent/list、/merchant/talent/detail/{userId}。
 * 3. 关键升级：验证非法页码、非法求职者ID会被控制器层提前拦截。
 * 4. 阅读建议：先看参数校验场景，再看角色拒绝场景。
 */
package com.example.backend.api;

import com.example.backend.service.TalentPoolService;
import com.example.backend.support.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TalentPoolApiTest extends ApiTestBase {

    @MockBean
    private TalentPoolService talentPoolService;

    @Test
    void shouldRejectApplicantReadingTalentPool() throws Exception {
        mockMvc.perform(get("/merchant/talent/list")
                        .with(authorizedAs(5L, "APPLICANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("仅商家可访问候选人库"));

        verifyNoInteractions(talentPoolService);
    }

    @Test
    void shouldRejectInvalidTalentPoolPageSize() throws Exception {
        mockMvc.perform(get("/merchant/talent/list")
                        .param("size", "0")
                        .with(authorizedAs(9L, "MERCHANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("每页条数必须为正数"));

        verifyNoInteractions(talentPoolService);
    }

    @Test
    void shouldRejectInvalidTalentCandidateId() throws Exception {
        mockMvc.perform(get("/merchant/talent/detail/0")
                        .with(authorizedAs(9L, "MERCHANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("求职者ID必须为正数"));

        verifyNoInteractions(talentPoolService);
    }
}
