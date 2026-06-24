package com.example.backend.support;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.util.List;

/**
 * 真实 MySQL 口径测试基类。
 * Task 10 首轮先走独立 MySQL 测试库方案，避免把现有 H2 快速回归直接替换掉。
 */
@SpringBootTest
@ActiveProfiles("mysql-test")
public abstract class MySqlPersistenceTestBase {

    @Autowired
    protected DataSource dataSource;

    /**
     * 断言当前连接确实落在 MySQL 测试库，避免误用 H2 或开发库。
     */
    protected void assertRunningOnMySqlTestDatabase() throws Exception {
        try (var connection = dataSource.getConnection()) {
            Assertions.assertEquals("MySQL", connection.getMetaData().getDatabaseProductName());
            Assertions.assertEquals("sme_recruitment_test", connection.getCatalog());
        }
    }

    /**
     * 为服务层真实数据库测试注入管理员/商家/求职者安全上下文。
     */
    protected void authenticateAs(long userId, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        String.valueOf(userId),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))
                )
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }
}
