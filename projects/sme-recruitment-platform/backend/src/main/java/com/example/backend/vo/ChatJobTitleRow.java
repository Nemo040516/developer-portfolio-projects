package com.example.backend.vo;

import lombok.Data;

/**
 * 会话关联职位信息行
 */
@Data
public class ChatJobTitleRow {
    private Long applicantId; // 求职者ID
    private Long merchantId;  // 商家ID
    private String jobTitle;  // 职位名称
}
