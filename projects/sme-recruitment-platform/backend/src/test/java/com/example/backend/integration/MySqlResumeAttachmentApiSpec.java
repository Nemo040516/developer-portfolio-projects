package com.example.backend.integration;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.backend.entity.ApplicantInfo;
import com.example.backend.mapper.ApplicantInfoMapper;
import com.example.backend.service.ApplicantInfoService;
import com.example.backend.support.MySqlApiTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.jdbc.Sql;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 独立 MySQL 口径下的附件简历上传 / 删除联测。
 * 类名不走默认 surefire 通配，避免影响默认 `mvn test`。
 */
@Sql(scripts = {"/sql/base-schema.sql", "/sql/base-seed.sql"})
class MySqlResumeAttachmentApiSpec extends MySqlApiTestBase {

    @MockBean
    private ApplicantInfoService applicantInfoService;

    @Autowired
    private ApplicantInfoMapper applicantInfoMapper;

    @BeforeEach
    void setupApplicantInfoServiceBridge() {
        lenient().when(applicantInfoService.getByUserId(anyLong()))
                .thenAnswer(invocation -> findApplicantInfo(invocation.getArgument(0)));
        lenient().when(applicantInfoService.getResumeUrl(anyLong()))
                .thenAnswer(invocation -> {
                    ApplicantInfo info = findApplicantInfo(invocation.getArgument(0));
                    return info != null ? info.getResumeUrl() : null;
                });
        lenient().when(applicantInfoService.updateResumeUrl(anyLong(), anyString()))
                .thenAnswer(invocation -> persistResumeUrl(invocation.getArgument(0), invocation.getArgument(1)));
        lenient().when(applicantInfoService.updateResumeUrl(anyLong(), eq(null)))
                .thenAnswer(invocation -> persistResumeUrl(invocation.getArgument(0), null));
    }

    @AfterEach
    void cleanupUploads() throws Exception {
        deleteDirectory(Paths.get(System.getProperty("user.dir"), "uploads", "resumes"));
    }

    @Test
    void shouldReplaceResumeAttachmentFileAndPersistUrlAgainstMySql() throws Exception {
        assertRunningOnMySqlTestDatabase();

        MockMultipartFile oldFile = new MockMultipartFile("file", "resume.pdf", "application/pdf", "old-pdf".getBytes());
        String firstUrl = uploadResumeAndReadUrl(oldFile);
        Path firstPath = resolveUploadedFile(firstUrl);
        assertEquals(firstUrl, applicantInfoService.getResumeUrl(3L));
        assertTrue(firstUrl.endsWith(".pdf"));
        assertTrue(Files.exists(firstPath));
        assertEquals("old-pdf", Files.readString(firstPath));

        MockMultipartFile newFile = new MockMultipartFile("file", "resume.pdf", "application/pdf", "new-pdf".getBytes());
        String secondUrl = uploadResumeAndReadUrl(newFile);
        Path secondPath = resolveUploadedFile(secondUrl);

        assertEquals(firstPath, secondPath);
        assertTrue(Files.exists(secondPath));
        assertEquals("new-pdf", Files.readString(secondPath));
        assertEquals(secondUrl, applicantInfoService.getResumeUrl(3L));
    }

