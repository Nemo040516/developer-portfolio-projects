/*
 * 文件速览：
 * 1. 文件职责：覆盖认证接口的注册、登录、注销与登录扩展字段返回分支。
 * 2. 对外入口：/auth/register、/auth/login、/auth/logout。
 * 3. 关键结构：注册成功、登录日志保存、forceChangePassword 字段透传、注销成功。
 * 4. 阅读建议：先看登录成功分支，再看 forceChangePassword 返回与异常兜底分支。
 */
package com.example.backend.api;

import com.example.backend.common.Result;
import com.example.backend.entity.SysUser;
import com.example.backend.entity.UserLoginLog;
import com.example.backend.service.AuthService;
import com.example.backend.service.UserLoginLogService;
import com.example.backend.support.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 认证接口测试。
 * Task 2 先锁住认证主入口和登录日志主分支，避免权限链路回归时难以及时发现。
 */
class AuthApiTest extends ApiTestBase {

    @MockBean
    private AuthService authService;

    @MockBean
    private UserLoginLogService userLoginLogService;

    @Test
    void shouldRegisterSuccessfully() throws Exception {
        SysUser savedUser = new SysUser();
        savedUser.setId(1L);
        savedUser.setUsername("app-new");
        savedUser.setRole("APPLICANT");

        when(authService.register(any(SysUser.class))).thenReturn(Result.success(savedUser));

        mockMvc.perform(post("/auth/register")
                        .contentType(json())
                        .content("""
                                {
                                  "username": "app-new",
                                  "password": "12345",
                                  "role": "APPLICANT"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("app-new"))
                .andExpect(jsonPath("$.data.role").value("APPLICANT"));

        verify(authService).register(any(SysUser.class));
    }

    @Test
    void shouldLoginSuccessfullyAndSaveLoginLog() throws Exception {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", "jwt-token");
        data.put("role", "APPLICANT");
        data.put("username", "app1");
        data.put("userId", 1L);

        when(authService.login(any(SysUser.class))).thenReturn(Result.success(data));
        when(userLoginLogService.count(any())).thenReturn(0L);

        mockMvc.perform(post("/auth/login")
                        .contentType(json())
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0)")
                        .content("""
                                {
                                  "username": "app1",
                                  "password": "12345"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").value("jwt-token"))
                .andExpect(jsonPath("$.data.role").value("APPLICANT"));

        verify(userLoginLogService).save(any(UserLoginLog.class));
    }

    @Test
    void shouldIgnoreLoginLogSaveFailure() throws Exception {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", "jwt-token");
        data.put("role", "MERCHANT");
        data.put("username", "boss1");
        data.put("userId", 2L);

        when(authService.login(any(SysUser.class))).thenReturn(Result.success(data));
        when(userLoginLogService.count(any())).thenReturn(0L);
        doThrow(new RuntimeException("save failed")).when(userLoginLogService).save(any(UserLoginLog.class));

        mockMvc.perform(post("/auth/login")
                        .contentType(json())
                        .header("User-Agent", "Mozilla/5.0 (Mac OS X)")
                        .content("""
                                {
                                  "username": "boss1",
                                  "password": "12345"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("boss1"));

        verify(authService).login(any(SysUser.class));
    }

    @Test
    void shouldExposeForceChangePasswordFlagOnLoginResponse() throws Exception {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", "jwt-token");
        data.put("role", "APPLICANT");
        data.put("username", "app-force");
        data.put("userId", 8L);
        data.put("forceChangePassword", true);

        when(authService.login(any(SysUser.class))).thenReturn(Result.success(data));
        when(userLoginLogService.count(any())).thenReturn(0L);

        mockMvc.perform(post("/auth/login")
                        .contentType(json())
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0)")
                        .content("""
                                {
                                  "username": "app-force",
                                  "password": "Temp1234"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.forceChangePassword").value(true))
                .andExpect(jsonPath("$.data.username").value("app-force"));
    }

    @Test
    void shouldNotSaveDuplicatedLoginLog() throws Exception {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", "jwt-token");
        data.put("role", "ADMIN");
        data.put("username", "admin1");
        data.put("userId", 3L);

        when(authService.login(any(SysUser.class))).thenReturn(Result.success(data));
        when(userLoginLogService.count(any())).thenReturn(1L);

        mockMvc.perform(post("/auth/login")
                        .contentType(json())
                        .header("User-Agent", "Mozilla/5.0 (Linux)")
                        .content("""
                                {
                                  "username": "admin1",
                                  "password": "12345"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(userLoginLogService, never()).save(any(UserLoginLog.class));
    }

    @Test
    void shouldLogoutSuccessfully() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("注销成功"));
    }
}
