package com.example.backend.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 附件授权状态返回对象
 */
@Data
public class ResumeAttachmentPermissionVO {
    private Long applicantId;
    private Long merchantId;
    private String status; // NONE/PENDING/GRANTED/REJECTED
    private LocalDateTime expireTime;
}
