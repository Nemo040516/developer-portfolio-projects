/**
 * @file 速览索引
 * @summary 盘点数据访问层，负责盘点单/明细查询写入、账面库存读取、确认时库存修正与流水落库。
 * @core 1. 提供盘点单分页、详情与明细查询
 * @core 2. 提供仓库/SKU/库位可用性校验与归属校验
 * @core 3. 提供盘点单主表明细新增、更新、删除与状态流转写入
 * @core 4. 提供锁库存、回写库存与库存/库位流水写入能力
 * @core 5. 提供盘点建单账面库存查询 listBookStocks
 * @entry 先看：pageByKeyword、insertItem、lockLocationStockQty、upsertInventoryStock、listBookStocks
 * @deps 关键依赖：JdbcTemplate、Stocktake DTO RowMapper、wms_stocktake_* 与 wms_*_stock/wms_*_txn 表
 * @state 关键数据：orderRowMapper、itemRowMapper、bookStockRowMapper、diff_qty 计算
 * @risk 高风险修改点：差异数量计算口径、FOR UPDATE 锁库存时机、库存回写与流水一致性
 * @link 相关文件：后端/src/main/java/com/wms/backend/stocktake/service/StocktakeService.java、后端/src/main/java/com/wms/backend/stocktake/controller/StocktakeController.java
 */
package com.wms.backend.stocktake.repository;

