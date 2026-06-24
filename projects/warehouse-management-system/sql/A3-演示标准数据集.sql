-- =========================================================
-- 智能仓库订货系统（A3）演示标准数据集脚本
-- 目标：提供一套可重复执行的统一演示数据，覆盖
--      入库 -> 上架 -> 出库 -> 补货建议 四个场景。
-- 约束：仅操作 wms_db。
-- 说明：
-- 1) 本脚本为幂等脚本，可重复执行。
-- 2) 仅清理本脚本生成的业务单据/流水（A3 固定单号），不影响其他历史联调数据。
-- =========================================================

USE wms_db;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ==================== 0. 主数据准备（幂等） ====================
INSERT INTO wms_warehouse
    (warehouse_code, warehouse_name, address, manager_name, contact_phone, status, remark)
VALUES
    ('WHA3', 'A3演示仓', '教学演示园区A3', '演示管理员', '13800000003', 1, 'A3标准数据集-演示仓')
ON DUPLICATE KEY UPDATE
    warehouse_name = VALUES(warehouse_name),
    address = VALUES(address),
    manager_name = VALUES(manager_name),
    contact_phone = VALUES(contact_phone),
    status = VALUES(status),
    remark = VALUES(remark),
    updated_at = CURRENT_TIMESTAMP;

SELECT id INTO @a3_warehouse_id
FROM wms_warehouse
WHERE warehouse_code = 'WHA3'
LIMIT 1;

INSERT INTO wms_location
    (warehouse_id, location_code, area_name, location_type, capacity, status, remark)
VALUES
    (@a3_warehouse_id, 'A3-01-01', 'A3演示区', 'RACK', 1000, 1, 'A3标准数据集-演示库位')
ON DUPLICATE KEY UPDATE
    warehouse_id = VALUES(warehouse_id),
    area_name = VALUES(area_name),
    location_type = VALUES(location_type),
    capacity = VALUES(capacity),
    status = VALUES(status),
    remark = VALUES(remark),
    updated_at = CURRENT_TIMESTAMP;

SELECT id INTO @a3_location_id
FROM wms_location
WHERE location_code = 'A3-01-01'
LIMIT 1;

INSERT INTO wms_supplier
    (supplier_code, supplier_name, contact_name, contact_phone, lead_time_days, status, remark)
VALUES
    ('SUPA3', 'A3演示供应商', '演示采购员', '13900000003', 3, 1, 'A3标准数据集-演示供应商')
ON DUPLICATE KEY UPDATE
    supplier_name = VALUES(supplier_name),
    contact_name = VALUES(contact_name),
    contact_phone = VALUES(contact_phone),
    lead_time_days = VALUES(lead_time_days),
    status = VALUES(status),
    remark = VALUES(remark),
    updated_at = CURRENT_TIMESTAMP;

SELECT id INTO @a3_supplier_id
FROM wms_supplier
WHERE supplier_code = 'SUPA3'
LIMIT 1;

INSERT INTO wms_sku
    (sku_code, sku_name, specification, unit, safe_stock, status, remark)
VALUES
    ('SKUA3-001', 'A3演示商品-标准件', '10mm', '件', 80, 1, 'A3标准数据集-演示SKU')
ON DUPLICATE KEY UPDATE
    sku_name = VALUES(sku_name),
    specification = VALUES(specification),
    unit = VALUES(unit),
    safe_stock = VALUES(safe_stock),
    status = VALUES(status),
    remark = VALUES(remark),
    updated_at = CURRENT_TIMESTAMP;

SELECT id INTO @a3_sku_id
FROM wms_sku
WHERE sku_code = 'SKUA3-001'
LIMIT 1;

-- 为演示 SKU 建立预警规则（供 M5/M6 一并使用）
INSERT INTO wms_stock_alert_rule
    (warehouse_id, sku_id, min_qty, safe_qty, max_qty, status, remark, created_by)
VALUES
    (@a3_warehouse_id, @a3_sku_id, 30, 80, 120, 1, 'A3标准数据集-预警规则', 1)
