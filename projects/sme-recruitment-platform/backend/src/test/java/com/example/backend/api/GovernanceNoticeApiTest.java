/*
 * 文件速览：
 * 1. 文件职责：验证用户侧治理通知控制器的登录要求、分页兜底、参数透传与已读/动作提交流程契约。
 * 2. 对外入口：覆盖 /governance/notices/my、/governance/notices/{id}/read、/governance/notices/{id}/actions。
 * 3. 关键结构：MockMvc + MockBean GovernanceNoticeService，重点锁定控制器与安全链的衔接行为。
 * 4. 阅读建议：先看未登录 401 口径，再看列表参数透传，最后看已读和 restrictedMode 动作提交。
 */
package com.example.backend.api;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.backend.dto.GovernanceNoticeActionDTO;
import com.example.backend.service.GovernanceNoticeService;
import com.example.backend.support.ApiTestBase;
import com.example.backend.vo.GovernanceNoticeVO;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户侧治理通知接口测试。
 */
class GovernanceNoticeApiTest extends ApiTestBase {

    @MockBean
    private GovernanceNoticeService governanceNoticeService;

    @Test
    void shouldRejectUnauthenticatedWhenReadingMyGovernanceList() throws Exception {
        mockMvc.perform(get("/governance/notices/my"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.msg").value("凭证已失效，请重新登录"));
    }

    @Test
    void shouldUseDefaultPaginationForMyGovernanceList() throws Exception {
        Page<GovernanceNoticeVO> page = new Page<>(1, 20);
        when(governanceNoticeService.getMyNoticePage(any(Page.class), any(), any(), any(), any()))
                .thenReturn(page);

        mockMvc.perform(get("/governance/notices/my")
                        .with(authorizedAs(7L, "APPLICANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.current").value(1))
                .andExpect(jsonPath("$.data.size").value(20));

        verify(governanceNoticeService).getMyNoticePage(
                argThat(pageArg -> pageArg.getCurrent() == 1 && pageArg.getSize() == 20),
                eq(7L),
                eq(null),
                eq(null),
                eq(null)
        );
    }

    @Test
    void shouldPassMyGovernanceFiltersAndPageFallbackToService() throws Exception {
        Page<GovernanceNoticeVO> page = new Page<>(2, 12);
        GovernanceNoticeVO notice = new GovernanceNoticeVO();
        notice.setId(19L);
        notice.setNoticeNo("GN202603090003");
        notice.setTitle("举报结果通知");
        notice.setStatus("PENDING_READ");
        page.setRecords(List.of(notice));
        when(governanceNoticeService.getMyNoticePage(any(Page.class), any(), any(), any(), any()))
                .thenReturn(page);

        mockMvc.perform(get("/governance/notices/my")
                        .with(authorizedAs(7L, "APPLICANT"))
                        .param("page", "2")
                        .param("size", "12")
                        .param("status", "PENDING_READ")
                        .param("noticeType", "REPORT_RESULT")
                        .param("stage", "READ"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.current").value(2))
                .andExpect(jsonPath("$.data.size").value(12))
                .andExpect(jsonPath("$.data.records[0].noticeNo").value("GN202603090003"));

        verify(governanceNoticeService).getMyNoticePage(
                argThat(pageArg -> pageArg.getCurrent() == 2 && pageArg.getSize() == 12),
                eq(7L),
                eq("PENDING_READ"),
                eq("REPORT_RESULT"),
                eq("READ")
        );
    }

    @Test
    void shouldRejectUnauthenticatedWhenReadingMyGovernanceDetail() throws Exception {
        mockMvc.perform(get("/governance/notices/my/18"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.msg").value("凭证已失效，请重新登录"));
    }

    @Test
    void shouldReturnMyGovernanceDetailForAuthenticatedUser() throws Exception {
        GovernanceNoticeVO notice = new GovernanceNoticeVO();
        notice.setId(18L);
        notice.setNoticeNo("GN202603090004");
        notice.setTitle("封禁说明");
        notice.setStatus("PENDING_ACTION");
        when(governanceNoticeService.getMyNoticeDetail(18L, 7L)).thenReturn(notice);

        mockMvc.perform(get("/governance/notices/my/18")
                        .with(authorizedAs(7L, "APPLICANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.title").value("封禁说明"))
                .andExpect(jsonPath("$.data.noticeNo").value("GN202603090004"));
    }

    @Test
    void shouldRejectUnauthenticatedWhenMarkingGovernanceNoticeRead() throws Exception {
        mockMvc.perform(put("/governance/notices/12/read"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.msg").value("凭证已失效，请重新登录"));
    }

    @Test
    void shouldMarkGovernanceNoticeReadWithCurrentUserAndRole() throws Exception {
        mockMvc.perform(put("/governance/notices/12/read")
                        .with(authorizedAs(9L, "MERCHANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("已读成功"));

        verify(governanceNoticeService).markRead(eq(12L), eq(9L), eq("MERCHANT"));
    }

    @Test
    void shouldRejectUnauthenticatedWhenSubmittingGovernanceAction() throws Exception {
        mockMvc.perform(post("/governance/notices/21/actions")
                        .contentType(json())
                        .content(buildActionPayload()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.msg").value("凭证已失效，请重新登录"));
    }

    @Test
    void shouldSubmitGovernanceActionWithRestrictedModeFlag() throws Exception {
        mockMvc.perform(post("/governance/notices/21/actions")
                        .with(authorizedAs(7L, "APPLICANT"))
                        .requestAttr("restrictedMode", true)
                        .contentType(json())
                        .content(buildActionPayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("提交成功"));

        verify(governanceNoticeService).submitAction(
                eq(21L),
                eq(7L),
                eq("APPLICANT"),
                argThat(dto ->
                        "APPEAL".equals(dto.getActionType())
                                && "这是一条自动化测试申诉说明".equals(dto.getContent())
                ),
                eq(true)
        );
    }

    /**
     * 统一构造动作请求体，便于测试聚焦接口契约。
     */
    private String buildActionPayload() {
        return """
                {
                  "actionType": "APPEAL",
                  "content": "这是一条自动化测试申诉说明"
                }
                """;
    }
}
