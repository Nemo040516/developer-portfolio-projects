/**
 * @file 速览索引
 * @summary 上架数据访问层，负责上架主单/明细查询写入、主数据校验与库位库存落位流水记录。
 * @core 1. 提供上架单分页、主单详情与明细查询
 * @core 2. 提供仓库/库位/SKU 可用性校验查询
 * @core 3. 提供上架单新增、更新、状态切换、删除与明细写入
 * @core 4. 提供总库存/已分配库存查询用于可上架量校验
 * @core 5. 提供库位库存 upsert 与库位流水写入
 * @entry 先看：pageByKeyword、findOrderById、insertOrder、insertItem、sumAllocatedQty、upsertLocationStock、insertLocationTxn
 * @deps 关键依赖：JdbcTemplate、PutawayOrderResponse、PutawayItemResponse、PutawayItemRequest
 * @state 关键数据：wms_putaway_order/wms_putaway_item/wms_location_stock/wms_inventory_stock/wms_location_txn
 * @risk 高风险修改点：可上架量计算口径(total-allocated)、状态更新SQL、流水字段一致性
 * @link 相关文件：后端/src/main/java/com/wms/backend/putaway/service/PutawayService.java、后端/src/main/java/com/wms/backend/putaway/controller/PutawayController.java
 */
package com.wms.backend.putaway.repository;

