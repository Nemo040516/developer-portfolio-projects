/**
 * @file 速览索引
 * @summary 角色状态变更请求 DTO，限制角色启停状态入参范围。
 * @core 1. 承载状态字段 status
 * @core 2. 约束状态必填
 * @core 3. 约束状态仅允许 0/1
 * @entry 先看：status
 * @deps 关键依赖：RoleController.updateStatus、RoleService.updateStatus
 * @state 关键字段：status(@Min(0) @Max(1))
 * @risk 高风险修改点：取值范围变更会联动 ADMIN 保护规则与角色启停接口行为
 * @link 相关文件：后端/src/main/java/com/wms/backend/role/service/RoleService.java、后端/src/main/java/com/wms/backend/role/controller/RoleController.java
 */
package com.wms.backend.role.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RoleStatusUpdateRequest(
        @NotNull(message = "状态不能为空")
        @Min(value = 0, message = "状态值不合法")
        @Max(value = 1, message = "状态值不合法")
        Integer status
) {
}
