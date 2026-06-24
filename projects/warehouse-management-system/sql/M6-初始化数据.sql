-- =========================================================
-- 智能仓库订货系统（M6）初始化数据脚本
-- 说明：
-- 1) 本脚本仅初始化 M6 最小联调数据（建议计划 + 明细 + 销量聚合 + 推荐对）。
-- 2) 本脚本仅操作 wms_db，不会改动其他数据库。
-- 3) 严禁在 其他独立项目库执行本脚本。
-- =========================================================

USE wms_db;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ==================== M6：销量日聚合初始化 ====================
-- 说明：以库存流水中的 OUTBOUND 交易回填销量日聚合，避免前端演示假数据。
INSERT INTO wms_sales_daily
    (stat_date, warehouse_id, sku_id, outbound_qty)
SELECT DATE(occurred_at) AS stat_date,
       warehouse_id,
       sku_id,
       SUM(ABS(qty_change)) AS outbound_qty
FROM wms_inventory_txn
WHERE biz_type = 'OUTBOUND'
GROUP BY DATE(occurred_at), warehouse_id, sku_id
ON DUPLICATE KEY UPDATE
    outbound_qty = VALUES(outbound_qty),
    updated_at = CURRENT_TIMESTAMP;

-- ==================== M6：关联推荐对初始化 ====================
-- 说明：按同一出库业务单中共同出现的 SKU 计算 support 与 confidence（轻量协同）。
INSERT INTO wms_reco_pair
    (sku_id, related_sku_id, support_count, confidence, lift)
SELECT pair.sku_id,
       pair.related_sku_id,
       pair.support_count,
       pair.confidence,
       NULL AS lift
FROM (
    SELECT a.sku_id AS sku_id,
           b.sku_id AS related_sku_id,
           COUNT(*) AS support_count,
           ROUND(
               COUNT(*) /
               NULLIF(base.base_cnt, 0),
               4
           ) AS confidence
    FROM (
        SELECT biz_no, sku_id
        FROM wms_inventory_txn
        WHERE biz_type = 'OUTBOUND'
        GROUP BY biz_no, sku_id
    ) a
    JOIN (
        SELECT biz_no, sku_id
        FROM wms_inventory_txn
        WHERE biz_type = 'OUTBOUND'
        GROUP BY biz_no, sku_id
    ) b
        ON a.biz_no = b.biz_no
       AND a.sku_id <> b.sku_id
    JOIN (
        SELECT sku_id, COUNT(DISTINCT biz_no) AS base_cnt
        FROM wms_inventory_txn
        WHERE biz_type = 'OUTBOUND'
        GROUP BY sku_id
    ) base
        ON base.sku_id = a.sku_id
    GROUP BY a.sku_id, b.sku_id, base.base_cnt
) pair
WHERE pair.support_count > 0
ON DUPLICATE KEY UPDATE
    support_count = VALUES(support_count),
    confidence = VALUES(confidence),
    lift = VALUES(lift),
    updated_at = CURRENT_TIMESTAMP;

-- 若真实出库单中暂无“同单多SKU”共现，补入最小兜底推荐对，便于前端演示推荐解释。
INSERT INTO wms_reco_pair
    (sku_id, related_sku_id, support_count, confidence, lift)
SELECT seed.sku_id, seed.related_sku_id, seed.support_count, seed.confidence, seed.lift
FROM (
    SELECT 1 AS sku_id, 2 AS related_sku_id, 1 AS support_count, 0.5000 AS confidence, NULL AS lift
    UNION ALL
    SELECT 2 AS sku_id, 1 AS related_sku_id, 1 AS support_count, 0.5000 AS confidence, NULL AS lift
    UNION ALL
    SELECT 3 AS sku_id, 1 AS related_sku_id, 1 AS support_count, 0.3333 AS confidence, NULL AS lift
) seed
WHERE NOT EXISTS (SELECT 1 FROM wms_reco_pair)
ON DUPLICATE KEY UPDATE
    support_count = VALUES(support_count),
    confidence = VALUES(confidence),
    lift = VALUES(lift),
    updated_at = CURRENT_TIMESTAMP;

-- ==================== M6：补货建议计划初始化 ====================
INSERT INTO wms_replenishment_plan
    (plan_no, warehouse_id, status, calc_days, lead_time_days, safety_days, created_by, remark)
