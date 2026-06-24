/*
 * 文件速览：
 * 1. 文件职责：提供管理员端治理通知与整改单的创建、列表、详情、复核接口。
 * 2. 对外入口：/admin/governance/notices。
 * 3. 关键结构：管理员鉴权、分页查询、创建通知、复核处理。
 * 4. 阅读建议：先看顶部管理员鉴权，再看列表与创建接口。
 */
package com.example.backend.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.backend.common.Result;
import com.example.backend.dto.AdminGovernanceNoticeCreateDTO;
import com.example.backend.dto.AdminGovernanceNoticeReviewDTO;
import com.example.backend.service.GovernanceNoticeService;
import com.example.backend.utils.ControllerAccessUtils;
import com.example.backend.utils.PageQueryUtils;
import com.example.backend.vo.AdminGovernanceNoticeVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员治理通知控制器
 */
@RestController
@Validated
@RequestMapping("/admin/governance")
public class AdminGovernanceController {

    @Autowired
    private GovernanceNoticeService governanceNoticeService;

    @GetMapping("/notices")
    public Result<IPage<AdminGovernanceNoticeVO>> getNoticePage(
            @RequestParam(required = false) Integer current,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String targetRole,
            @RequestParam(required = false) String noticeType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sourceModule,
            @RequestParam(required = false) Boolean overdueOnly
    ) {
        ControllerAccessUtils.requireAdmin("仅管理员可访问");
        Page<?> mpPage = PageQueryUtils.buildPage(current, page, size, 20);
        return Result.success(governanceNoticeService.getAdminNoticePage(mpPage, targetRole, noticeType, status, sourceModule, overdueOnly));
    }

    @GetMapping("/notices/{id}")
    public Result<AdminGovernanceNoticeVO> getNoticeDetail(@PathVariable @Positive(message = "治理事项ID必须为正数") Long id) {
        ControllerAccessUtils.requireAdmin("仅管理员可访问");
        return Result.success(governanceNoticeService.getAdminNoticeDetail(id));
    }

    @PostMapping("/notices")
    public Result<Long> createNotice(@RequestBody @Valid AdminGovernanceNoticeCreateDTO dto) {
        Long adminUserId = ControllerAccessUtils.requireAdmin("仅管理员可操作");
        return Result.success(governanceNoticeService.createNotice(dto, adminUserId));
    }

    @PostMapping("/notices/{id}/review")
    public Result<?> reviewNotice(@PathVariable @Positive(message = "治理事项ID必须为正数") Long id, @RequestBody @Valid AdminGovernanceNoticeReviewDTO dto) {
        Long adminUserId = ControllerAccessUtils.requireAdmin("仅管理员可操作");
        governanceNoticeService.reviewNotice(id, dto, adminUserId);
        return Result.success("操作成功");
    }
}
