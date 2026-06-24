package com.example.backend.integration;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.backend.service.AdminService;
import com.example.backend.support.MySqlPersistenceTestBase;
import com.example.backend.vo.AdminJobAuditCountVO;
import com.example.backend.vo.AdminJobAuditVO;
import com.example.backend.vo.AdminMerchantAuditVO;
import com.example.backend.vo.AdminReportVO;
import com.example.backend.vo.AdminStatsVO;
import com.example.backend.vo.AdminUserVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 独立 MySQL 口径下的管理侧真实 SQL 查询与统计专项测试。
 * 类名不走默认 surefire 通配，避免影响默认 `mvn test`。
 */
@Sql(scripts = {"/sql/base-schema.sql", "/sql/seed-admin-query.sql"})
class MySqlAdminQuerySpec extends MySqlPersistenceTestBase {

    @Autowired
    private AdminService adminService;

    @Test
    void shouldReturnJobAuditListWithRealOrderingAgainstMySql() throws Exception {
        assertRunningOnMySqlTestDatabase();

        IPage<AdminJobAuditVO> result = adminService.getJobAuditList(
                new Page<>(1, 10),
                null,
                null,
                "auditStatus",
                "descend",
                "asc"
        );

        assertEquals(4, result.getTotal());
        assertEquals(List.of(102L, 104L, 101L, 103L), result.getRecords().stream()
                .map(AdminJobAuditVO::getId)
                .toList());
        assertEquals("云海物流", result.getRecords().get(0).getCompanyName());
        assertEquals("25-35K", result.getRecords().get(1).getSalary());
        assertEquals("薪资区间调整", result.getRecords().get(1).getLastEditSummary());
    }

    @Test
    void shouldFilterMerchantAuditListAndProjectReportCountAgainstMySql() throws Exception {
        assertRunningOnMySqlTestDatabase();

        IPage<AdminMerchantAuditVO> result = adminService.getMerchantAuditList(
                new Page<>(1, 10),
                "星辰",
                0
        );

        assertEquals(1, result.getTotal());
        AdminMerchantAuditVO merchant = result.getRecords().get(0);
        assertEquals(11L, merchant.getId());
        assertEquals("星辰科技", merchant.getCompanyName());
        assertEquals("星辰负责人", merchant.getContact());
        assertEquals("13800000002", merchant.getPhone());
        assertEquals("上海市上海市浦东新区张江科学城", merchant.getAddress());
        assertEquals(2, merchant.getReportCount());
    }

    @Test
    void shouldReturnMerchantReportProjectionAgainstMySql() throws Exception {
        assertRunningOnMySqlTestDatabase();

        IPage<AdminReportVO> result = adminService.getReportList(new Page<>(1, 10), "MERCHANT", 0);

        assertEquals(1, result.getTotal());
        AdminReportVO report = result.getRecords().get(0);
        assertEquals(201L, report.getId());
        assertEquals("星辰科技", report.getTargetName());
        assertEquals("候选人甲", report.getReporter());
        assertEquals("APPLICANT", report.getReporterRole());
        assertEquals(2, report.getReportCount());
        assertEquals("星辰科技", report.getMerchantCompanyName());
        assertEquals("互联网", report.getMerchantIndustry());
        assertEquals(1, report.getMerchantPublishStatus());
        assertTrue(report.getTargetSnapshot().contains("\"companyName\":\"星辰科技\""));
    }

    @Test
    void shouldReturnUserReportEvidenceProjectionAgainstMySql() throws Exception {
        assertRunningOnMySqlTestDatabase();

        IPage<AdminReportVO> result = adminService.getReportList(new Page<>(1, 10), "USER", 0);

        assertEquals(1, result.getTotal());
        AdminReportVO report = result.getRecords().get(0);
        assertEquals(203L, report.getId());
        assertEquals("候选人甲", report.getTargetName());
        assertEquals("星辰负责人", report.getReporter());
        assertEquals("MERCHANT", report.getReporterRole());
        assertEquals("/uploads/reports/2/user-chat-1.png,/uploads/reports/2/user-chat-2.pdf", report.getEvidence());
        assertNotNull(report.getEvidenceFiles());
        assertTrue(report.getEvidenceFiles().contains("IMAGE"));
        assertTrue(report.getEvidenceFiles().contains("/uploads/reports/2/user-chat-2.pdf"));
        assertEquals("候选人甲", report.getUserNickname());
        assertEquals(1, report.getUserBanStatus());
    }

    @Test
    void shouldReturnUserListAndStatsAgainstMySql() throws Exception {
        assertRunningOnMySqlTestDatabase();

        IPage<AdminUserVO> userResult = adminService.getUserList(
                new Page<>(1, 10),
                "候选",
                "APPLICANT",
                1,
                null
        );

        assertEquals(2, userResult.getTotal());
        assertEquals(List.of(5L, 3L), userResult.getRecords().stream()
                .map(AdminUserVO::getId)
                .toList());

        AdminStatsVO stats = adminService.getStats();
        assertEquals(2, stats.getJobPending());
        assertEquals(1, stats.getMerchantPending());
        assertEquals(2, stats.getReportPending());
        assertEquals(2, stats.getTodayJobs());
        assertEquals(1, stats.getTodayMerchants());
        assertEquals(2, stats.getTodayReports());
        assertEquals(4, stats.getTotalJobs());
        assertEquals(2, stats.getTotalMerchants());
        assertEquals(3, stats.getTotalReports());
    }

    @Test
    void shouldReturnJobAuditCountsAgainstMySql() throws Exception {
        assertRunningOnMySqlTestDatabase();

        AdminJobAuditCountVO counts = adminService.getJobAuditCounts();
        assertEquals(4, counts.getTotal());
        assertEquals(2, counts.getPending());
        assertEquals(1, counts.getApproved());
        assertEquals(1, counts.getRejected());
    }
}
