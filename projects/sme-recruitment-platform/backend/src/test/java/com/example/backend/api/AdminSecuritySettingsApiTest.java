/*
 * 文件速览：
 * 1. 文件职责：覆盖管理员安全设置接口及日志接口的权限校验与成功主分支。
 * 2. 对外入口：/admin/security-settings、/admin/security-settings/password-force-change、/admin/security-settings/logs。
 * 3. 关键结构：非管理员拦截、管理员查询成功、管理员更新成功、日志读取成功。
 * 4. 阅读建议：按“读取 -> 更新 -> 日志”顺序查看测试用例。
 */
package com.example.backend.api;

import com.example.backend.service.AdminService;
import com.example.backend.support.ApiTestBase;
import com.example.backend.vo.AdminAuthSecuritySettingsVO;
import com.example.backend.vo.AdminSecuritySettingLogVO;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminSecuritySettingsApiTest extends ApiTestBase {

    @MockBean
    private AdminService adminService;

    @Test
    void shouldRejectNonAdminWhenReadingSecuritySettings() throws Exception {
        mockMvc.perform(get("/admin/security-settings")
                        .with(authorizedAs(2L, "MERCHANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("仅管理员可访问"));
    }

    @Test
    void shouldReturnSecuritySettingsForAdmin() throws Exception {
        AdminAuthSecuritySettingsVO vo = new AdminAuthSecuritySettingsVO();
        vo.setForcePasswordChangeEnabled(false);
        vo.setDefaultForcePasswordChangeEnabled(false);
        vo.setRuntimeOverrideActive(false);
        when(adminService.getAuthSecuritySettings()).thenReturn(vo);

        mockMvc.perform(get("/admin/security-settings")
                        .with(authorizedAs(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.forcePasswordChangeEnabled").value(false))
                .andExpect(jsonPath("$.data.defaultForcePasswordChangeEnabled").value(false))
                .andExpect(jsonPath("$.data.runtimeOverrideActive").value(false));
    }

    @Test
    void shouldRejectNonAdminWhenUpdatingForcePasswordChangeSetting() throws Exception {
        mockMvc.perform(put("/admin/security-settings/password-force-change")
                        .with(authorizedAs(2L, "MERCHANT"))
                        .contentType(json())
                        .content("""
                                {
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("仅管理员可操作"));
    }

    @Test
    void shouldUpdateForcePasswordChangeSettingForAdmin() throws Exception {
        AdminAuthSecuritySettingsVO vo = new AdminAuthSecuritySettingsVO();
        vo.setForcePasswordChangeEnabled(true);
        vo.setDefaultForcePasswordChangeEnabled(false);
        vo.setRuntimeOverrideActive(true);
        when(adminService.updateForcePasswordChangeEnabled(true)).thenReturn(vo);

        mockMvc.perform(put("/admin/security-settings/password-force-change")
                        .with(authorizedAs(1L, "ADMIN"))
                        .contentType(json())
                        .content("""
                                {
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.forcePasswordChangeEnabled").value(true))
                .andExpect(jsonPath("$.data.runtimeOverrideActive").value(true));

        verify(adminService).updateForcePasswordChangeEnabled(eq(true));
    }

    @Test
    void shouldReturnSecuritySettingLogsForAdmin() throws Exception {
        AdminSecuritySettingLogVO log = new AdminSecuritySettingLogVO();
        log.setId(3L);
        log.setOperatorId(1L);
        log.setOperatorName("管理员甲");
        log.setOperatorRole("ADMIN");
        log.setDetail("更新临时密码登录后强制修改密码开关为开启");
        log.setEnabledValue(true);
        log.setCreateTime(LocalDateTime.of(2026, 3, 4, 12, 0, 0));
        when(adminService.getSecuritySettingLogs()).thenReturn(List.of(log));

        mockMvc.perform(get("/admin/security-settings/logs")
                        .with(authorizedAs(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].operatorName").value("管理员甲"))
                .andExpect(jsonPath("$.data[0].enabledValue").value(true))
                .andExpect(jsonPath("$.data[0].detail").value("更新临时密码登录后强制修改密码开关为开启"));
    }

    @Test
    void shouldRejectNonAdminWhenReadingSecuritySettingLogs() throws Exception {
        mockMvc.perform(get("/admin/security-settings/logs")
                        .with(authorizedAs(2L, "MERCHANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("仅管理员可访问"));
    }
}
