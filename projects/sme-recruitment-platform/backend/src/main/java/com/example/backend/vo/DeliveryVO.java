package com.example.backend.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class DeliveryVO {
    private Long id;            // 投递记录ID
    private Long jobId;
    private String jobTitle;    // 职位名称
    private String companyName; // 公司名称
    private Integer minSalary;  // 最低薪资
    private Integer maxSalary;  // 最高薪资
    private String salaryRange; // 薪资范围 (拼接 min-max)
    private String workLocation;// 工作地点
    private Integer status;     // 投递状态
    private String feedback;    // 商家反馈/面试备注
    private LocalDateTime createTime; // 投递时间

    // 面试邀约结构化信息
    private LocalDateTime interviewTime; // 面试时间
    private String interviewLocation;    // 面试地点
    private String interviewMethod;      // 面试方式
    private String interviewRemark;      // 面试备注
    private Integer interviewStatus;     // 面试状态（最新轮次）

    // 职位状态：0-已下架，1-招聘中（可能为空，表示职位被删除或未同步）
    private Integer jobStatus;

    // 审核状态：0-待审核，1-通过，2-驳回（可能为空，表示职位被删除或未同步）
    private Integer jobAuditStatus;
}
