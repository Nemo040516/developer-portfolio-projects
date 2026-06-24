/**
 * 文件速览：
 * 1. 文件职责：覆盖举报提交与证据上传接口的登录态、归属校验、结构化副作用与边界场景。
 * 2. 关键升级：本轮新增“商家举报求职者必须绑定本人职位”的越权测试。
 * 3. 关键入口：/report/submit、/report/evidence。
 * 4. 阅读建议：先看 USER 举报的归属校验，再看证据上传与结构化落库断言。
 */
package com.example.backend.api;

import com.example.backend.entity.JobInfo;
import com.example.backend.entity.ReportEvidence;
import com.example.backend.entity.ReportInfo;
import com.example.backend.entity.SysUser;
import com.example.backend.service.AuditLogService;
import com.example.backend.service.ChatService;
import com.example.backend.service.JobDeliveryService;
import com.example.backend.service.JobInfoService;
import com.example.backend.service.MerchantInfoService;
import com.example.backend.service.ReportEvidenceService;
import com.example.backend.service.ReportInfoService;
import com.example.backend.service.SysUserService;
import com.example.backend.support.ApiTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReportFlowApiTest extends ApiTestBase {

    @MockBean
    private ReportInfoService reportInfoService;

    @MockBean
    private ReportEvidenceService reportEvidenceService;

    @MockBean
    private SysUserService sysUserService;

    @MockBean
    private JobInfoService jobInfoService;

    @MockBean
    private MerchantInfoService merchantInfoService;

    @MockBean
    private JobDeliveryService jobDeliveryService;

    @MockBean
    private AuditLogService auditLogService;

    @MockBean
    private ChatService chatService;

    @AfterEach
    void cleanupUploads() throws Exception {
        deleteDirectory(Paths.get(System.getProperty("user.dir"), "uploads", "reports"));
    }

    @Test
    void shouldRequireLoginWhenSubmittingReport() throws Exception {
        mockMvc.perform(post("/report/submit")
                        .contentType(json())
                        .content("""
                                {
                                  "type": "JOB",
                                  "targetId": 10,
                                  "reason": "违规信息"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void shouldRejectApplicantReportingUserAccount() throws Exception {
        mockMvc.perform(post("/report/submit")
                        .with(authorizedAs(1L, "APPLICANT"))
                        .contentType(json())
                        .content("""
                                {
                                  "type": "USER",
                                  "targetId": 2,
                                  "reason": "沟通不当",
                                  "evidenceList": ["/uploads/reports/1/evidence.png"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("仅商家可举报求职者账号"));
    }

    @Test
    void shouldRequireEvidenceWhenMerchantReportsUser() throws Exception {
        mockMvc.perform(post("/report/submit")
                        .with(authorizedAs(1L, "MERCHANT"))
                        .contentType(json())
                        .content("""
                                {
                                  "type": "USER",
                                  "targetId": 2,
                                  "reason": "存在违规行为"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("举报求职者账号时请至少上传1份证据截图"));
    }

    @Test
    void shouldRejectMerchantReportingUserWithoutRelatedJob() throws Exception {
        SysUser targetUser = new SysUser();
        targetUser.setId(2L);
        targetUser.setRole("APPLICANT");

        when(sysUserService.getById(2L)).thenReturn(targetUser);

        mockMvc.perform(post("/report/submit")
                        .with(authorizedAs(1L, "MERCHANT"))
                        .contentType(json())
                        .content("""
                                {
                                  "type": "USER",
                                  "targetId": 2,
                                  "reason": "存在违规行为",
                                  "jobId": null,
                                  "evidenceList": ["/uploads/reports/1/evidence.png"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("举报求职者账号时必须关联职位"));

        verify(reportInfoService, never()).save(any(ReportInfo.class));
    }

    @Test
    void shouldReturnNotFoundWhenReportedJobMissing() throws Exception {
        when(jobInfoService.getById(100L)).thenReturn(null);

        mockMvc.perform(post("/report/submit")
                        .with(authorizedAs(1L, "APPLICANT"))
                        .contentType(json())
                        .content("""
                                {
                                  "type": "JOB",
                                  "targetId": 100,
                                  "reason": "岗位信息虚假"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.msg").value("被举报职位不存在"));
    }

    @Test
    void shouldSubmitReportSuccessfullyForMerchant() throws Exception {
        SysUser targetUser = new SysUser();
        targetUser.setId(2L);
        targetUser.setRole("APPLICANT");
        targetUser.setUsername("app2");

        SysUser reporterUser = new SysUser();
        reporterUser.setId(1L);
        reporterUser.setUsername("boss1");
        reporterUser.setNickname("招聘方");

        SysUser adminUser = new SysUser();
        adminUser.setId(99L);
        adminUser.setRole("ADMIN");

        JobInfo relatedJob = new JobInfo();
        relatedJob.setId(100L);
        relatedJob.setMerchantId(1L);

        when(sysUserService.getById(2L)).thenReturn(targetUser);
        when(sysUserService.getById(1L)).thenReturn(reporterUser);
        when(jobInfoService.getById(100L)).thenReturn(relatedJob);
        when(jobDeliveryService.count(any())).thenReturn(1L);
        when(sysUserService.list(org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>>any()))
                .thenReturn(List.of(adminUser));
        doAnswer(invocation -> {
            ReportInfo report = invocation.getArgument(0);
            report.setId(88L);
            return true;
        }).when(reportInfoService).save(any(ReportInfo.class));

        mockMvc.perform(post("/report/submit")
                        .with(authorizedAs(1L, "MERCHANT"))
                        .contentType(json())
                        .content("""
                                {
                                  "type": "USER",
                                  "targetId": 2,
                                  "jobId": 100,
                                  "reason": "存在违规行为",
                                  "evidenceList": ["/uploads/reports/1/evidence.png"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("success"));

        verify(reportInfoService).save(any(ReportInfo.class));
        verify(reportEvidenceService).saveBatch(any());
        verify(auditLogService).record(anyString(), anyString(), anyLong(), anyString());
        verify(chatService).sendMessageWithPush(anyLong(), anyLong(), anyString());
    }

    @Test
    void shouldNormalizeEvidenceListAndPersistStructuredSideEffectsForMerchantReport() throws Exception {
        SysUser targetUser = new SysUser();
        targetUser.setId(2L);
        targetUser.setRole("APPLICANT");
        targetUser.setUsername("app2");
        targetUser.setNickname("候选人甲");

        SysUser reporterUser = new SysUser();
        reporterUser.setId(1L);
        reporterUser.setUsername("boss1");
        reporterUser.setNickname("招聘方");

        SysUser adminUser = new SysUser();
        adminUser.setId(99L);
        adminUser.setRole("ADMIN");

        JobInfo relatedJob = new JobInfo();
        relatedJob.setId(100L);
        relatedJob.setMerchantId(1L);

        when(sysUserService.getById(2L)).thenReturn(targetUser);
        when(sysUserService.getById(1L)).thenReturn(reporterUser);
        when(jobInfoService.getById(100L)).thenReturn(relatedJob);
        when(jobDeliveryService.count(any())).thenReturn(1L);
        when(sysUserService.list(org.mockito.ArgumentMatchers.<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>>any()))
                .thenReturn(List.of(adminUser));
        doAnswer(invocation -> {
            ReportInfo report = invocation.getArgument(0);
            report.setId(88L);
            return true;
        }).when(reportInfoService).save(any(ReportInfo.class));

        mockMvc.perform(post("/report/submit")
                        .with(authorizedAs(1L, "MERCHANT"))
                        .contentType(json())
                        .content("""
                                {
                                  "type": "USER",
                                  "targetId": 2,
                                  "jobId": 100,
                                  "reason": "存在违规行为",
                                  "evidenceList": [
                                    "   ",
                                    "/uploads/reports/1/proof-1.png",
                                    "/uploads/reports/1/proof-2.pdf",
                                    "",
                                    "/uploads/reports/1/proof-3.jpeg",
                                    "/uploads/reports/1/proof-4.doc",
                                    "/uploads/reports/1/proof-5.png",
                                    "/uploads/reports/1/proof-6.png"
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<ReportInfo> reportCaptor = ArgumentCaptor.forClass(ReportInfo.class);
        verify(reportInfoService).save(reportCaptor.capture());
        ReportInfo savedReport = reportCaptor.getValue();
        assertNotNull(savedReport);
        assertEquals("/uploads/reports/1/proof-1.png,/uploads/reports/1/proof-2.pdf,/uploads/reports/1/proof-3.jpeg,/uploads/reports/1/proof-4.doc,/uploads/reports/1/proof-5.png",
                savedReport.getEvidence());
        assertNotNull(savedReport.getTargetSnapshot());

        var snapshotJson = objectMapper.readTree(savedReport.getTargetSnapshot());
        assertEquals("USER", snapshotJson.path("type").asText());
        assertEquals(2L, snapshotJson.path("targetId").asLong());
        assertEquals("app2", snapshotJson.path("username").asText());
        assertEquals("APPLICANT", snapshotJson.path("role").asText());

        ArgumentCaptor<Collection<ReportEvidence>> evidenceCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(reportEvidenceService).saveBatch(evidenceCaptor.capture());
        List<ReportEvidence> evidenceRows = evidenceCaptor.getValue().stream().toList();
        assertEquals(5, evidenceRows.size());
        assertEquals("IMAGE", evidenceRows.get(0).getFileType());
        assertEquals("PDF", evidenceRows.get(1).getFileType());
        assertEquals("IMAGE", evidenceRows.get(2).getFileType());
        assertEquals("FILE", evidenceRows.get(3).getFileType());
        assertEquals("IMAGE", evidenceRows.get(4).getFileType());
        for (int index = 0; index < evidenceRows.size(); index++) {
            ReportEvidence evidence = evidenceRows.get(index);
            assertEquals(88L, evidence.getReportId());
            assertEquals(1L, evidence.getUploaderId());
            assertEquals(index + 1, evidence.getSortOrder());
            assertNotNull(evidence.getCreateTime());
        }

        verify(auditLogService).record(eq("REPORT"), eq("SUBMIT"), eq(88L),
                argThat(detail -> detail != null && detail.contains("type=USER") && detail.contains("targetId=2")));
        verify(chatService).sendMessageWithPush(eq(99L), eq(2L),
                argThat(content -> content != null && content.contains("收到举报") && content.contains("招聘方")));
    }

    @Test
    void shouldRejectMerchantReportWhenJobNotOwnedByReporter() throws Exception {
        SysUser targetUser = new SysUser();
        targetUser.setId(2L);
        targetUser.setRole("APPLICANT");

        JobInfo otherMerchantJob = new JobInfo();
        otherMerchantJob.setId(100L);
        otherMerchantJob.setMerchantId(9L);

        when(sysUserService.getById(2L)).thenReturn(targetUser);
        when(jobInfoService.getById(100L)).thenReturn(otherMerchantJob);

        mockMvc.perform(post("/report/submit")
                        .with(authorizedAs(1L, "MERCHANT"))
                        .contentType(json())
                        .content("""
                                {
                                  "type": "USER",
                                  "targetId": 2,
                                  "jobId": 100,
                                  "reason": "存在违规行为",
                                  "evidenceList": ["/uploads/reports/1/evidence.png"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("仅可举报自己职位的候选人"));

        verify(reportInfoService, never()).save(any(ReportInfo.class));
        verify(reportEvidenceService, never()).saveBatch(any());
    }

    @Test
    void shouldRejectMerchantReportWhenApplicantHasNoDeliveryForSpecifiedJob() throws Exception {
        SysUser targetUser = new SysUser();
        targetUser.setId(2L);
        targetUser.setRole("APPLICANT");

        JobInfo jobInfo = new JobInfo();
        jobInfo.setId(100L);
        jobInfo.setMerchantId(1L);

        when(sysUserService.getById(2L)).thenReturn(targetUser);
        when(jobInfoService.getById(100L)).thenReturn(jobInfo);
        when(jobDeliveryService.count(any())).thenReturn(0L);

        mockMvc.perform(post("/report/submit")
                        .with(authorizedAs(1L, "MERCHANT"))
                        .contentType(json())
                        .content("""
                                {
                                  "type": "USER",
                                  "targetId": 2,
                                  "jobId": 100,
                                  "reason": "存在违规行为",
                                  "evidenceList": ["/uploads/reports/1/evidence.png"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("该候选人与该职位无投递关系"));

        verify(reportInfoService, never()).save(any(ReportInfo.class));
        verify(reportEvidenceService, never()).saveBatch(any());
        verify(auditLogService, never()).record(anyString(), anyString(), anyLong(), anyString());
        verify(chatService, never()).sendMessageWithPush(anyLong(), anyLong(), anyString());
    }

    @Test
    void shouldRejectOversizeEvidenceUpload() throws Exception {
        MockMultipartFile oversizeFile = new MockMultipartFile(
                "file",
                "proof.pdf",
                "application/pdf",
                new byte[5 * 1024 * 1024 + 1]
        );

        mockMvc.perform(multipart("/report/evidence")
                        .file(oversizeFile)
                        .with(authorizedAs(1L, "MERCHANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("证据文件不能超过5MB"));
    }

    @Test
    void shouldRejectEmptyEvidenceUpload() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "", "application/octet-stream", new byte[0]);

        mockMvc.perform(multipart("/report/evidence")
                        .file(emptyFile)
                        .with(authorizedAs(1L, "MERCHANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("文件不能为空"));
    }

    @Test
    void shouldRejectInvalidEvidenceFormat() throws Exception {
        MockMultipartFile invalidFile = new MockMultipartFile("file", "bad.txt", "text/plain", "bad".getBytes());

        mockMvc.perform(multipart("/report/evidence")
                        .file(invalidFile)
                        .with(authorizedAs(1L, "MERCHANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("仅支持 JPG/PNG/PDF 格式"));
    }

    @Test
    void shouldUploadEvidenceSuccessfully() throws Exception {
        MockMultipartFile image = new MockMultipartFile("file", "proof.png", "image/png", new byte[]{1, 2, 3});

        var result = mockMvc.perform(multipart("/report/evidence")
                        .file(image)
                        .with(authorizedAs(1L, "MERCHANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isString())
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.containsString("/uploads/reports/1/")))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        String fileUrl = objectMapper.readTree(responseBody).path("data").asText();
        Path uploadedFile = Paths.get(System.getProperty("user.dir"), fileUrl.replaceFirst("^/", "").replace("/", java.io.File.separator));
        assertTrue(Files.exists(uploadedFile));
        assertFalse(Files.isDirectory(uploadedFile));
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
