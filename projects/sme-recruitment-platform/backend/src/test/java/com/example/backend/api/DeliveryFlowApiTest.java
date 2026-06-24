/*
 * 文件速览：
 * 1. 文件职责：覆盖投递提交、投递状态更新与投递记录查询的控制器层接口测试。
 * 2. 对外入口：/delivery/submit、/delivery/status、/delivery/seeker/list、/delivery/merchant/list。
 * 3. 关键结构：验证登录态、角色隔离、字段校验与基础成功流。
 * 4. 阅读建议：先看 submit 的参数与角色校验，再看状态更新断言。
 */
package com.example.backend.api;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.backend.service.JobDeliveryService;
import com.example.backend.support.ApiTestBase;
import com.example.backend.vo.DeliveryVO;
import com.example.backend.vo.MerchantDeliveryVO;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 投递闭环接口测试。
 * 当前阶段优先锁住角色隔离和控制器层成功流。
 */
class DeliveryFlowApiTest extends ApiTestBase {

    @MockBean
    private JobDeliveryService jobDeliveryService;

    @Test
    void shouldRequireLoginWhenSubmittingDelivery() throws Exception {
        mockMvc.perform(post("/delivery/submit")
                        .contentType(json())
                        .content("""
                                {
                                  "jobId": 1001
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void shouldRejectMerchantWhenSubmittingDelivery() throws Exception {
        mockMvc.perform(post("/delivery/submit")
                        .with(authorizedAs(2L, "MERCHANT"))
                        .contentType(json())
                        .content("""
                                {
                                  "jobId": 1001
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("仅求职者可投递简历"));
    }

    @Test
    void shouldSubmitDeliveryForApplicant() throws Exception {
        when(jobDeliveryService.submitDelivery(1L, 1001L)).thenReturn(true);

        mockMvc.perform(post("/delivery/submit")
                        .with(authorizedAs(1L, "APPLICANT"))
                        .contentType(json())
                        .content("""
                                {
                                  "jobId": 1001
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(true));

        verify(jobDeliveryService).submitDelivery(1L, 1001L);
    }

    @Test
    void shouldRejectDeliverySubmitWhenJobIdMissing() throws Exception {
        mockMvc.perform(post("/delivery/submit")
                        .with(authorizedAs(1L, "APPLICANT"))
                        .contentType(json())
                        .content("""
                                {
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("职位ID不能为空"));
    }

    @Test
    void shouldRejectApplicantWhenReadingMerchantDeliveryList() throws Exception {
        mockMvc.perform(get("/delivery/merchant/list")
                        .with(authorizedAs(1L, "APPLICANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("仅商家可查看投递列表"));
    }

    @Test
    void shouldUpdateDeliveryStatusForMerchant() throws Exception {
        when(jobDeliveryService.updateDeliveryStatus(eq(2L), eq(11L), eq(2), eq("请准备面试"), any(), eq("线上会议"), eq("线上面试"), eq("带上作品")))
                .thenReturn(true);

        mockMvc.perform(put("/delivery/status")
                        .with(authorizedAs(2L, "MERCHANT"))
                        .contentType(json())
                        .content("""
                                {
                                  "id": 11,
                                  "status": 2,
                                  "feedback": "请准备面试",
                                  "interviewTime": "2026-03-05T10:30:00",
                                  "interviewLocation": "线上会议",
                                  "interviewMethod": "线上面试",
                                  "interviewRemark": "带上作品"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    void shouldReturnDeliveryStatusForApplicant() throws Exception {
        when(jobDeliveryService.getDeliveryStatus(1L, 2001L)).thenReturn(2);

        mockMvc.perform(get("/delivery/status/2001")
                        .with(authorizedAs(1L, "APPLICANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(2));
    }

    @Test
    void shouldReturnApplicantDeliveryList() throws Exception {
        DeliveryVO item = new DeliveryVO();
        item.setId(21L);

        when(jobDeliveryService.getMyDeliveries(1L)).thenReturn(List.of(item));

        mockMvc.perform(get("/delivery/seeker/list")
                        .with(authorizedAs(1L, "APPLICANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(21));
    }
}
