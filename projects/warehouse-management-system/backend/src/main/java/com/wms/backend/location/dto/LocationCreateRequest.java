/**
 * @file 速览索引
 * @summary 库位新增请求 DTO，承载创建库位所需字段并提供注解校验。
 * @core 1. 承载 warehouseId/locationCode 等新增字段
 * @core 2. 约束库位编码、库区、类型、备注长度
 * @core 3. 约束容量 capacity 不能为负数
 * @entry 先看：warehouseId、locationCode、capacity
 * @deps 关键依赖：LocationController.create、LocationService.create、LocationRepository.insert
 * @state 关键字段：locationCode(max=64)、capacity(@DecimalMin(\"0\"))、remark(max=255)
 * @risk 高风险修改点：字段或注解变更会联动前端表单校验与库位新增接口契约
 * @link 相关文件：后端/src/main/java/com/wms/backend/location/service/LocationService.java、后端/src/main/java/com/wms/backend/location/controller/LocationController.java
 */
package com.wms.backend.location.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record LocationCreateRequest(
        @NotNull(message = "所属仓库不能为空")
        Long warehouseId,
        @NotBlank(message = "库位编码不能为空")
        @Size(max = 64, message = "库位编码长度不能超过64")
        String locationCode,
        @Size(max = 64, message = "库区名称长度不能超过64")
        String areaName,
        @Size(max = 32, message = "库位类型长度不能超过32")
        String locationType,
        @DecimalMin(value = "0", message = "容量上限不能为负数")
        BigDecimal capacity,
        @Size(max = 255, message = "备注长度不能超过255")
        String remark
) {
}
