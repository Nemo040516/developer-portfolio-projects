package com.example.backend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class JobPostDTO {
    private Long id; // 编辑时需要，新增时为空

    @NotBlank(message = "职位名称不能为空")
    private String title;

    @NotNull(message = "职位分类不能为空")
    @JsonAlias("category_id")
    private Long categoryId;

    // 薪资范围
    @NotNull(message = "最低薪资不能为空")
    @Min(value = 1, message = "薪资不能小于1k")
    @JsonAlias("min_salary")
    private Integer minSalary;

    @NotNull(message = "最高薪资不能为空")
    @JsonAlias("max_salary")
    private Integer maxSalary;

    // 招聘人数（同一岗位多人需求时使用）
    @Min(value = 1, message = "招聘人数不能小于1")
    private Integer headcount;

    // 城市与区域
    @NotBlank(message = "工作城市不能为空")
    @JsonAlias("work_location")
    private String workLocation;
    
    private String district; // 选填

    // 核心描述 (富文本 HTML 字符串)
    @NotBlank(message = "职位描述不能为空")
    private String description;

    private String requirement; // 任职要求

    // 兼容处理：前端可能传 List<String> 或 String (逗号分隔)
    // 使用 Object 接收，Service 层再处理
    private Object tags;

    private String experience; // 经验
    private String degree;     // 学历
}