ON DUPLICATE KEY UPDATE
    min_qty = VALUES(min_qty),
    safe_qty = VALUES(safe_qty),
    max_qty = VALUES(max_qty),
    status = VALUES(status),
    remark = VALUES(remark),
    created_by = VALUES(created_by),
    updated_at = CURRENT_TIMESTAMP;

-- ==================== 1. 入库场景（完成态） ====================
INSERT INTO wms_inbound_order
    (inbound_no, supplier_id, warehouse_id, status, remark, created_by)
VALUES
    ('inA3202602260001', @a3_supplier_id, @a3_warehouse_id, 2, 'A3标准数据集-入库完成单', 1)
ON DUPLICATE KEY UPDATE
    supplier_id = VALUES(supplier_id),
    warehouse_id = VALUES(warehouse_id),
    status = VALUES(status),
    remark = VALUES(remark),
    created_by = VALUES(created_by),
    updated_at = CURRENT_TIMESTAMP;

SELECT id INTO @a3_inbound_id
FROM wms_inbound_order
WHERE inbound_no = 'inA3202602260001'
LIMIT 1;

DELETE FROM wms_inbound_item
WHERE inbound_order_id = @a3_inbound_id;

INSERT INTO wms_inbound_item
    (inbound_order_id, sku_id, plan_qty, received_qty, remark)
VALUES
    (@a3_inbound_id, @a3_sku_id, 100, 100, 'A3标准数据集-入库明细');

-- ==================== 2. 上架场景（完成态） ====================
INSERT INTO wms_putaway_order
    (putaway_no, source_type, source_order_id, source_order_no, warehouse_id, status, remark, created_by)
VALUES
    ('paA3202602260001', 'INBOUND', @a3_inbound_id, 'inA3202602260001', @a3_warehouse_id, 2, 'A3标准数据集-上架完成单', 1)
ON DUPLICATE KEY UPDATE
    source_type = VALUES(source_type),
    source_order_id = VALUES(source_order_id),
    source_order_no = VALUES(source_order_no),
    warehouse_id = VALUES(warehouse_id),
    status = VALUES(status),
    remark = VALUES(remark),
    created_by = VALUES(created_by),
    updated_at = CURRENT_TIMESTAMP;

SELECT id INTO @a3_putaway_id
FROM wms_putaway_order
WHERE putaway_no = 'paA3202602260001'
LIMIT 1;

DELETE FROM wms_putaway_item
WHERE putaway_order_id = @a3_putaway_id;

INSERT INTO wms_putaway_item
    (putaway_order_id, sku_id, location_id, plan_qty, actual_qty, remark)
VALUES
    (@a3_putaway_id, @a3_sku_id, @a3_location_id, 80, 80, 'A3标准数据集-上架明细');

-- ==================== 3. 出库场景（完成态） ====================
INSERT INTO wms_outbound_order
    (outbound_no, outbound_type, target_name, warehouse_id, status, remark, created_by)
VALUES
    ('outA3202602260001', 'SALES', 'A3演示客户', @a3_warehouse_id, 2, 'A3标准数据集-出库完成单', 1)
ON DUPLICATE KEY UPDATE
    outbound_type = VALUES(outbound_type),
    target_name = VALUES(target_name),
    warehouse_id = VALUES(warehouse_id),
    status = VALUES(status),
    remark = VALUES(remark),
    created_by = VALUES(created_by),
    updated_at = CURRENT_TIMESTAMP;

SELECT id INTO @a3_outbound_id
FROM wms_outbound_order
WHERE outbound_no = 'outA3202602260001'
LIMIT 1;

DELETE FROM wms_outbound_item
WHERE outbound_order_id = @a3_outbound_id;

INSERT INTO wms_outbound_item
    (outbound_order_id, sku_id, location_id, plan_qty, actual_qty, remark)
VALUES
    (@a3_outbound_id, @a3_sku_id, @a3_location_id, 30, 30, 'A3标准数据集-出库明细');

