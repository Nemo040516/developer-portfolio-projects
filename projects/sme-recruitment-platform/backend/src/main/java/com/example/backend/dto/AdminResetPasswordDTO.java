/*
 * 文件速览：
 * 1. 文件职责：定义管理员重置用户密码时提交的请求体。
 * 2. 对外入口：AdminController#resetUserPassword。
 * 3. 关键字段：newPassword、confirmPassword、reason。
 * 4. 阅读建议：先看字段校验，再看控制器中的一致性校验。
 */
package com.example.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员重置账号密码 DTO
 */
@Data
public class AdminResetPasswordDTO {

    @NotBlank(message = "临时密码不能为空")
    @Size(min = 6, max = 50, message = "临时密码长度需在6-50之间")
    private String newPassword;

    @NotBlank(message = "确认密码不能为空")
    private String confirmPassword;

    @NotBlank(message = "重置原因不能为空")
    @Size(max = 200, message = "重置原因不能超过200个字符")
    private String reason;
}
