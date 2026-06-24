/**
 * @file 速览索引
 * @summary 仓库新增请求 DTO，承载仓库主数据创建参数并执行字段校验。
 * @core 1. 约束 warehouseCode/warehouseName 必填与长度
 * @core 2. 约束地址、负责人与备注长度
 * @core 3. 约束联系电话手机号格式
 * @entry 先看：warehouseCode、warehouseName、contactPhone
 * @deps 关键依赖：WarehouseController.create、WarehouseService.create、WarehouseRepository.insert
 * @state 关键字段：warehouseCode(max=64)、warehouseName(max=128)、contactPhone(手机号正则)
 * @risk 高风险修改点：编码规则与电话格式调整会影响仓库新增校验与唯一性策略
 * @link 相关文件：后端/src/main/java/com/wms/backend/warehouse/service/WarehouseService.java、后端/src/main/java/com/wms/backend/warehouse/controller/WarehouseController.java
 */
package com.wms.backend.warehouse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record WarehouseCreateRequest(
        @NotBlank(message = "仓库编码不能为空")
        @Size(max = 64, message = "仓库编码长度不能超过64")
        String warehouseCode,
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
