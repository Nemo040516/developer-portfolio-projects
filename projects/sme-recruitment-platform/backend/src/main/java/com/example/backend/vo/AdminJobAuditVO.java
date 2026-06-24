package com.example.backend.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员职位审核列表 VO
 */
@Data
public class AdminJobAuditVO {
    private Long id;
    private String title;
    private String companyName;
    private String location;
    private String salary;
    // 招聘人数（同一岗位多名需求时使用）
    private Integer headcount;
    private String tags;
    private Integer auditStatus;
    private String reason;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;

    // 最近修改摘要（管理员快速识别变更内容）
    private String lastEditSummary;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime lastEditTime;
}
