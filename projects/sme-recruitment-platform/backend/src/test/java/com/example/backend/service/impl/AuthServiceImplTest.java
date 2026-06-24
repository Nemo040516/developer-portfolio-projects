/*
 * 文件速览：
 * 1. 文件职责：验证认证服务的登录强制改密判断，以及自助注册角色边界。
 * 2. 对外入口：AuthServiceImpl#login、register。
 * 3. 关键结构：重置密码日志判断、开关关闭兜底、管理员自助注册拦截、历史 STUDENT 角色兼容。
 * 4. 阅读建议：先看 setUp，再看登录相关断言，最后看注册边界用例。
 */
package com.example.backend.service.impl;

import com.example.backend.common.Result;
import com.example.backend.config.RuntimeAuthSecuritySettings;
import com.example.backend.entity.AuditLog;
import com.example.backend.entity.SysUser;
import com.example.backend.mapper.SysUserMapper;
import com.example.backend.service.AuditLogService;
import com.example.backend.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String TEST_JWT_SECRET = "unit-test-jwt-secret-for-auth-service-123456";

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditLogService auditLogService;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl();
        ReflectionTestUtils.setField(authService, "sysUserMapper", sysUserMapper);
        ReflectionTestUtils.setField(authService, "passwordEncoder", passwordEncoder);
        ReflectionTestUtils.setField(authService, "jwtUtils", new JwtUtils(TEST_JWT_SECRET, 1800000L));
        ReflectionTestUtils.setField(authService, "auditLogService", auditLogService);
        ReflectionTestUtils.setField(authService, "runtimeAuthSecuritySettings", new RuntimeAuthSecuritySettings(false));
    }

    @Test
    void shouldRequirePasswordChangeWhenFeatureEnabledAndLatestAuditIsReset() {
        ReflectionTestUtils.setField(authService, "runtimeAuthSecuritySettings", new RuntimeAuthSecuritySettings(true));
        SysUser persistedUser = buildUser(9L, "reset-user", "APPLICANT");
        SysUser loginUser = new SysUser();
        loginUser.setUsername("reset-user");
        loginUser.setPassword("Temp1234");

        when(sysUserMapper.selectOne(any())).thenReturn(persistedUser);
        when(passwordEncoder.matches("Temp1234", "encoded-password")).thenReturn(true);
        when(auditLogService.getOne(any(), org.mockito.ArgumentMatchers.eq(false))).thenReturn(buildAuditLog("RESET_PASSWORD"));

        Result<Map<String, Object>> result = authService.login(loginUser);

        assertEquals(200, result.getCode());
        assertTrue((Boolean) result.getData().get("forceChangePassword"));
        assertEquals("reset-user", result.getData().get("username"));
        assertNotNull(result.getData().get("token"));
    }

    @Test
    void shouldNotRequirePasswordChangeWhenLatestAuditIsChangePassword() {
        ReflectionTestUtils.setField(authService, "runtimeAuthSecuritySettings", new RuntimeAuthSecuritySettings(true));
        SysUser persistedUser = buildUser(10L, "changed-user", "MERCHANT");
        SysUser loginUser = new SysUser();
        loginUser.setUsername("changed-user");
        loginUser.setPassword("Temp1234");

        when(sysUserMapper.selectOne(any())).thenReturn(persistedUser);
        when(passwordEncoder.matches("Temp1234", "encoded-password")).thenReturn(true);
        when(auditLogService.getOne(any(), org.mockito.ArgumentMatchers.eq(false))).thenReturn(buildAuditLog("CHANGE_PASSWORD"));

        Result<Map<String, Object>> result = authService.login(loginUser);

        assertEquals(200, result.getCode());
        assertFalse((Boolean) result.getData().get("forceChangePassword"));
    }

    @Test
    void shouldNotRequirePasswordChangeWhenFeatureDisabled() {
        ReflectionTestUtils.setField(authService, "runtimeAuthSecuritySettings", new RuntimeAuthSecuritySettings(false));
        SysUser persistedUser = buildUser(11L, "demo-user", "ADMIN");
        SysUser loginUser = new SysUser();
        loginUser.setUsername("demo-user");
        loginUser.setPassword("Temp1234");

        when(sysUserMapper.selectOne(any())).thenReturn(persistedUser);
        when(passwordEncoder.matches("Temp1234", "encoded-password")).thenReturn(true);

        Result<Map<String, Object>> result = authService.login(loginUser);

        assertEquals(200, result.getCode());
        assertFalse((Boolean) result.getData().get("forceChangePassword"));
    }

    @Test
    void shouldRejectAdminSelfRegistration() {
        SysUser registerUser = new SysUser();
        registerUser.setUsername("admin-hacker");
        registerUser.setPassword("12345");
        registerUser.setRole("ADMIN");

        when(sysUserMapper.selectCount(any())).thenReturn(0L);

        Result<SysUser> result = authService.register(registerUser);

        assertEquals(400, result.getCode());
        assertEquals("注册仅支持求职者或商家角色", result.getMsg());
        verify(passwordEncoder, never()).encode(any());
        verify(sysUserMapper, never()).insert(any());
    }

    @Test
    void shouldNormalizeLegacyStudentRoleWhenRegistering() {
        SysUser registerUser = new SysUser();
        registerUser.setUsername("student-user");
        registerUser.setPassword("12345");
        registerUser.setRole("STUDENT");

        when(sysUserMapper.selectCount(any())).thenReturn(0L);
        when(passwordEncoder.encode(eq("12345"))).thenReturn("encoded-password");

        Result<SysUser> result = authService.register(registerUser);

        assertEquals(200, result.getCode());
        assertEquals("APPLICANT", result.getData().getRole());
        verify(sysUserMapper).insert(any(SysUser.class));
    }

    private SysUser buildUser(Long id, String username, String role) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setRole(role);
        user.setPassword("encoded-password");
        user.setStatus(1);
        user.setBanStatus(0);
        return user;
    }

    private AuditLog buildAuditLog(String action) {
        AuditLog log = new AuditLog();
        log.setAction(action);
        log.setCreateTime(LocalDateTime.of(2026, 3, 4, 10, 0, 0));
        return log;
    }
}
