/**
 * @file 速览索引
 * @summary SKU 编辑请求 DTO，承载商品更新字段并执行字段约束。
 * @core 1. 约束 skuName 必填与长度上限
 * @core 2. 约束规格、单位、备注长度
 * @core 3. 约束 safeStock 不得为负
 * @entry 先看：skuName、specification、unit、safeStock
 * @deps 关键依赖：SkuController.update、SkuService.update、SkuRepository.update
 * @state 关键字段：skuName(max=128)、safeStock(@Min(0))、remark(max=255)
 * @risk 高风险修改点：字段语义变更会影响编辑表单回填与更新 SQL 写入口径
 * @link 相关文件：后端/src/main/java/com/wms/backend/sku/repository/SkuRepository.java、后端/src/main/java/com/wms/backend/sku/service/SkuService.java
 */
package com.wms.backend.sku.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SkuUpdateRequest(
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
