/**
 * @file 速览索引
 * @summary 库位状态变更请求 DTO，用于启停状态更新入参校验。
 * @core 1. 承载状态字段 status
 * @core 2. 约束状态必填
 * @core 3. 约束状态取值仅允许 0/1
 * @entry 先看：status
 * @deps 关键依赖：LocationController.updateStatus、LocationService.updateStatus
 * @state 关键字段：status(@Min(0) @Max(1))
 * @risk 高风险修改点：状态取值范围变更会联动服务层业务校验与前端状态切换逻辑
 * @link 相关文件：后端/src/main/java/com/wms/backend/location/controller/LocationController.java、后端/src/main/java/com/wms/backend/location/service/LocationService.java
 */
package com.wms.backend.location.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record LocationStatusUpdateRequest(
        @NotNull(message = "状态不能为空")
        @Min(value = 0, message = "状态值不合法")
        @Max(value = 1, message = "状态值不合法")
        Integer status
) {
}
