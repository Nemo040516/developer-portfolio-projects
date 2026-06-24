package com.example.backend.api;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.backend.service.AdminService;
import com.example.backend.support.ApiTestBase;
import com.example.backend.vo.AdminUserVO;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理员账号封禁接口测试。
 * Task 3 先锁住权限校验和封禁状态基本分支。
 */
class AdminUserBanApiTest extends ApiTestBase {

    @MockBean
    private AdminService adminService;

    @Test
    void shouldRejectNonAdminWhenUpdatingUserBan() throws Exception {
        mockMvc.perform(put("/admin/users/5/ban")
                        .with(authorizedAs(2L, "MERCHANT"))
                        .contentType(json())
                        .content("""
                                {
                                  "banStatus": 2,
                                  "banReason": "违规"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("仅管理员可操作"));
    }

    @Test
    void shouldRejectInvalidBanStatus() throws Exception {
        mockMvc.perform(put("/admin/users/5/ban")
                        .with(authorizedAs(1L, "ADMIN"))
                        .contentType(json())
                        .content("""
                                {
                                  "banStatus": 9,
                                  "banReason": "非法状态"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("封禁状态不合法"));
    }

    @Test
    void shouldReturnUserListForAdmin() throws Exception {
        Page<AdminUserVO> page = new Page<>(1, 20);
        when(adminService.getUserList(any(Page.class), any(), any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/admin/users")
                        .with(authorizedAs(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void shouldRejectNonAdminWhenReadingUserList() throws Exception {
        mockMvc.perform(get("/admin/users")
                        .with(authorizedAs(2L, "MERCHANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("仅管理员可访问"));
    }

    @Test
    void shouldPassUserListFiltersAndPageFallbackToService() throws Exception {
        Page<AdminUserVO> page = new Page<>(4, 25);
        AdminUserVO user = new AdminUserVO();
        user.setId(5L);
        user.setUsername("app1");
        user.setRole("APPLICANT");
        user.setBanStatus(2);
        page.setRecords(java.util.List.of(user));
        when(adminService.getUserList(any(Page.class), any(), any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/admin/users")
                        .with(authorizedAs(1L, "ADMIN"))
                        .param("page", "4")
                        .param("size", "25")
                        .param("keyword", "app")
                        .param("role", "APPLICANT")
                        .param("status", "1")
                        .param("banStatus", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.current").value(4))
                .andExpect(jsonPath("$.data.size").value(25))
                .andExpect(jsonPath("$.data.records[0].username").value("app1"))
                .andExpect(jsonPath("$.data.records[0].banStatus").value(2));

        verify(adminService).getUserList(
                argThat(pageArg -> pageArg.getCurrent() == 4 && pageArg.getSize() == 25),
                eq("app"),
                eq("APPLICANT"),
                eq(1),
                eq(2)
        );
    }

    @Test
    void shouldUseDefaultPaginationWhenReadingUserListWithoutPageParams() throws Exception {
        Page<AdminUserVO> page = new Page<>(1, 20);
        when(adminService.getUserList(any(Page.class), any(), any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/admin/users")
                        .with(authorizedAs(1L, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.current").value(1))
                .andExpect(jsonPath("$.data.size").value(20));

        verify(adminService).getUserList(
                argThat(pageArg -> pageArg.getCurrent() == 1 && pageArg.getSize() == 20),
                eq(null),
                eq(null),
                eq(null),
                eq(null)
        );
    }

    @Test
    void shouldUpdateUserBanSuccessfully() throws Exception {
        mockMvc.perform(put("/admin/users/5/ban")
                        .with(authorizedAs(1L, "ADMIN"))
                        .contentType(json())
                        .content("""
                                {
                                  "banStatus": 2,
                                  "banReason": "恶意违规",
                                  "banUntil": "2026-03-31T18:00:00"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("操作成功"));

        verify(adminService).updateUserBan(eq(5L), eq(2), any(), eq("恶意违规"));
    }
}
