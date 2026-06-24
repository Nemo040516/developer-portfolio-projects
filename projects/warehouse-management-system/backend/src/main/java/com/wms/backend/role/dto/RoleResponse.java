/**
 * @file 速览索引
 * @summary 角色分页响应 DTO，返回角色主数据与启停状态信息。
 * @core 1. 返回角色编码与名称
 * @core 2. 返回状态与备注
 * @core 3. 返回创建/更新时间用于审计展示
 * @entry 先看：roleCode、roleName、status
 * @deps 关键依赖：RoleRepository.roleRowMapper、RoleService.page/updateStatus
 * @state 关键字段：status(0停用/1启用)、remark
 * @risk 高风险修改点：状态字段或编码字段变更会联动角色治理页与鉴权语义
 * @link 相关文件：后端/src/main/java/com/wms/backend/role/repository/RoleRepository.java、后端/src/main/java/com/wms/backend/role/service/RoleService.java
 */
package com.wms.backend.role.dto;

import java.time.LocalDateTime;

public record RoleResponse(
        Long id,
        String roleCode,
        String roleName,
        Integer status,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
