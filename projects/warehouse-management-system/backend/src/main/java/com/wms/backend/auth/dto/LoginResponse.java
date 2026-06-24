/**
 * @file 速览索引
 * @summary 登录成功响应DTO，返回令牌、角色与菜单权限信息。
 * @core 1. 返回 JWT `token`
 * @core 2. 返回用户标识 `username/roleCode`
 * @core 3. 返回前端可见菜单 `menus`
 * @entry 先看：record 字段 `token、username、roleCode、menus`
 * @deps 关键依赖：AuthService、MenuService、JwtTokenService
 * @risk 高风险修改点：menus 字段口径与前端菜单渲染联动
 * @link 相关文件：后端/src/main/java/com/wms/backend/auth/service/AuthService.java
 */
package com.wms.backend.auth.dto;

import java.util.List;

public record LoginResponse(
        String token,
        String username,
        String roleCode,
        List<String> menus
) {
}
