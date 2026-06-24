/*
 * 文件速览：
 * 1. 文件职责：处理面试安排列表查询与状态更新。
 * 2. 对外入口：/interview/list、/interview/status。
 * 3. 关键结构：当前登录用户校验、InterviewSchedule 到 VO 的字段映射。
 * 4. 阅读建议：先看 getInterviewList，再看 updateStatus。
 */
package com.example.backend.controller;

import com.example.backend.common.Result;
import com.example.backend.dto.InterviewStatusUpdateDTO;
import com.example.backend.entity.InterviewSchedule;
import com.example.backend.service.InterviewScheduleService;
import com.example.backend.utils.ControllerAccessUtils;
import com.example.backend.utils.SecurityUtils;
import com.example.backend.vo.InterviewScheduleVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 面试安排接口（支持多轮面试查询）
 */
@RestController
@Validated
@RequestMapping("/interview")
public class InterviewScheduleController {

    @Autowired
    private InterviewScheduleService interviewScheduleService;

    @GetMapping("/list")
    public Result<List<InterviewScheduleVO>> getInterviewList(
            @RequestParam @Positive(message = "投递记录ID必须为正数") Long deliveryId
    ) {
        Long userId = ControllerAccessUtils.requireLogin();
        String role = SecurityUtils.getRole();
        List<InterviewSchedule> list = interviewScheduleService.getScheduleList(deliveryId, userId, role);
        List<InterviewScheduleVO> vos = list.stream().map(this::toVO).collect(Collectors.toList());
        return Result.success(vos);
    }

    @PutMapping("/status")
    public Result<Boolean> updateStatus(@RequestBody @Valid InterviewStatusUpdateDTO dto) {
        Long userId = ControllerAccessUtils.requireLogin();
        String role = SecurityUtils.getRole();
        boolean ok = interviewScheduleService.updateStatus(dto.getId(), dto.getStatus(), userId, role);
        return Result.success(ok);
    }

    private InterviewScheduleVO toVO(InterviewSchedule schedule) {
        InterviewScheduleVO vo = new InterviewScheduleVO();
        vo.setId(schedule.getId());
        vo.setDeliveryId(schedule.getDeliveryId());
        vo.setRoundNo(schedule.getRoundNo());
        vo.setStatus(schedule.getStatus());
        vo.setScheduleTime(schedule.getScheduleTime());
        vo.setLocation(schedule.getLocation());
        vo.setMethod(schedule.getMethod());
        vo.setRemark(schedule.getRemark());
        vo.setCreateTime(schedule.getCreateTime());
        vo.setUpdateTime(schedule.getUpdateTime());
        return vo;
    }
}
