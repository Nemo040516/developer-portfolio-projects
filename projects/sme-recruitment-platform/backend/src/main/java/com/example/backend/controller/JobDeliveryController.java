/*
 * 文件速览：
 * 1. 文件职责：处理投递提交、求职者投递记录、商家候选人列表与投递状态更新。
 * 2. 对外入口：/delivery 下的 submit、seeker/list、merchant/list、status。
 * 3. 关键结构：求职者/商家角色校验、分页查询、投递状态流转。
 * 4. 阅读建议：先看 submit 与 updateStatus，再看两侧列表查询。
 */
package com.example.backend.controller;

import com.example.backend.common.Result;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.backend.dto.DeliveryStatusUpdateDTO;
import com.example.backend.dto.DeliverySubmitDTO;
import com.example.backend.service.JobDeliveryService;
import com.example.backend.utils.ControllerAccessUtils;
import com.example.backend.vo.DeliveryVO;
import com.example.backend.vo.MerchantDeliveryVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Validated
@RequestMapping("/delivery")
public class JobDeliveryController {

    @Autowired
    private JobDeliveryService jobDeliveryService;

    // 投递简历
    @PostMapping("/submit")
    public Result<Boolean> submit(@RequestBody @Valid DeliverySubmitDTO dto) {
        Long userId = ControllerAccessUtils.requireApplicant("仅求职者可投递简历");
        boolean success = jobDeliveryService.submitDelivery(userId, dto.getJobId());
        return Result.success(success);
    }

    // 获取我的投递记录
    @GetMapping("/seeker/list")
    public Result<List<DeliveryVO>> getMyDeliveries() {
        Long userId = ControllerAccessUtils.requireApplicant("仅求职者可查看投递记录");
        List<DeliveryVO> list = jobDeliveryService.getMyDeliveries(userId);
        return Result.success(list);
    }

    // 商家端：候选人投递列表
    @GetMapping("/merchant/list")
    public Result<IPage<MerchantDeliveryVO>> getMerchantDeliveries(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long jobId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String degree
    ) {
        Long merchantId = ControllerAccessUtils.requireMerchant("仅商家可查看投递列表");
        Page<MerchantDeliveryVO> page = new Page<>(current, size);
        IPage<MerchantDeliveryVO> result = jobDeliveryService.getMerchantDeliveries(page, merchantId, jobId, status, degree);
        return Result.success(result);
    }

    // 商家端：更新投递状态
    @PutMapping("/status")
    public Result<Boolean> updateStatus(@RequestBody @Valid DeliveryStatusUpdateDTO dto) {
        Long merchantId = ControllerAccessUtils.requireMerchant("仅商家可更新投递状态");
        boolean ok = jobDeliveryService.updateDeliveryStatus(
                merchantId,
                dto.getId(),
                dto.getStatus(),
                dto.getFeedback(),
                dto.getInterviewTime(),
                dto.getInterviewLocation(),
                dto.getInterviewMethod(),
                dto.getInterviewRemark()
        );
        if (!ok) {
            return Result.error(403, "无权限或记录不存在");
        }
        return Result.success(true);
    }

    // 求职者端：查询某职位的投递状态
    @GetMapping("/status/{jobId}")
    public Result<Integer> getDeliveryStatus(@PathVariable @Positive(message = "职位ID必须为正数") Long jobId) {
        Long userId = ControllerAccessUtils.requireApplicant("仅求职者可查询投递状态");
        Integer status = jobDeliveryService.getDeliveryStatus(userId, jobId);
        return Result.success(status);
    }
}
