/*
 * @file 速览索引
 * @summary 开发环境初始化数据脚本，负责角色账号、主数据与M3-M6演示样例数据回填。
 * @core 1. 初始化 `sys_role/sys_user/sys_user_role` 默认账号权限
 * @core 2. 初始化仓库/库位/SKU/供应商主数据
 * @core 3. 初始化上架、出库、预警、盘点、补货建议演示数据
 * @core 4. 基于库存流水聚合 `wms_sales_daily` 并生成默认补货明细
 * @entry 先看：`INSERT INTO sys_*`、`INSERT INTO wms_*`、`INSERT INTO wms_replenishment_item ... SELECT`
 * @deps 依赖：schema.sql 建表结果、application.yml 的 `spring.sql.init.mode=always`
 * @risk 高风险修改点：默认账号密码、固定ID冲突、ON DUPLICATE口径覆盖历史演示数据
 * @link 相关文件：后端/src/main/resources/schema.sql、文档/05-技术手册/A3-演示标准数据集说明.md
 */
-- ==================== 账号密码说明（请务必阅读） ====================
-- 1) 本文件仅用于开发环境初始化，默认账号如下：
--    admin / 12345
--    warehouse / 12345
--    purchaser / 12345
-- 2) 新同学下载后请第一时间修改默认密码，避免演示环境被误用。
-- 3) 生产环境严禁使用 {noop} 明文密码，请改为 BCrypt 等安全哈希。
-- ==============================================================

INSERT INTO sys_role (id, role_code, role_name, status, remark)
VALUES
    (1, 'ADMIN', '管理员', 1, '系统最高权限'),
    (2, 'WAREHOUSE', '仓库员', 1, '仓内作业角色'),
    (3, 'PURCHASER', '采购员', 1, '采购与补货角色')
ON DUPLICATE KEY UPDATE
    role_name = VALUES(role_name),
    status = VALUES(status),
    remark = VALUES(remark);

INSERT INTO sys_user (id, username, password, real_name, status)
VALUES
    (1, 'admin', '{noop}12345', '系统管理员', 1),
    (2, 'warehouse', '{noop}12345', '仓库专员', 1),
    (3, 'purchaser', '{noop}12345', '采购专员', 1)
ON DUPLICATE KEY UPDATE
    password = VALUES(password),
    real_name = VALUES(real_name),
    status = VALUES(status);

INSERT INTO sys_user_role (id, user_id, role_id)
VALUES
    (1, 1, 1),
    (2, 2, 2),
    (3, 3, 3)
ON DUPLICATE KEY UPDATE
    user_id = VALUES(user_id),
    role_id = VALUES(role_id);

INSERT INTO wms_warehouse (id, warehouse_code, warehouse_name, address, manager_name, contact_phone, status, remark)
VALUES
    (1, 'WH001', '一号主仓', '深圳市南山区示例路100号', '张三', '13800000001', 1, 'M1联调默认仓库')
ON DUPLICATE KEY UPDATE
    warehouse_name = VALUES(warehouse_name),
    address = VALUES(address),
    manager_name = VALUES(manager_name),
    contact_phone = VALUES(contact_phone),
    status = VALUES(status),
    remark = VALUES(remark);

INSERT INTO wms_location (id, warehouse_id, location_code, area_name, location_type, capacity, status, remark)
VALUES
    (1, 1, 'A01-01', 'A区', '标准货架位', 100.00, 1, 'M1联调默认库位')
ON DUPLICATE KEY UPDATE
    warehouse_id = VALUES(warehouse_id),
    area_name = VALUES(area_name),
    location_type = VALUES(location_type),
    capacity = VALUES(capacity),
    status = VALUES(status),
    remark = VALUES(remark);

INSERT INTO wms_sku (id, sku_code, sku_name, specification, unit, safe_stock, status, remark)
VALUES
    (1, 'SKU001', '演示商品-螺丝', 'M4*20', '盒', 50, 1, 'M1联调默认SKU')
ON DUPLICATE KEY UPDATE
    sku_name = VALUES(sku_name),
    specification = VALUES(specification),
    unit = VALUES(unit),
    safe_stock = VALUES(safe_stock),
    status = VALUES(status),
    remark = VALUES(remark);

