/*
 * 文件速览：
 * 1. 文件职责：承接职位发布、编辑、上下架、复审与公开查询接口。
 * 2. 对外入口：/jobs 下的 add、update、status、merchant、public、search 等接口。
 * 3. 关键结构：商家身份校验、商家发布状态校验、公开职位分页查询。
 * 4. 阅读建议：先看 add/update 的发布守卫，再看公开查询接口。
 */
package com.example.backend.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.backend.common.Result;
import com.example.backend.dto.JobPostDTO;
import com.example.backend.entity.JobInfo;
import com.example.backend.entity.MerchantInfo;
import com.example.backend.service.JobInfoService;
import com.example.backend.service.MerchantInfoService;
import com.example.backend.utils.ControllerAccessUtils;
import com.example.backend.utils.PageQueryUtils;
import com.example.backend.utils.SecurityUtils;
import com.example.backend.vo.JobDetailVO;
import com.example.backend.vo.JobInfoVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/jobs") // 1. 统一类路径为复数
public class JobController {

    private final JobInfoService jobInfoService;
    private final MerchantInfoService merchantInfoService;

    public JobController(JobInfoService jobInfoService, MerchantInfoService merchantInfoService) {
        this.jobInfoService = jobInfoService;
        this.merchantInfoService = merchantInfoService;
    }

    private Long requireMerchant(String forbiddenMessage) {
        return ControllerAccessUtils.requireMerchant("Unauthorized", forbiddenMessage);
    }

    // 校验商家是否已通过审核且允许发布
    private MerchantInfo getMerchantInfo(Long merchantId) {
        return merchantInfoService.getByUserId(merchantId);
    }

    private boolean isMerchantAllowedPublish(MerchantInfo info) {
        if (info == null) {
            return false;
        }
        if (info.getAuditStatus() == null || info.getAuditStatus() != 1) {
            return false;
        }
        Integer publishStatus = info.getPublishStatus();
        return publishStatus == null || publishStatus == 1;
    }

    private String buildMerchantPublishError(MerchantInfo info) {
        if (info == null || info.getAuditStatus() == null || info.getAuditStatus() != 1) {
            return "商家未通过审核，暂不能发布职位";
        }
        Integer publishStatus = info.getPublishStatus();
        if (publishStatus != null && publishStatus == 0) {
            return "商家已被限制发布，请联系管理员";
        }
        if (publishStatus != null && publishStatus == 2) {
            return "商家已被封禁，暂不能发布职位";
        }
        return "商家状态异常，暂不能发布职位";
    }

    @PostMapping({"/add", ""})
    public Result<?> add(@RequestBody @Valid JobPostDTO jobPostDTO) {
        Long merchantId = requireMerchant("仅商家可发布职位");
        MerchantInfo info = getMerchantInfo(merchantId);
        if (!isMerchantAllowedPublish(info)) {
            return Result.error(403, buildMerchantPublishError(info));
        }
        jobInfoService.createJob(jobPostDTO, merchantId);
        return Result.success();
    }

    @PutMapping({"/update", ""})
    public Result<?> update(@RequestBody @Valid JobPostDTO jobPostDTO) {
        Long merchantId = requireMerchant("仅商家可更新职位");
        MerchantInfo info = getMerchantInfo(merchantId);
        if (!isMerchantAllowedPublish(info)) {
            return Result.error(403, buildMerchantPublishError(info).replace("发布", "更新"));
        }
        jobInfoService.updateJob(jobPostDTO, merchantId);
        return Result.success();
    }

    /**
     * 修改职位状态 (上架/下架)
     * URL示例: PUT /jobs/status/15/0
     */
    @PutMapping("/status/{id}/{status}") // 2. 明确的动词+资源+参数
    public Result<?> updateStatus(
            @PathVariable @Positive(message = "职位ID必须为正数") Long id,
            @PathVariable @Min(value = 0, message = "状态值不合法") @Max(value = 2, message = "状态值不合法") Integer status
    ) {
        Long userId = ControllerAccessUtils.requireAdminOrMerchant("Unauthorized", "无权限操作");
        String role = SecurityUtils.getRole();
        if (SecurityUtils.isMerchantRole(role)) {
            JobInfo jobInfo = jobInfoService.getById(id);
            if (jobInfo == null) {
                return Result.error(404, "职位不存在");
            }
            if (jobInfo.getMerchantId() == null || !jobInfo.getMerchantId().equals(userId)) {
                return Result.error(403, "仅可操作自己的职位");
            }
        }
        jobInfoService.updateStatus(id, status);
        return Result.success();
    }

    @GetMapping({"/merchant/list", "/merchant"})
    public Result<IPage<JobInfo>> merchantList(
            @RequestParam(required = false) Integer current,
            @RequestParam(required = false) Integer page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer auditStatus
    ) {
        Long merchantId = requireMerchant("仅商家可查看职位列表");
        Page<JobInfo> mpPage = PageQueryUtils.buildPage(current, page, size);
        IPage<JobInfo> jobListPage = jobInfoService.getMerchantJobList(mpPage, merchantId, status, auditStatus);
        return Result.success(jobListPage);
    }

    /**
     * 商家端：提交复审
     * URL示例: PUT /jobs/resubmit/15
     */
    @PutMapping("/resubmit/{id}")
    public Result<?> resubmit(@PathVariable @Positive(message = "职位ID必须为正数") Long id) {
        Long merchantId = requireMerchant("仅商家可提交复审");
        jobInfoService.resubmitAudit(id, merchantId);
        return Result.success("已提交复审");
    }

    // ================== 公开接口 (学生端/首页) ==================

    /**
     * 公开职位列表 (支持分页和关键词搜索)
     * URL: GET /jobs/public/list?current=1&size=10&keyword=Java
     * 新增: GET /jobs/search?current=1&size=10&keyword=Java&location=北京&exp=1-3年&edu=本科
     */
    @GetMapping({"/public/list", "/search"})
    public Result<IPage<JobInfoVO>> publicList(
            @RequestParam(required = false) Integer current,
            @RequestParam(required = false) Integer page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String degree,
            @RequestParam(required = false) String experience,
            @RequestParam(required = false) String location, // 兼容旧参数
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer minSalary,
            @RequestParam(required = false) Integer maxSalary,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) String industry,
            @RequestParam(required = false) String scale,
            @RequestParam(required = false) String financing,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String smartDegreeCap,
            @RequestParam(required = false) String smartExperienceCap,
            @RequestParam(required = false) Integer smartMinSalary
    ) {
        Page<JobInfoVO> mpPage = PageQueryUtils.buildPage(current, page, size);
        IPage<JobInfoVO> result = jobInfoService.getPublicJobList(mpPage, keyword, degree, experience, location,
                categoryId, minSalary, maxSalary, city, district, industry, scale, financing, sort,
                smartDegreeCap, smartExperienceCap, smartMinSalary);
        return Result.success(result);
    }

    /**
     * 公开职位详情
     * URL: GET /jobs/public/15
     */
    @GetMapping("/public/{id}")
    public Result<JobDetailVO> publicDetail(@PathVariable @Positive(message = "职位ID必须为正数") Long id) {
        JobDetailVO detail = jobInfoService.getPublicJobDetail(id);
        return Result.success(detail);
    }
}
