/*
 * 文件速览：
 * 1. 文件职责：验证管理员治理通知控制器的权限、分页兜底、参数透传与创建/复核调用约定。
 * 2. 对外入口：覆盖 /admin/governance/notices 相关接口。
 * 3. 关键结构：MockMvc + MockBean GovernanceNoticeService，只锁控制器契约不重复测 service 细节。
 * 4. 阅读建议：先看权限分支，再看列表分页，再看创建与复核的参数传递断言。
 */
package com.example.backend.api;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.backend.dto.AdminGovernanceNoticeCreateDTO;
import com.example.backend.dto.AdminGovernanceNoticeReviewDTO;
import com.example.backend.service.GovernanceNoticeService;
import com.example.backend.support.ApiTestBase;
import com.example.backend.vo.AdminGovernanceNoticeVO;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理员治理通知接口测试。
 */
class AdminGovernanceApiTest extends ApiTestBase {

    @MockBean
    private GovernanceNoticeService governanceNoticeService;

    @Test
    void shouldRejectNonAdminWhenReadingGovernanceList() throws Exception {
        mockMvc.perform(get("/admin/governance/notices")
                        .with(authorizedAs(2L, "MERCHANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("仅管理员可访问"));
    }

    @Test
    void shouldUseDefaultPaginationWhenReadingGovernanceList() throws Exception {
        Page<AdminGovernanceNoticeVO> page = new Page<>(1, 20);
        when(governanceNoticeService.getAdminNoticePage(any(Page.class), any(), any(), any(), any(), any()))
                .thenReturn(page);

        mockMvc.perform(get("/admin/governance/notices")
                        .with(authorizedAs(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.current").value(1))
                .andExpect(jsonPath("$.data.size").value(20));

        verify(governanceNoticeService).getAdminNoticePage(
                argThat(pageArg -> pageArg.getCurrent() == 1 && pageArg.getSize() == 20),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null)
        );
    }

    @Test
    void shouldPassGovernanceFiltersAndPageFallbackToService() throws Exception {
        Page<AdminGovernanceNoticeVO> page = new Page<>(3, 15);
        AdminGovernanceNoticeVO notice = new AdminGovernanceNoticeVO();
        notice.setId(8L);
        notice.setNoticeNo("GN202603090001");
        notice.setTitle("职位整改提醒");
        notice.setNoticeType("JOB_RECTIFY");
        notice.setStatus("PENDING_REVIEW");
        page.setRecords(List.of(notice));
        when(governanceNoticeService.getAdminNoticePage(any(Page.class), any(), any(), any(), any(), any()))
                .thenReturn(page);

        mockMvc.perform(get("/admin/governance/notices")
                        .with(authorizedAs(1L, "ADMIN"))
                        .param("page", "3")
                        .param("size", "15")
                        .param("targetRole", "MERCHANT")
                        .param("noticeType", "JOB_RECTIFY")
                        .param("status", "PENDING_REVIEW")
                        .param("sourceModule", "JOB_AUDIT")
                        .param("overdueOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.current").value(3))
                .andExpect(jsonPath("$.data.size").value(15))
                .andExpect(jsonPath("$.data.records[0].noticeNo").value("GN202603090001"))
                .andExpect(jsonPath("$.data.records[0].status").value("PENDING_REVIEW"));

        verify(governanceNoticeService).getAdminNoticePage(
                argThat(pageArg -> pageArg.getCurrent() == 3 && pageArg.getSize() == 15),
                eq("MERCHANT"),
                eq("JOB_RECTIFY"),
                eq("PENDING_REVIEW"),
                eq("JOB_AUDIT"),
                eq(true)
        );
    }

    @Test
    void shouldRejectNonAdminWhenReadingGovernanceDetail() throws Exception {
        mockMvc.perform(get("/admin/governance/notices/9")
                        .with(authorizedAs(3L, "APPLICANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("仅管理员可访问"));
    }

    @Test
    void shouldReturnGovernanceDetailForAdmin() throws Exception {
        AdminGovernanceNoticeVO notice = new AdminGovernanceNoticeVO();
        notice.setId(9L);
        notice.setNoticeNo("GN202603090002");
        notice.setTitle("用户警告通知");
        notice.setStatus("PENDING_ACTION");
        when(governanceNoticeService.getAdminNoticeDetail(9L)).thenReturn(notice);

        mockMvc.perform(get("/admin/governance/notices/9")
                        .with(authorizedAs(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.noticeNo").value("GN202603090002"))
                .andExpect(jsonPath("$.data.title").value("用户警告通知"));
    }

    @Test
    void shouldRejectNonAdminWhenCreatingGovernanceNotice() throws Exception {
        mockMvc.perform(post("/admin/governance/notices")
                        .with(authorizedAs(2L, "MERCHANT"))
                        .contentType(json())
                        .content(buildCreatePayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("仅管理员可操作"));
    }

    @Test
    void shouldCreateGovernanceNoticeWithCurrentAdminId() throws Exception {
        when(governanceNoticeService.createNotice(any(AdminGovernanceNoticeCreateDTO.class), eq(1L)))
                .thenReturn(66L);

        mockMvc.perform(post("/admin/governance/notices")
                        .with(authorizedAs(1L, "ADMIN"))
                        .contentType(json())
                        .content(buildCreatePayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(66));

        verify(governanceNoticeService).createNotice(
                argThat(dto ->
                        "APPLICANT".equals(dto.getTargetRole())
                                && Long.valueOf(15L).equals(dto.getTargetUserId())
                                && "USER_WARNING".equals(dto.getNoticeType())
                                && "RISK_CONTROL".equals(dto.getSourceModule())
                                && "测试治理通知".equals(dto.getTitle())
                                && Integer.valueOf(1).equals(dto.getNeedAck())
                                && Integer.valueOf(1).equals(dto.getNeedReply())
                ),
                eq(1L)
        );
    }

    @Test
    void shouldRejectNonAdminWhenReviewingGovernanceNotice() throws Exception {
        mockMvc.perform(post("/admin/governance/notices/11/review")
                        .with(authorizedAs(8L, "MERCHANT"))
                        .contentType(json())
                        .content(buildReviewPayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("仅管理员可操作"));
    }

    @Test
    void shouldReviewGovernanceNoticeWithCurrentAdminId() throws Exception {
        mockMvc.perform(post("/admin/governance/notices/11/review")
                        .with(authorizedAs(1L, "ADMIN"))
                        .contentType(json())
                        .content(buildReviewPayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("操作成功"));

        verify(governanceNoticeService).reviewNotice(
                eq(11L),
                argThat(dto ->
                        "APPROVE".equals(dto.getReviewStatus())
                                && "复核通过".equals(dto.getReviewComment())
                ),
                eq(1L)
        );
    }

    /**
     * 统一构造创建治理通知请求体，避免测试正文里堆叠大段 JSON。
     */
    private String buildCreatePayload() {
        return """
                {
                  "targetRole": "APPLICANT",
                  "targetUserId": 15,
                  "noticeType": "USER_WARNING",
                  "severity": "WARNING",
                  "sourceModule": "RISK_CONTROL",
                  "title": "测试治理通知",
                  "summary": "这是一条自动化测试通知",
                  "detail": "请核对平台提醒内容。",
                  "requiredAction": "请先阅读说明并提交反馈。",
                  "dueTime": "2026-03-12T18:00:00",
                  "needAck": 1,
                  "needReply": 1
                }
                """;
    }

    /**
     * 统一构造复核请求体，便于聚焦复核接口断言。
     */
    private String buildReviewPayload() {
        return """
                {
                  "reviewStatus": "APPROVE",
                  "reviewComment": "复核通过"
                }
                """;
    }
}