INSERT INTO wms_supplier (id, supplier_code, supplier_name, contact_name, contact_phone, lead_time_days, status, remark)
VALUES
    (1, 'SUP001', '演示供应商A', '李四', '13900000002', 3, 1, 'M1联调默认供应商')
ON DUPLICATE KEY UPDATE
    supplier_name = VALUES(supplier_name),
    contact_name = VALUES(contact_name),
    contact_phone = VALUES(contact_phone),
    lead_time_days = VALUES(lead_time_days),
    status = VALUES(status),
    remark = VALUES(remark);

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

INSERT INTO wms_location_stock
    (id, warehouse_id, location_id, sku_id, on_hand_qty)
VALUES
    (1, 1, 1, 1, 80)
ON DUPLICATE KEY UPDATE
    on_hand_qty = VALUES(on_hand_qty);

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

INSERT INTO wms_outbound_order
    (id, outbound_no, outbound_type, target_name, warehouse_id, status, remark, created_by)
VALUES
    (1, 'out202602260001', 'SALES', '教学演示客户A', 1, 1, 'M4联调默认出库单（已提交）', 1),
    (2, 'out202602260002', 'TRANSFER', '教学演示部门B', 1, 0, 'M4联调默认出库单（草稿）', 1)
ON DUPLICATE KEY UPDATE
    outbound_type = VALUES(outbound_type),
    target_name = VALUES(target_name),
    warehouse_id = VALUES(warehouse_id),
    status = VALUES(status),
    remark = VALUES(remark),
    created_by = VALUES(created_by);

INSERT INTO wms_outbound_item
    (id, outbound_order_id, sku_id, location_id, plan_qty, actual_qty, remark)
VALUES
    (1, 1, 1, 1, 5, 0, 'M4默认明细-待确认出库'),
    (2, 2, 1, 1, 3, 0, 'M4默认明细-草稿')
ON DUPLICATE KEY UPDATE
    outbound_order_id = VALUES(outbound_order_id),
    sku_id = VALUES(sku_id),
    location_id = VALUES(location_id),
    plan_qty = VALUES(plan_qty),
    actual_qty = VALUES(actual_qty),
    remark = VALUES(remark);

INSERT INTO wms_stock_alert_rule
    (id, warehouse_id, sku_id, min_qty, safe_qty, max_qty, status, remark, created_by)
VALUES
    (1, 1, 1, 20, 50, 120, 1, 'M5联调默认规则-低库存/超储阈值', 1)
ON DUPLICATE KEY UPDATE
    warehouse_id = VALUES(warehouse_id),
    sku_id = VALUES(sku_id),
    min_qty = VALUES(min_qty),
    safe_qty = VALUES(safe_qty),
    max_qty = VALUES(max_qty),
    status = VALUES(status),
    remark = VALUES(remark),
    created_by = VALUES(created_by);

INSERT INTO wms_stocktake_order
    (id, stocktake_no, warehouse_id, scope_type, status, remark, created_by)
VALUES
    (1, 'st202602260001', 1, 'BY_WAREHOUSE', 1, 'M5联调默认盘点单（已提交）', 1),
    (2, 'st202602260002', 1, 'BY_WAREHOUSE', 0, 'M5联调默认盘点单（草稿）', 1)
ON DUPLICATE KEY UPDATE
    stocktake_no = VALUES(stocktake_no),
    warehouse_id = VALUES(warehouse_id),
    scope_type = VALUES(scope_type),
    status = VALUES(status),
    remark = VALUES(remark),
    created_by = VALUES(created_by);

INSERT INTO wms_stocktake_item
    (id, stocktake_order_id, sku_id, location_id, book_qty, count_qty, diff_qty, reason, remark)
VALUES
    (1, 1, 1, 1, 80, 80, 0, 'NORMAL', 'M5默认明细-账实一致'),
    (2, 2, 1, 1, 80, 0, 0, NULL, 'M5默认明细-草稿待盘点')
ON DUPLICATE KEY UPDATE
    stocktake_order_id = VALUES(stocktake_order_id),
    sku_id = VALUES(sku_id),
    location_id = VALUES(location_id),
    book_qty = VALUES(book_qty),
    count_qty = VALUES(count_qty),
    diff_qty = VALUES(diff_qty),
    reason = VALUES(reason),
    remark = VALUES(remark);

