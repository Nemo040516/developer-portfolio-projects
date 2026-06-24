/*
 * 文件速览：
 * 1. 文件职责：覆盖面试安排查询与状态更新的控制器层接口测试。
 * 2. 对外入口：/interview/list、/interview/status。
 * 3. 关键结构：验证登录态、字段级参数校验与基础成功流。
 * 4. 阅读建议：先看查询权限，再看状态更新的校验与成功断言。
 */
package com.example.backend.api;

import com.example.backend.entity.InterviewSchedule;
import com.example.backend.service.InterviewScheduleService;
import com.example.backend.support.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 面试安排接口测试。
 * 当前阶段先覆盖登录态、参数校验和基础成功流。
 */
class InterviewFlowApiTest extends ApiTestBase {

    @MockBean
    private InterviewScheduleService interviewScheduleService;

    @Test
    void shouldRequireLoginWhenReadingInterviewList() throws Exception {
        mockMvc.perform(get("/interview/list").param("deliveryId", "101"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void shouldReturnInterviewListForLoggedInUser() throws Exception {
        InterviewSchedule schedule = new InterviewSchedule();
        schedule.setId(31L);
        schedule.setDeliveryId(101L);
        schedule.setRoundNo(1);
        schedule.setStatus(0);
        schedule.setLocation("线上会议室");
        schedule.setMethod("线上面试");
        schedule.setScheduleTime(LocalDateTime.of(2026, 3, 6, 14, 0));

        when(interviewScheduleService.getScheduleList(101L, 1L, "APPLICANT")).thenReturn(List.of(schedule));

        mockMvc.perform(get("/interview/list")
                        .with(authorizedAs(1L, "APPLICANT"))
                        .param("deliveryId", "101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(31))
                .andExpect(jsonPath("$.data[0].location").value("线上会议室"));
    }

    @Test
    void shouldRejectInterviewStatusUpdateWhenMissingRequiredFields() throws Exception {
        mockMvc.perform(put("/interview/status")
                        .with(authorizedAs(2L, "MERCHANT"))
                        .contentType(json())
                        .content("""
                                {
                                  "id": 33
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("面试状态不能为空"));
    }

    @Test
    void shouldUpdateInterviewStatusSuccessfully() throws Exception {
        when(interviewScheduleService.updateStatus(33L, 1, 2L, "MERCHANT")).thenReturn(true);

        mockMvc.perform(put("/interview/status")
                        .with(authorizedAs(2L, "MERCHANT"))
                        .contentType(json())
                        .content("""
                                {
                                  "id": 33,
                                  "status": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(true));

        verify(interviewScheduleService).updateStatus(33L, 1, 2L, "MERCHANT");
    }
}
