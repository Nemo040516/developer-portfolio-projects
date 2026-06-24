/**
 * @file 速览索引
 * @summary 仓库编辑请求 DTO，承载仓库可更新字段并执行校验。
 * @core 1. 约束 warehouseName 必填与长度
 * @core 2. 约束地址、负责人与备注长度
 * @core 3. 约束联系电话手机号格式
 * @entry 先看：warehouseName、contactPhone、remark
 * @deps 关键依赖：WarehouseController.update、WarehouseService.update、WarehouseRepository.update
 * @state 关键字段：warehouseName(max=128)、contactPhone(手机号正则)、remark(max=255)
 * @risk 高风险修改点：更新字段集合变更会影响仓库编辑表单回填与落库口径
 * @link 相关文件：后端/src/main/java/com/wms/backend/warehouse/repository/WarehouseRepository.java、后端/src/main/java/com/wms/backend/warehouse/service/WarehouseService.java
 */
package com.wms.backend.warehouse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record WarehouseUpdateRequest(
        @NotBlank(message = "仓库名称不能为空")
        @Size(max = 128, message = "仓库名称长度不能超过128")
        String warehouseName,
        @Size(max = 255, message = "仓库地址长度不能超过255")
        String address,
        @Size(max = 64, message = "负责人长度不能超过64")
        String managerName,
        @Pattern(regexp = "^$|^1\\d{10}$", message = "联系电话格式不正确")
        String contactPhone,
        @Size(max = 255, message = "备注长度不能超过255")
        String remark
) {
}