import com.wms.backend.stocktake.dto.StocktakeBookStockResponse;
import com.wms.backend.stocktake.dto.StocktakeItemRequest;
import com.wms.backend.stocktake.dto.StocktakeItemResponse;
import com.wms.backend.stocktake.dto.StocktakeOrderResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class StocktakeRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<StocktakeOrderResponse> orderRowMapper = (rs, rowNum) ->
            new StocktakeOrderResponse(
                    rs.getLong("id"),
                    rs.getString("stocktake_no"),
                    rs.getLong("warehouse_id"),
                    rs.getString("warehouse_name"),
                    rs.getString("scope_type"),
                    rs.getInt("status"),
                    rs.getString("remark"),
                    rs.getObject("created_by", Long.class),
                    toLocalDateTime(rs.getTimestamp("created_at")),
                    toLocalDateTime(rs.getTimestamp("updated_at"))
            );

    private final RowMapper<StocktakeItemResponse> itemRowMapper = (rs, rowNum) ->
            new StocktakeItemResponse(
                    rs.getLong("id"),
                    rs.getLong("sku_id"),
                    rs.getString("sku_code"),
                    rs.getString("sku_name"),
                    rs.getLong("location_id"),
                    rs.getString("location_code"),
                    rs.getString("area_name"),
                    rs.getInt("book_qty"),
                    rs.getInt("count_qty"),
                    rs.getInt("diff_qty"),
                    rs.getString("reason"),
                    rs.getString("remark")
            );

    private final RowMapper<StocktakeBookStockResponse> bookStockRowMapper = (rs, rowNum) ->
            new StocktakeBookStockResponse(
                    rs.getLong("warehouse_id"),
                    rs.getLong("location_id"),
                    rs.getString("location_code"),
                    rs.getString("area_name"),
                    rs.getLong("sku_id"),
                    rs.getString("sku_code"),
                    rs.getString("sku_name"),
                    rs.getInt("book_qty")
            );

    public StocktakeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long countByKeyword(String keyword, Integer status) {
        String sql = """
                SELECT COUNT(1)
                FROM wms_stocktake_order o
                JOIN wms_warehouse w ON o.warehouse_id = w.id
                WHERE (? = '' OR o.stocktake_no LIKE ? OR w.warehouse_name LIKE ?)
                  AND (? IS NULL OR o.status = ?)
                """;
        String like = "%" + keyword + "%";
        Long total = jdbcTemplate.queryForObject(sql, Long.class, keyword, like, like, status, status);
        return total == null ? 0L : total;
    }

    public List<StocktakeOrderResponse> pageByKeyword(String keyword, Integer status, int offset, int size) {
        String sql = """
                SELECT o.id, o.stocktake_no, o.warehouse_id, w.warehouse_name, o.scope_type,
                       o.status, o.remark, o.created_by, o.created_at, o.updated_at
                FROM wms_stocktake_order o
                JOIN wms_warehouse w ON o.warehouse_id = w.id
                WHERE (? = '' OR o.stocktake_no LIKE ? OR w.warehouse_name LIKE ?)
                  AND (? IS NULL OR o.status = ?)
                ORDER BY o.id DESC
                LIMIT ? OFFSET ?
                """;
        String like = "%" + keyword + "%";
        return jdbcTemplate.query(sql, orderRowMapper, keyword, like, like, status, status, size, offset);
    }

    public Optional<StocktakeOrderResponse> findOrderById(Long id) {
        String sql = """
                SELECT o.id, o.stocktake_no, o.warehouse_id, w.warehouse_name, o.scope_type,
                       o.status, o.remark, o.created_by, o.created_at, o.updated_at
                FROM wms_stocktake_order o
                JOIN wms_warehouse w ON o.warehouse_id = w.id
                WHERE o.id = ?
                """;
        return jdbcTemplate.query(sql, orderRowMapper, id).stream().findFirst();
    }

    public List<StocktakeItemResponse> listItemsByOrderId(Long orderId) {
        String sql = """
                SELECT i.id, i.sku_id, s.sku_code, s.sku_name,
                       i.location_id, l.location_code, l.area_name,
                       i.book_qty, i.count_qty, i.diff_qty, i.reason, i.remark
                FROM wms_stocktake_item i
                JOIN wms_sku s ON i.sku_id = s.id
                JOIN wms_location l ON i.location_id = l.id
                WHERE i.stocktake_order_id = ?
                ORDER BY i.id ASC
                """;
        return jdbcTemplate.query(sql, itemRowMapper, orderId);
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

    public Optional<Long> findUserIdByUsername(String username) {
        String sql = "SELECT id FROM sys_user WHERE username = ?";
        List<Long> list = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("id"), username);
        return list.stream().findFirst();
    }

    public int insertOrder(String stocktakeNo, Long warehouseId, String scopeType, String remark, Long createdBy) {
        String sql = """
                INSERT INTO wms_stocktake_order
                (stocktake_no, warehouse_id, scope_type, status, remark, created_by)
                VALUES (?, ?, ?, 0, ?, ?)
                """;
        return jdbcTemplate.update(sql, stocktakeNo, warehouseId, scopeType, remark, createdBy);
    }

    public Optional<Long> findOrderIdByStocktakeNo(String stocktakeNo) {
        String sql = "SELECT id FROM wms_stocktake_order WHERE stocktake_no = ?";
        List<Long> list = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("id"), stocktakeNo);
        return list.stream().findFirst();
    }

    public Optional<String> findMaxStocktakeNoByPrefix(String prefix) {
        String sql = """
                SELECT stocktake_no
                FROM wms_stocktake_order
                WHERE stocktake_no LIKE CONCAT(?, '%')
                ORDER BY stocktake_no DESC
                LIMIT 1
                """;
        List<String> list = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("stocktake_no"), prefix);
        return list.stream().findFirst();
    }

    public int updateOrder(Long id, Long warehouseId, String scopeType, String remark) {
        String sql = """
                UPDATE wms_stocktake_order
                SET warehouse_id = ?, scope_type = ?, remark = ?
                WHERE id = ?
                """;
        return jdbcTemplate.update(sql, warehouseId, scopeType, remark, id);
    }

    public int updateStatus(Long id, int status) {
        String sql = "UPDATE wms_stocktake_order SET status = ? WHERE id = ?";
        return jdbcTemplate.update(sql, status, id);
    }

    public int deleteItemsByOrderId(Long orderId) {
        String sql = "DELETE FROM wms_stocktake_item WHERE stocktake_order_id = ?";
        return jdbcTemplate.update(sql, orderId);
    }

    public int deleteOrderById(Long orderId) {
        String sql = "DELETE FROM wms_stocktake_order WHERE id = ?";
        return jdbcTemplate.update(sql, orderId);
    }

    public int insertItem(Long orderId, StocktakeItemRequest item, int bookQty) {
        int countQty = item.countQty() == null ? 0 : item.countQty();
        int diffQty = countQty - bookQty;
        String sql = """
                INSERT INTO wms_stocktake_item
                (stocktake_order_id, sku_id, location_id, book_qty, count_qty, diff_qty, reason, remark)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        return jdbcTemplate.update(sql, orderId, item.skuId(), item.locationId(), bookQty, countQty, diffQty, item.reason(), item.remark());
    }

    public int updateItemSnapshot(Long itemId, int bookQty, int countQty, int diffQty) {
        String sql = "UPDATE wms_stocktake_item SET book_qty = ?, count_qty = ?, diff_qty = ? WHERE id = ?";
        return jdbcTemplate.update(sql, bookQty, countQty, diffQty, itemId);
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

    public int upsertLocationStock(Long warehouseId, Long locationId, Long skuId, int onHandQty) {
        String sql = """
                INSERT INTO wms_location_stock (warehouse_id, location_id, sku_id, on_hand_qty)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE on_hand_qty = VALUES(on_hand_qty)
                """;
        return jdbcTemplate.update(sql, warehouseId, locationId, skuId, onHandQty);
    }

    public int upsertInventoryStock(Long warehouseId, Long skuId, int onHandQty) {
        String sql = """
                INSERT INTO wms_inventory_stock (warehouse_id, sku_id, on_hand_qty)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE on_hand_qty = VALUES(on_hand_qty)
                """;
        return jdbcTemplate.update(sql, warehouseId, skuId, onHandQty);
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

    public List<StocktakeBookStockResponse> listBookStocks(Long warehouseId, Long skuId, String keyword, int limit) {
        String sql = """
                SELECT s.warehouse_id, s.location_id, l.location_code, l.area_name,
                       s.sku_id, k.sku_code, k.sku_name, s.on_hand_qty AS book_qty
                FROM wms_location_stock s
                JOIN wms_location l ON s.location_id = l.id
                JOIN wms_sku k ON s.sku_id = k.id
                WHERE s.warehouse_id = ?
                  AND (? IS NULL OR s.sku_id = ?)
                  AND (? = '' OR l.location_code LIKE ? OR k.sku_code LIKE ? OR k.sku_name LIKE ?)
                ORDER BY s.on_hand_qty DESC, s.id DESC
                LIMIT ?
                """;
        String safeKeyword = keyword == null ? "" : keyword.trim();
        String like = "%" + safeKeyword + "%";
        return jdbcTemplate.query(sql, bookStockRowMapper, warehouseId, skuId, skuId, safeKeyword, like, like, like, limit);
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
