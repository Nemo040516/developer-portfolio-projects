/*
 * 文件速览：
 * 1. 文件职责：覆盖 /uploads/** 受控访问链路，锁住公开 Logo、简历、举报证据与资质材料的权限边界。
 * 2. 对外入口：GET /uploads/**。
 * 3. 关键结构：匿名访问、角色直连访问、query token 预览兼容、商家附件授权判断。
 * 4. 阅读建议：先看公开 Logo 与匿名拦截，再看简历授权和 query token 用例。
 */
package com.example.backend.api;

import com.example.backend.entity.ResumeAttachmentPermission;
import com.example.backend.service.ApplicantInfoService;
import com.example.backend.service.JobDeliveryService;
import com.example.backend.service.MerchantInfoService;
import com.example.backend.service.ResumeAttachmentPermissionService;
import com.example.backend.support.ApiTestBase;
import com.example.backend.utils.JwtUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UploadAccessApiTest extends ApiTestBase {

    @Autowired
    private JwtUtils jwtUtils;

    @MockBean
    private ApplicantInfoService applicantInfoService;

    @MockBean
    private MerchantInfoService merchantInfoService;

    @MockBean
    private JobDeliveryService jobDeliveryService;

    @MockBean
    private ResumeAttachmentPermissionService permissionService;

    @AfterEach
    void cleanupUploads() throws Exception {
        deleteDirectory(Paths.get(System.getProperty("user.dir"), "uploads", "logos"));
        deleteDirectory(Paths.get(System.getProperty("user.dir"), "uploads", "resumes"));
        deleteDirectory(Paths.get(System.getProperty("user.dir"), "uploads", "reports"));
        deleteDirectory(Paths.get(System.getProperty("user.dir"), "uploads", "qualifications"));
    }

    @Test
    void shouldAllowPublicLogoWithoutLogin() throws Exception {
        Path file = createFile(Paths.get(System.getProperty("user.dir"), "uploads", "logos", "company.png"), "logo-bytes");

        mockMvc.perform(get("/uploads/logos/company.png"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("inline")))
                .andExpect(content().bytes(Files.readAllBytes(file)));
    }

    @Test
    void shouldRejectResumeWhenAnonymous() throws Exception {
        createFile(Paths.get(System.getProperty("user.dir"), "uploads", "resumes", "9", "resume.pdf"), "resume");

        mockMvc.perform(get("/uploads/resumes/9/resume.pdf"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.msg").value("请先登录后再访问该文件"));
    }

    @Test
    void shouldAllowApplicantReadingOwnResume() throws Exception {
        Path file = createFile(Paths.get(System.getProperty("user.dir"), "uploads", "resumes", "9", "resume.pdf"), "resume");

        mockMvc.perform(get("/uploads/resumes/9/resume.pdf")
                        .with(authorizedAs(9L, "APPLICANT")))
                .andExpect(status().isOk())
                .andExpect(content().bytes(Files.readAllBytes(file)));
    }

    @Test
    void shouldRejectMerchantReadingResumeWithoutGrantedPermission() throws Exception {
        createFile(Paths.get(System.getProperty("user.dir"), "uploads", "resumes", "9", "resume.pdf"), "resume");
        when(jobDeliveryService.hasDeliveryRelation(2L, 9L)).thenReturn(true);
        when(permissionService.getPermission(9L, 2L)).thenReturn(null);

        mockMvc.perform(get("/uploads/resumes/9/resume.pdf")
                        .with(authorizedAs(2L, "MERCHANT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void shouldAllowMerchantReadingResumeWithActivePermission() throws Exception {
        Path file = createFile(Paths.get(System.getProperty("user.dir"), "uploads", "resumes", "9", "resume.pdf"), "resume");
        ResumeAttachmentPermission permission = new ResumeAttachmentPermission();
        permission.setStatus(1);
        permission.setExpireTime(LocalDateTime.now().plusHours(4));
        when(jobDeliveryService.hasDeliveryRelation(2L, 9L)).thenReturn(true);
        when(permissionService.getPermission(9L, 2L)).thenReturn(permission);

        mockMvc.perform(get("/uploads/resumes/9/resume.pdf")
                        .with(authorizedAs(2L, "MERCHANT")))
                .andExpect(status().isOk())
                .andExpect(content().bytes(Files.readAllBytes(file)));
    }

    @Test
    void shouldAllowReportOwnerReadingByQueryToken() throws Exception {
        Path file = createFile(Paths.get(System.getProperty("user.dir"), "uploads", "reports", "7", "evidence.png"), "report");
        String token = jwtUtils.generateToken(7L, "APPLICANT");

        mockMvc.perform(get("/uploads/reports/7/evidence.png").param("token", token))
                .andExpect(status().isOk())
                .andExpect(content().bytes(Files.readAllBytes(file)));
    }

    @Test
    void shouldAllowMerchantReadingOwnQualification() throws Exception {
        Path file = createFile(Paths.get(System.getProperty("user.dir"), "uploads", "qualifications", "2_1711111111111_license.pdf"), "qualification");

        mockMvc.perform(get("/uploads/qualifications/2_1711111111111_license.pdf")
                        .with(authorizedAs(2L, "MERCHANT")))
                .andExpect(status().isOk())
                .andExpect(content().bytes(Files.readAllBytes(file)));
    }

    private Path createFile(Path path, String content) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
        return path;
    }

    private void deleteDirectory(Path path) throws Exception {
        if (!Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            stream.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(item -> {
                        try {
                            Files.deleteIfExists(item);
                        } catch (Exception ignored) {
                        }
                    });
        }
    }
}
