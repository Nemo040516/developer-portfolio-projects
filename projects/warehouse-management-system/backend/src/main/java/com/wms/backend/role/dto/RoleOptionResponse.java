/**
 * @file 速览索引
 * @summary 角色下拉选项响应 DTO，用于前端用户管理页选择可用角色。
 * @core 1. 返回角色主键 id
 * @core 2. 返回角色编码 roleCode（鉴权语义标识）
 * @core 3. 返回角色名称 roleName（展示文案）
 * @entry 先看：roleCode、roleName
 * @deps 关键依赖：RoleRepository.listEnabledOptions、RoleService.options、RoleController.options
 * @state 关键规则：仅返回启用状态角色（repository 里 status=1）
 * @risk 高风险修改点：字段变更会影响用户创建/编辑页面角色下拉绑定
 * @link 相关文件：后端/src/main/java/com/wms/backend/role/repository/RoleRepository.java、后端/src/main/java/com/wms/backend/role/controller/RoleController.java
 */
package com.wms.backend.role.dto;

public record RoleOptionResponse(
        Long id,
        String roleCode,
        String roleName
) {
}