INSERT INTO wms_sales_daily
    (stat_date, warehouse_id, sku_id, outbound_qty)
SELECT DATE(occurred_at), warehouse_id, sku_id, SUM(ABS(qty_change))
FROM wms_inventory_txn
WHERE biz_type = 'OUTBOUND'
GROUP BY DATE(occurred_at), warehouse_id, sku_id
ON DUPLICATE KEY UPDATE
    outbound_qty = VALUES(outbound_qty),
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO wms_replenishment_plan
    (id, plan_no, warehouse_id, status, calc_days, lead_time_days, safety_days, purchase_draft_no, remark, created_by)
VALUES
    (1, 'rp202602260001', 1, 0, 15, 3, 2, NULL, 'M6联调默认建议计划（草稿）', 1)
ON DUPLICATE KEY UPDATE
    warehouse_id = VALUES(warehouse_id),
    status = VALUES(status),
    calc_days = VALUES(calc_days),
    lead_time_days = VALUES(lead_time_days),
    safety_days = VALUES(safety_days),
    purchase_draft_no = VALUES(purchase_draft_no),
    remark = VALUES(remark),
    created_by = VALUES(created_by);

INSERT INTO wms_replenishment_item
    (plan_id, sku_id, current_qty, safe_qty, predicted_daily_sales, predicted_total_qty,
     shortage_qty, suggested_qty, final_qty, reco_source, confidence, reason)
SELECT p.id,
       s.id,
       COALESCE(st.on_hand_qty, 0),
       COALESCE(ar.safe_qty, s.safe_stock, 0),
       COALESCE(d.avg_daily_sales, 0.00),
       CEIL(COALESCE(d.avg_daily_sales, 0.00) * (p.lead_time_days + p.safety_days)),
       GREATEST(
           0,
           COALESCE(ar.safe_qty, s.safe_stock, 0)
           + CEIL(COALESCE(d.avg_daily_sales, 0.00) * (p.lead_time_days + p.safety_days))
           - COALESCE(st.on_hand_qty, 0)
       ),
       GREATEST(
           0,
           COALESCE(ar.safe_qty, s.safe_stock, 0)
           + CEIL(COALESCE(d.avg_daily_sales, 0.00) * (p.lead_time_days + p.safety_days))
           - COALESCE(st.on_hand_qty, 0)
       ),
       GREATEST(
           0,
           COALESCE(ar.safe_qty, s.safe_stock, 0)
           + CEIL(COALESCE(d.avg_daily_sales, 0.00) * (p.lead_time_days + p.safety_days))
           - COALESCE(st.on_hand_qty, 0)
       ),
       NULL,
       NULL,
       CASE
           WHEN GREATEST(
               0,
               COALESCE(ar.safe_qty, s.safe_stock, 0)
               + CEIL(COALESCE(d.avg_daily_sales, 0.00) * (p.lead_time_days + p.safety_days))
               - COALESCE(st.on_hand_qty, 0)
           ) > 0
           THEN '预测覆盖不足，建议补货'
           ELSE '库存覆盖充足，建议观察'
       END
FROM wms_replenishment_plan p
JOIN wms_sku s ON s.status = 1
LEFT JOIN wms_inventory_stock st
    ON st.warehouse_id = p.warehouse_id
   AND st.sku_id = s.id
LEFT JOIN wms_stock_alert_rule ar
    ON ar.warehouse_id = p.warehouse_id
   AND ar.sku_id = s.id
   AND ar.status = 1
LEFT JOIN (
    SELECT warehouse_id, sku_id, ROUND(AVG(outbound_qty), 2) AS avg_daily_sales
    FROM wms_sales_daily
    GROUP BY warehouse_id, sku_id
) d
    ON d.warehouse_id = p.warehouse_id
   AND d.sku_id = s.id
WHERE p.plan_no = 'rp202602260001'
ON DUPLICATE KEY UPDATE
    current_qty = VALUES(current_qty),
    safe_qty = VALUES(safe_qty),
    predicted_daily_sales = VALUES(predicted_daily_sales),
    predicted_total_qty = VALUES(predicted_total_qty),
    shortage_qty = VALUES(shortage_qty),
    suggested_qty = VALUES(suggested_qty),
    final_qty = VALUES(final_qty),
    reason = VALUES(reason),
    updated_at = CURRENT_TIMESTAMP;
