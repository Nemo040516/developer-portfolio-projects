package com.example.backend.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Data
public class JobInfoVO {
    private Long id;
    private String title;
    private Integer minSalary;
    private Integer maxSalary;
    // 招聘人数（同一岗位多人需求时使用）
    private Integer headcount;
    private String workLocation;
    private String district; // 新增：行政区
    private String experience; // 新增：经验要求
    private String degree; // 新增：学历要求
    private String requirement;
    
    // --- 发布者信息 ---
    private String publisherName;
    private String publisherAvatar;

    // --- 公司信息 ---
    private String companyName;
    private String companyLogo;

    private String tags;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    public String getSalaryText() {
        if (minSalary != null && maxSalary != null) {
            return minSalary + "-" + maxSalary + "K";
        }
        return "面议";
    }

    public List<String> getTagList() {
        if (this.tags != null && !this.tags.isEmpty()) {
            return Arrays.asList(this.tags.split(","));
        }
        return new ArrayList<>();
    }
}
