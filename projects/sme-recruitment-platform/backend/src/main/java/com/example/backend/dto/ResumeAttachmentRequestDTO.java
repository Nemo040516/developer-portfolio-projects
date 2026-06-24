/*
 * 文件速览：
 * 1. 文件职责：承接商家发起附件简历授权申请时的请求体。
 * 2. 对外入口：ResumeAttachmentPermissionController.requestPermission。
 * 3. 关键结构：约束求职者 ID 合法，避免无效授权对象进入关系校验。
 * 4. 阅读建议：结合 ResumeAttachmentPermissionController 的投递关系校验一起看。
 */
package com.example.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商家申请查看附件简历请求
 */
@Data
public class ResumeAttachmentRequestDTO {
    @NotNull(message = "求职者ID不能为空")
    @Positive(message = "求职者ID必须为正数")
    private Long applicantId; // 求职者ID
    private LocalDateTime expireTime; // 授权有效期（可选）
}
