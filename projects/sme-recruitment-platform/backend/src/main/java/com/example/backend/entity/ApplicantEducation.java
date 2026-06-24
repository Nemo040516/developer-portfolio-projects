package com.example.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("applicant_education") // 修正为全拼
public class ApplicantEducation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String school;
    private String major;
    private String degree;
    private String startDate;
    private String endDate;
    private LocalDateTime createTime;
}
