package com.example.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("applicant_experience") // 修正为全拼
public class ApplicantExperience {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String company;
    private String position;
    private String content;
    private String startDate;
    private String endDate;
    private Integer type; // 0:全职 1:实习
    private LocalDateTime createTime;
}