import com.wms.backend.putaway.dto.PutawayItemRequest;
import com.wms.backend.putaway.dto.PutawayItemResponse;
import com.wms.backend.putaway.dto.PutawayOrderResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class PutawayRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<PutawayOrderResponse> orderRowMapper = (rs, rowNum) ->
            new PutawayOrderResponse(
                    rs.getLong("id"),
                    rs.getString("putaway_no"),
                    rs.getString("source_type"),
                    rs.getObject("source_order_id", Long.class),
                    rs.getString("source_order_no"),
                    rs.getLong("warehouse_id"),
                    rs.getString("warehouse_name"),
                    rs.getInt("status"),
                    rs.getString("remark"),
                    rs.getObject("created_by", Long.class),
                    toLocalDateTime(rs.getTimestamp("created_at")),
                    toLocalDateTime(rs.getTimestamp("updated_at"))
            );

    private final RowMapper<PutawayItemResponse> itemRowMapper = (rs, rowNum) ->
            new PutawayItemResponse(
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

    public PutawayRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long countByKeyword(String keyword, Integer status) {
        String sql = """
                SELECT COUNT(1)
                FROM wms_putaway_order o
                JOIN wms_warehouse w ON o.warehouse_id = w.id
                WHERE (? = '' OR o.putaway_no LIKE ? OR o.source_order_no LIKE ? OR w.warehouse_name LIKE ?)
                  AND (? IS NULL OR o.status = ?)
                """;
        String like = "%" + keyword + "%";
        Long total = jdbcTemplate.queryForObject(sql, Long.class, keyword, like, like, like, status, status);
        return total == null ? 0L : total;
    }

    public List<PutawayOrderResponse> pageByKeyword(String keyword, Integer status, int offset, int size) {
        String sql = """
                SELECT o.id, o.putaway_no, o.source_type, o.source_order_id, o.source_order_no,
                       o.warehouse_id, w.warehouse_name, o.status, o.remark, o.created_by, o.created_at, o.updated_at
                FROM wms_putaway_order o
                JOIN wms_warehouse w ON o.warehouse_id = w.id
                WHERE (? = '' OR o.putaway_no LIKE ? OR o.source_order_no LIKE ? OR w.warehouse_name LIKE ?)
                  AND (? IS NULL OR o.status = ?)
                ORDER BY o.id DESC
                LIMIT ? OFFSET ?
                """;
        String like = "%" + keyword + "%";
        return jdbcTemplate.query(sql, orderRowMapper, keyword, like, like, like, status, status, size, offset);
    }

    public Optional<PutawayOrderResponse> findOrderById(Long id) {
        String sql = """
                SELECT o.id, o.putaway_no, o.source_type, o.source_order_id, o.source_order_no,
                       o.warehouse_id, w.warehouse_name, o.status, o.remark, o.created_by, o.created_at, o.updated_at
                FROM wms_putaway_order o
                JOIN wms_warehouse w ON o.warehouse_id = w.id
                WHERE o.id = ?
                """;
        return jdbcTemplate.query(sql, orderRowMapper, id).stream().findFirst();
    }

    public List<PutawayItemResponse> listItemsByOrderId(Long orderId) {
        String sql = """
                SELECT i.id, i.sku_id, s.sku_code, s.sku_name,
                       i.location_id, l.location_code, l.area_name,
                       i.plan_qty, i.actual_qty, i.remark
                FROM wms_putaway_item i
                JOIN wms_sku s ON i.sku_id = s.id
                JOIN wms_location l ON i.location_id = l.id
                WHERE i.putaway_order_id = ?
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

    public int insertOrder(String putawayNo, String sourceType, Long sourceOrderId, String sourceOrderNo,
                           Long warehouseId, String remark, Long createdBy) {
        String sql = """
                INSERT INTO wms_putaway_order
                (putaway_no, source_type, source_order_id, source_order_no, warehouse_id, status, remark, created_by)
                VALUES (?, ?, ?, ?, ?, 0, ?, ?)
                """;
        return jdbcTemplate.update(sql, putawayNo, sourceType, sourceOrderId, sourceOrderNo, warehouseId, remark, createdBy);
    }

    public Optional<Long> findOrderIdByPutawayNo(String putawayNo) {
        String sql = "SELECT id FROM wms_putaway_order WHERE putaway_no = ?";
        List<Long> list = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("id"), putawayNo);
        return list.stream().findFirst();
    }

    public Optional<String> findMaxPutawayNoByPrefix(String prefix) {
        String sql = """
                SELECT putaway_no
                FROM wms_putaway_order
                WHERE putaway_no LIKE CONCAT(?, '%')
                ORDER BY putaway_no DESC
                LIMIT 1
                """;
        List<String> list = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("putaway_no"), prefix);
        return list.stream().findFirst();
    }

    public int updateOrder(Long id, String sourceType, Long sourceOrderId, String sourceOrderNo, Long warehouseId, String remark) {
        String sql = """
                UPDATE wms_putaway_order
                SET source_type = ?, source_order_id = ?, source_order_no = ?, warehouse_id = ?, remark = ?
                WHERE id = ?
                """;
        return jdbcTemplate.update(sql, sourceType, sourceOrderId, sourceOrderNo, warehouseId, remark, id);
    }

    public int updateStatus(Long id, int status) {
        String sql = "UPDATE wms_putaway_order SET status = ? WHERE id = ?";
        return jdbcTemplate.update(sql, status, id);
    }

    public int deleteItemsByOrderId(Long orderId) {
        String sql = "DELETE FROM wms_putaway_item WHERE putaway_order_id = ?";
        return jdbcTemplate.update(sql, orderId);
    }

    public int deleteOrderById(Long orderId) {
        String sql = "DELETE FROM wms_putaway_order WHERE id = ?";
        return jdbcTemplate.update(sql, orderId);
    }

    public int insertItem(Long orderId, PutawayItemRequest item) {
        String sql = """
                INSERT INTO wms_putaway_item
                (putaway_order_id, sku_id, location_id, plan_qty, actual_qty, remark)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        Integer actualQty = item.actualQty() == null ? 0 : item.actualQty();
        return jdbcTemplate.update(sql, orderId, item.skuId(), item.locationId(), item.planQty(), actualQty, item.remark());
    }

    public Optional<Integer> findTotalStockQty(Long warehouseId, Long skuId) {
        String sql = "SELECT on_hand_qty FROM wms_inventory_stock WHERE warehouse_id = ? AND sku_id = ?";
        List<Integer> list = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getInt("on_hand_qty"), warehouseId, skuId);
        return list.stream().findFirst();
    }

    public int sumAllocatedQty(Long warehouseId, Long skuId) {
        String sql = "SELECT COALESCE(SUM(on_hand_qty), 0) FROM wms_location_stock WHERE warehouse_id = ? AND sku_id = ?";
        Integer qty = jdbcTemplate.queryForObject(sql, Integer.class, warehouseId, skuId);
        return qty == null ? 0 : qty;
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

    public int updateActualQty(Long itemId, int actualQty) {
        String sql = "UPDATE wms_putaway_item SET actual_qty = ? WHERE id = ?";
        return jdbcTemplate.update(sql, actualQty, itemId);
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

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
