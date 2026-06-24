/**
 * @file 速览索引
 * @summary 用户响应 DTO，返回账号基础信息、状态与角色信息。
 * @core 1. 返回账号、姓名、联系方式
 * @core 2. 返回用户状态与角色三元信息(roleId/roleCode/roleName)
 * @core 3. 返回创建/更新时间用于治理页展示
 * @entry 先看：username、status、roleCode、roleName
 * @deps 关键依赖：UserRepository.userRowMapper、UserService.page/create/updateStatus/resetPassword
 * @state 关键字段：status、roleId、roleCode、roleName
 * @risk 高风险修改点：角色字段口径变更会影响用户管理页回显与鉴权角色映射理解
 * @link 相关文件：后端/src/main/java/com/wms/backend/user/repository/UserRepository.java、后端/src/main/java/com/wms/backend/user/controller/UserController.java
 */
package com.wms.backend.user.dto;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String username,
        String realName,
        String mobile,
        String email,
        Integer status,
        Long roleId,
        String roleCode,
        String roleName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
