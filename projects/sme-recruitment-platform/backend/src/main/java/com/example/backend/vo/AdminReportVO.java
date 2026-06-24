package com.example.backend.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员举报处理列表 VO
 */
@Data
public class AdminReportVO {
    // 举报基础信息
    private Long id;
    private String type;
    private Long targetId;
    private String targetName;
    private String reporter;
    private String reporterRole;
    private String reason;
    private Integer status;
    private String result;
    private String evidence;
    /**
     * 证据文件列表（JSON字符串，数组对象）
     */
    private String evidenceFiles;
    private String actionCode;
    private Long handledBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime handledTime;
    private String targetSnapshot;
    private Integer reportCount;

    // 被举报对象：职位详情（type=JOB）
    private String jobTitle;
    private String jobCompanyName;
    private String jobLocation;
    private String jobSalary;
    private String jobDegree;
    private String jobExperience;
    private Integer jobHeadcount;
    private Integer jobStatus;
    private Integer jobAuditStatus;
    private String jobDescription;
    private String jobRequirement;
    private String jobTags;
    private Long jobCategoryId;
    private String jobCategoryName;
    private String jobCompanyLogo;
    private String jobCompanyScale;
    private String jobCompanyIndustry;
    private String jobCompanyAddress;
    private Long jobPublisherId;
    private String jobPublisherName;
    private String jobPublisherAvatar;

    // 被举报对象：企业详情（type=MERCHANT）
    private String merchantCompanyName;
    private String merchantIndustry;
    private String merchantScale;
    private String merchantFinancing;
    private String merchantAddress;
    private String merchantContactName;
    private String merchantContactPhone;
    private Integer merchantPublishStatus;
    private Integer merchantAuditStatus;
    private String merchantDescription;

    // 被举报对象：账号详情（type=USER）
    private String userNickname;
    private String userUsername;
    private String userRole;
    private String userPhone;
    private String userEmail;
    private Integer userStatus;
    private Integer userBanStatus;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;
}
