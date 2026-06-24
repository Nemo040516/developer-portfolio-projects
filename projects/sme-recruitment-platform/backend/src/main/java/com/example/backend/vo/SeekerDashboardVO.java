package com.example.backend.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SeekerDashboardVO {
    // 简历相关
    private String resumeFileName;
    private LocalDateTime resumeUpdateTime;
    private Integer resumeCompleteness;

    // 投递统计
    private Integer appliedCount;   // 总投递数 / 或状态为 0 的
    private Integer viewedCount;    // 状态 1
    private Integer interviewCount; // 状态 2
    private Integer rejectedCount;  // 状态 3
}
