/*
 * 文件速览：
 * 1. 文件职责：覆盖管理员重置密码接口的权限与主成功分支。
 * 2. 对外入口：/admin/users/{id}/password/reset。
 * 3. 关键结构：非管理员拦截、确认密码不一致、管理员成功提交。
 * 4. 阅读建议：按“失败分支 -> 成功分支”顺序查看。
 */
package com.example.backend.api;

import com.example.backend.service.AdminService;
import com.example.backend.support.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminUserPasswordResetApiTest extends ApiTestBase {

    @MockBean
    private AdminService adminService;

    @Test
    void shouldRejectNonAdminWhenResettingPassword() throws Exception {
        mockMvc.perform(put("/admin/users/8/password/reset")
                        .with(authorizedAs(2L, "MERCHANT"))
                        .contentType(json())
                        .content("""
                                {
                                  "newPassword": "Temp1234",
                                  "confirmPassword": "Temp1234",
                                  "reason": "线下核验"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("仅管理员可操作"));

        verify(adminService, never()).resetUserPassword(eq(8L), eq("Temp1234"), eq("线下核验"));
    }

    @Test
    void shouldRejectResetWhenPasswordsDoNotMatch() throws Exception {
        mockMvc.perform(put("/admin/users/8/password/reset")
                        .with(authorizedAs(1L, "ADMIN"))
                        .contentType(json())
                        .content("""
                                {
                                  "newPassword": "Temp1234",
                                  "confirmPassword": "Temp5678",
                                  "reason": "用户忘记密码"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("两次输入的密码不一致"));

        verify(adminService, never()).resetUserPassword(eq(8L), eq("Temp1234"), eq("用户忘记密码"));
    }

    @Test
    void shouldResetUserPasswordSuccessfullyForAdmin() throws Exception {
        mockMvc.perform(put("/admin/users/8/password/reset")
                        .with(authorizedAs(1L, "ADMIN"))
                        .contentType(json())
                        .content("""
                                {
                                  "newPassword": "Temp1234",
                                  "confirmPassword": "Temp1234",
                                  "reason": "用户忘记密码，已电话核验"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("密码已重置"));

        verify(adminService).resetUserPassword(8L, "Temp1234", "用户忘记密码，已电话核验");
    }
}
