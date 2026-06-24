package com.example.backend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.backend.entity.InterviewSchedule;

import java.time.LocalDateTime;
import java.util.List;

public interface InterviewScheduleService extends IService<InterviewSchedule> {

    InterviewSchedule createSchedule(Long deliveryId,
                                     Long merchantId,
                                     LocalDateTime scheduleTime,
                                     String location,
                                     String method,
                                     String remark);

    List<InterviewSchedule> getScheduleList(Long deliveryId, Long userId, String role);

    boolean updateStatus(Long scheduleId, Integer status, Long userId, String role);
}
