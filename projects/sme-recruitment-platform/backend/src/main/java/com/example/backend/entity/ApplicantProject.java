package com.example.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("applicant_project") // 修正为全拼
public class ApplicantProject {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String name;
    private String role;
    private String description;
    private String startDate;
    private String endDate;
    private LocalDateTime createTime;
}
