-- =========================================================
-- 智能仓库订货系统（M5）初始化数据脚本
-- 说明：
-- 1) 本脚本仅初始化 M5 最小联调数据（预警规则 + 盘点单与明细）。
-- 2) 本脚本仅操作 wms_db，不会改动其他数据库。
-- 3) 严禁在 sme_recruitment_db 等独立项目库执行本脚本。
-- =========================================================

USE wms_db;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ==================== M5：预警规则初始化 ====================
INSERT INTO wms_stock_alert_rule
    (warehouse_id, sku_id, min_qty, safe_qty, max_qty, status, remark, created_by)
VALUES
    (1, 1, 20, 50, 120, 1, 'M5联调默认规则-低库存/超储阈值', 1)
ON DUPLICATE KEY UPDATE
    min_qty = VALUES(min_qty),
    safe_qty = VALUES(safe_qty),
    max_qty = VALUES(max_qty),
    status = VALUES(status),
    remark = VALUES(remark),
    created_by = VALUES(created_by);

-- ==================== M5：盘点单主表初始化 ====================
INSERT INTO wms_stocktake_order
    (stocktake_no, warehouse_id, scope_type, status, remark, created_by)
VALUES
    ('st202602260001', 1, 'BY_WAREHOUSE', 1, 'M5联调默认盘点单（已提交）', 1),
    ('st202602260002', 1, 'BY_WAREHOUSE', 0, 'M5联调默认盘点单（草稿）', 1)
ON DUPLICATE KEY UPDATE
    warehouse_id = VALUES(warehouse_id),
    scope_type = VALUES(scope_type),
    status = VALUES(status),
    remark = VALUES(remark),
    created_by = VALUES(created_by);

-- ==================== M5：盘点单明细初始化 ====================
-- 已提交单：默认账实一致，便于后续确认接口联调
INSERT INTO wms_stocktake_item
    (stocktake_order_id, sku_id, location_id, book_qty, count_qty, diff_qty, reason, remark)
SELECT o.id,
       1,
       1,
       COALESCE(ls.on_hand_qty, 0) AS book_qty,
       COALESCE(ls.on_hand_qty, 0) AS count_qty,
       0 AS diff_qty,
       'NORMAL' AS reason,
       'M5默认明细-账实一致'
FROM wms_stocktake_order o
LEFT JOIN wms_location_stock ls
    ON ls.warehouse_id = o.warehouse_id
   AND ls.location_id = 1
   AND ls.sku_id = 1
WHERE o.stocktake_no = 'st202602260001'
ON DUPLICATE KEY UPDATE
    book_qty = VALUES(book_qty),
    count_qty = VALUES(count_qty),
    diff_qty = VALUES(diff_qty),
    reason = VALUES(reason),
    remark = VALUES(remark);

-- 草稿单：预置待填实盘数量，便于前端录入联调
INSERT INTO wms_stocktake_item
    (stocktake_order_id, sku_id, location_id, book_qty, count_qty, diff_qty, reason, remark)
SELECT o.id,
       1,
       1,
       COALESCE(ls.on_hand_qty, 0) AS book_qty,
       0 AS count_qty,
       0 AS diff_qty,
       NULL AS reason,
       'M5默认明细-草稿待盘点'
FROM wms_stocktake_order o
LEFT JOIN wms_location_stock ls
    ON ls.warehouse_id = o.warehouse_id
   AND ls.location_id = 1
   AND ls.sku_id = 1
WHERE o.stocktake_no = 'st202602260002'
ON DUPLICATE KEY UPDATE
    book_qty = VALUES(book_qty),
    count_qty = VALUES(count_qty),
    diff_qty = VALUES(diff_qty),
    reason = VALUES(reason),
    remark = VALUES(remark);

SET FOREIGN_KEY_CHECKS = 1;

