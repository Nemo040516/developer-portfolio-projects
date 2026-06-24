package com.example.backend.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 面试安排返回对象
 */
@Data
public class InterviewScheduleVO {
    private Long id;
    private Long deliveryId;
    private Integer roundNo;
    private Integer status;
    private LocalDateTime scheduleTime;
    private String location;
    private String method;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
