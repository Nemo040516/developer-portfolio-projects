/**
 * @file 速览索引
 * @summary 出库数据访问层，负责出库主单/明细查询写入、库存扣减落库与流水记录。
 * @core 1. 提供出库单分页、详情主表与明细查询
 * @core 2. 提供主数据有效性校验（仓库/库位/SKU）
 * @core 3. 提供出库单新增、更新、状态切换、删除与明细写入
 * @core 4. 提供库位库存/总库存锁定、更新与流水写入
 * @core 5. 提供可用库位库存查询给出库分配
 * @entry 先看：pageByKeyword、findOrderById、insertOrder、insertItem、lockLocationStockQty、lockInventoryStockQty、listAvailableLocationStocks
 * @deps 关键依赖：JdbcTemplate、OutboundItemRequest、OutboundOrderResponse、OutboundItemResponse、OutboundAvailableStockResponse
 * @state 关键数据：wms_outbound_order/wms_outbound_item/wms_location_stock/wms_inventory_stock/wms_location_txn/wms_inventory_txn
 * @risk 高风险修改点：FOR UPDATE 加锁口径、出库流水字段一致性、可用库存查询排序与过滤条件
 * @link 相关文件：后端/src/main/java/com/wms/backend/outbound/service/OutboundService.java、后端/src/main/java/com/wms/backend/outbound/controller/OutboundController.java
 */
package com.wms.backend.outbound.repository;

