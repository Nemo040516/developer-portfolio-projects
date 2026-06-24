/*
 * 文件速览：
 * 1. 文件职责：提供商家 / 求职者侧治理通知列表、详情、已读与提交动作接口。
 * 2. 对外入口：/governance/notices。
 * 3. 关键结构：当前用户身份识别、本人数据读取、状态/阶段筛选、已读、提交整改说明。
 * 4. 阅读建议：先看 my 列表的 status/stage 参数，再看 read、actions 两个写接口。
 */
package com.example.backend.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.backend.common.Result;
import com.example.backend.dto.GovernanceNoticeActionDTO;
import com.example.backend.service.GovernanceNoticeService;
import com.example.backend.utils.ControllerAccessUtils;
import com.example.backend.utils.PageQueryUtils;
import com.example.backend.utils.SecurityUtils;
import com.example.backend.vo.GovernanceNoticeVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 用户侧治理通知控制器
 */
@RestController
@Validated
@RequestMapping("/governance/notices")
public class GovernanceNoticeController {

    @Autowired
    private GovernanceNoticeService governanceNoticeService;

    @GetMapping("/my")
    public Result<IPage<GovernanceNoticeVO>> getMyNoticePage(
            @RequestParam(required = false) Integer current,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String noticeType,
            @RequestParam(required = false) String stage
    ) {
        Long userId = ControllerAccessUtils.requireLogin();
        Page<?> mpPage = PageQueryUtils.buildPage(current, page, size, 20);
        return Result.success(governanceNoticeService.getMyNoticePage(mpPage, userId, status, noticeType, stage));
    }

    @GetMapping("/my/{id}")
    public Result<GovernanceNoticeVO> getMyNoticeDetail(@PathVariable @Positive(message = "治理事项ID必须为正数") Long id) {
        Long userId = ControllerAccessUtils.requireLogin();
        return Result.success(governanceNoticeService.getMyNoticeDetail(id, userId));
    }

    @PutMapping("/{id}/read")
    public Result<?> markRead(@PathVariable @Positive(message = "治理事项ID必须为正数") Long id) {
        Long userId = ControllerAccessUtils.requireLogin();
        governanceNoticeService.markRead(id, userId, SecurityUtils.getRole());
        return Result.success("已读成功");
    }

    @PostMapping("/{id}/actions")
    public Result<?> submitAction(@PathVariable @Positive(message = "治理事项ID必须为正数") Long id,
                                  @RequestBody @Valid GovernanceNoticeActionDTO dto,
                                  HttpServletRequest request) {
        Long userId = ControllerAccessUtils.requireLogin();
        boolean restrictedMode = Boolean.TRUE.equals(request.getAttribute("restrictedMode"));
        governanceNoticeService.submitAction(id, userId, SecurityUtils.getRole(), dto, restrictedMode);
        return Result.success("提交成功");
    }
}
