/**
 * @file 速览索引
 * @summary 供应商编辑请求 DTO，承载供应商可更新字段并执行校验。
 * @core 1. 约束 supplierName 必填与长度
 * @core 2. 约束联系人与手机号格式
 * @core 3. 约束交期 leadTimeDays 非负
 * @entry 先看：supplierName、contactPhone、leadTimeDays
 * @deps 关键依赖：SupplierController.update、SupplierService.update、SupplierRepository.update
 * @state 关键字段：supplierName(max=128)、contactPhone(手机号正则)、remark(max=255)
 * @risk 高风险修改点：联系方式或交期字段变更会影响采购参数维护与前端编辑回填
 * @link 相关文件：后端/src/main/java/com/wms/backend/supplier/repository/SupplierRepository.java、后端/src/main/java/com/wms/backend/supplier/service/SupplierService.java
 */
package com.wms.backend.supplier.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SupplierUpdateRequest(
        @NotBlank(message = "供应商名称不能为空")
        @Size(max = 128, message = "供应商名称长度不能超过128")
        String supplierName,
        @Size(max = 64, message = "联系人长度不能超过64")
        String contactName,
        @Pattern(regexp = "^$|^1\\d{10}$", message = "联系电话格式不正确")
        String contactPhone,
        @Min(value = 0, message = "交期不能为负数")
        Integer leadTimeDays,
        @Size(max = 255, message = "备注长度不能超过255")
        String remark
) {
}
