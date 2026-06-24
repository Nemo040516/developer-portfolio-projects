/**
 * @file 速览索引
 * @summary 入库数据访问层，负责入库单/明细查询写入、状态更新与库存流水落库。
 * @core 1. 提供入库单分页、详情与明细查询
 * @core 2. 提供供应商/仓库/SKU 可用性校验查询
 * @core 3. 提供入库单主表与明细表新增、更新、删除
 * @core 4. 在提交/确认场景写入库存台账与库存流水
 * @entry 先看：pageByKeyword、insertOrder、updateStatus、upsertStock、insertInventoryTxn
 * @deps 关键依赖：JdbcTemplate、InboundItemRequest、InboundOrderResponse、InboundItemResponse
 * @state 关键数据：orderRowMapper、itemRowMapper、wms_inbound_order/wms_inbound_item/wms_inventory_* 表
 * @risk 高风险修改点：状态过滤 SQL、库存增量计算相关 SQL、单据号查询逻辑
 * @link 相关文件：后端/src/main/java/com/wms/backend/inbound/service/InboundService.java、后端/src/main/resources/schema.sql
 */
package com.wms.backend.inbound.repository;

import com.wms.backend.inbound.dto.InboundItemRequest;
import com.wms.backend.inbound.dto.InboundItemResponse;
import com.wms.backend.inbound.dto.InboundOrderResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class InboundRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<InboundOrderResponse> orderRowMapper = (rs, rowNum) ->
            new InboundOrderResponse(
                    rs.getLong("id"),
                    rs.getString("inbound_no"),
                    rs.getLong("supplier_id"),
                    rs.getString("supplier_name"),
                    rs.getLong("warehouse_id"),
                    rs.getString("warehouse_name"),
                    rs.getInt("status"),
                    rs.getString("remark"),
                    rs.getObject("created_by", Long.class),
                    toLocalDateTime(rs.getTimestamp("created_at")),
                    toLocalDateTime(rs.getTimestamp("updated_at"))
            );

    private final RowMapper<InboundItemResponse> itemRowMapper = (rs, rowNum) ->
            new InboundItemResponse(
                    rs.getLong("id"),
                    rs.getLong("sku_id"),
                    rs.getString("sku_code"),
                    rs.getString("sku_name"),
                    rs.getInt("plan_qty"),
                    rs.getInt("received_qty"),
                    rs.getString("remark")
            );

    public InboundRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long countByKeyword(String keyword, Integer status) {
        String sql = """
                SELECT COUNT(1)
                FROM wms_inbound_order o
                JOIN wms_supplier s ON o.supplier_id = s.id
                JOIN wms_warehouse w ON o.warehouse_id = w.id
                WHERE (? = '' OR o.inbound_no LIKE ? OR s.supplier_name LIKE ? OR w.warehouse_name LIKE ?)
                  AND (? IS NULL OR o.status = ?)
                """;
        String like = "%" + keyword + "%";
        Long total = jdbcTemplate.queryForObject(sql, Long.class, keyword, like, like, like, status, status);
        return total == null ? 0L : total;
    }

    public List<InboundOrderResponse> pageByKeyword(String keyword, Integer status, int offset, int size) {
        String sql = """
                SELECT o.id, o.inbound_no, o.supplier_id, s.supplier_name, o.warehouse_id, w.warehouse_name,
                       o.status, o.remark, o.created_by, o.created_at, o.updated_at
                FROM wms_inbound_order o
                JOIN wms_supplier s ON o.supplier_id = s.id
                JOIN wms_warehouse w ON o.warehouse_id = w.id
                WHERE (? = '' OR o.inbound_no LIKE ? OR s.supplier_name LIKE ? OR w.warehouse_name LIKE ?)
                  AND (? IS NULL OR o.status = ?)
                ORDER BY o.id DESC
                LIMIT ? OFFSET ?
                """;
        String like = "%" + keyword + "%";
        return jdbcTemplate.query(sql, orderRowMapper, keyword, like, like, like, status, status, size, offset);
    }

    public Optional<InboundOrderResponse> findOrderById(Long id) {
        String sql = """
                SELECT o.id, o.inbound_no, o.supplier_id, s.supplier_name, o.warehouse_id, w.warehouse_name,
                       o.status, o.remark, o.created_by, o.created_at, o.updated_at
                FROM wms_inbound_order o
                JOIN wms_supplier s ON o.supplier_id = s.id
                JOIN wms_warehouse w ON o.warehouse_id = w.id
                WHERE o.id = ?
                """;
        return jdbcTemplate.query(sql, orderRowMapper, id).stream().findFirst();
    }

    public List<InboundItemResponse> listItemsByOrderId(Long orderId) {
        String sql = """
                SELECT i.id, i.sku_id, k.sku_code, k.sku_name, i.plan_qty, i.received_qty, i.remark
                FROM wms_inbound_item i
                JOIN wms_sku k ON i.sku_id = k.id
                WHERE i.inbound_order_id = ?
                ORDER BY i.id ASC
                """;
        return jdbcTemplate.query(sql, itemRowMapper, orderId);
    }

    public boolean existsSupplierActive(Long supplierId) {
        String sql = "SELECT COUNT(1) FROM wms_supplier WHERE id = ? AND status = 1";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, supplierId);
        return count != null && count > 0;
    }

    public boolean existsWarehouseActive(Long warehouseId) {
        String sql = "SELECT COUNT(1) FROM wms_warehouse WHERE id = ? AND status = 1";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, warehouseId);
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

    public int insertOrder(String inboundNo, Long supplierId, Long warehouseId, String remark, Long createdBy) {
        String sql = """
                INSERT INTO wms_inbound_order
                (inbound_no, supplier_id, warehouse_id, status, remark, created_by)
                VALUES (?, ?, ?, 0, ?, ?)
                """;
        return jdbcTemplate.update(sql, inboundNo, supplierId, warehouseId, remark, createdBy);
    }

    public Optional<Long> findOrderIdByInboundNo(String inboundNo) {
        String sql = "SELECT id FROM wms_inbound_order WHERE inbound_no = ?";
        List<Long> list = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("id"), inboundNo);
        return list.stream().findFirst();
    }

    public Optional<String> findMaxInboundNoByPrefix(String prefix) {
        String sql = """
                SELECT inbound_no
                FROM wms_inbound_order
                WHERE inbound_no LIKE CONCAT(?, '%')
                ORDER BY inbound_no DESC
                LIMIT 1
                """;
        List<String> list = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("inbound_no"), prefix);
        return list.stream().findFirst();
    }

    public int updateOrder(Long id, Long supplierId, Long warehouseId, String remark) {
        String sql = """
                UPDATE wms_inbound_order
                SET supplier_id = ?, warehouse_id = ?, remark = ?
                WHERE id = ?
                """;
        return jdbcTemplate.update(sql, supplierId, warehouseId, remark, id);
    }

    public int updateStatus(Long id, int status) {
        String sql = "UPDATE wms_inbound_order SET status = ? WHERE id = ?";
        return jdbcTemplate.update(sql, status, id);
    }

    public int deleteItemsByOrderId(Long orderId) {
        String sql = "DELETE FROM wms_inbound_item WHERE inbound_order_id = ?";
        return jdbcTemplate.update(sql, orderId);
    }

    public int deleteOrderById(Long orderId) {
        String sql = "DELETE FROM wms_inbound_order WHERE id = ?";
        return jdbcTemplate.update(sql, orderId);
    }

    public int insertItem(Long orderId, InboundItemRequest item) {
        String sql = """
                INSERT INTO wms_inbound_item
                (inbound_order_id, sku_id, plan_qty, received_qty, remark)
                VALUES (?, ?, ?, ?, ?)
                """;
        Integer receivedQty = item.receivedQty() == null ? 0 : item.receivedQty();
        return jdbcTemplate.update(sql, orderId, item.skuId(), item.planQty(), receivedQty, item.remark());
    }

    public Optional<Integer> findStockQty(Long warehouseId, Long skuId) {
        String sql = "SELECT on_hand_qty FROM wms_inventory_stock WHERE warehouse_id = ? AND sku_id = ?";
        List<Integer> list = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getInt("on_hand_qty"), warehouseId, skuId);
        return list.stream().findFirst();
    }

    public int upsertStock(Long warehouseId, Long skuId, int onHandQty) {
        String sql = """
                INSERT INTO wms_inventory_stock (warehouse_id, sku_id, on_hand_qty)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE on_hand_qty = VALUES(on_hand_qty)
                """;
        return jdbcTemplate.update(sql, warehouseId, skuId, onHandQty);
    }

    public int updateReceivedQty(Long itemId, int receivedQty) {
        String sql = "UPDATE wms_inbound_item SET received_qty = ? WHERE id = ?";
        return jdbcTemplate.update(sql, receivedQty, itemId);
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
        return jdbcTemplate.update(sql, bizType, bizNo, bizId, warehouseId, skuId, qtyChange, beforeQty, afterQty, operatorId, operatorName, remark);
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
