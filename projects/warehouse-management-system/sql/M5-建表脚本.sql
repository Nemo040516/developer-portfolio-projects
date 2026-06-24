-- =========================================================
-- 智能仓库订货系统（M5）建表脚本
-- 说明：
-- 1) 本脚本仅操作 wms_db，不会改动其他数据库。
-- 2) 本脚本用于库存预警与盘点差异修正的最小闭环。
-- 3) 严禁在 sme_recruitment_db 等独立项目库执行本脚本。
-- =========================================================

USE wms_db;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ==================== M5：库存预警规则表 ====================
CREATE TABLE IF NOT EXISTS wms_stock_alert_rule (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '预警规则ID',
    warehouse_id BIGINT UNSIGNED NOT NULL COMMENT '仓库ID',
    sku_id BIGINT UNSIGNED NOT NULL COMMENT 'SKU ID',
    min_qty INT NOT NULL DEFAULT 0 COMMENT '预警下限',
    safe_qty INT NOT NULL DEFAULT 0 COMMENT '安全库存',
    max_qty INT NOT NULL DEFAULT 999999 COMMENT '预警上限（超储阈值）',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0停用',
    remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
    created_by BIGINT UNSIGNED DEFAULT NULL COMMENT '创建人ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wms_stock_alert_rule_wh_sku (warehouse_id, sku_id),
    KEY idx_wms_stock_alert_rule_sku_id (sku_id),
    KEY idx_wms_stock_alert_rule_status (status),
    CONSTRAINT fk_wms_stock_alert_rule_wh_id FOREIGN KEY (warehouse_id) REFERENCES wms_warehouse (id),
    CONSTRAINT fk_wms_stock_alert_rule_sku_id FOREIGN KEY (sku_id) REFERENCES wms_sku (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存预警规则表';

-- ==================== M5：盘点单主表 ====================
CREATE TABLE IF NOT EXISTS wms_stocktake_order (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '盘点单ID',
    stocktake_no VARCHAR(64) NOT NULL COMMENT '盘点单号',
    warehouse_id BIGINT UNSIGNED NOT NULL COMMENT '仓库ID',
    scope_type VARCHAR(32) NOT NULL DEFAULT 'BY_WAREHOUSE' COMMENT '盘点范围类型',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0草稿，1已提交，2已完成',
    remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
    created_by BIGINT UNSIGNED DEFAULT NULL COMMENT '创建人ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wms_stocktake_order_no (stocktake_no),
    KEY idx_wms_stocktake_order_wh_id (warehouse_id),
    KEY idx_wms_stocktake_order_status (status),
    CONSTRAINT fk_wms_stocktake_order_wh_id FOREIGN KEY (warehouse_id) REFERENCES wms_warehouse (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='盘点单主表';

-- ==================== M5：盘点单明细表 ====================
CREATE TABLE IF NOT EXISTS wms_stocktake_item (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '盘点明细ID',
    stocktake_order_id BIGINT UNSIGNED NOT NULL COMMENT '盘点单ID',
    sku_id BIGINT UNSIGNED NOT NULL COMMENT 'SKU ID',
    location_id BIGINT UNSIGNED NOT NULL COMMENT '库位ID',
    book_qty INT NOT NULL DEFAULT 0 COMMENT '账面数量',
    count_qty INT NOT NULL DEFAULT 0 COMMENT '实盘数量',
    diff_qty INT NOT NULL DEFAULT 0 COMMENT '差异数量（实盘-账面）',
    reason VARCHAR(128) DEFAULT NULL COMMENT '差异原因',
    remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wms_stocktake_item_order_sku_loc (stocktake_order_id, sku_id, location_id),
    KEY idx_wms_stocktake_item_sku_id (sku_id),
    KEY idx_wms_stocktake_item_location_id (location_id),
    CONSTRAINT fk_wms_stocktake_item_order_id FOREIGN KEY (stocktake_order_id) REFERENCES wms_stocktake_order (id),
    CONSTRAINT fk_wms_stocktake_item_sku_id FOREIGN KEY (sku_id) REFERENCES wms_sku (id),
    CONSTRAINT fk_wms_stocktake_item_location_id FOREIGN KEY (location_id) REFERENCES wms_location (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='盘点单明细表';

SET FOREIGN_KEY_CHECKS = 1;

