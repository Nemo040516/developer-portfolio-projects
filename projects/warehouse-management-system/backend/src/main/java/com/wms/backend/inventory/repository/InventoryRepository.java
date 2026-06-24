/**
 * @file 速览索引
 * @summary 库存数据访问层，负责库存/流水/库位台账以及预警规则与预警结果查询。
 * @core 1. 提供库存台账与库存流水分页查询
 * @core 2. 提供库位库存与库位流水分页查询
 * @core 3. 提供预警规则分页、详情、新增/更新基础查询
 * @core 4. 提供预警结果按关键字/类型/级别过滤查询
 * @entry 先看：pageTxnByKeyword、pageLocationStockByKeyword、pageAlertRuleByKeyword、pageAlertsByKeywordTypeAndLevel
 * @deps 关键依赖：JdbcTemplate、Inventory/Location DTO RowMapper、wms_inventory_* 与 wms_stock_alert_rule 表
 * @state 关键数据：stockRowMapper、txnRowMapper、locationStockRowMapper、locationTxnRowMapper、alertRuleRowMapper、alertRowMapper
 * @risk 高风险修改点：库存流水 UNION 补偿逻辑、预警类型/级别过滤条件、SQL 别名与 DTO 字段对齐
 * @link 相关文件：后端/src/main/java/com/wms/backend/inventory/service/InventoryService.java、后端/src/main/java/com/wms/backend/inventory/controller/InventoryController.java
 */
package com.wms.backend.inventory.repository;

