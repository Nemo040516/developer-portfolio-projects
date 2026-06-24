/**
 * @file 速览索引
 * @summary 供应商响应 DTO，返回供应商基础信息、状态与审计字段。
 * @core 1. 返回供应商编码、名称与联系人信息
 * @core 2. 返回交期与状态字段
 * @core 3. 返回创建/更新时间用于列表与详情展示
 * @entry 先看：supplierCode、supplierName、leadTimeDays、status
 * @deps 关键依赖：SupplierRepository.supplierRowMapper、SupplierService.page/create/update
 * @state 关键字段：status(0停用/1启用)、contactPhone、leadTimeDays
 * @risk 高风险修改点：字段口径变更会影响供应商列表、采购链路与补货推荐参数读取
 * @link 相关文件：后端/src/main/java/com/wms/backend/supplier/repository/SupplierRepository.java、后端/src/main/java/com/wms/backend/supplier/controller/SupplierController.java
 */
package com.wms.backend.supplier.dto;

import java.time.LocalDateTime;

public record SupplierResponse(
        Long id,
        String supplierCode,
        String supplierName,
        String contactName,
        String contactPhone,
        Integer leadTimeDays,
        Integer status,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
