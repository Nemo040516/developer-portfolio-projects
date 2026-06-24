-- =========================================================
-- 智能仓库订货系统（M1）数据库优化与隔离脚本
-- 执行目标库：wms_db
-- 重要红线：严禁对其他项目数据库执行任何 DDL/DML
-- =========================================================

USE wms_db;

-- 1) 为高频状态筛选场景补充索引（若不存在则创建）
SET @schema_name = DATABASE();

SET @sql = IF(
    (SELECT COUNT(1) FROM information_schema.statistics WHERE table_schema = @schema_name AND table_name = 'sys_user' AND index_name = 'idx_sys_user_status') = 0,
    'ALTER TABLE sys_user ADD INDEX idx_sys_user_status (status)',
    'SELECT ''idx_sys_user_status already exists'' AS info'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(1) FROM information_schema.statistics WHERE table_schema = @schema_name AND table_name = 'sys_role' AND index_name = 'idx_sys_role_status') = 0,
    'ALTER TABLE sys_role ADD INDEX idx_sys_role_status (status)',
    'SELECT ''idx_sys_role_status already exists'' AS info'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(1) FROM information_schema.statistics WHERE table_schema = @schema_name AND table_name = 'wms_warehouse' AND index_name = 'idx_wms_warehouse_status') = 0,
    'ALTER TABLE wms_warehouse ADD INDEX idx_wms_warehouse_status (status)',
    'SELECT ''idx_wms_warehouse_status already exists'' AS info'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(1) FROM information_schema.statistics WHERE table_schema = @schema_name AND table_name = 'wms_location' AND index_name = 'idx_wms_location_status') = 0,
    'ALTER TABLE wms_location ADD INDEX idx_wms_location_status (status)',
    'SELECT ''idx_wms_location_status already exists'' AS info'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(1) FROM information_schema.statistics WHERE table_schema = @schema_name AND table_name = 'wms_sku' AND index_name = 'idx_wms_sku_status') = 0,
    'ALTER TABLE wms_sku ADD INDEX idx_wms_sku_status (status)',
    'SELECT ''idx_wms_sku_status already exists'' AS info'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF(
    (SELECT COUNT(1) FROM information_schema.statistics WHERE table_schema = @schema_name AND table_name = 'wms_supplier' AND index_name = 'idx_wms_supplier_status') = 0,
    'ALTER TABLE wms_supplier ADD INDEX idx_wms_supplier_status (status)',
    'SELECT ''idx_wms_supplier_status already exists'' AS info'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) 更新统计信息并优化表空间（仅 wms_db）
ANALYZE TABLE sys_user, sys_role, sys_user_role, wms_warehouse, wms_location, wms_sku, wms_supplier;
OPTIMIZE TABLE sys_user, sys_role, sys_user_role, wms_warehouse, wms_location, wms_sku, wms_supplier;

-- 3) 输出优化后的索引清单（便于核验）
SHOW INDEX FROM sys_user;
SHOW INDEX FROM sys_role;
SHOW INDEX FROM wms_warehouse;
SHOW INDEX FROM wms_location;
SHOW INDEX FROM wms_sku;
SHOW INDEX FROM wms_supplier;

