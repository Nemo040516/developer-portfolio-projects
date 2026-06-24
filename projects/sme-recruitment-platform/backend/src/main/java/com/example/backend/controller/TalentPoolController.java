/*
 * 文件速览：
 * 1. 文件职责：处理商家候选人库的分页列表与候选人详情查询。
 * 2. 对外入口：/merchant/talent/list、/merchant/talent/detail/{userId}。
 * 3. 关键结构：商家身份校验、候选人分页条件、详情兜底返回。
 * 4. 阅读建议：先看 list，再看 detail。
 */
package com.example.backend.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.backend.common.Result;
import com.example.backend.service.TalentPoolService;
import com.example.backend.utils.ControllerAccessUtils;
import com.example.backend.utils.PageQueryUtils;
import com.example.backend.vo.TalentCandidateVO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商家候选人库接口
 */
@RestController
@Validated
@RequestMapping("/merchant/talent")
public class TalentPoolController {

    @Autowired
    private TalentPoolService talentPoolService;

    @GetMapping("/list")
    public Result<IPage<TalentCandidateVO>> list(
            @RequestParam(required = false) @Positive(message = "页码必须为正数") Integer current,
            @RequestParam(required = false) @Positive(message = "每页条数必须为正数") @Max(value = 50, message = "每页条数不能超过50") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String expectJob,
            @RequestParam(required = false) String city
    ) {
        Long merchantId = ControllerAccessUtils.requireMerchant("仅商家可访问候选人库");
        Page<TalentCandidateVO> page = PageQueryUtils.buildPage(current, null, size, 12);
        IPage<TalentCandidateVO> result = talentPoolService.getTalentPage(page, merchantId, keyword, expectJob, city);
        return Result.success(result);
    }

    @GetMapping("/detail/{userId}")
    public Result<TalentCandidateVO> detail(@PathVariable @Positive(message = "求职者ID必须为正数") Long userId) {
        Long merchantId = ControllerAccessUtils.requireMerchant("仅商家可访问候选人库");
        TalentCandidateVO detail = talentPoolService.getTalentDetail(merchantId, userId);
        if (detail == null) {
            return Result.error(404, "未找到候选人信息");
        }
        return Result.success(detail);
    }
}
