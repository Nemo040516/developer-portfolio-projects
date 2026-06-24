-- =========================================================
-- 智能仓库订货系统（M2）初始化数据脚本
-- 说明：
-- 1) 本脚本仅初始化 M2 最小联调数据（入库单、明细、库存汇总、库存流水）。
-- 2) 本脚本仅操作 wms_db，不会改动其他数据库。
-- 3) 严禁在 sme_recruitment_db 等独立项目库执行本脚本。
-- =========================================================

USE wms_db;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 入库单主表初始化
INSERT INTO wms_inbound_order
    (id, inbound_no, supplier_id, warehouse_id, status, remark, created_by)
VALUES
    (1, 'in202602250001', 1, 1, 2, 'M2联调默认入库单（已完成）', 1),
    (2, 'in202602250002', 1, 1, 1, 'M2联调默认入库单（已提交）', 1)
ON DUPLICATE KEY UPDATE
    supplier_id = VALUES(supplier_id),
    warehouse_id = VALUES(warehouse_id),
    status = VALUES(status),
    remark = VALUES(remark),
    created_by = VALUES(created_by);

-- 入库单明细初始化
INSERT INTO wms_inbound_item
    (id, inbound_order_id, sku_id, plan_qty, received_qty, remark)
VALUES
    (1, 1, 1, 120, 120, '默认明细-已收货'),
    (2, 2, 1, 80, 0, '默认明细-待确认')
ON DUPLICATE KEY UPDATE
    inbound_order_id = VALUES(inbound_order_id),
    sku_id = VALUES(sku_id),
    plan_qty = VALUES(plan_qty),
    received_qty = VALUES(received_qty),
    remark = VALUES(remark);

-- 库存汇总初始化
INSERT INTO wms_inventory_stock
    (id, warehouse_id, sku_id, on_hand_qty)
VALUES
    (1, 1, 1, 120)
ON DUPLICATE KEY UPDATE
    on_hand_qty = VALUES(on_hand_qty);

-- 库存流水初始化
INSERT INTO wms_inventory_txn
    (id, biz_type, biz_no, biz_id, warehouse_id, sku_id, qty_change, before_qty, after_qty, operator_id, operator_name, remark)
VALUES
    (1, 'INBOUND', 'in202602250001', 1, 1, 1, 120, 0, 120, 1, '系统管理员', 'M2联调默认入库流水')
ON DUPLICATE KEY UPDATE
    biz_type = VALUES(biz_type),
    biz_no = VALUES(biz_no),
    biz_id = VALUES(biz_id),
    warehouse_id = VALUES(warehouse_id),
    sku_id = VALUES(sku_id),
    qty_change = VALUES(qty_change),
    before_qty = VALUES(before_qty),
    after_qty = VALUES(after_qty),
    operator_id = VALUES(operator_id),
    operator_name = VALUES(operator_name),
    remark = VALUES(remark);

SET FOREIGN_KEY_CHECKS = 1;
