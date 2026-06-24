package com.example.backend.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Data
public class JobDetailVO {
    private Long id;
    private String title;
    private String description;
    private Integer minSalary;
    private Integer maxSalary;
    // 招聘人数（同一岗位多人需求时使用）
    private Integer headcount;
    private String workLocation;
    private String district; // 新增：行政区
    private String experience; // 新增：经验
    private String degree; // 新增：学历
    private String requirement;
    private String tags;
    
    // --- 发布者信息（商家/招聘负责人） ---
    private Long publisherId;      // 发布者ID (用于发起聊天)
    private String publisherName;  // 发布者昵称 (如: 邢女士)
    private String publisherAvatar;// 发布者头像
    private String publisherTitle; // 发布者头衔（如“招聘负责人”）

    // --- 公司信息 ---
    private String companyName;
    private String companyLogo;
    private String companyScale;
    private String companyIndustry;
    private String companyAddress; // 关键！获取详细地址

    private Long categoryId;
    private String categoryName;
    private Integer status;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

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
