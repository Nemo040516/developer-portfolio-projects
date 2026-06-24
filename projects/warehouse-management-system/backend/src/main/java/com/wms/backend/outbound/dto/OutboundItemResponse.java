/**
 * @file 速览索引
 * @summary 出库明细响应 DTO，返回出库行的 SKU、库位与数量信息。
 * @core 1. 返回 SKU 编码与名称
 * @core 2. 返回库位编码与库区名称
 * @core 3. 返回计划/实出数量用于确认与回显
 * @entry 先看：skuCode、locationCode、planQty、actualQty
 * @deps 关键依赖：OutboundRepository.itemRowMapper、OutboundService.detail/confirm
 * @state 关键字段：locationId、skuId、planQty、actualQty、remark
 * @risk 高风险修改点：数量字段与库位字段调整会影响确认扣减流程与详情页面明细渲染
 * @link 相关文件：后端/src/main/java/com/wms/backend/outbound/repository/OutboundRepository.java、后端/src/main/java/com/wms/backend/outbound/dto/OutboundDetailResponse.java
 */
package com.wms.backend.outbound.dto;

public record OutboundItemResponse(
        Long id,
        Long skuId,
        String skuCode,
        String skuName,
        Long locationId,
        String locationCode,
        String areaName,
        Integer planQty,
        Integer actualQty,
        String remark
) {
}