-- ==================== 4. 库存与流水（确定最终一致态） ====================
-- 最终库存：仓库总库存 70；库位库存 50。
INSERT INTO wms_inventory_stock
    (warehouse_id, sku_id, on_hand_qty)
VALUES
    (@a3_warehouse_id, @a3_sku_id, 70)
ON DUPLICATE KEY UPDATE
    on_hand_qty = VALUES(on_hand_qty),
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO wms_location_stock
    (warehouse_id, location_id, sku_id, on_hand_qty)
VALUES
    (@a3_warehouse_id, @a3_location_id, @a3_sku_id, 50)
ON DUPLICATE KEY UPDATE
    on_hand_qty = VALUES(on_hand_qty),
    updated_at = CURRENT_TIMESTAMP;

-- 清理本脚本历史流水，避免重复
DELETE FROM wms_inventory_txn
WHERE biz_no IN ('inA3202602260001', 'outA3202602260001');

DELETE FROM wms_location_txn
WHERE biz_no IN ('paA3202602260001', 'outA3202602260001');

-- 仓库流水：入库 +100，出库 -30
INSERT INTO wms_inventory_txn
    (biz_type, biz_no, biz_id, warehouse_id, sku_id, qty_change, before_qty, after_qty, operator_id, operator_name, remark)
VALUES
    ('INBOUND', 'inA3202602260001', @a3_inbound_id, @a3_warehouse_id, @a3_sku_id, 100, 0, 100, 1, '系统管理员', 'A3标准数据集-入库流水'),
    ('OUTBOUND', 'outA3202602260001', @a3_outbound_id, @a3_warehouse_id, @a3_sku_id, -30, 100, 70, 1, '系统管理员', 'A3标准数据集-出库流水');

-- 库位流水：上架 +80，出库 -30
INSERT INTO wms_location_txn
    (biz_type, biz_no, biz_id, warehouse_id, location_id, sku_id, qty_change, before_qty, after_qty, operator_id, operator_name, remark)
VALUES
    ('PUTAWAY', 'paA3202602260001', @a3_putaway_id, @a3_warehouse_id, @a3_location_id, @a3_sku_id, 80, 0, 80, 1, '系统管理员', 'A3标准数据集-上架流水'),
    ('OUTBOUND', 'outA3202602260001', @a3_outbound_id, @a3_warehouse_id, @a3_location_id, @a3_sku_id, -30, 80, 50, 1, '系统管理员', 'A3标准数据集-出库库位流水');

-- ==================== 5. 补货建议场景（确认态） ====================
INSERT INTO wms_sales_daily
    (stat_date, warehouse_id, sku_id, outbound_qty)
VALUES
    ('2026-02-24', @a3_warehouse_id, @a3_sku_id, 18),
    ('2026-02-25', @a3_warehouse_id, @a3_sku_id, 20),
    ('2026-02-26', @a3_warehouse_id, @a3_sku_id, 22)
ON DUPLICATE KEY UPDATE
    outbound_qty = VALUES(outbound_qty),
    updated_at = CURRENT_TIMESTAMP;

-- 关联推荐：A3 演示 SKU 与 SKU001 互相推荐
INSERT INTO wms_reco_pair
    (sku_id, related_sku_id, support_count, confidence, lift)
VALUES
    (@a3_sku_id, 1, 5, 0.6500, 1.2000),
    (1, @a3_sku_id, 4, 0.5000, 1.1000)
ON DUPLICATE KEY UPDATE
    support_count = VALUES(support_count),
    confidence = VALUES(confidence),
    lift = VALUES(lift),
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO wms_replenishment_plan
    (plan_no, warehouse_id, status, calc_days, lead_time_days, safety_days, purchase_draft_no, created_by, remark)
VALUES
    ('rpA3202602260001', @a3_warehouse_id, 2, 15, 3, 2, 'poa3202602260001', 1, 'A3标准数据集-补货建议已转采购草稿')