import com.wms.backend.inventory.dto.InventoryStockResponse;
import com.wms.backend.inventory.dto.InventoryAlertResponse;
import com.wms.backend.inventory.dto.InventoryAlertRuleResponse;
import com.wms.backend.inventory.dto.InventoryTxnResponse;
import com.wms.backend.inventory.dto.LocationStockResponse;
import com.wms.backend.inventory.dto.LocationTxnResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class InventoryRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<InventoryStockResponse> stockRowMapper = (rs, rowNum) ->
            new InventoryStockResponse(
                    rs.getLong("id"),
                    rs.getLong("warehouse_id"),
                    rs.getString("warehouse_code"),
                    rs.getString("warehouse_name"),
                    rs.getLong("sku_id"),
                    rs.getString("sku_code"),
                    rs.getString("sku_name"),
                    rs.getInt("on_hand_qty"),
                    toLocalDateTime(rs.getTimestamp("updated_at"))
            );

    private final RowMapper<InventoryTxnResponse> txnRowMapper = (rs, rowNum) ->
            new InventoryTxnResponse(
                    rs.getLong("id"),
                    rs.getString("biz_type"),
                    rs.getString("biz_no"),
                    rs.getObject("biz_id", Long.class),
                    rs.getLong("warehouse_id"),
                    rs.getString("warehouse_code"),
                    rs.getString("warehouse_name"),
                    rs.getLong("sku_id"),
                    rs.getString("sku_code"),
                    rs.getString("sku_name"),
                    rs.getInt("qty_change"),
                    rs.getInt("before_qty"),
                    rs.getInt("after_qty"),
                    rs.getObject("operator_id", Long.class),
                    rs.getString("operator_name"),
                    toLocalDateTime(rs.getTimestamp("occurred_at")),
                    rs.getString("remark")
            );

    private final RowMapper<LocationStockResponse> locationStockRowMapper = (rs, rowNum) ->
            new LocationStockResponse(
                    rs.getLong("id"),
                    rs.getLong("warehouse_id"),
                    rs.getString("warehouse_code"),
                    rs.getString("warehouse_name"),
                    rs.getLong("location_id"),
                    rs.getString("location_code"),
                    rs.getString("area_name"),
                    rs.getLong("sku_id"),
                    rs.getString("sku_code"),
                    rs.getString("sku_name"),
                    rs.getInt("on_hand_qty"),
                    toLocalDateTime(rs.getTimestamp("updated_at"))
            );

    private final RowMapper<LocationTxnResponse> locationTxnRowMapper = (rs, rowNum) ->
            new LocationTxnResponse(
                    rs.getLong("id"),
                    rs.getString("biz_type"),
                    rs.getString("biz_no"),
                    rs.getObject("biz_id", Long.class),
                    rs.getLong("warehouse_id"),
                    rs.getString("warehouse_code"),
                    rs.getString("warehouse_name"),
                    rs.getLong("location_id"),
                    rs.getString("location_code"),
                    rs.getString("area_name"),
                    rs.getLong("sku_id"),
                    rs.getString("sku_code"),
                    rs.getString("sku_name"),
                    rs.getInt("qty_change"),
                    rs.getInt("before_qty"),
                    rs.getInt("after_qty"),
                    rs.getObject("operator_id", Long.class),
                    rs.getString("operator_name"),
                    toLocalDateTime(rs.getTimestamp("occurred_at")),
                    rs.getString("remark")
            );

    private final RowMapper<InventoryAlertRuleResponse> alertRuleRowMapper = (rs, rowNum) ->
            new InventoryAlertRuleResponse(
                    rs.getLong("id"),
                    rs.getLong("warehouse_id"),
                    rs.getString("warehouse_code"),
                    rs.getString("warehouse_name"),
                    rs.getLong("sku_id"),
                    rs.getString("sku_code"),
                    rs.getString("sku_name"),
                    rs.getInt("min_qty"),
                    rs.getInt("safe_qty"),
                    rs.getInt("max_qty"),
                    rs.getInt("status"),
                    rs.getString("remark"),
                    rs.getObject("created_by", Long.class),
                    toLocalDateTime(rs.getTimestamp("created_at")),
                    toLocalDateTime(rs.getTimestamp("updated_at"))
            );

    private final RowMapper<InventoryAlertResponse> alertRowMapper = (rs, rowNum) ->
            new InventoryAlertResponse(
                    rs.getLong("warehouse_id"),
                    rs.getString("warehouse_code"),
                    rs.getString("warehouse_name"),
                    rs.getLong("sku_id"),
                    rs.getString("sku_code"),
                    rs.getString("sku_name"),
                    rs.getInt("current_qty"),
                    rs.getInt("min_qty"),
                    rs.getInt("safe_qty"),
                    rs.getInt("max_qty"),
                    rs.getString("alert_type"),
                    rs.getString("alert_level")
            );

    public InventoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long countStockByKeyword(String keyword) {
        String sql = """
                SELECT COUNT(1)
                FROM wms_inventory_stock s
                JOIN wms_warehouse w ON s.warehouse_id = w.id
                JOIN wms_sku k ON s.sku_id = k.id
                WHERE (? = '' OR w.warehouse_code LIKE ? OR w.warehouse_name LIKE ? OR k.sku_code LIKE ? OR k.sku_name LIKE ?)
                """;
        String like = "%" + keyword + "%";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, keyword, like, like, like, like);
        return count == null ? 0L : count;
    }

    public List<InventoryStockResponse> pageStockByKeyword(String keyword, int offset, int size) {
        String sql = """
                SELECT s.id, s.warehouse_id, w.warehouse_code, w.warehouse_name,
                       s.sku_id, k.sku_code, k.sku_name, s.on_hand_qty, s.updated_at
                FROM wms_inventory_stock s
                JOIN wms_warehouse w ON s.warehouse_id = w.id
                JOIN wms_sku k ON s.sku_id = k.id
                WHERE (? = '' OR w.warehouse_code LIKE ? OR w.warehouse_name LIKE ? OR k.sku_code LIKE ? OR k.sku_name LIKE ?)
                ORDER BY s.id DESC
                LIMIT ? OFFSET ?
                """;
        String like = "%" + keyword + "%";
        return jdbcTemplate.query(sql, stockRowMapper, keyword, like, like, like, like, size, offset);
    }

    public long countTxnByKeyword(String keyword) {
        String sql = """
                SELECT COUNT(1)
                FROM (
                    SELECT t.id
                    FROM wms_inventory_txn t
                    JOIN wms_warehouse w ON t.warehouse_id = w.id
                    JOIN wms_sku k ON t.sku_id = k.id
                    WHERE (? = '' OR t.biz_no LIKE ? OR k.sku_code LIKE ? OR k.sku_name LIKE ?)
                    UNION ALL
                    SELECT -o.id
                    FROM wms_inbound_order o
                    JOIN wms_warehouse w ON o.warehouse_id = w.id
                    JOIN wms_inbound_item i ON o.id = i.inbound_order_id
                    JOIN wms_sku k ON i.sku_id = k.id
                    WHERE o.status = 1
                      AND NOT EXISTS (
                          SELECT 1
                          FROM wms_inventory_txn t2
                          WHERE t2.biz_type = 'INBOUND_SUBMIT'
                            AND t2.biz_id = o.id
                            AND t2.sku_id = i.sku_id
                      )
                      AND (? = '' OR o.inbound_no LIKE ? OR k.sku_code LIKE ? OR k.sku_name LIKE ?)
                ) q
                """;
        String like = "%" + keyword + "%";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, keyword, like, like, like, keyword, like, like, like);
        return count == null ? 0L : count;
    }

    public List<InventoryTxnResponse> pageTxnByKeyword(String keyword, int offset, int size) {
        String sql = """
                SELECT *
                FROM (
                    SELECT t.id, t.biz_type, t.biz_no, t.biz_id, t.warehouse_id, w.warehouse_code, w.warehouse_name,
                           t.sku_id, k.sku_code, k.sku_name, t.qty_change, t.before_qty, t.after_qty,
                           t.operator_id, t.operator_name, t.occurred_at, t.remark
                    FROM wms_inventory_txn t
                    JOIN wms_warehouse w ON t.warehouse_id = w.id
                    JOIN wms_sku k ON t.sku_id = k.id
                    WHERE (? = '' OR t.biz_no LIKE ? OR k.sku_code LIKE ? OR k.sku_name LIKE ?)
                    UNION ALL
                    SELECT
                           -o.id AS id,
                           'INBOUND_SUBMIT' AS biz_type,
                           o.inbound_no AS biz_no,
                           o.id AS biz_id,
                           o.warehouse_id,
                           w.warehouse_code,
                           w.warehouse_name,
                           i.sku_id,
                           k.sku_code,
                           k.sku_name,
                           0 AS qty_change,
                           COALESCE(s.on_hand_qty, 0) AS before_qty,
                           COALESCE(s.on_hand_qty, 0) AS after_qty,
                           o.created_by AS operator_id,
                           COALESCE(u.username, 'system') AS operator_name,
                           o.updated_at AS occurred_at,
                           '入库单已提交，待确认入库（补偿展示）' AS remark
                    FROM wms_inbound_order o
                    JOIN wms_warehouse w ON o.warehouse_id = w.id
                    JOIN wms_inbound_item i ON o.id = i.inbound_order_id
                    JOIN wms_sku k ON i.sku_id = k.id
                    LEFT JOIN wms_inventory_stock s ON s.warehouse_id = o.warehouse_id AND s.sku_id = i.sku_id
                    LEFT JOIN sys_user u ON u.id = o.created_by
                    WHERE o.status = 1
                      AND NOT EXISTS (
                          SELECT 1
                          FROM wms_inventory_txn t2
                          WHERE t2.biz_type = 'INBOUND_SUBMIT'
                            AND t2.biz_id = o.id
                            AND t2.sku_id = i.sku_id
                      )
                      AND (? = '' OR o.inbound_no LIKE ? OR k.sku_code LIKE ? OR k.sku_name LIKE ?)
                ) q
                ORDER BY q.occurred_at DESC, q.id DESC
                LIMIT ? OFFSET ?
                """;
        String like = "%" + keyword + "%";
        return jdbcTemplate.query(sql, txnRowMapper, keyword, like, like, like, keyword, like, like, like, size, offset);
    }

    public long countLocationStockByKeyword(String keyword) {
        String sql = """
                SELECT COUNT(1)
                FROM wms_location_stock s
                JOIN wms_warehouse w ON s.warehouse_id = w.id
                JOIN wms_location l ON s.location_id = l.id
                JOIN wms_sku k ON s.sku_id = k.id
                WHERE (? = '' OR w.warehouse_code LIKE ? OR w.warehouse_name LIKE ?
                    OR l.location_code LIKE ? OR k.sku_code LIKE ? OR k.sku_name LIKE ?)
                """;
        String like = "%" + keyword + "%";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, keyword, like, like, like, like, like);
        return count == null ? 0L : count;
    }

    public List<LocationStockResponse> pageLocationStockByKeyword(String keyword, int offset, int size) {
        String sql = """
                SELECT s.id, s.warehouse_id, w.warehouse_code, w.warehouse_name,
                       s.location_id, l.location_code, l.area_name,
                       s.sku_id, k.sku_code, k.sku_name, s.on_hand_qty, s.updated_at
                FROM wms_location_stock s
                JOIN wms_warehouse w ON s.warehouse_id = w.id
                JOIN wms_location l ON s.location_id = l.id
                JOIN wms_sku k ON s.sku_id = k.id
                WHERE (? = '' OR w.warehouse_code LIKE ? OR w.warehouse_name LIKE ?
                    OR l.location_code LIKE ? OR k.sku_code LIKE ? OR k.sku_name LIKE ?)
                ORDER BY s.id DESC
                LIMIT ? OFFSET ?
                """;
        String like = "%" + keyword + "%";
        return jdbcTemplate.query(sql, locationStockRowMapper, keyword, like, like, like, like, like, size, offset);
    }

    public long countLocationTxnByKeyword(String keyword) {
        String sql = """
                SELECT COUNT(1)
                FROM wms_location_txn t
                JOIN wms_warehouse w ON t.warehouse_id = w.id
                JOIN wms_location l ON t.location_id = l.id
                JOIN wms_sku k ON t.sku_id = k.id
                WHERE (? = '' OR t.biz_no LIKE ? OR l.location_code LIKE ? OR k.sku_code LIKE ? OR k.sku_name LIKE ?)
                """;
        String like = "%" + keyword + "%";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, keyword, like, like, like, like);
        return count == null ? 0L : count;
    }

    public List<LocationTxnResponse> pageLocationTxnByKeyword(String keyword, int offset, int size) {
        String sql = """
                SELECT t.id, t.biz_type, t.biz_no, t.biz_id,
                       t.warehouse_id, w.warehouse_code, w.warehouse_name,
                       t.location_id, l.location_code, l.area_name,
                       t.sku_id, k.sku_code, k.sku_name,
                       t.qty_change, t.before_qty, t.after_qty,
                       t.operator_id, t.operator_name, t.occurred_at, t.remark
                FROM wms_location_txn t
                JOIN wms_warehouse w ON t.warehouse_id = w.id
                JOIN wms_location l ON t.location_id = l.id
                JOIN wms_sku k ON t.sku_id = k.id
                WHERE (? = '' OR t.biz_no LIKE ? OR l.location_code LIKE ? OR k.sku_code LIKE ? OR k.sku_name LIKE ?)
                ORDER BY t.occurred_at DESC, t.id DESC
                LIMIT ? OFFSET ?
                """;
        String like = "%" + keyword + "%";
        return jdbcTemplate.query(sql, locationTxnRowMapper, keyword, like, like, like, like, size, offset);
    }

    public long countAlertRuleByKeyword(String keyword) {
        String sql = """
                SELECT COUNT(1)
                FROM wms_stock_alert_rule r
                JOIN wms_warehouse w ON r.warehouse_id = w.id
                JOIN wms_sku k ON r.sku_id = k.id
                WHERE (? = '' OR w.warehouse_code LIKE ? OR w.warehouse_name LIKE ? OR k.sku_code LIKE ? OR k.sku_name LIKE ?)
                """;
        String like = "%" + keyword + "%";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, keyword, like, like, like, like);
        return count == null ? 0L : count;
    }

    public List<InventoryAlertRuleResponse> pageAlertRuleByKeyword(String keyword, int offset, int size) {
        String sql = """
                SELECT r.id, r.warehouse_id, w.warehouse_code, w.warehouse_name,
                       r.sku_id, k.sku_code, k.sku_name,
                       r.min_qty, r.safe_qty, r.max_qty, r.status, r.remark, r.created_by, r.created_at, r.updated_at
                FROM wms_stock_alert_rule r
                JOIN wms_warehouse w ON r.warehouse_id = w.id
                JOIN wms_sku k ON r.sku_id = k.id
                WHERE (? = '' OR w.warehouse_code LIKE ? OR w.warehouse_name LIKE ? OR k.sku_code LIKE ? OR k.sku_name LIKE ?)
                ORDER BY r.id DESC
                LIMIT ? OFFSET ?
                """;
        String like = "%" + keyword + "%";
        return jdbcTemplate.query(sql, alertRuleRowMapper, keyword, like, like, like, like, size, offset);
    }

    public Optional<InventoryAlertRuleResponse> findAlertRuleById(Long id) {
        String sql = """
                SELECT r.id, r.warehouse_id, w.warehouse_code, w.warehouse_name,
                       r.sku_id, k.sku_code, k.sku_name,
                       r.min_qty, r.safe_qty, r.max_qty, r.status, r.remark, r.created_by, r.created_at, r.updated_at
                FROM wms_stock_alert_rule r
                JOIN wms_warehouse w ON r.warehouse_id = w.id
                JOIN wms_sku k ON r.sku_id = k.id
                WHERE r.id = ?
                """;
        return jdbcTemplate.query(sql, alertRuleRowMapper, id).stream().findFirst();
    }

    public Optional<Long> findAlertRuleIdByWarehouseSku(Long warehouseId, Long skuId) {
        String sql = "SELECT id FROM wms_stock_alert_rule WHERE warehouse_id = ? AND sku_id = ?";
        List<Long> list = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("id"), warehouseId, skuId);
        return list.stream().findFirst();
    }

    public int insertAlertRule(Long warehouseId, Long skuId, int minQty, int safeQty, int maxQty, int status, String remark, Long createdBy) {
        String sql = """
                INSERT INTO wms_stock_alert_rule
                (warehouse_id, sku_id, min_qty, safe_qty, max_qty, status, remark, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        return jdbcTemplate.update(sql, warehouseId, skuId, minQty, safeQty, maxQty, status, remark, createdBy);
    }

    public int updateAlertRule(Long id, Long warehouseId, Long skuId, int minQty, int safeQty, int maxQty, int status, String remark) {
        String sql = """
                UPDATE wms_stock_alert_rule
                SET warehouse_id = ?, sku_id = ?, min_qty = ?, safe_qty = ?, max_qty = ?, status = ?, remark = ?
                WHERE id = ?
                """;
        return jdbcTemplate.update(sql, warehouseId, skuId, minQty, safeQty, maxQty, status, remark, id);
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

    public long countAlertsByKeywordTypeAndLevel(String keyword, String alertType, String alertLevel) {
        String sql = """
                SELECT COUNT(1)
                FROM (
                    SELECT r.warehouse_id, r.sku_id
                    FROM wms_stock_alert_rule r
                    JOIN wms_warehouse w ON r.warehouse_id = w.id
                    JOIN wms_sku k ON r.sku_id = k.id
                    LEFT JOIN wms_inventory_stock s ON s.warehouse_id = r.warehouse_id AND s.sku_id = r.sku_id
                    WHERE r.status = 1
                      AND (? = '' OR w.warehouse_code LIKE ? OR w.warehouse_name LIKE ? OR k.sku_code LIKE ? OR k.sku_name LIKE ?)
                      AND (
                           (? = '' AND (COALESCE(s.on_hand_qty, 0) <= r.min_qty OR COALESCE(s.on_hand_qty, 0) >= r.max_qty))
                        OR (? = 'LOW' AND COALESCE(s.on_hand_qty, 0) <= r.min_qty)
                        OR (? = 'HIGH' AND COALESCE(s.on_hand_qty, 0) >= r.max_qty)
                      )
                      AND (
                           ? = ''
                        OR (? = 'CRITICAL' AND COALESCE(s.on_hand_qty, 0) = 0)
                        OR (? = 'WARN' AND COALESCE(s.on_hand_qty, 0) > 0 AND COALESCE(s.on_hand_qty, 0) <= r.min_qty)
                        OR (? = 'INFO' AND COALESCE(s.on_hand_qty, 0) >= r.max_qty)
                      )
                ) t
                """;
        String like = "%" + keyword + "%";
        String safeAlertType = alertType == null ? "" : alertType;
        String safeAlertLevel = alertLevel == null ? "" : alertLevel;
        Long count = jdbcTemplate.queryForObject(sql, Long.class,
                keyword, like, like, like, like,
                safeAlertType, safeAlertType, safeAlertType,
                safeAlertLevel, safeAlertLevel, safeAlertLevel, safeAlertLevel);
        return count == null ? 0L : count;
    }

    public List<InventoryAlertResponse> pageAlertsByKeywordTypeAndLevel(String keyword, String alertType, String alertLevel, int offset, int size) {
        String sql = """
                SELECT r.warehouse_id, w.warehouse_code, w.warehouse_name,
                       r.sku_id, k.sku_code, k.sku_name,
                       COALESCE(s.on_hand_qty, 0) AS current_qty,
                       r.min_qty, r.safe_qty, r.max_qty,
                       CASE
                           WHEN COALESCE(s.on_hand_qty, 0) <= r.min_qty THEN 'LOW'
                           WHEN COALESCE(s.on_hand_qty, 0) >= r.max_qty THEN 'HIGH'
                           ELSE 'NORMAL'
                       END AS alert_type,
                       CASE
                           WHEN COALESCE(s.on_hand_qty, 0) = 0 THEN 'CRITICAL'
                           WHEN COALESCE(s.on_hand_qty, 0) <= r.min_qty THEN 'WARN'
                           WHEN COALESCE(s.on_hand_qty, 0) >= r.max_qty THEN 'INFO'
                           ELSE 'NORMAL'
                       END AS alert_level,
                       CASE
                           WHEN COALESCE(s.on_hand_qty, 0) = 0 THEN 1
                           WHEN COALESCE(s.on_hand_qty, 0) <= r.min_qty THEN 2
                           WHEN COALESCE(s.on_hand_qty, 0) >= r.max_qty THEN 3
                           ELSE 9
                       END AS alert_priority
                FROM wms_stock_alert_rule r
                JOIN wms_warehouse w ON r.warehouse_id = w.id
                JOIN wms_sku k ON r.sku_id = k.id
                LEFT JOIN wms_inventory_stock s ON s.warehouse_id = r.warehouse_id AND s.sku_id = r.sku_id
                WHERE r.status = 1
                  AND (? = '' OR w.warehouse_code LIKE ? OR w.warehouse_name LIKE ? OR k.sku_code LIKE ? OR k.sku_name LIKE ?)
                  AND (
                       (? = '' AND (COALESCE(s.on_hand_qty, 0) <= r.min_qty OR COALESCE(s.on_hand_qty, 0) >= r.max_qty))
                    OR (? = 'LOW' AND COALESCE(s.on_hand_qty, 0) <= r.min_qty)
                    OR (? = 'HIGH' AND COALESCE(s.on_hand_qty, 0) >= r.max_qty)
                  )
                  AND (
                       ? = ''
                    OR (? = 'CRITICAL' AND COALESCE(s.on_hand_qty, 0) = 0)
                    OR (? = 'WARN' AND COALESCE(s.on_hand_qty, 0) > 0 AND COALESCE(s.on_hand_qty, 0) <= r.min_qty)
                    OR (? = 'INFO' AND COALESCE(s.on_hand_qty, 0) >= r.max_qty)
                  )
                ORDER BY alert_priority ASC, current_qty ASC, r.id DESC
                LIMIT ? OFFSET ?
                """;
        String like = "%" + keyword + "%";
        String safeAlertType = alertType == null ? "" : alertType;
        String safeAlertLevel = alertLevel == null ? "" : alertLevel;
        return jdbcTemplate.query(sql, alertRowMapper,
                keyword, like, like, like, like,
                safeAlertType, safeAlertType, safeAlertType,
                safeAlertLevel, safeAlertLevel, safeAlertLevel, safeAlertLevel,
                size, offset);
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
