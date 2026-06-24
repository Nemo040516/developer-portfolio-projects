package com.example.backend.vo;

import lombok.Data;

/**
 * 管理员看板统计 VO
 */
@Data
public class AdminStatsVO {
    private Integer jobPending;
    private Integer merchantPending;
    private Integer reportPending;
    private Integer todayJobs;
    private Integer todayMerchants;
    private Integer todayReports;
    private Integer totalJobs;
    private Integer totalMerchants;
    private Integer totalReports;
}
