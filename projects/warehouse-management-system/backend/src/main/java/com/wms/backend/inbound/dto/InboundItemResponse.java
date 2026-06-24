/**
 * @file 速览索引
 * @summary 入库明细响应DTO，承载单条明细展示字段（SKU信息、数量、备注）。
 * @core 1. 返回明细主键与SKU标识
 * @core 2. 返回 SKU 编码与名称
 * @core 3. 返回计划/实收数量与备注
 * @entry 先看：skuCode、skuName、planQty、receivedQty
 * @deps 关键依赖：InboundDetailResponse、InboundService
 * @risk 高风险修改点：数量字段语义（计划量 vs 实收量）与前端列映射
 * @link 相关文件：后端/src/main/java/com/wms/backend/inbound/dto/InboundDetailResponse.java
 */
package com.wms.backend.inbound.dto;

public record InboundItemResponse(
        Long id,
        Long skuId,
        String skuCode,
        String skuName,
        Integer planQty,
        Integer receivedQty,
        String remark
) {
}
