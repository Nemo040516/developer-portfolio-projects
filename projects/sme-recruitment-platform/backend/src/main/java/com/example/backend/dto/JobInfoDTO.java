package com.example.backend.dto;

import lombok.Data;
import java.util.List;

@Data
public class JobInfoDTO {
    private Long id;
    
    private Long categoryId;
    
    private String title;
    
    private String description;
    
    private String requirement;
    
    private String workLocation;
    
    private String district;
    
    private Integer minSalary;
    
    private Integer maxSalary;

    // 招聘人数（同一岗位多名需求使用该字段）
    private Integer headcount;
    
    private String experience;
    
    private String degree;
    
    private String tags; // 逗号分隔的字符串，用于接收或存储
    
    private List<String> tagList; // 方便前端传递数组

    private Integer status;
}
