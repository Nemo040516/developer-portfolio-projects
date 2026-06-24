package com.example.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("job_post")
public class JobInfo {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    @TableField("merchant_id")
    private Long merchantId;
    
    @TableField("category_id")
    private Long categoryId;
    
    // 非数据库字段，用于展示分类名称
    @TableField(exist = false)
    private String categoryName;

    private String title;
    
    private String description;
    
    private String requirement;
    
    @TableField("work_location")
    private String workLocation;
    
    private String district;
    
    @TableField("min_salary")
    private Integer minSalary;
    
    @TableField("max_salary")
    private Integer maxSalary;

    // 招聘人数（同一岗位多名需求使用该字段）
    @TableField("headcount")
    private Integer headcount;
    
    private String experience;
    
    private String degree;
    
    private String tags;
    
    private Integer status;

    // 审核状态：0-待审核，1-通过，2-驳回
    @TableField("audit_status")
    private Integer auditStatus;

    // 审核驳回原因
    @TableField("audit_reason")
    private String auditReason;

    // 审核时间
    @TableField("audit_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime auditTime;

    // 最近修改摘要（商家修改后用于管理员快速确认变更内容）
    @TableField("last_edit_summary")
    private String lastEditSummary;

    // 最近修改时间
    @TableField("last_edit_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime lastEditTime;
    
    @TableField("view_count")
    private Integer viewCount;
    
    @TableField("create_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
    
    @TableField("update_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    // 【魔法字段】自动拼接薪资范围，供前端直接显示
    // 前端收到 JSON 时，会多出一个 "salary_range": "6-11k"
    @JsonProperty("salary_range") 
    public String getSalaryRange() {
        if (minSalary != null && maxSalary != null) {
            return minSalary + "-" + maxSalary + "k";
        }
        return "面议";
    }
}
