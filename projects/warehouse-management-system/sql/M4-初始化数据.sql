-- =========================================================
-- 智能仓库订货系统（M4）初始化数据脚本
-- 说明：
-- 1) 本脚本仅初始化 M4 最小联调数据（出库单与明细，不直接扣减库存）。
-- 2) 本脚本仅操作 wms_db，不会改动其他数据库。
-- 3) 严禁在 其他独立项目库执行本脚本。
-- =========================================================

USE wms_db;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 出库单主表初始化（仅草稿/已提交，方便后续联调确认接口）
INSERT INTO wms_outbound_order
    (outbound_no, outbound_type, target_name, warehouse_id, status, remark, created_by)
VALUES
    ('out202602260001', 'SALES', '教学演示客户A', 1, 1, 'M4联调默认出库单（已提交）', 1),
    ('out202602260002', 'TRANSFER', '教学演示部门B', 1, 0, 'M4联调默认出库单（草稿）', 1)
ON DUPLICATE KEY UPDATE
    outbound_type = VALUES(outbound_type),
    target_name = VALUES(target_name),
    warehouse_id = VALUES(warehouse_id),
    status = VALUES(status),
    remark = VALUES(remark),
    created_by = VALUES(created_by);

-- 出库单明细初始化（幂等写入，默认不触发库存扣减）
INSERT INTO wms_outbound_item
    (outbound_order_id, sku_id, location_id, plan_qty, actual_qty, remark)
SELECT o.id, 1, 1, 5, 0, 'M4默认明细-待确认出库'
FROM wms_outbound_order o
WHERE o.outbound_no = 'out202602260001'
ON DUPLICATE KEY UPDATE
    plan_qty = VALUES(plan_qty),
    actual_qty = VALUES(actual_qty),
    remark = VALUES(remark);

INSERT INTO wms_outbound_item
    (outbound_order_id, sku_id, location_id, plan_qty, actual_qty, remark)
SELECT o.id, 1, 1, 3, 0, 'M4默认明细-草稿'
FROM wms_outbound_order o
WHERE o.outbound_no = 'out202602260002'
ON DUPLICATE KEY UPDATE
    plan_qty = VALUES(plan_qty),
    actual_qty = VALUES(actual_qty),
    remark = VALUES(remark);

SET FOREIGN_KEY_CHECKS = 1;

