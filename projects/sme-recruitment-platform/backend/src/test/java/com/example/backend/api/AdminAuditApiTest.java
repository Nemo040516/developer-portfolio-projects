package com.example.backend.api;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.backend.entity.AuditLog;
import com.example.backend.service.AdminService;
import com.example.backend.support.ApiTestBase;
import com.example.backend.vo.AdminJobAuditVO;
import com.example.backend.vo.AdminMerchantAuditVO;
import com.example.backend.vo.AdminReportVO;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理员审核与处理接口测试。
 * Task 3 当前先覆盖最关键的鉴权、参数校验和基础成功流。
 */
class AdminAuditApiTest extends ApiTestBase {

    @MockBean
    private AdminService adminService;

    @Test
    void shouldRejectNonAdminWhenReadingJobAuditList() throws Exception {
        mockMvc.perform(get("/admin/jobs")
                        .with(authorizedAs(2L, "MERCHANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("仅管理员可访问"));
    }

    @Test
    void shouldReturnJobAuditListForAdmin() throws Exception {
        Page<AdminJobAuditVO> page = new Page<>(1, 20);
        when(adminService.getJobAuditList(any(Page.class), any(), any(), any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/admin/jobs")
                        .with(authorizedAs(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void shouldPassJobAuditFiltersAndPageFallbackToService() throws Exception {
        Page<AdminJobAuditVO> page = new Page<>(3, 15);
        AdminJobAuditVO record = new AdminJobAuditVO();
        record.setId(11L);
        record.setTitle("Java 后端工程师");
        page.setRecords(List.of(record));
        when(adminService.getJobAuditList(any(Page.class), any(), any(), any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/admin/jobs")
                        .with(authorizedAs(1L, "ADMIN"))
                        .param("page", "3")
                        .param("size", "15")
                        .param("keyword", "Java")
                        .param("status", "1")
                        .param("sortField", "createdAt")
                        .param("sortOrder", "descend")
                        .param("timeOrder", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.current").value(3))
                .andExpect(jsonPath("$.data.size").value(15))
                .andExpect(jsonPath("$.data.records[0].id").value(11))
                .andExpect(jsonPath("$.data.records[0].title").value("Java 后端工程师"));

        verify(adminService).getJobAuditList(
                argThat(pageArg -> pageArg.getCurrent() == 3 && pageArg.getSize() == 15),
                eq("Java"),
                eq(1),
                eq("createdAt"),
                eq("descend"),
                eq("asc")
        );
    }

    @Test
    void shouldUseDefaultPaginationWhenReadingMerchantAuditList() throws Exception {
        Page<AdminMerchantAuditVO> page = new Page<>(1, 20);
        AdminMerchantAuditVO record = new AdminMerchantAuditVO();
        record.setId(9L);
        record.setCompanyName("测试企业");
        page.setRecords(List.of(record));
        when(adminService.getMerchantAuditList(any(Page.class), any(), any())).thenReturn(page);

        mockMvc.perform(get("/admin/merchants")
                        .with(authorizedAs(1L, "ADMIN"))
                        .param("keyword", "测试")
                        .param("status", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.current").value(1))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.records[0].companyName").value("测试企业"));

        verify(adminService).getMerchantAuditList(
                argThat(pageArg -> pageArg.getCurrent() == 1 && pageArg.getSize() == 20),
                eq("测试"),
                eq(0)
        );
    }

    @Test
    void shouldRejectJobAuditWithoutReasonWhenRejected() throws Exception {
        mockMvc.perform(put("/admin/jobs/11/audit")
                        .with(authorizedAs(1L, "ADMIN"))
                        .contentType(json())
                        .content("""
                                {
                                  "status": 2,
                                  "reason": " "
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("驳回原因不能为空"));
    }

    @Test
    void shouldAuditJobSuccessfullyForAdmin() throws Exception {
        mockMvc.perform(put("/admin/jobs/11/audit")
                        .with(authorizedAs(1L, "ADMIN"))
                        .contentType(json())
                        .content("""
                                {
                                  "status": 1,
                                  "reason": "通过"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("操作成功"));

        verify(adminService).auditJob(11L, 1, "通过");
    }

    @Test
    void shouldRejectInvalidMerchantPublishStatus() throws Exception {
        mockMvc.perform(put("/admin/merchants/9/status")
                        .with(authorizedAs(1L, "ADMIN"))
                        .contentType(json())
                        .content("""
                                {
                                  "status": 7,
                                  "reason": "非法值"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("发布状态不合法"));
    }

    @Test
    void shouldHandleReportAndFillDefaultRejectResult() throws Exception {
        mockMvc.perform(put("/admin/reports/7/handle")
                        .with(authorizedAs(1L, "ADMIN"))
                        .contentType(json())
                        .content("""
                                {
                                  "status": 2,
                                  "action": "REJECT"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("操作成功"));

        verify(adminService).handleReport(7L, 2, "REJECT", "驳回举报");
    }

    @Test
    void shouldReturnReportListForAdmin() throws Exception {
        Page<AdminReportVO> page = new Page<>(1, 20);
        when(adminService.getReportList(any(Page.class), any(), any())).thenReturn(page);

        mockMvc.perform(get("/admin/reports")
                        .with(authorizedAs(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void shouldPassReportFiltersAndCurrentPaginationToService() throws Exception {
        Page<AdminReportVO> page = new Page<>(2, 30);
        AdminReportVO record = new AdminReportVO();
        record.setId(7L);
        record.setType("USER");
        record.setTargetName("候选人甲");
        page.setRecords(List.of(record));
        when(adminService.getReportList(any(Page.class), any(), any())).thenReturn(page);

        mockMvc.perform(get("/admin/reports")
                        .with(authorizedAs(1L, "ADMIN"))
                        .param("current", "2")
                        .param("size", "30")
                        .param("type", "USER")
                        .param("status", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.current").value(2))
                .andExpect(jsonPath("$.data.size").value(30))
                .andExpect(jsonPath("$.data.records[0].targetName").value("候选人甲"));

        verify(adminService).getReportList(
                argThat(pageArg -> pageArg.getCurrent() == 2 && pageArg.getSize() == 30),
                eq("USER"),
                eq(0)
        );
    }

    @Test
    void shouldRejectNonAdminWhenReadingJobAuditLogs() throws Exception {
        mockMvc.perform(get("/admin/jobs/11/logs")
                        .with(authorizedAs(2L, "MERCHANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("仅管理员可访问"));
    }

    @Test
    void shouldReturnJobAuditLogsForAdmin() throws Exception {
        when(adminService.getJobAuditLogs(11L)).thenReturn(List.of(buildAuditLog("JOB", "AUDIT", 11L)));

        mockMvc.perform(get("/admin/jobs/11/logs")
                        .with(authorizedAs(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].module").value("JOB"))
                .andExpect(jsonPath("$.data[0].action").value("AUDIT"));

        verify(adminService).getJobAuditLogs(11L);
    }

    @Test
    void shouldReturnMerchantAuditLogsForAdmin() throws Exception {
        when(adminService.getMerchantAuditLogs(9L)).thenReturn(List.of(buildAuditLog("MERCHANT", "AUDIT", 9L)));

        mockMvc.perform(get("/admin/merchants/9/logs")
                        .with(authorizedAs(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].module").value("MERCHANT"))
                .andExpect(jsonPath("$.data[0].targetId").value(9));

        verify(adminService).getMerchantAuditLogs(9L);
    }

    @Test
    void shouldReturnReportLogsForAdmin() throws Exception {
        when(adminService.getReportLogs(7L)).thenReturn(List.of(buildAuditLog("REPORT", "HANDLE", 7L)));

        mockMvc.perform(get("/admin/reports/7/logs")
                        .with(authorizedAs(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].module").value("REPORT"))
                .andExpect(jsonPath("$.data[0].action").value("HANDLE"));

        verify(adminService).getReportLogs(7L);
    }

    /**
     * 构造日志夹具，减少日志接口测试的重复样板。
     */
    private AuditLog buildAuditLog(String module, String action, Long targetId) {
        AuditLog log = new AuditLog();
        log.setId(1L);
        log.setModule(module);
        log.setAction(action);
        log.setTargetId(targetId);
        log.setOperatorId(1L);
        log.setOperatorRole("ADMIN");
        log.setDetail("测试日志");
        log.setCreateTime(LocalDateTime.of(2026, 3, 3, 10, 0, 0));
        return log;
    }
}
