/**
 * @file 速览索引
 * @summary 用户新增请求 DTO，承载账号创建参数并做基础字段校验。
 * @core 1. 约束 username/realName 必填与长度
 * @core 2. 支持可选初始密码并限制长度
 * @core 3. 约束手机号、邮箱长度与角色必填
 * @entry 先看：username、password、realName、roleId
 * @deps 关键依赖：UserController.create、UserService.create、UserRepository.insertUser
 * @state 关键字段：password(min=5,max=64)、mobile(手机号正则)、roleId(@NotNull)
 * @risk 高风险修改点：密码与角色字段规则变更会联动账号创建权限控制与默认密码策略
 * @link 相关文件：后端/src/main/java/com/wms/backend/user/service/UserService.java、后端/src/main/java/com/wms/backend/user/controller/UserController.java
 */
package com.wms.backend.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserCreateRequest(
        @NotBlank(message = "账号不能为空")
        @Size(max = 64, message = "账号长度不能超过64")
        String username,
        @Size(min = 5, max = 64, message = "密码长度需在5-64位之间")
        String password,
        @NotBlank(message = "姓名不能为空")
        @Size(max = 64, message = "姓名长度不能超过64")
        String realName,
        @Pattern(regexp = "^$|^1\\d{10}$", message = "手机号格式不正确")
        String mobile,
        @Size(max = 128, message = "邮箱长度不能超过128")
        String email,
        @NotNull(message = "角色不能为空")
        Long roleId
) {
}
