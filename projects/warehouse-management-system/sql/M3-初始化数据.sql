-- =========================================================
-- 智能仓库订货系统（M3）初始化数据脚本
-- 说明：
-- 1) 本脚本仅初始化 M3 最小联调数据（上架单、明细、库位库存、库位流水）。
-- 2) 本脚本仅操作 wms_db，不会改动其他数据库。
-- 3) 严禁在 sme_recruitment_db 等独立项目库执行本脚本。
-- =========================================================

USE wms_db;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 上架单主表初始化
INSERT INTO wms_putaway_order
    (id, putaway_no, source_type, source_order_id, source_order_no, warehouse_id, status, remark, created_by)
VALUES
    (1, 'pa202602250001', 'INBOUND', 1, 'in202602250001', 1, 2, 'M3联调默认上架单（已完成）', 1),
    (2, 'pa202602250002', 'INBOUND', 2, 'in202602250002', 1, 1, 'M3联调默认上架单（已提交）', 1)
ON DUPLICATE KEY UPDATE
    source_type = VALUES(source_type),
    source_order_id = VALUES(source_order_id),
    source_order_no = VALUES(source_order_no),
    warehouse_id = VALUES(warehouse_id),
    status = VALUES(status),
    remark = VALUES(remark),
    created_by = VALUES(created_by);

-- 上架单明细初始化
INSERT INTO wms_putaway_item
    (id, putaway_order_id, sku_id, location_id, plan_qty, actual_qty, remark)
VALUES
    (1, 1, 1, 1, 80, 80, '默认明细-已上架'),
    (2, 2, 1, 1, 20, 0, '默认明细-待确认')
ON DUPLICATE KEY UPDATE
    putaway_order_id = VALUES(putaway_order_id),
    sku_id = VALUES(sku_id),
    location_id = VALUES(location_id),
    plan_qty = VALUES(plan_qty),
    actual_qty = VALUES(actual_qty),
    remark = VALUES(remark);

-- 库位库存初始化
INSERT INTO wms_location_stock
    (id, warehouse_id, location_id, sku_id, on_hand_qty)
VALUES
    (1, 1, 1, 1, 80)
ON DUPLICATE KEY UPDATE
    on_hand_qty = VALUES(on_hand_qty);

-- 库位流水初始化
INSERT INTO wms_location_txn
    (id, biz_type, biz_no, biz_id, warehouse_id, location_id, sku_id, qty_change, before_qty, after_qty, operator_id, operator_name, remark)
VALUES
    (1, 'PUTAWAY', 'pa202602250001', 1, 1, 1, 1, 80, 0, 80, 1, '系统管理员', 'M3联调默认上架流水')
ON DUPLICATE KEY UPDATE
    biz_type = VALUES(biz_type),
    biz_no = VALUES(biz_no),
    biz_id = VALUES(biz_id),
    warehouse_id = VALUES(warehouse_id),
    location_id = VALUES(location_id),
    sku_id = VALUES(sku_id),
    qty_change = VALUES(qty_change),
    before_qty = VALUES(before_qty),
    after_qty = VALUES(after_qty),
    operator_id = VALUES(operator_id),
    operator_name = VALUES(operator_name),
    remark = VALUES(remark);

SET FOREIGN_KEY_CHECKS = 1;

