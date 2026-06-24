/**
 * @file 速览索引
 * @summary 供应商新增请求 DTO，承载供应商主数据创建参数并执行字段校验。
 * @core 1. 约束 supplierCode/supplierName 必填与长度
 * @core 2. 约束联系人与手机号格式
 * @core 3. 约束交期 leadTimeDays 非负
 * @entry 先看：supplierCode、supplierName、contactPhone、leadTimeDays
 * @deps 关键依赖：SupplierController.create、SupplierService.create、SupplierRepository.insert
 * @state 关键字段：supplierCode(max=64)、contactPhone(手机号正则)、leadTimeDays(@Min(0))
 * @risk 高风险修改点：编码与手机号校验规则变化会影响新增表单校验与唯一性策略
 * @link 相关文件：后端/src/main/java/com/wms/backend/supplier/service/SupplierService.java、后端/src/main/java/com/wms/backend/supplier/controller/SupplierController.java
 */
package com.wms.backend.supplier.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SupplierCreateRequest(
        @NotBlank(message = "供应商编码不能为空")
        @Size(max = 64, message = "供应商编码长度不能超过64")
        String supplierCode,
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
