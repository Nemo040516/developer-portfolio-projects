/**
 * @file 速览索引
 * @summary SKU 新增请求 DTO，承载商品主数据创建参数并提供字段校验。
 * @core 1. 约束 skuCode/skuName 必填与长度上限
 * @core 2. 约束规格、单位、备注长度
 * @core 3. 约束 safeStock 不得为负
 * @entry 先看：skuCode、skuName、safeStock
 * @deps 关键依赖：SkuController.create、SkuService.create、SkuRepository.insert
 * @state 关键字段：skuCode(max=64)、skuName(max=128)、safeStock(@Min(0))
 * @risk 高风险修改点：字段或注解变更会联动前端新增表单校验与唯一编码校验逻辑
 * @link 相关文件：后端/src/main/java/com/wms/backend/sku/service/SkuService.java、后端/src/main/java/com/wms/backend/sku/controller/SkuController.java
 */
package com.wms.backend.sku.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SkuCreateRequest(
        @NotBlank(message = "SKU编码不能为空")
        @Size(max = 64, message = "SKU编码长度不能超过64")
        String skuCode,
        @NotBlank(message = "SKU名称不能为空")
        @Size(max = 128, message = "SKU名称长度不能超过128")
        String skuName,
        @Size(max = 128, message = "规格长度不能超过128")
        String specification,
        @Size(max = 32, message = "单位长度不能超过32")
        String unit,
        @Min(value = 0, message = "安全库存不能为负数")
        Integer safeStock,
        @Size(max = 255, message = "备注长度不能超过255")
        String remark
) {
}