    @Test
    void shouldDeleteResumeAttachmentFileAndClearDatabaseAgainstMySql() throws Exception {
        assertRunningOnMySqlTestDatabase();

        String fileUrl = "/uploads/resumes/3/manual-delete.pdf";
        Path filePath = Paths.get(System.getProperty("user.dir"), "uploads", "resumes", "3", "manual-delete.pdf");
        createFile(filePath, "delete-me".getBytes());
        applicantInfoService.updateResumeUrl(3L, fileUrl);

        assertTrue(Files.exists(filePath));

        mockMvc.perform(delete("/seeker/resume-attachment")
                        .with(authorizedAs(3L, "APPLICANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(true));

        assertFalse(Files.exists(filePath));
        assertNull(applicantInfoService.getResumeUrl(3L));
    }

    @Test
    void shouldDeletePreviousResumeFileWhenReplacingWithDifferentExtensionAgainstMySql() throws Exception {
        assertRunningOnMySqlTestDatabase();

        MockMultipartFile oldFile = new MockMultipartFile("file", "resume.pdf", "application/pdf", "old-pdf".getBytes());
        String firstUrl = uploadResumeAndReadUrl(oldFile);
        Path firstPath = resolveUploadedFile(firstUrl);

        MockMultipartFile newFile = new MockMultipartFile(
                "file",
                "resume.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "new-docx".getBytes()
        );
        String secondUrl = uploadResumeAndReadUrl(newFile);
        Path secondPath = resolveUploadedFile(secondUrl);

        assertTrue(firstUrl.endsWith(".pdf"));
        assertTrue(secondUrl.endsWith(".docx"));
        assertFalse(firstPath.equals(secondPath));
        assertFalse(Files.exists(firstPath));
        assertTrue(Files.exists(secondPath));
        assertEquals("new-docx", Files.readString(secondPath));
        assertEquals(secondUrl, applicantInfoService.getResumeUrl(3L));
    }

    @Test
    void shouldDeleteLegacyResumeAttachmentPathAgainstMySql() throws Exception {
        assertRunningOnMySqlTestDatabase();

        String fileUrl = "http://localhost:8080/uploads/resumes/legacy-resume.pdf";
        Path filePath = Paths.get(System.getProperty("user.dir"), "uploads", "resumes", "legacy-resume.pdf");
        createFile(filePath, "legacy".getBytes());
        applicantInfoService.updateResumeUrl(3L, fileUrl);

        assertTrue(Files.exists(filePath));

        mockMvc.perform(delete("/seeker/resume-attachment")
                        .with(authorizedAs(3L, "APPLICANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(true));

        assertFalse(Files.exists(filePath));
        assertNull(applicantInfoService.getResumeUrl(3L));
    }

    @Test
    void shouldRollbackStoredFileWhenResumeUrlPersistenceFailsAgainstMySql() throws Exception {
        assertRunningOnMySqlTestDatabase();

        MockMultipartFile oldFile = new MockMultipartFile("file", "resume.pdf", "application/pdf", "old-pdf".getBytes());
        String originalUrl = uploadResumeAndReadUrl(oldFile);
        Path originalPath = resolveUploadedFile(originalUrl);

        doThrow(new RuntimeException("persist failed"))
                .when(applicantInfoService)
                .updateResumeUrl(eq(3L), eq(originalUrl));

        MockMultipartFile replacingFile = new MockMultipartFile("file", "resume.pdf", "application/pdf", "new-pdf".getBytes());
        mockMvc.perform(multipart("/seeker/resume-attachment")
                        .file(replacingFile)
                        .with(authorizedAs(3L, "APPLICANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("上传失败"));

        assertEquals(originalUrl, applicantInfoService.getResumeUrl(3L));
        assertTrue(Files.exists(originalPath));
        assertEquals("old-pdf", Files.readString(originalPath));
    }

    /**
     * 统一执行上传并提取真实文件地址，减少重复的 MockMvc 样板。
     */
    private String uploadResumeAndReadUrl(MockMultipartFile file) throws Exception {
        var result = mockMvc.perform(multipart("/seeker/resume-attachment")
                        .file(file)
                        .with(authorizedAs(3L, "APPLICANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.fileUrl").value(org.hamcrest.Matchers.containsString("/uploads/resumes/3/")))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .path("data")
                .path("fileUrl")
                .asText();
    }

    /**
     * 将接口返回的上传地址还原成项目内真实文件路径，便于校验磁盘副作用。
     */
    private Path resolveUploadedFile(String fileUrl) {
        String relativePath = fileUrl;
        if (relativePath.startsWith("http://") || relativePath.startsWith("https://")) {
            int uploadsIndex = relativePath.indexOf("/uploads/");
            relativePath = uploadsIndex >= 0 ? relativePath.substring(uploadsIndex + 1) : relativePath;
        } else if (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }
        return Paths.get(System.getProperty("user.dir")).resolve(relativePath);
    }

    /**
     * 预置真实磁盘文件，便于删除链路联测覆盖“文件 + 数据库”双副作用。
     */
    private void createFile(Path path, byte[] content) throws Exception {
        Files.createDirectories(path.getParent());
        Files.write(path, content);
        assertTrue(Files.exists(path));
    }

    private void deleteDirectory(Path path) throws Exception {
        if (!Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            stream.sorted(Comparator.comparingInt(Path::getNameCount).reversed())
                    .forEach(item -> {
                        try {
                            Files.deleteIfExists(item);
                        } catch (Exception ignored) {
                        }
                    });
        }
    }

    /**
     * 用真实 Mapper 为 MockBean 提供最小桥接，保留真实 MySQL 落库断言能力。
     */
    private ApplicantInfo findApplicantInfo(Long userId) {
        return applicantInfoMapper.selectOne(
                Wrappers.<ApplicantInfo>lambdaQuery()
                        .eq(ApplicantInfo::getUserId, userId)
                        .last("LIMIT 1")
        );
    }

    /**
     * 模拟真实服务的 `resume_url` 写入 / 清空语义，避免测试替身把数据库行为简化掉。
     */
    private boolean persistResumeUrl(Long userId, String resumeUrl) {
        ApplicantInfo existing = findApplicantInfo(userId);
        if (existing == null) {
            if (resumeUrl == null) {
                return true;
            }
            ApplicantInfo created = new ApplicantInfo();
            created.setUserId(userId);
            created.setResumeUrl(resumeUrl);
            return applicantInfoMapper.insert(created) > 0;
        }
        return applicantInfoMapper.update(
                null,
                Wrappers.<ApplicantInfo>lambdaUpdate()
                        .eq(ApplicantInfo::getUserId, userId)
                        .set(ApplicantInfo::getResumeUrl, resumeUrl)
        ) > 0;
    }
}
