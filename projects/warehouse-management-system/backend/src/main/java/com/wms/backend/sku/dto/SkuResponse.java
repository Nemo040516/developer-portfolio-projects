/**
 * @file 速览索引
 * @summary SKU 响应 DTO，返回商品主数据及启停状态用于列表与详情展示。
 * @core 1. 返回 skuCode/skuName 基础标识
 * @core 2. 返回规格、单位、安全库存与状态
 * @core 3. 返回创建/更新时间审计字段
 * @entry 先看：skuCode、skuName、safeStock、status
 * @deps 关键依赖：SkuRepository.skuRowMapper、SkuService.page/create/update
 * @state 关键字段：status(0停用/1启用)、safeStock、remark
 * @risk 高风险修改点：字段口径调整会联动 SKU 列表、出入库选择与补货计算输入
 * @link 相关文件：后端/src/main/java/com/wms/backend/sku/repository/SkuRepository.java、后端/src/main/java/com/wms/backend/sku/controller/SkuController.java
 */
package com.wms.backend.sku.dto;

import java.time.LocalDateTime;

public record SkuResponse(
        Long id,
        String skuCode,
        String skuName,
        String specification,
        String unit,
        Integer safeStock,
        Integer status,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