import com.wms.backend.outbound.dto.OutboundItemRequest;
import com.wms.backend.outbound.dto.OutboundItemResponse;
import com.wms.backend.outbound.dto.OutboundOrderResponse;
import com.wms.backend.outbound.dto.OutboundAvailableStockResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class OutboundRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<OutboundOrderResponse> orderRowMapper = (rs, rowNum) ->
            new OutboundOrderResponse(
                    rs.getLong("id"),
                    rs.getString("outbound_no"),
                    rs.getString("outbound_type"),
                    rs.getString("target_name"),
                    rs.getLong("warehouse_id"),
                    rs.getString("warehouse_name"),
                    rs.getInt("status"),
                    rs.getString("remark"),
                    rs.getObject("created_by", Long.class),
                    toLocalDateTime(rs.getTimestamp("created_at")),
                    toLocalDateTime(rs.getTimestamp("updated_at"))
            );

    private final RowMapper<OutboundItemResponse> itemRowMapper = (rs, rowNum) ->
            new OutboundItemResponse(
                    rs.getLong("id"),
                    rs.getLong("sku_id"),
                    rs.getString("sku_code"),
                    rs.getString("sku_name"),
                    rs.getLong("location_id"),
                    rs.getString("location_code"),
                    rs.getString("area_name"),
                    rs.getInt("plan_qty"),
                    rs.getInt("actual_qty"),
                    rs.getString("remark")
            );

    private final RowMapper<OutboundAvailableStockResponse> availableStockRowMapper = (rs, rowNum) ->
            new OutboundAvailableStockResponse(
                    rs.getLong("warehouse_id"),
                    rs.getLong("location_id"),
                    rs.getString("location_code"),
                    rs.getString("area_name"),
                    rs.getLong("sku_id"),
                    rs.getString("sku_code"),
                    rs.getString("sku_name"),
                    rs.getInt("on_hand_qty")
            );

    public OutboundRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long countByKeyword(String keyword, Integer status) {
        String sql = """
                SELECT COUNT(1)
                FROM wms_outbound_order o
                JOIN wms_warehouse w ON o.warehouse_id = w.id
                WHERE (? = '' OR o.outbound_no LIKE ? OR o.target_name LIKE ? OR w.warehouse_name LIKE ?)
                  AND (? IS NULL OR o.status = ?)
                """;
        String like = "%" + keyword + "%";
        Long total = jdbcTemplate.queryForObject(sql, Long.class, keyword, like, like, like, status, status);
        return total == null ? 0L : total;
    }

    public List<OutboundOrderResponse> pageByKeyword(String keyword, Integer status, int offset, int size) {
        String sql = """
                SELECT o.id, o.outbound_no, o.outbound_type, o.target_name,
                       o.warehouse_id, w.warehouse_name, o.status, o.remark, o.created_by, o.created_at, o.updated_at
                FROM wms_outbound_order o
                JOIN wms_warehouse w ON o.warehouse_id = w.id
                WHERE (? = '' OR o.outbound_no LIKE ? OR o.target_name LIKE ? OR w.warehouse_name LIKE ?)
                  AND (? IS NULL OR o.status = ?)
                ORDER BY o.id DESC
                LIMIT ? OFFSET ?
                """;
        String like = "%" + keyword + "%";
        return jdbcTemplate.query(sql, orderRowMapper, keyword, like, like, like, status, status, size, offset);
    }

    public Optional<OutboundOrderResponse> findOrderById(Long id) {
        String sql = """
                SELECT o.id, o.outbound_no, o.outbound_type, o.target_name,
                       o.warehouse_id, w.warehouse_name, o.status, o.remark, o.created_by, o.created_at, o.updated_at
                FROM wms_outbound_order o
                JOIN wms_warehouse w ON o.warehouse_id = w.id
                WHERE o.id = ?
                """;
        return jdbcTemplate.query(sql, orderRowMapper, id).stream().findFirst();
    }

    public List<OutboundItemResponse> listItemsByOrderId(Long orderId) {
        String sql = """
                SELECT i.id, i.sku_id, s.sku_code, s.sku_name,
                       i.location_id, l.location_code, l.area_name,
                       i.plan_qty, i.actual_qty, i.remark
                FROM wms_outbound_item i
                JOIN wms_sku s ON i.sku_id = s.id
                JOIN wms_location l ON i.location_id = l.id
                WHERE i.outbound_order_id = ?
                ORDER BY i.id ASC
                """;
        return jdbcTemplate.query(sql, itemRowMapper, orderId);
    }

    public boolean existsWarehouseActive(Long warehouseId) {
        String sql = "SELECT COUNT(1) FROM wms_warehouse WHERE id = ? AND status = 1";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, warehouseId);
        return count != null && count > 0;
    }

    public boolean existsLocationActive(Long locationId) {
        String sql = "SELECT COUNT(1) FROM wms_location WHERE id = ? AND status = 1";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, locationId);
        return count != null && count > 0;
    }

    public boolean existsLocationInWarehouse(Long locationId, Long warehouseId) {
        String sql = "SELECT COUNT(1) FROM wms_location WHERE id = ? AND warehouse_id = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, locationId, warehouseId);
        return count != null && count > 0;
    }

    public boolean existsSkuActive(Long skuId) {
        String sql = "SELECT COUNT(1) FROM wms_sku WHERE id = ? AND status = 1";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, skuId);
        return count != null && count > 0;
    }

    public Optional<Long> findUserIdByUsername(String username) {
        String sql = "SELECT id FROM sys_user WHERE username = ?";
        List<Long> list = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("id"), username);
        return list.stream().findFirst();
    }

    public int insertOrder(String outboundNo, String outboundType, String targetName, Long warehouseId, String remark, Long createdBy) {
        String sql = """
                INSERT INTO wms_outbound_order
                (outbound_no, outbound_type, target_name, warehouse_id, status, remark, created_by)
                VALUES (?, ?, ?, ?, 0, ?, ?)
                """;
        return jdbcTemplate.update(sql, outboundNo, outboundType, targetName, warehouseId, remark, createdBy);
    }

    public Optional<Long> findOrderIdByOutboundNo(String outboundNo) {
        String sql = "SELECT id FROM wms_outbound_order WHERE outbound_no = ?";
        List<Long> list = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("id"), outboundNo);
        return list.stream().findFirst();
    }

    public Optional<String> findMaxOutboundNoByPrefix(String prefix) {
        String sql = """
                SELECT outbound_no
                FROM wms_outbound_order
                WHERE outbound_no LIKE CONCAT(?, '%')
                ORDER BY outbound_no DESC
                LIMIT 1
                """;
        List<String> list = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("outbound_no"), prefix);
        return list.stream().findFirst();
    }

    public int updateOrder(Long id, String outboundType, String targetName, Long warehouseId, String remark) {
        String sql = """
                UPDATE wms_outbound_order
                SET outbound_type = ?, target_name = ?, warehouse_id = ?, remark = ?
                WHERE id = ?
                """;
        return jdbcTemplate.update(sql, outboundType, targetName, warehouseId, remark, id);
    }

    public int updateStatus(Long id, int status) {
        String sql = "UPDATE wms_outbound_order SET status = ? WHERE id = ?";
        return jdbcTemplate.update(sql, status, id);
    }

    public int deleteItemsByOrderId(Long orderId) {
        String sql = "DELETE FROM wms_outbound_item WHERE outbound_order_id = ?";
        return jdbcTemplate.update(sql, orderId);
    }

    public int deleteOrderById(Long orderId) {
        String sql = "DELETE FROM wms_outbound_order WHERE id = ?";
        return jdbcTemplate.update(sql, orderId);
    }

    public int insertItem(Long orderId, OutboundItemRequest item) {
        String sql = """
                INSERT INTO wms_outbound_item
                (outbound_order_id, sku_id, location_id, plan_qty, actual_qty, remark)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        Integer actualQty = item.actualQty() == null ? 0 : item.actualQty();
        return jdbcTemplate.update(sql, orderId, item.skuId(), item.locationId(), item.planQty(), actualQty, item.remark());
    }

    public int insertLocationTxn(
            String bizType,
            String bizNo,
            Long bizId,
            Long warehouseId,
            Long locationId,
            Long skuId,
            int qtyChange,
            int beforeQty,
            int afterQty,
            Long operatorId,
            String operatorName,
            String remark
    ) {
        String sql = """
                INSERT INTO wms_location_txn
                (biz_type, biz_no, biz_id, warehouse_id, location_id, sku_id, qty_change, before_qty, after_qty, operator_id, operator_name, remark)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        return jdbcTemplate.update(
                sql, bizType, bizNo, bizId, warehouseId, locationId, skuId, qtyChange, beforeQty, afterQty, operatorId, operatorName, remark
        );
    }

    public Optional<Integer> findLocationStockQty(Long warehouseId, Long locationId, Long skuId) {
        String sql = """
                SELECT on_hand_qty
                FROM wms_location_stock
                WHERE warehouse_id = ? AND location_id = ? AND sku_id = ?
                """;
        List<Integer> list = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getInt("on_hand_qty"), warehouseId, locationId, skuId);
        return list.stream().findFirst();
    }

    public int upsertLocationStock(Long warehouseId, Long locationId, Long skuId, int onHandQty) {
        String sql = """
                INSERT INTO wms_location_stock (warehouse_id, location_id, sku_id, on_hand_qty)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE on_hand_qty = VALUES(on_hand_qty)
                """;
        return jdbcTemplate.update(sql, warehouseId, locationId, skuId, onHandQty);
    }

    public Optional<Integer> findInventoryStockQty(Long warehouseId, Long skuId) {
        String sql = """
                SELECT on_hand_qty
                FROM wms_inventory_stock
                WHERE warehouse_id = ? AND sku_id = ?
                """;
        List<Integer> list = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getInt("on_hand_qty"), warehouseId, skuId);
        return list.stream().findFirst();
    }

    public int upsertInventoryStock(Long warehouseId, Long skuId, int onHandQty) {
        String sql = """
                INSERT INTO wms_inventory_stock (warehouse_id, sku_id, on_hand_qty)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE on_hand_qty = VALUES(on_hand_qty)
                """;
        return jdbcTemplate.update(sql, warehouseId, skuId, onHandQty);
    }

    public int insertInventoryTxn(
            String bizType,
            String bizNo,
            Long bizId,
            Long warehouseId,
            Long skuId,
            int qtyChange,
            int beforeQty,
            int afterQty,
            Long operatorId,
            String operatorName,
            String remark
    ) {
        String sql = """
                INSERT INTO wms_inventory_txn
                (biz_type, biz_no, biz_id, warehouse_id, sku_id, qty_change, before_qty, after_qty, operator_id, operator_name, remark)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        return jdbcTemplate.update(
                sql, bizType, bizNo, bizId, warehouseId, skuId, qtyChange, beforeQty, afterQty, operatorId, operatorName, remark
        );
    }

    public int updateActualQty(Long itemId, int actualQty) {
        String sql = "UPDATE wms_outbound_item SET actual_qty = ? WHERE id = ?";
        return jdbcTemplate.update(sql, actualQty, itemId);
    }

    public Optional<Integer> lockLocationStockQty(Long warehouseId, Long locationId, Long skuId) {
        String sql = """
                SELECT on_hand_qty
                FROM wms_location_stock
                WHERE warehouse_id = ? AND location_id = ? AND sku_id = ?
                FOR UPDATE
                """;
        List<Integer> list = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getInt("on_hand_qty"), warehouseId, locationId, skuId);
        return list.stream().findFirst();
    }

    public Optional<Integer> lockInventoryStockQty(Long warehouseId, Long skuId) {
        String sql = """
                SELECT on_hand_qty
                FROM wms_inventory_stock
                WHERE warehouse_id = ? AND sku_id = ?
                FOR UPDATE
                """;
        List<Integer> list = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getInt("on_hand_qty"), warehouseId, skuId);
        return list.stream().findFirst();
    }

    public List<OutboundAvailableStockResponse> listAvailableLocationStocks(Long warehouseId, Long skuId, String keyword, int limit) {
        String sql = """
                SELECT ls.warehouse_id, ls.location_id, l.location_code, l.area_name,
                       ls.sku_id, s.sku_code, s.sku_name, ls.on_hand_qty
                FROM wms_location_stock ls
                JOIN wms_location l ON ls.location_id = l.id
                JOIN wms_sku s ON ls.sku_id = s.id
                WHERE ls.warehouse_id = ?
                  AND ls.sku_id = ?
                  AND ls.on_hand_qty > 0
                  AND (? = '' OR l.location_code LIKE ? OR l.area_name LIKE ? OR s.sku_code LIKE ? OR s.sku_name LIKE ?)
                ORDER BY ls.on_hand_qty DESC, l.location_code ASC
                LIMIT ?
                """;
        String safeKeyword = keyword == null ? "" : keyword.trim();
        String like = "%" + safeKeyword + "%";
        return jdbcTemplate.query(sql, availableStockRowMapper, warehouseId, skuId, safeKeyword, like, like, like, like, limit);
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
