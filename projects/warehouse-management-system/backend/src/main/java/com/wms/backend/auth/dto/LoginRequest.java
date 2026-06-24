/**
 * @file 速览索引
 * @summary 登录请求DTO，承载登录接口入参并执行基础非空校验。
 * @core 1. 定义用户名 `username`
 * @core 2. 定义密码 `password`
 * @core 3. 通过 `@NotBlank` 约束请求参数
 * @entry 先看：record 字段 `username/password`
 * @deps 关键依赖：AuthController、AuthService、jakarta.validation
 * @risk 高风险修改点：字段名与前端入参一致性、校验提示文案
 * @link 相关文件：后端/src/main/java/com/wms/backend/auth/controller/AuthController.java
 */
package com.wms.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "账号不能为空")
        String username,
        @NotBlank(message = "密码不能为空")
        String password
) {
}
