/*
 * 文件速览：
 * 1. 文件职责：承接商家资料、Logo/资质上传、状态检查与工作台统计接口。
 * 2. 对外入口：/merchant 下的 check-status、dashboard-stats、info、update、logo、qualification、detail。
 * 3. 关键结构：商家身份校验、资料完整度判断、上传落盘、工作台统计汇总。
 * 4. 阅读建议：先看 checkStatus / update，再看上传与详情接口。
 */
package com.example.backend.controller;

import com.example.backend.common.Result;
import com.example.backend.dto.MerchantInfoDTO;
import com.example.backend.entity.JobDelivery;
import com.example.backend.entity.JobInfo;
import com.example.backend.entity.JobViewLog;
import com.example.backend.entity.MerchantInfo;
import com.example.backend.entity.InterviewSchedule;
import com.example.backend.service.ChatService;
import com.example.backend.service.InterviewScheduleService;
import com.example.backend.service.JobDeliveryService;
import com.example.backend.service.JobInfoService;
import com.example.backend.service.JobViewLogService;
import com.example.backend.service.MerchantInfoService;
import com.example.backend.utils.ControllerAccessUtils;
import com.example.backend.utils.SecurityUtils;
import com.example.backend.utils.UploadFileUtils;
import com.example.backend.vo.MerchantDashboardVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.extern.slf4j.Slf4j;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@RestController
@Validated
@RequestMapping("/merchant")
public class MerchantController {

    @Autowired
    private MerchantInfoService merchantInfoService;

    @Autowired
    private JobInfoService jobInfoService;

    @Autowired
    private JobDeliveryService jobDeliveryService;

    @Autowired
    private InterviewScheduleService interviewScheduleService;

    @Autowired
    private ChatService chatService;

    @Autowired
    private JobViewLogService jobViewLogService;

