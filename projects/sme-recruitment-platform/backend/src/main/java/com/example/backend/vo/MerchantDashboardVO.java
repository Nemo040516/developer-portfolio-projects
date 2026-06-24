package com.example.backend.vo;

import lombok.Data;

/**
 * 商家工作台统计视图对象
 */
@Data
public class MerchantDashboardVO {
    // 统计周期（天）
    private Integer rangeDays;

    // 职位浏览量（累计）
    private Integer jobViewCount;

    // 职位浏览量模式：RANGE/TOTAL
    private String viewCountMode;

    // 职位总数
    private Integer jobTotalCount;

    // 已上架职位数
    private Integer jobOnlineCount;

    // 审核通过职位数
    private Integer jobApprovedCount;

    // 收到简历（周期内）
    private Integer deliveryCount;

    // 沟通中会话数量（当前）
    private Integer chatSessionCount;

    // 面试完成数量（周期内）
    private Integer interviewDoneCount;

    // 漏斗：职位浏览
    private Integer funnelViewCount;

    // 漏斗：投递简历
    private Integer funnelDeliveryCount;

    // 漏斗：简历被查看
    private Integer funnelViewedCount;

    // 漏斗：发起面试
    private Integer funnelInterviewCount;
}
