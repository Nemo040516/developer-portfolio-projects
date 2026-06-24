package com.example.backend.vo;

import lombok.Data;

import java.util.List;

/**
 * 在线简历返回对象
 */
@Data
public class ResumeVO {
    private BasicInfo basicInfo;
    private List<Education> education;
    private List<Experience> experience;
    private List<Project> project;
    private List<Certificate> certificate;
    private List<Award> award;
    private List<Language> language;

    @Data
    public static class BasicInfo {
        private String name;
        private Integer gender;
        private String phone;
        private String email;
        // 兼容历史拼写 collage，同时返回标准字段 college
        private String college;
        private String collage;
        private String major;
        private String degree;
        private String advantage;
        // 出生日期（YYYY-MM-DD）
        private String birthday;
        // 当前身份：STUDENT/FRESH_GRAD/WORKER
        private String currentIdentity;
        // 工作年限（如：3年/应届生/在校生）
        private String workYears;
        // 求职状态
        private String currentStatus;
        // 期望城市
        private String expectCity;
        // 期望薪资
        private String expectSalary;
        private Integer expectSalaryMin;
        private Integer expectSalaryMax;
        // 期望职位
        private String expectJob;
        // 技能标签（逗号分隔）
        private String skills;
    }

    @Data
    public static class Education {
        private String school;
        private String major;
        private String degree;
        private List<String> timeRange;
    }

    @Data
    public static class Experience {
        private String company;
        private String position;
        private String content;
        private List<String> timeRange;
    }

    @Data
    public static class Project {
        private String name;
        private String role;
        private List<String> timeRange;
        private String techStack;
        private String description;
    }

    @Data
    public static class Certificate {
        private String name;
        private String issuer;
        private String date; // YYYY-MM
    }

    @Data
    public static class Award {
        private String name;
        private String level;
        private String date; // YYYY-MM
        private String description;
    }

    @Data
    public static class Language {
        private String name;
        private String level;
        private String score;
    }
}