ON DUPLICATE KEY UPDATE
    warehouse_id = VALUES(warehouse_id),
    status = VALUES(status),
    calc_days = VALUES(calc_days),
    lead_time_days = VALUES(lead_time_days),
    safety_days = VALUES(safety_days),
    purchase_draft_no = VALUES(purchase_draft_no),
    created_by = VALUES(created_by),
    remark = VALUES(remark),
    updated_at = CURRENT_TIMESTAMP;

SELECT id INTO @a3_plan_id
FROM wms_replenishment_plan
WHERE plan_no = 'rpA3202602260001'
LIMIT 1;

INSERT INTO wms_replenishment_item
    (plan_id, sku_id, current_qty, safe_qty, predicted_daily_sales, predicted_total_qty,
     shortage_qty, suggested_qty, final_qty, reco_source, confidence, reason)
VALUES
    (@a3_plan_id, @a3_sku_id, 70, 80, 20.00, 100, 110, 110, 110, 'SKU001', 0.6500, 'A3标准数据集-库存覆盖不足，建议补货')
ON DUPLICATE KEY UPDATE
    current_qty = VALUES(current_qty),
    safe_qty = VALUES(safe_qty),
    predicted_daily_sales = VALUES(predicted_daily_sales),
    predicted_total_qty = VALUES(predicted_total_qty),
    shortage_qty = VALUES(shortage_qty),
    suggested_qty = VALUES(suggested_qty),
    final_qty = VALUES(final_qty),
    reco_source = VALUES(reco_source),
    confidence = VALUES(confidence),
    reason = VALUES(reason),
    updated_at = CURRENT_TIMESTAMP;

SET FOREIGN_KEY_CHECKS = 1;

-- ==================== 6. 快速核验输出 ====================
SELECT 'A3主数据' AS section, w.id AS warehouse_id, l.id AS location_id, s.id AS sku_id, sp.id AS supplier_id
FROM wms_warehouse w
JOIN wms_location l ON l.warehouse_id = w.id
JOIN wms_sku s ON s.sku_code = 'SKUA3-001'
JOIN wms_supplier sp ON sp.supplier_code = 'SUPA3'
WHERE w.warehouse_code = 'WHA3' AND l.location_code = 'A3-01-01';

SELECT 'A3单据状态' AS section,
       (SELECT status FROM wms_inbound_order WHERE inbound_no = 'inA3202602260001') AS inbound_status,
       (SELECT status FROM wms_putaway_order WHERE putaway_no = 'paA3202602260001') AS putaway_status,
       (SELECT status FROM wms_outbound_order WHERE outbound_no = 'outA3202602260001') AS outbound_status,
       (SELECT status FROM wms_replenishment_plan WHERE plan_no = 'rpA3202602260001') AS replenishment_status;

SELECT 'A3库存结果' AS section,
       (SELECT on_hand_qty FROM wms_inventory_stock WHERE warehouse_id = @a3_warehouse_id AND sku_id = @a3_sku_id) AS inventory_qty,
       (SELECT on_hand_qty FROM wms_location_stock WHERE warehouse_id = @a3_warehouse_id AND location_id = @a3_location_id AND sku_id = @a3_sku_id) AS location_qty;

SELECT 'A3流水条数' AS section,
       (SELECT COUNT(*) FROM wms_inventory_txn WHERE biz_no IN ('inA3202602260001','outA3202602260001')) AS inventory_txn_cnt,
       (SELECT COUNT(*) FROM wms_location_txn WHERE biz_no IN ('paA3202602260001','outA3202602260001')) AS location_txn_cnt;

SELECT 'A3补货明细' AS section,
       ri.current_qty,
       ri.safe_qty,
       ri.predicted_daily_sales,
       ri.predicted_total_qty,
       ri.suggested_qty,
       ri.final_qty,
       ri.reco_source,
       ri.confidence
FROM wms_replenishment_item ri
JOIN wms_replenishment_plan rp ON rp.id = ri.plan_id
WHERE rp.plan_no = 'rpA3202602260001';