    private Long requireMerchant(String forbiddenMessage) {
        return ControllerAccessUtils.requireMerchant("Unauthorized", forbiddenMessage);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    @GetMapping("/check-status")
    public Result<Map<String, Object>> checkStatus() {
        Long userId = requireMerchant("仅商家可访问");

        // 查询 merchant_profile 表看有没有这个 userId 的记录
        MerchantInfo info = merchantInfoService.getByUserId(userId);
        
        Map<String, Object> map = new HashMap<>();
        
        // 🔥 核心修改：不仅看有没有记录，还要看关键资料是否完整
        boolean isEffectiveNew = (info == null)
                || isBlank(info.getCompanyName())
                || isBlank(info.getContactName())
                || isBlank(info.getContactPhone())
                || isBlank(info.getCreditCode())
                || isBlank(info.getLegalPerson())
                || isBlank(info.getLicenseUrl());

        if (isEffectiveNew) {
            map.put("isNew", true); // 是新手，没填过资料
            map.put("auditStatus", -1);
        } else {
            map.put("isNew", false);
            map.put("auditStatus", info.getAuditStatus()); // 0-待审核 1-通过
            map.put("companyName", info.getCompanyName());
        }
        return Result.success(map);
    }

    /**
     * 商家工作台统计数据
     */
    @GetMapping("/dashboard-stats")
    public Result<MerchantDashboardVO> getDashboardStats(
            @RequestParam(value = "rangeDays", required = false)
            @Positive(message = "统计天数必须为正数") Integer rangeDays) {
        Long userId = requireMerchant("仅商家可访问");

        int safeRange = (rangeDays == null || rangeDays <= 0) ? 7 : Math.min(rangeDays, 90);
        LocalDateTime startTime = LocalDateTime.now().minusDays(safeRange);

        // 1. 查询商家职位与累计浏览量
        List<JobInfo> jobList = jobInfoService.list(new QueryWrapper<JobInfo>()
                .eq("merchant_id", userId)
                .select("id", "view_count", "status", "audit_status"));
        List<Long> jobIds = jobList.stream()
                .map(JobInfo::getId)
                .filter(Objects::nonNull)
                .toList();
        int totalJobViewCount = jobList.stream()
                .map(JobInfo::getViewCount)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        int jobTotalCount = jobList.size();
        int jobOnlineCount = (int) jobList.stream()
                .filter(job -> job.getStatus() != null && job.getStatus() == 1)
                .count();
        int jobApprovedCount = (int) jobList.stream()
                .filter(job -> job.getAuditStatus() != null && job.getAuditStatus() == 1)
                .count();

        // 2. 统计投递漏斗（周期内）
        int deliveryCount = 0;
        int viewedCount = 0;
        int interviewCount = 0;
        if (!jobIds.isEmpty()) {
            QueryWrapper<JobDelivery> deliveryWrapper = new QueryWrapper<JobDelivery>()
                    .in("job_id", jobIds)
                    .ge("create_time", startTime);
            deliveryCount = (int) jobDeliveryService.count(deliveryWrapper);

            QueryWrapper<JobDelivery> viewedWrapper = new QueryWrapper<JobDelivery>()
                    .in("job_id", jobIds)
                    .ge("create_time", startTime)
                    .ge("status", 1);
            viewedCount = (int) jobDeliveryService.count(viewedWrapper);

            QueryWrapper<JobDelivery> interviewWrapper = new QueryWrapper<JobDelivery>()
                    .in("job_id", jobIds)
                    .ge("create_time", startTime)
                    .eq("status", 2);
            interviewCount = (int) jobDeliveryService.count(interviewWrapper);
        }

        // 3. 浏览量（优先按周期统计，失败时回退累计）
        int jobViewCount = totalJobViewCount;
        String viewCountMode = "TOTAL";
        try {
            if (!jobIds.isEmpty()) {
                QueryWrapper<JobViewLog> viewWrapper = new QueryWrapper<JobViewLog>()
                        .eq("merchant_id", userId)
                        .ge("view_time", startTime);
                jobViewCount = (int) jobViewLogService.count(viewWrapper);
                viewCountMode = "RANGE";
            } else {
                jobViewCount = 0;
                viewCountMode = "RANGE";
            }
        } catch (Exception e) {
            // 表不存在或查询失败时兜底累计
            jobViewCount = totalJobViewCount;
            viewCountMode = "TOTAL";
        }

        // 3. 沟通中会话数（当前）
        int chatSessionCount = chatService.getSessionList(userId).size();

        // 4. 面试完成数量（周期内）
        int interviewDoneCount = (int) interviewScheduleService.count(new QueryWrapper<InterviewSchedule>()
                .eq("creator_id", userId)
                .eq("status", 4)
                .ge("create_time", startTime));

        MerchantDashboardVO vo = new MerchantDashboardVO();
        vo.setRangeDays(safeRange);
        vo.setJobViewCount(jobViewCount);
        vo.setViewCountMode(viewCountMode);
        vo.setJobTotalCount(jobTotalCount);
        vo.setJobOnlineCount(jobOnlineCount);
        vo.setJobApprovedCount(jobApprovedCount);
        vo.setDeliveryCount(deliveryCount);
        vo.setChatSessionCount(chatSessionCount);
        vo.setInterviewDoneCount(interviewDoneCount);
        vo.setFunnelViewCount(jobViewCount);
        vo.setFunnelDeliveryCount(deliveryCount);
        vo.setFunnelViewedCount(viewedCount);
        vo.setFunnelInterviewCount(interviewCount);
        return Result.success(vo);
    }

    // 获取当前商家的详细信息
    @GetMapping("/info")
    public Result<MerchantInfo> getInfo() {
        Long userId = requireMerchant("仅商家可访问");
        MerchantInfo info = merchantInfoService.getByUserId(userId);
        // 如果没填过，返回 null 或者空对象给前端
        return Result.success(info);
    }

    // 保存/更新商家信息
    @PostMapping("/update")
    public Result<String> update(@RequestBody @Valid MerchantInfoDTO dto) {
        Long userId = requireMerchant("仅商家可操作");
        merchantInfoService.saveOrUpdateMerchant(userId, dto);
        return Result.success("保存成功");
    }

    /**
     * 上传企业 Logo
     */
    @PostMapping("/logo")
    public Result<String> uploadLogo(@RequestParam("file") MultipartFile file) {
        Long userId = requireMerchant("仅商家可操作");
        if (file == null || file.isEmpty()) {
            return Result.error(400, "文件不能为空");
        }
        // 基础校验：仅允许 JPG/PNG，大小不超过 2MB
        long maxSize = 2 * 1024 * 1024;
        if (file.getSize() > maxSize) {
            return Result.error(400, "Logo 图片大小不能超过2MB");
        }

        String extension = UploadFileUtils.extractExtension(file.getOriginalFilename());
        if (!UploadFileUtils.isAllowedExtension(extension, "jpg", "jpeg", "png")) {
            return Result.error(400, "仅支持 JPG/PNG 图片格式");
        }

        try {
            String fileUrl = UploadFileUtils.storeUnderUploads(file, userId, "logo", "logos");
            merchantInfoService.updateCompanyLogo(userId, fileUrl);
            return Result.success(fileUrl);
        } catch (Exception e) {
            return Result.error(500, "上传失败");
        }
    }

    /**
     * 上传企业资质材料（营业执照/法人身份证/授权书/门头照等）
     */
    @PostMapping("/qualification")
    public Result<String> uploadQualification(@RequestParam("file") MultipartFile file) {
        Long userId = requireMerchant("仅商家可操作");
        if (file == null || file.isEmpty()) {
            return Result.error(400, "文件不能为空");
        }
        // 允许图片/PDF，大小不超过 5MB
        long maxSize = 5 * 1024 * 1024;
        if (file.getSize() > maxSize) {
            return Result.error(400, "资质材料大小不能超过5MB");
        }

        String extension = UploadFileUtils.extractExtension(file.getOriginalFilename());
        if (!UploadFileUtils.isAllowedExtension(extension, "jpg", "jpeg", "png", "pdf")) {
            return Result.error(400, "仅支持 JPG/PNG/PDF 格式");
        }

        try {
            String fileUrl = UploadFileUtils.storeUnderUploads(file, userId, "qualification", "qualifications");
            return Result.success(fileUrl);
        } catch (Exception e) {
            return Result.error(500, "上传失败");
        }
    }

    @GetMapping(value = "/detail/{userId}", produces = "application/json;charset=UTF-8")
    public Result<?> getDetailByUserId(@PathVariable("userId") @Positive(message = "商家用户ID必须为正数") Long userId) {
        log.debug("收到商家详情请求, userId={}", userId);
        try {
            MerchantInfo info = merchantInfoService.getByUserId(userId);
            if (info == null) {
                return Result.error(404, "未找到商家资料");
            }
            
            Map<String, Object> safeMap = new HashMap<>();
            safeInfo(info, safeMap);
            
            log.debug("成功返回商家数据, companyName={}", info.getCompanyName());
            return Result.success(safeMap);
        } catch (Exception e) {
            log.error("商家详情查询失败, userId={}", userId, e);
            return Result.error(500, "服务器处理详情失败");
        }
    }

    private void safeInfo(MerchantInfo info, Map<String, Object> map) {
        map.put("companyName", info.getCompanyName());
        map.put("companyLogo", info.getCompanyLogo());
        map.put("industry", info.getIndustry());
        map.put("scale", info.getScale());
        map.put("financing", info.getFinancing());
        map.put("description", info.getDescription()); // MyBatis-Plus 通常会自动处理 BLOB 到 String
        map.put("address", info.getAddress());
        map.put("city", info.getCity());
    }
}
