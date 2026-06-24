package com.example.backend.api;

import com.example.backend.entity.ApplicantInfo;
import com.example.backend.entity.SysUser;
import com.example.backend.mapper.SysUserMapper;
import com.example.backend.service.ApplicantInfoService;
import com.example.backend.service.ChatService;
import com.example.backend.service.InterviewScheduleService;
import com.example.backend.service.JobDeliveryService;
import com.example.backend.service.JobInfoService;
import com.example.backend.service.JobViewLogService;
import com.example.backend.service.MerchantInfoService;
import com.example.backend.service.SeekerService;
import com.example.backend.support.ApiTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 关键文件上传接口测试。
 * Task 8 继续补齐超大文件、删除与历史路径兼容边界。
 */
class FileUploadApiTest extends ApiTestBase {

    @MockBean
    private SeekerService seekerService;

    @MockBean
    private ApplicantInfoService applicantInfoService;

    @MockBean
    private JobDeliveryService jobDeliveryService;

    @MockBean
    private SysUserMapper sysUserMapper;

    @MockBean
    private MerchantInfoService merchantInfoService;

    @MockBean
    private JobInfoService jobInfoService;

    @MockBean
    private InterviewScheduleService interviewScheduleService;

    @MockBean
    private ChatService chatService;

    @MockBean
    private JobViewLogService jobViewLogService;

    @AfterEach
    void cleanupUploads() throws Exception {
        deleteDirectory(Paths.get(System.getProperty("user.dir"), "uploads", "resumes"));
        deleteDirectory(Paths.get(System.getProperty("user.dir"), "uploads", "logos"));
        deleteDirectory(Paths.get(System.getProperty("user.dir"), "uploads", "qualifications"));
    }

    @Test
    void shouldRequireLoginWhenUploadingResumeAttachment() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", "pdf".getBytes());

        mockMvc.perform(multipart("/seeker/resume-attachment").file(file))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void shouldRejectResumeAttachmentUploadForMerchant() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", "pdf".getBytes());

        mockMvc.perform(multipart("/seeker/resume-attachment")
                        .file(file)
                        .with(authorizedAs(2L, "MERCHANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("仅求职者可上传附件简历"));
    }

    @Test
    void shouldRejectResumeAttachmentUploadForInvalidFormat() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "resume.txt", "text/plain", "bad".getBytes());

        mockMvc.perform(multipart("/seeker/resume-attachment")
                        .file(file)
                        .with(authorizedAs(1L, "APPLICANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("仅支持 PDF/DOC/DOCX 格式"));
    }

    @Test
    void shouldRejectOversizeResumeAttachmentUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.pdf",
                "application/pdf",
                new byte[10 * 1024 * 1024 + 1]
        );

        mockMvc.perform(multipart("/seeker/resume-attachment")
                        .file(file)
                        .with(authorizedAs(1L, "APPLICANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("附件简历大小不能超过10MB"));
    }

    @Test
    void shouldUploadResumeAttachmentSuccessfully() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", "resume".getBytes());
        ApplicantInfo applicantInfo = new ApplicantInfo();
        applicantInfo.setRealName("张三");
        when(applicantInfoService.getByUserId(1L)).thenReturn(applicantInfo);
        when(applicantInfoService.getResumeUrl(1L)).thenReturn(null);
        when(applicantInfoService.updateResumeUrl(org.mockito.ArgumentMatchers.eq(1L),
                argThat(value -> value != null && value.startsWith("/uploads/resumes/1/")))).thenReturn(true);

        mockMvc.perform(multipart("/seeker/resume-attachment")
                        .file(file)
                        .with(authorizedAs(1L, "APPLICANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.fileUrl").value(org.hamcrest.Matchers.containsString("/uploads/resumes/1/")))
                .andExpect(jsonPath("$.data.fileName").value("张三的简历.pdf"));

        verify(applicantInfoService).updateResumeUrl(org.mockito.ArgumentMatchers.eq(1L),
                argThat(value -> value != null && value.startsWith("/uploads/resumes/1/")));
    }

    @Test
    void shouldRejectLogoUploadForInvalidFormat() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "logo.txt", "text/plain", "bad".getBytes());

        mockMvc.perform(multipart("/merchant/logo")
                        .file(file)
                        .with(authorizedAs(2L, "MERCHANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("仅支持 JPG/PNG 图片格式"));
    }

    @Test
    void shouldRejectOversizeLogoUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "logo.png",
                "image/png",
                new byte[2 * 1024 * 1024 + 1]
        );

        mockMvc.perform(multipart("/merchant/logo")
                        .file(file)
                        .with(authorizedAs(2L, "MERCHANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("Logo 图片大小不能超过2MB"));
    }

    @Test
    void shouldUploadLogoSuccessfully() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/merchant/logo")
                        .file(file)
                        .with(authorizedAs(2L, "MERCHANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.containsString("/uploads/logos/")));
    }

    @Test
    void shouldRejectQualificationUploadWhenFileEmpty() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "", "application/octet-stream", new byte[0]);

        mockMvc.perform(multipart("/merchant/qualification")
                        .file(file)
                        .with(authorizedAs(2L, "MERCHANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("文件不能为空"));
    }

    @Test
    void shouldRejectOversizeQualificationUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "license.pdf",
                "application/pdf",
                new byte[5 * 1024 * 1024 + 1]
        );

        mockMvc.perform(multipart("/merchant/qualification")
                        .file(file)
                        .with(authorizedAs(2L, "MERCHANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("资质材料大小不能超过5MB"));
    }

    @Test
    void shouldUploadQualificationSuccessfully() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "license.pdf", "application/pdf", "pdf".getBytes());

        mockMvc.perform(multipart("/merchant/qualification")
                        .file(file)
                        .with(authorizedAs(2L, "MERCHANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.containsString("/uploads/qualifications/")));
    }

    @Test
    void shouldDeleteResumeAttachmentAndClearDatabaseField() throws Exception {
        Path resumePath = Paths.get(System.getProperty("user.dir"), "uploads", "resumes", "1", "resume.pdf");
        createFile(resumePath);
        when(applicantInfoService.getResumeUrl(1L)).thenReturn("/uploads/resumes/1/resume.pdf");

        mockMvc.perform(delete("/seeker/resume-attachment")
                        .with(authorizedAs(1L, "APPLICANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(true));

        verify(applicantInfoService).updateResumeUrl(1L, null);
        assertFalse(Files.exists(resumePath));
    }

    @Test
    void shouldDeleteLegacyResumeAttachmentPathWhenResumeUrlUsesOldLayout() throws Exception {
        Path legacyPath = Paths.get(System.getProperty("user.dir"), "uploads", "resumes", "legacy.pdf");
        createFile(legacyPath);
        when(applicantInfoService.getResumeUrl(1L)).thenReturn("http://localhost:8080/uploads/resumes/legacy.pdf");

        mockMvc.perform(delete("/seeker/resume-attachment")
                        .with(authorizedAs(1L, "APPLICANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(true));

        verify(applicantInfoService).updateResumeUrl(1L, null);
        assertFalse(Files.exists(legacyPath));
    }

    /**
     * 创建测试文件，模拟真实上传后的磁盘状态。
     */
    private void createFile(Path path) throws Exception {
        Files.createDirectories(path.getParent());
        Files.write(path, "fixture".getBytes());
        assertTrue(Files.exists(path));
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
