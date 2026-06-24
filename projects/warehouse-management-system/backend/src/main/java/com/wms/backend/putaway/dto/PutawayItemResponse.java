/**
 * @file 速览索引
 * @summary 上架明细响应 DTO，返回上架行的 SKU、库位与数量信息。
 * @core 1. 返回 skuCode/skuName 与 locationCode/areaName
 * @core 2. 返回 planQty/actualQty 支撑数量展示与确认
 * @core 3. 返回 remark 支撑作业备注回显
 * @entry 先看：skuCode、locationCode、planQty、actualQty
 * @deps 关键依赖：PutawayRepository.itemRowMapper、PutawayService.detail/submit/confirm
 * @state 关键字段：planQty、actualQty(为空或0时确认使用 planQty)
 * @risk 高风险修改点：数量字段语义变化会影响确认逻辑与库存流水计算
 * @link 相关文件：后端/src/main/java/com/wms/backend/putaway/repository/PutawayRepository.java、后端/src/main/java/com/wms/backend/putaway/service/PutawayService.java
 */
package com.wms.backend.putaway.dto;

public record PutawayItemResponse(
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
