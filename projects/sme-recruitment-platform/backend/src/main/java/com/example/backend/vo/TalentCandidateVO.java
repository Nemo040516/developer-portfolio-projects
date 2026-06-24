package com.example.backend.vo;

import lombok.Data;

import java.util.List;

/**
 * 商家候选人库卡片信息
 */
@Data
public class TalentCandidateVO {
    private Long userId;
    private String name;
    private String avatar;
    private Integer gender;
    private Integer age;
    private String phone;
    private String email;
    private String currentIdentity;
    private String currentStatus;
    private String degree;
    private String workYears;
    // 标准字段名
    private String college;
    // 兼容历史字段名
    private String collage;
    private String major;
    private Integer gradYear;
    private String city;
    private String expectJob;
    private String expectSalary;
    private List<String> skills;
    private String summary;

    // 结构化简历字段（JSON字符串）
    private String educationJson;
    private String experienceJson;
    private String projectJson;
    private String certificateJson;
    private String awardJson;
    private String languageJson;
}
