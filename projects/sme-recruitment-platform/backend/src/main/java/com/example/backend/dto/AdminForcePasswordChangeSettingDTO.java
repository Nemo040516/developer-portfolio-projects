/*
 * 文件速览：
 * 1. 文件职责：定义管理员更新“临时密码登录后强制修改密码”开关时的请求体。
 * 2. 对外入口：AdminController#updateForcePasswordChangeSetting。
 * 3. 关键字段：enabled。
 * 4. 阅读建议：直接查看 enabled 的非空校验即可。
 */
package com.example.backend.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理员更新强制改密开关 DTO
 */
@Data
public class AdminForcePasswordChangeSettingDTO {

    @NotNull(message = "开关状态不能为空")
    private Boolean enabled;
}
