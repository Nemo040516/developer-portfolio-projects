package com.example.backend.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.backend.entity.AuditLog;
import com.example.backend.entity.ApplicantInfo;
import com.example.backend.entity.ReportEvidence;
import com.example.backend.entity.ReportInfo;
import com.example.backend.entity.SysUser;
import com.example.backend.service.AdminService;
import com.example.backend.service.ApplicantInfoService;
import com.example.backend.service.AuditLogService;
import com.example.backend.service.ReportEvidenceService;
import com.example.backend.service.ReportInfoService;
import com.example.backend.service.SysUserService;
import com.example.backend.support.MySqlPersistenceTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实 MySQL 口径首轮持久化测试。
 * 说明：类名故意不走默认 surefire 通配，避免默认 `mvn test` 强制依赖本机 MySQL。
 */
@Sql(scripts = {"/sql/base-schema.sql", "/sql/base-seed.sql"})
class MySqlPersistenceSpec extends MySqlPersistenceTestBase {

    @Autowired
    private SysUserService sysUserService;

    @Autowired
    private ApplicantInfoService applicantInfoService;

    @Autowired
    private ReportInfoService reportInfoService;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private AdminService adminService;

    @Autowired
    private ReportEvidenceService reportEvidenceService;

    @Test
    void shouldUpdateSysUserWithoutWritingGeneratedRoleSortColumn() throws Exception {
        assertRunningOnMySqlTestDatabase();

        SysUser merchant = sysUserService.getById(2L);
        assertNotNull(merchant);
        assertEquals("MERCHANT", merchant.getRole());
        assertEquals(2, merchant.getRoleSort());

        merchant.setNickname("已验证商家");
        merchant.setBanStatus(2);
        merchant.setBanReason("真实 MySQL 生成列回归");

        assertTrue(sysUserService.updateById(merchant));

        SysUser persisted = sysUserService.getById(2L);
        assertNotNull(persisted);
        assertEquals("已验证商家", persisted.getNickname());
        assertEquals(2, persisted.getBanStatus());
        assertEquals("真实 MySQL 生成列回归", persisted.getBanReason());
        assertEquals(2, persisted.getRoleSort());
    }

    @Test
    void shouldPersistAndClearResumeUrlAgainstMySql() throws Exception {
        assertRunningOnMySqlTestDatabase();
        assertNull(applicantInfoService.getResumeUrl(3L));

        String fileUrl = "/uploads/resumes/3/app1.pdf";
        assertTrue(applicantInfoService.updateResumeUrl(3L, fileUrl));
        assertEquals(fileUrl, applicantInfoService.getResumeUrl(3L));

        ApplicantInfo savedInfo = applicantInfoService.getByUserId(3L);
        assertNotNull(savedInfo);
        assertEquals(fileUrl, savedInfo.getResumeUrl());

        assertTrue(applicantInfoService.updateResumeUrl(3L, null));

        ApplicantInfo clearedInfo = applicantInfoService.getByUserId(3L);
        assertNotNull(clearedInfo);
        assertNull(clearedInfo.getResumeUrl());
        assertNull(applicantInfoService.getResumeUrl(3L));
    }

    @Test
    void shouldPersistReportAndEvidenceRowsAgainstMySql() throws Exception {
        assertRunningOnMySqlTestDatabase();

        LocalDateTime now = LocalDateTime.of(2026, 3, 3, 11, 0, 0);
        ReportInfo report = new ReportInfo();
        report.setType("USER");
        report.setTargetId(3L);
        report.setReporterId(2L);
        report.setReason("存在违规行为");
        report.setStatus(0);
        report.setEvidence("/uploads/reports/2/evidence-1.png,/uploads/reports/2/evidence-2.pdf");
        report.setTargetSnapshot("{\"type\":\"USER\",\"targetId\":3,\"username\":\"app1\"}");
        report.setCreateTime(now);
        report.setUpdateTime(now);

        assertTrue(reportInfoService.save(report));
        assertNotNull(report.getId());

        ReportEvidence image = new ReportEvidence();
        image.setReportId(report.getId());
        image.setFileUrl("/uploads/reports/2/evidence-1.png");
        image.setFileType("IMAGE");
        image.setSortOrder(1);
        image.setUploaderId(2L);
        image.setCreateTime(now);

        ReportEvidence pdf = new ReportEvidence();
        pdf.setReportId(report.getId());
        pdf.setFileUrl("/uploads/reports/2/evidence-2.pdf");
        pdf.setFileType("PDF");
        pdf.setSortOrder(2);
        pdf.setUploaderId(2L);
        pdf.setCreateTime(now);

        assertTrue(reportEvidenceService.saveBatch(List.of(image, pdf)));

        ReportInfo persistedReport = reportInfoService.getById(report.getId());
        assertNotNull(persistedReport);
        assertEquals("USER", persistedReport.getType());
        assertEquals(3L, persistedReport.getTargetId());
        assertEquals(2L, persistedReport.getReporterId());
        assertEquals("{\"type\":\"USER\",\"targetId\":3,\"username\":\"app1\"}", persistedReport.getTargetSnapshot());

        List<ReportEvidence> rows = reportEvidenceService.list(new LambdaQueryWrapper<ReportEvidence>()
                .eq(ReportEvidence::getReportId, report.getId())
                .orderByAsc(ReportEvidence::getSortOrder));
        assertEquals(2, rows.size());
        assertEquals("IMAGE", rows.get(0).getFileType());
        assertEquals(1, rows.get(0).getSortOrder());
        assertEquals("PDF", rows.get(1).getFileType());
        assertEquals(2, rows.get(1).getSortOrder());
    }

    @Test
    void shouldHandleUserReportAndPersistAuditSideEffectsAgainstMySql() throws Exception {
        assertRunningOnMySqlTestDatabase();

        ReportInfo report = new ReportInfo();
        report.setType("USER");
        report.setTargetId(3L);
        report.setReporterId(2L);
        report.setReason("恶意骚扰");
        report.setStatus(0);
        report.setCreateTime(LocalDateTime.of(2026, 3, 3, 11, 30, 0));
        report.setUpdateTime(LocalDateTime.of(2026, 3, 3, 11, 30, 0));
        assertTrue(reportInfoService.save(report));

        authenticateAs(1L, "ADMIN");
        adminService.handleReport(report.getId(), 1, "USER_BAN", "恶意骚扰");

        ReportInfo handled = reportInfoService.getById(report.getId());
        assertNotNull(handled);
        assertEquals(1, handled.getStatus());
        assertEquals("USER_BAN", handled.getActionCode());
        assertEquals(1L, handled.getHandledBy());
        assertEquals("恶意骚扰", handled.getResult());
        assertNotNull(handled.getHandledTime());

        SysUser bannedUser = sysUserService.getById(3L);
        assertNotNull(bannedUser);
        assertEquals(1, bannedUser.getBanStatus());
        assertEquals("恶意骚扰", bannedUser.getBanReason());

        List<AuditLog> logs = auditLogService.list(new LambdaQueryWrapper<AuditLog>()
                .eq(AuditLog::getModule, "REPORT")
                .eq(AuditLog::getTargetId, report.getId())
                .orderByAsc(AuditLog::getId));
        assertEquals(1, logs.size());
        assertEquals("HANDLE", logs.get(0).getAction());
        assertEquals(1L, logs.get(0).getOperatorId());
        assertEquals("ADMIN", logs.get(0).getOperatorRole());
        assertTrue(logs.get(0).getDetail().contains("USER_BAN"));
    }
}
