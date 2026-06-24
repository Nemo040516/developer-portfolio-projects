/**
 * @file 速览索引
 * @summary 库位编辑请求 DTO，承载库位更新字段并提供参数校验。
 * @core 1. 承载仓库归属、库区、类型、容量、备注等可编辑字段
 * @core 2. 约束仓库必填与文本长度上限
 * @core 3. 约束容量不能为负数
 * @entry 先看：warehouseId、areaName、locationType、capacity
 * @deps 关键依赖：LocationController.update、LocationService.update、LocationRepository.update
 * @state 关键字段：warehouseId(@NotNull)、capacity(@DecimalMin(\"0\"))、remark(max=255)
 * @risk 高风险修改点：字段/注解变更会联动前端编辑表单与后端更新接口契约
 * @link 相关文件：后端/src/main/java/com/wms/backend/location/service/LocationService.java、后端/src/main/java/com/wms/backend/location/controller/LocationController.java
 */
package com.wms.backend.location.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record LocationUpdateRequest(
        @NotNull(message = "所属仓库不能为空")
        Long warehouseId,
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
