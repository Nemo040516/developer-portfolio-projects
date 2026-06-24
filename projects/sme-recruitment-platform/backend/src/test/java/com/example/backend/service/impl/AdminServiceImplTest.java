/*
 * 文件速览：
 * 1. 文件职责：验证管理员服务中的密码重置、安全设置读写、安全日志投影与最近重置记录投影。
 * 2. 对外入口：AdminServiceImpl#resetUserPassword、#getAuthSecuritySettings、#updateForcePasswordChangeEnabled、#getSecuritySettingLogs、#getUserList。
 * 3. 关键结构：密码加密更新、审计日志记录、运行时开关快照、安全日志映射、账号列表重置记录补充。
 * 4. 阅读建议：先看 setUp，再看密码重置、安全设置、安全日志、账号列表四个测试分组。
 */
package com.example.backend.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.backend.config.RuntimeAuthSecuritySettings;
import com.example.backend.entity.AuditLog;
import com.example.backend.entity.SysUser;
import com.example.backend.service.AuditLogService;
import com.example.backend.service.SysUserService;
import com.example.backend.vo.AdminAuthSecuritySettingsVO;
import com.example.backend.vo.AdminSecuritySettingLogVO;
import com.example.backend.vo.AdminUserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceImplTest {

    @Mock
    private SysUserService sysUserService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditLogService auditLogService;

    private AdminServiceImpl adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminServiceImpl();
        ReflectionTestUtils.setField(adminService, "sysUserService", sysUserService);
        ReflectionTestUtils.setField(adminService, "passwordEncoder", passwordEncoder);
        ReflectionTestUtils.setField(adminService, "auditLogService", auditLogService);
        ReflectionTestUtils.setField(adminService, "runtimeAuthSecuritySettings", new RuntimeAuthSecuritySettings(false));
    }

    @Test
    void shouldResetPasswordAndRecordAuditLog() {
        SysUser user = new SysUser();
        user.setId(8L);
        user.setUsername("app8");
        user.setPassword("encoded-old");

        when(sysUserService.getById(8L)).thenReturn(user);
        when(passwordEncoder.matches("Temp1234", "encoded-old")).thenReturn(false);
        when(passwordEncoder.encode("Temp1234")).thenReturn("encoded-new");
        when(sysUserService.updateById(any(SysUser.class))).thenReturn(true);

        adminService.resetUserPassword(8L, "Temp1234", "线下核验后重置");

        ArgumentCaptor<SysUser> userCaptor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserService).updateById(userCaptor.capture());
        assertEquals("encoded-new", userCaptor.getValue().getPassword());
        assertNotNull(userCaptor.getValue().getUpdateTime());
        verify(auditLogService).record("AUTH", "RESET_PASSWORD", 8L, "管理员重置密码，账号=app8，原因=线下核验后重置");
    }

    @Test
    void shouldReturnCurrentAuthSecuritySettingsSnapshot() {
        ReflectionTestUtils.setField(adminService, "runtimeAuthSecuritySettings", new RuntimeAuthSecuritySettings(true));

        AdminAuthSecuritySettingsVO result = adminService.getAuthSecuritySettings();

        assertTrue(result.getForcePasswordChangeEnabled());
        assertTrue(result.getDefaultForcePasswordChangeEnabled());
        assertEquals(false, result.getRuntimeOverrideActive());
    }

    @Test
    void shouldUpdateForcePasswordChangeSettingAndMarkRuntimeOverride() {
        AdminAuthSecuritySettingsVO result = adminService.updateForcePasswordChangeEnabled(true);

        assertTrue(result.getForcePasswordChangeEnabled());
        assertEquals(false, result.getDefaultForcePasswordChangeEnabled());
        assertTrue(result.getRuntimeOverrideActive());
        verify(auditLogService).record("AUTH", "UPDATE_FORCE_PASSWORD_CHANGE", 0L, "更新临时密码登录后强制修改密码开关为开启");
    }

    @Test
    void shouldReturnSecuritySettingLogsWithReadableOperatorName() {
        AuditLog log = new AuditLog();
        log.setId(3L);
        log.setOperatorId(1L);
        log.setOperatorRole("ADMIN");
        log.setDetail("更新临时密码登录后强制修改密码开关为关闭");
        log.setCreateTime(LocalDateTime.of(2026, 3, 4, 12, 0, 0));

        SysUser operator = new SysUser();
        operator.setId(1L);
        operator.setUsername("admin1");
        operator.setNickname("管理员甲");

        when(auditLogService.list(org.mockito.ArgumentMatchers.<Wrapper<AuditLog>>any())).thenReturn(List.of(log));
        when(sysUserService.listByIds(any())).thenReturn(List.of(operator));

        List<AdminSecuritySettingLogVO> result = adminService.getSecuritySettingLogs();

        assertEquals(1, result.size());
        assertEquals("管理员甲", result.get(0).getOperatorName());
        assertEquals("ADMIN", result.get(0).getOperatorRole());
        assertEquals(false, result.get(0).getEnabledValue());
        assertEquals("更新临时密码登录后强制修改密码开关为关闭", result.get(0).getDetail());
    }

    @Test
    void shouldAttachLatestPasswordResetInfoWhenReadingUserList() {
        SysUser applicant = new SysUser();
        applicant.setId(5L);
        applicant.setUsername("app5");
        applicant.setNickname("候选人乙");
        applicant.setRole("APPLICANT");
        applicant.setStatus(1);
        applicant.setBanStatus(0);

        SysUser merchant = new SysUser();
        merchant.setId(6L);
        merchant.setUsername("boss6");
        merchant.setRole("MERCHANT");
        merchant.setStatus(1);
        merchant.setBanStatus(0);

        Page<SysUser> sourcePage = new Page<>(1, 20);
        sourcePage.setRecords(List.of(applicant, merchant));
        sourcePage.setTotal(2);

        AuditLog resetLog = new AuditLog();
        resetLog.setTargetId(5L);
        resetLog.setOperatorId(1L);
        resetLog.setDetail("管理员重置密码，账号=app5，原因=用户忘记密码");
        resetLog.setCreateTime(LocalDateTime.of(2026, 3, 4, 11, 0, 0));

        SysUser operator = new SysUser();
        operator.setId(1L);
        operator.setUsername("admin1");
        operator.setNickname("管理员甲");

        when(sysUserService.page(any(Page.class), any())).thenReturn(sourcePage);
        when(auditLogService.list(org.mockito.ArgumentMatchers.<Wrapper<AuditLog>>any())).thenReturn(List.of(resetLog));
        when(sysUserService.listByIds(any())).thenReturn(List.of(operator));

        IPage<AdminUserVO> result = adminService.getUserList(new Page<>(1, 20), null, null, null, null);

        assertEquals(2, result.getRecords().size());
        AdminUserVO first = result.getRecords().get(0);
        assertEquals(5L, first.getId());
        assertEquals("管理员甲", first.getLatestPasswordResetOperatorName());
        assertEquals("管理员重置密码，账号=app5，原因=用户忘记密码", first.getLatestPasswordResetDetail());
        assertEquals(LocalDateTime.of(2026, 3, 4, 11, 0, 0), first.getLatestPasswordResetTime());

        AdminUserVO second = result.getRecords().get(1);
        assertEquals(6L, second.getId());
        assertNull(second.getLatestPasswordResetTime());
        assertTrue(second.getLatestPasswordResetDetail() == null || second.getLatestPasswordResetDetail().isEmpty());
    }
}
