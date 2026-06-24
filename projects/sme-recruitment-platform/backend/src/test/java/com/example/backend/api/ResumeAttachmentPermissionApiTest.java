/**
 * 文件速览：
 * 1. 文件职责：覆盖附件简历授权接口的登录态、投递关系校验与成功流。
 * 2. 关键升级：验证申请、授权、状态查询都不能绕过真实投递关系。
 * 3. 关键入口：/resume-attachment/permission/request、/grant、/status。
 * 4. 阅读建议：先看越权失败场景，再看成功返回的状态映射。
 */
/*
 * 文件速览：
 * 1. 文件职责：覆盖附件简历授权申请、授权确认与状态查询接口测试。
 * 2. 对外入口：/resume-attachment/permission/request、/grant、/status。
 * 3. 关键结构：验证字段校验、投递关系约束与成功状态映射。
 * 4. 阅读建议：先看 request/grant 的参数与关系校验，再看 status 查询结果。
 */
package com.example.backend.api;

import com.example.backend.entity.ResumeAttachmentPermission;
import com.example.backend.service.JobDeliveryService;
import com.example.backend.service.ResumeAttachmentPermissionService;
import com.example.backend.support.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ResumeAttachmentPermissionApiTest extends ApiTestBase {

    @MockBean
    private ResumeAttachmentPermissionService permissionService;

    @MockBean
    private JobDeliveryService jobDeliveryService;

    @Test
    void shouldRejectPermissionRequestWithoutDeliveryRelation() throws Exception {
        when(jobDeliveryService.hasDeliveryRelation(1L, 2L)).thenReturn(false);

        mockMvc.perform(post("/resume-attachment/permission/request")
                        .with(authorizedAs(1L, "MERCHANT"))
                        .contentType(json())
                        .content("""
                                {
                                  "applicantId": 2
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("仅可向有投递关系的求职者申请附件简历"));

        verify(permissionService, never()).requestPermission(any(), any(), any());
    }

    @Test
    void shouldRejectPermissionRequestWhenApplicantIdMissing() throws Exception {
        mockMvc.perform(post("/resume-attachment/permission/request")
                        .with(authorizedAs(1L, "MERCHANT"))
                        .contentType(json())
                        .content("""
                                {
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("求职者ID不能为空"));
    }

    @Test
    void shouldCreatePermissionRequestWhenDeliveryRelationExists() throws Exception {
        ResumeAttachmentPermission permission = new ResumeAttachmentPermission();
        permission.setApplicantId(2L);
        permission.setMerchantId(1L);
        permission.setStatus(0);
        permission.setExpireTime(LocalDateTime.of(2026, 3, 10, 12, 0));

        when(jobDeliveryService.hasDeliveryRelation(1L, 2L)).thenReturn(true);
        when(permissionService.requestPermission(any(), any(), any())).thenReturn(permission);

        mockMvc.perform(post("/resume-attachment/permission/request")
                        .with(authorizedAs(1L, "MERCHANT"))
                        .contentType(json())
                        .content("""
                                {
                                  "applicantId": 2
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.applicantId").value(2))
                .andExpect(jsonPath("$.data.merchantId").value(1))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void shouldRejectGrantWithoutDeliveryRelation() throws Exception {
        when(jobDeliveryService.hasDeliveryRelation(9L, 2L)).thenReturn(false);

        mockMvc.perform(post("/resume-attachment/permission/grant")
                        .with(authorizedAs(2L, "APPLICANT"))
                        .contentType(json())
                        .content("""
                                {
                                  "merchantId": 9
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("仅可向有投递关系的商家授权附件简历"));

        verify(permissionService, never()).grantPermission(any(), any(), any());
    }

    @Test
    void shouldRejectStatusQueryWithoutDeliveryRelation() throws Exception {
        when(jobDeliveryService.hasDeliveryRelation(9L, 2L)).thenReturn(false);

        mockMvc.perform(get("/resume-attachment/permission/status")
                        .with(authorizedAs(2L, "APPLICANT"))
                        .param("applicantId", "2")
                        .param("merchantId", "9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("当前双方不存在投递关系"));

        verify(permissionService, never()).getPermission(any(), any());
    }

    @Test
    void shouldReturnNoneStatusWhenRelationExistsButNoRecord() throws Exception {
        when(jobDeliveryService.hasDeliveryRelation(9L, 2L)).thenReturn(true);
        when(permissionService.getPermission(2L, 9L)).thenReturn(null);

        mockMvc.perform(get("/resume-attachment/permission/status")
                        .with(authorizedAs(2L, "APPLICANT"))
                        .param("applicantId", "2")
                        .param("merchantId", "9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.applicantId").value(2))
                .andExpect(jsonPath("$.data.merchantId").value(9))
                .andExpect(jsonPath("$.data.status").value("NONE"));
    }
}