VALUES
    ('rp202602260001', 1, 0, 15, 3, 2, 1, 'M6联调默认建议计划（草稿）')
ON DUPLICATE KEY UPDATE
    warehouse_id = VALUES(warehouse_id),
    status = VALUES(status),
    calc_days = VALUES(calc_days),
    lead_time_days = VALUES(lead_time_days),
    safety_days = VALUES(safety_days),
    created_by = VALUES(created_by),
    remark = VALUES(remark),
    updated_at = CURRENT_TIMESTAMP;

-- ==================== M6：补货建议明细初始化 ====================
-- 规则：建议量 = max(0, 安全库存 + 预测周期需求 - 当前库存)
INSERT INTO wms_replenishment_item
    (plan_id, sku_id, current_qty, safe_qty, predicted_daily_sales, predicted_total_qty,
     shortage_qty, suggested_qty, final_qty, reco_source, confidence, reason)
SELECT p.id AS plan_id,
       s.id AS sku_id,
       COALESCE(st.on_hand_qty, 0) AS current_qty,
       COALESCE(ar.safe_qty, s.safe_stock, 0) AS safe_qty,
       COALESCE(d.avg_daily_sales, 0.00) AS predicted_daily_sales,
       CEIL(COALESCE(d.avg_daily_sales, 0.00) * (p.lead_time_days + p.safety_days)) AS predicted_total_qty,
       GREATEST(
           0,
           COALESCE(ar.safe_qty, s.safe_stock, 0)
           + CEIL(COALESCE(d.avg_daily_sales, 0.00) * (p.lead_time_days + p.safety_days))
           - COALESCE(st.on_hand_qty, 0)
       ) AS shortage_qty,
       GREATEST(
           0,
           COALESCE(ar.safe_qty, s.safe_stock, 0)
           + CEIL(COALESCE(d.avg_daily_sales, 0.00) * (p.lead_time_days + p.safety_days))
           - COALESCE(st.on_hand_qty, 0)
       ) AS suggested_qty,
       GREATEST(
           0,
           COALESCE(ar.safe_qty, s.safe_stock, 0)
           + CEIL(COALESCE(d.avg_daily_sales, 0.00) * (p.lead_time_days + p.safety_days))
           - COALESCE(st.on_hand_qty, 0)
       ) AS final_qty,
       CASE
           WHEN rp.related_sku_id IS NULL THEN NULL
           ELSE CONCAT('SKU', rp.related_sku_id)
       END AS reco_source,
       rp.confidence,
       CASE
           WHEN GREATEST(
               0,
               COALESCE(ar.safe_qty, s.safe_stock, 0)
               + CEIL(COALESCE(d.avg_daily_sales, 0.00) * (p.lead_time_days + p.safety_days))
               - COALESCE(st.on_hand_qty, 0)
           ) > 0
           THEN '预测覆盖不足，建议补货'
           ELSE '库存覆盖充足，建议观察'
       END AS reason
FROM wms_replenishment_plan p
JOIN wms_sku s
    ON s.status = 1
LEFT JOIN wms_inventory_stock st
    ON st.warehouse_id = p.warehouse_id
   AND st.sku_id = s.id
LEFT JOIN wms_stock_alert_rule ar
    ON ar.warehouse_id = p.warehouse_id
   AND ar.sku_id = s.id
   AND ar.status = 1
LEFT JOIN (
    SELECT warehouse_id,
           sku_id,
           ROUND(AVG(outbound_qty), 2) AS avg_daily_sales
    FROM wms_sales_daily
    WHERE stat_date >= DATE_SUB(CURDATE(), INTERVAL 15 DAY)
    GROUP BY warehouse_id, sku_id
) d
    ON d.warehouse_id = p.warehouse_id
   AND d.sku_id = s.id
LEFT JOIN (
    SELECT r1.sku_id, r1.related_sku_id, r1.confidence
    FROM wms_reco_pair r1
    JOIN (
        SELECT sku_id, MAX(confidence) AS max_confidence
        FROM wms_reco_pair
        GROUP BY sku_id
    ) top1
        ON top1.sku_id = r1.sku_id
       AND top1.max_confidence = r1.confidence
) rp
    ON rp.sku_id = s.id
WHERE p.plan_no = 'rp202602260001'
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
