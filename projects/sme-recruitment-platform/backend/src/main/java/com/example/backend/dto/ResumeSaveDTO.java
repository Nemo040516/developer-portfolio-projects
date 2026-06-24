/*
 * 文件速览：
 * 1. 文件职责：承载求职者在线简历保存请求，并对关键字段做基础格式校验。
 * 2. 对外入口：/seeker/resume 保存在线简历接口。
 * 3. 关键结构：BasicInfo、Education、Experience、Project、Certificate、Award、Language。
 * 4. 阅读建议：先看 BasicInfo 的薪资范围校验，再看教育/经历/项目里的时间范围约束。
 */
package com.example.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 在线简历保存请求
 */
@Data
public class ResumeSaveDTO {

    private static final String YEAR_MONTH_PATTERN = "^\\d{4}-(0[1-9]|1[0-2])$";
    private static final String YEAR_DATE_PATTERN = "^\\d{4}-(0[1-9]|1[0-2])-([0-2]\\d|3[01])$";

    @Valid
    private BasicInfo basicInfo;

    @Valid
    @Size(max = 20, message = "教育经历最多保留20条")
    private List<Education> education;

    @Valid
    @Size(max = 20, message = "工作经历最多保留20条")
    private List<Experience> experience;

    @Valid
    @Size(max = 20, message = "项目经历最多保留20条")
    private List<Project> project;

    @Valid
    @Size(max = 20, message = "证书信息最多保留20条")
    private List<Certificate> certificate;

    @Valid
    @Size(max = 20, message = "获奖信息最多保留20条")
    private List<Award> award;

    @Valid
    @Size(max = 20, message = "语言能力最多保留20条")
    private List<Language> language;

    @Data
    public static class BasicInfo {
        @Size(max = 50, message = "姓名长度不能超过50")
        private String name;

        @Min(value = 1, message = "性别取值不合法")
        @Max(value = 2, message = "性别取值不合法")
        private Integer gender;

        private String phone;
        private String email;

        @Size(max = 1000, message = "个人优势长度不能超过1000")
        private String advantage;

        // 出生日期（YYYY-MM-DD）
        @Pattern(regexp = YEAR_DATE_PATTERN, message = "出生日期格式必须为YYYY-MM-DD")
        private String birthday;

        // 当前身份：STUDENT/FRESH_GRAD/WORKER
        private String currentIdentity;

        // 工作年限（如：3年/应届生/在校生）
        @Size(max = 20, message = "工作年限描述不能超过20")
        private String workYears;

        // 求职状态
        @Size(max = 30, message = "求职状态长度不能超过30")
        private String currentStatus;

        // 期望城市
        @Size(max = 30, message = "期望城市长度不能超过30")
        private String expectCity;

        // 期望薪资
        @Size(max = 30, message = "期望薪资描述不能超过30")
        private String expectSalary;

        // 期望薪资范围 (k)
        @Min(value = 1, message = "期望薪资最低值必须为正数")
        @Max(value = 99, message = "期望薪资最低值不能超过99")
        private Integer expectSalaryMin;

        @Min(value = 1, message = "期望薪资最高值必须为正数")
        @Max(value = 99, message = "期望薪资最高值不能超过99")
        private Integer expectSalaryMax;

        // 期望职位
        @Size(max = 50, message = "期望职位长度不能超过50")
        private String expectJob;

        // 技能标签（逗号分隔）
        @Size(max = 300, message = "技能关键词长度不能超过300")
        private String skills;

        @AssertTrue(message = "期望薪资最低值不能大于最高值")
        public boolean isSalaryRangeValid() {
            return expectSalaryMin == null || expectSalaryMax == null || expectSalaryMin <= expectSalaryMax;
        }
    }

    @Data
    public static class Education {
        @Size(max = 100, message = "学校名称长度不能超过100")
        private String school;

        @Size(max = 100, message = "专业名称长度不能超过100")
        private String major;

        @Size(max = 20, message = "学历名称长度不能超过20")
        private String degree;

        private List<String> timeRange;

        @AssertTrue(message = "教育经历时间范围必须为两个YYYY-MM值，且开始时间不能晚于结束时间")
        public boolean isTimeRangeValid() {
            return isValidYearMonthRange(timeRange);
        }
    }

    @Data
    public static class Experience {
        @Size(max = 100, message = "公司名称长度不能超过100")
        private String company;

        @Size(max = 50, message = "职位名称长度不能超过50")
        private String position;

        @Size(max = 2000, message = "工作内容长度不能超过2000")
        private String content;

        private List<String> timeRange;

        @AssertTrue(message = "工作经历时间范围必须为两个YYYY-MM值，且开始时间不能晚于结束时间")
        public boolean isTimeRangeValid() {
            return isValidYearMonthRange(timeRange);
        }
    }

    @Data
    public static class Project {
        @Size(max = 100, message = "项目名称长度不能超过100")
        private String name;

        @Size(max = 50, message = "项目角色长度不能超过50")
        private String role;

        private List<String> timeRange;

        @Size(max = 200, message = "技术栈长度不能超过200")
        private String techStack;

        @Size(max = 2000, message = "项目描述长度不能超过2000")
        private String description;

        @AssertTrue(message = "项目经历时间范围必须为两个YYYY-MM值，且开始时间不能晚于结束时间")
        public boolean isTimeRangeValid() {
            return isValidYearMonthRange(timeRange);
        }
    }

    @Data
    public static class Certificate {
        @Size(max = 100, message = "证书名称长度不能超过100")
        private String name;

        @Size(max = 100, message = "发证机构长度不能超过100")
        private String issuer;

        @Pattern(regexp = YEAR_MONTH_PATTERN, message = "证书日期格式必须为YYYY-MM")
        private String date; // YYYY-MM
    }

    @Data
    public static class Award {
        @Size(max = 100, message = "奖项名称长度不能超过100")
        private String name;

        @Size(max = 50, message = "奖项级别长度不能超过50")
        private String level;

        @Pattern(regexp = YEAR_MONTH_PATTERN, message = "获奖日期格式必须为YYYY-MM")
        private String date; // YYYY-MM

        @Size(max = 1000, message = "奖项描述长度不能超过1000")
        private String description;
    }

    @Data
    public static class Language {
        @Size(max = 50, message = "语言名称长度不能超过50")
        private String name;

        @Size(max = 50, message = "语言等级长度不能超过50")
        private String level;

        @Size(max = 50, message = "语言成绩长度不能超过50")
        private String score;
    }

    private static boolean isValidYearMonthRange(List<String> range) {
        if (range == null || range.isEmpty()) {
            return true;
        }
        if (range.size() != 2) {
            return false;
        }
        String start = normalizeYearMonth(range.get(0));
        String end = normalizeYearMonth(range.get(1));
        return start != null && end != null && start.compareTo(end) <= 0;
    }

    private static String normalizeYearMonth(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.matches(YEAR_MONTH_PATTERN) ? trimmed : null;
    }
}
