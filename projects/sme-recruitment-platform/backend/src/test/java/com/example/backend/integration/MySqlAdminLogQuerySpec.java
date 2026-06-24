package com.example.backend.integration;

import com.example.backend.entity.AuditLog;
import com.example.backend.service.AdminService;
import com.example.backend.support.MySqlPersistenceTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 独立 MySQL 口径下的管理侧日志查询专项测试。
 * 类名不走默认 surefire 通配，避免影响默认 `mvn test`。
 */
@Sql(scripts = {"/sql/base-schema.sql", "/sql/seed-admin-log-query.sql"})
class MySqlAdminLogQuerySpec extends MySqlPersistenceTestBase {

    @Autowired
    private AdminService adminService;

    @Test
    void shouldReturnNewestThirtyJobAuditLogsAgainstMySql() throws Exception {
        assertRunningOnMySqlTestDatabase();

        List<AuditLog> logs = adminService.getJobAuditLogs(101L);

        assertEquals(30, logs.size());
        assertTrue(logs.stream().allMatch(log -> "JOB".equals(log.getModule())));
        assertTrue(logs.stream().allMatch(log -> Long.valueOf(101L).equals(log.getTargetId())));
        assertEquals("JOB-101 日志#35", logs.get(0).getDetail());
        assertEquals("JOB-101 日志#06", logs.get(logs.size() - 1).getDetail());
    }

    @Test
    void shouldFilterMerchantLogsByTargetAgainstMySql() throws Exception {
        assertRunningOnMySqlTestDatabase();

        List<AuditLog> logs = adminService.getMerchantAuditLogs(11L);

        assertEquals(4, logs.size());
        assertTrue(logs.stream().allMatch(log -> "MERCHANT".equals(log.getModule())));
        assertTrue(logs.stream().allMatch(log -> Long.valueOf(11L).equals(log.getTargetId())));
        assertEquals("MERCHANT-11 日志#04", logs.get(0).getDetail());
        assertEquals("MERCHANT-11 日志#01", logs.get(logs.size() - 1).getDetail());
    }

    @Test
    void shouldFilterReportLogsByTargetAgainstMySql() throws Exception {
        assertRunningOnMySqlTestDatabase();

        List<AuditLog> logs = adminService.getReportLogs(201L);

        assertEquals(3, logs.size());
        assertTrue(logs.stream().allMatch(log -> "REPORT".equals(log.getModule())));
        assertTrue(logs.stream().allMatch(log -> Long.valueOf(201L).equals(log.getTargetId())));
        assertEquals("REPORT-201 日志#03", logs.get(0).getDetail());
        assertEquals("REPORT-201 日志#01", logs.get(logs.size() - 1).getDetail());
    }
}
