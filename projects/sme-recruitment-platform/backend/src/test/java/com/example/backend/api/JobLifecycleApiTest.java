package com.example.backend.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.backend.entity.JobCategory;
import com.example.backend.entity.JobInfo;
import com.example.backend.entity.MerchantInfo;
import com.example.backend.service.JobCategoryService;
import com.example.backend.service.JobInfoService;
import com.example.backend.service.MerchantInfoService;
import com.example.backend.support.ApiTestBase;
import com.example.backend.vo.JobDetailVO;
import com.example.backend.vo.JobInfoVO;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 职位与分类接口测试。
 * Task 2 先覆盖最容易回归的权限判断和公开查询链路。
 */
class JobLifecycleApiTest extends ApiTestBase {

    @MockBean
    private JobInfoService jobInfoService;

    @MockBean
    private MerchantInfoService merchantInfoService;

    @MockBean
    private JobCategoryService jobCategoryService;

    @Test
    void shouldReturnCategoryTree() throws Exception {
        JobCategory category = new JobCategory();
        category.setId(10L);
        category.setCategoryName("技术");

        when(jobCategoryService.getAllCategories()).thenReturn(List.of(category));

        mockMvc.perform(get("/category/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].id").value(10))
                .andExpect(jsonPath("$.data[0].categoryName").value("技术"));
    }

    @Test
    void shouldReturnCategoryFlatList() throws Exception {
        JobCategory category = new JobCategory();
        category.setId(11L);
        category.setCategoryName("运营");

        when(jobCategoryService.list(any(QueryWrapper.class))).thenReturn(List.of(category));

        mockMvc.perform(get("/category/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].categoryName").value("运营"));
    }

    @Test
    void shouldRequireLoginWhenAddingJob() throws Exception {
        mockMvc.perform(post("/jobs")
                        .contentType(json())
                        .content(validJobPayload()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void shouldRejectApplicantWhenAddingJob() throws Exception {
        mockMvc.perform(post("/jobs")
                        .with(authorizedAs(1L, "APPLICANT"))
                        .contentType(json())
                        .content(validJobPayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("仅商家可发布职位"));

        verify(jobInfoService, never()).createJob(any(), any());
    }

    @Test
    void shouldCreateJobForApprovedMerchant() throws Exception {
        MerchantInfo merchantInfo = new MerchantInfo();
        merchantInfo.setUserId(1L);
        merchantInfo.setAuditStatus(1);
        merchantInfo.setPublishStatus(1);

        when(merchantInfoService.getByUserId(1L)).thenReturn(merchantInfo);

        mockMvc.perform(post("/jobs")
                        .with(authorizedAs(1L, "MERCHANT"))
                        .contentType(json())
                        .content(validJobPayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(jobInfoService).createJob(any(), eq(1L));
    }

    @Test
    void shouldRejectMerchantWhenUpdatingOthersJobStatus() throws Exception {
        JobInfo jobInfo = new JobInfo();
        jobInfo.setId(101L);
        jobInfo.setMerchantId(99L);

        when(jobInfoService.getById(101L)).thenReturn(jobInfo);

        mockMvc.perform(put("/jobs/status/101/1")
                        .with(authorizedAs(1L, "MERCHANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("仅可操作自己的职位"));
    }

    @Test
    void shouldReturnMerchantListOnlyForMerchant() throws Exception {
        mockMvc.perform(get("/jobs/merchant")
                        .with(authorizedAs(2L, "APPLICANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("仅商家可查看职位列表"));
    }

    @Test
    void shouldReturnPublicJobList() throws Exception {
        JobInfoVO job = new JobInfoVO();
        job.setId(201L);
        job.setTitle("Java开发工程师");

        Page<JobInfoVO> page = new Page<>(1, 10);
        page.setRecords(List.of(job));

        when(jobInfoService.getPublicJobList(any(Page.class), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(page);

        mockMvc.perform(get("/jobs/search").param("keyword", "Java"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].title").value("Java开发工程师"));
    }

    @Test
    void shouldReturnPublicJobDetail() throws Exception {
        JobDetailVO detail = new JobDetailVO();
        detail.setId(301L);
        detail.setTitle("产品经理");

        when(jobInfoService.getPublicJobDetail(301L)).thenReturn(detail);

        mockMvc.perform(get("/jobs/public/301"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.title").value("产品经理"));
    }

    private String validJobPayload() {
        return """
                {
                  "title": "Java开发工程师",
                  "categoryId": 1,
                  "minSalary": 8,
                  "maxSalary": 15,
                  "headcount": 2,
                  "workLocation": "杭州",
                  "description": "负责后端开发",
                  "requirement": "熟悉 Spring Boot",
                  "experience": "1-3年",
                  "degree": "本科"
                }
                """;
    }
}
