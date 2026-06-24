package com.example.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("applicant_profile") // Refactored table name
public class ApplicantInfo {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String currentIdentity;
    private String realName;
    private Integer gender;
    private LocalDate birthday;
    private String workYears;
    private String phone;
    private String email;
    
    private String college; // Fixed typo (was collage)
    private String major;
    private String degree;
    private Integer gradYear;
    
    private String currentStatus;
    private String expectCity;
    // 薪资范围（单位：k）
    private Integer expectSalaryMin;
    private Integer expectSalaryMax;
    // 保留原字段作为兼容展示，或在插入时自动拼接
    private String expectSalary;
    private String expectJob;
    private String advantage;
    private String skills;
    
    // Deprecated JSON fields (kept for compatibility if needed, but logic moved to child tables)
    private String educationJson;
    private String experienceJson;
    private String projectJson;
    private String certificateJson;
    private String awardJson;
    private String languageJson;
    
    private String resumeUrl;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
