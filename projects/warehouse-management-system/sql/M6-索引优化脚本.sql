-- =========================================================
-- 智能仓库订货系统（M6）索引优化脚本
-- 说明：
-- 1) 本脚本仅操作 wms_db，不会改动其他数据库。
-- 2) 本脚本用于已有环境的索引增量优化（不会改字段/删数据）。
-- 3) 严禁在 sme_recruitment_db 等独立项目库执行本脚本。
-- =========================================================

USE wms_db;

SET @db_name = 'wms_db';

-- ==================== wms_replenishment_plan ====================
SELECT COUNT(1) INTO @idx_exists
FROM information_schema.statistics
WHERE table_schema = @db_name
  AND table_name = 'wms_replenishment_plan'
  AND index_name = 'idx_wms_replenishment_plan_status_id';
SET @sql = IF(
    @idx_exists = 0,
    'ALTER TABLE wms_replenishment_plan ADD INDEX idx_wms_replenishment_plan_status_id (status, id)',
    'SELECT ''skip idx_wms_replenishment_plan_status_id'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @idx_exists
FROM information_schema.statistics
WHERE table_schema = @db_name
  AND table_name = 'wms_replenishment_plan'
  AND index_name = 'idx_wms_replenishment_plan_generated_at';
SET @sql = IF(
    @idx_exists = 0,
    'ALTER TABLE wms_replenishment_plan ADD INDEX idx_wms_replenishment_plan_generated_at (generated_at)',
    'SELECT ''skip idx_wms_replenishment_plan_generated_at'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT COUNT(1) INTO @idx_exists
FROM information_schema.statistics
WHERE table_schema = @db_name
  AND table_name = 'wms_replenishment_plan'
  AND index_name = 'idx_wms_replenishment_plan_wh_status_id';
SET @sql = IF(
    @idx_exists = 0,
    'ALTER TABLE wms_replenishment_plan ADD INDEX idx_wms_replenishment_plan_wh_status_id (warehouse_id, status, id)',
    'SELECT ''skip idx_wms_replenishment_plan_wh_status_id'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ==================== wms_replenishment_item ====================
SELECT COUNT(1) INTO @idx_exists
FROM information_schema.statistics
WHERE table_schema = @db_name
  AND table_name = 'wms_replenishment_item'
  AND index_name = 'idx_wms_replenishment_item_plan_id_id';
SET @sql = IF(
    @idx_exists = 0,
    'ALTER TABLE wms_replenishment_item ADD INDEX idx_wms_replenishment_item_plan_id_id (plan_id, id)',
    'SELECT ''skip idx_wms_replenishment_item_plan_id_id'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ==================== wms_sales_daily ====================
SELECT COUNT(1) INTO @idx_exists
FROM information_schema.statistics
WHERE table_schema = @db_name
  AND table_name = 'wms_sales_daily'
  AND index_name = 'idx_wms_sales_daily_wh_date_sku';
SET @sql = IF(
    @idx_exists = 0,
    'ALTER TABLE wms_sales_daily ADD INDEX idx_wms_sales_daily_wh_date_sku (warehouse_id, stat_date, sku_id)',
    'SELECT ''skip idx_wms_sales_daily_wh_date_sku'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ==================== wms_reco_pair ====================
SELECT COUNT(1) INTO @idx_exists
FROM information_schema.statistics
WHERE table_schema = @db_name
  AND table_name = 'wms_reco_pair'
  AND index_name = 'idx_wms_reco_pair_sku_conf_support_id';
SET @sql = IF(
    @idx_exists = 0,
    'ALTER TABLE wms_reco_pair ADD INDEX idx_wms_reco_pair_sku_conf_support_id (sku_id, confidence, support_count, id)',
    'SELECT ''skip idx_wms_reco_pair_sku_conf_support_id'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

