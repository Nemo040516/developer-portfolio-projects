package com.example.backend.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商家端候选人投递列表
 */
@Data
public class MerchantDeliveryVO {
    private Long id;                  // 投递记录ID
    private Long jobId;               // 职位ID
    private String jobName;           // 职位名称
    private Integer status;           // 投递状态
    private String feedback;          // 商家反馈/面试备注
    private String resumeUrl;         // 附件简历地址
    private Integer attachmentStatus; // 附件授权状态：0-待同意，1-已授权，2-已拒绝，null-未申请
    private Integer hasResumeAttachment; // 是否存在附件简历（0/1）
    private LocalDateTime createTime; // 投递时间

    // 面试邀约结构化信息
    private LocalDateTime interviewTime; // 面试时间
    private String interviewLocation;    // 面试地点
    private String interviewMethod;      // 面试方式
    private String interviewRemark;      // 面试备注
    private Integer interviewStatus;     // 面试状态（最新轮次）

    private ApplicantSimpleVO applicant; // 候选人信息
}
