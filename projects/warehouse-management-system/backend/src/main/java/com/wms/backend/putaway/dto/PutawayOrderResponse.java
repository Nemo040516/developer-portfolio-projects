/**
 * @file 速览索引
 * @summary 上架单列表响应 DTO，承载上架主单摘要字段供分页与详情头部展示。
 * @core 1. 返回上架单号、来源信息与仓库信息
 * @core 2. 返回状态字段支撑状态机展示
 * @core 3. 返回创建人与审计时间字段
 * @entry 先看：putawayNo、sourceType、warehouseName、status
 * @deps 关键依赖：PutawayRepository.orderRowMapper、PutawayService.page/detail
 * @state 关键字段：status(0草稿/1已提交/2已完成)、createdBy、updatedAt
 * @risk 高风险修改点：字段口径变化会影响上架列表筛选与详情拼装
 * @link 相关文件：后端/src/main/java/com/wms/backend/putaway/repository/PutawayRepository.java、后端/src/main/java/com/wms/backend/putaway/service/PutawayService.java
 */
package com.wms.backend.putaway.dto;

import java.time.LocalDateTime;

public record PutawayOrderResponse(
        Long id,
        String putawayNo,
        String sourceType,
        Long sourceOrderId,
        String sourceOrderNo,
        Long warehouseId,
        String warehouseName,
        Integer status,
        String remark,
        Long createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
