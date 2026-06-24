/*
 * 文件速览：
 * 1. 文件职责：验证 JWT 工具类的可配置有效期与参数校验逻辑。
 * 2. 对外入口：JwtUtils 构造、generateToken、getClaimsByToken。
 * 3. 关键结构：自定义 expire-ms 生效校验、非法有效期拦截。
 * 4. 阅读建议：先看有效期断言，再看异常校验用例。
 */
package com.example.backend.utils;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilsTest {

    private static final String TEST_SECRET = "unit-test-jwt-secret-for-configurable-expire-123456";

    @Test
    void shouldUseConfiguredExpireMsWhenGeneratingToken() {
        JwtUtils jwtUtils = new JwtUtils(TEST_SECRET, 4L * 60 * 60 * 1000);

        long before = System.currentTimeMillis();
        String token = jwtUtils.generateToken(7L, "ADMIN");
        long after = System.currentTimeMillis();

        Claims claims = jwtUtils.getClaimsByToken(token);
        long actualExpireMs = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();

        assertEquals(4L * 60 * 60 * 1000, jwtUtils.getExpireMs());
        assertTrue(actualExpireMs >= (4L * 60 * 60 * 1000) - (after - before) - 1000);
        assertTrue(actualExpireMs <= (4L * 60 * 60 * 1000) + 1000);
    }

    @Test
    void shouldRejectNonPositiveExpireMs() {
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> new JwtUtils(TEST_SECRET, 0));
        assertEquals("app.jwt.expire-ms 必须为正整数毫秒值", error.getMessage());
    }
}
