/*
 * 文件速览：
 * 1. 文件职责：承接求职者确认附件简历授权时的请求体。
 * 2. 对外入口：ResumeAttachmentPermissionController.grantPermission。
 * 3. 关键结构：约束商家 ID 合法，避免无效授权目标进入业务层。
 * 4. 阅读建议：结合 ResumeAttachmentPermissionController.grantPermission 一起看。
 */
package com.example.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 求职者授权附件简历请求
 */
@Data
public class ResumeAttachmentGrantDTO {
    @NotNull(message = "商家ID不能为空")
    @Positive(message = "商家ID必须为正数")
    private Long merchantId; // 商家ID
    private LocalDateTime expireTime; // 授权有效期（可选）
}
