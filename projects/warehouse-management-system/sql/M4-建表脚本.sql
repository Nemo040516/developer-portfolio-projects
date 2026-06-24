-- =========================================================
-- 智能仓库订货系统（M4）建表脚本
-- 说明：
-- 1) 本脚本仅操作 wms_db，不会改动其他数据库。
-- 2) 本脚本用于出库管理与库存扣减追溯的最小闭环。
-- 3) 严禁在 sme_recruitment_db 等独立项目库执行本脚本。
-- =========================================================

USE wms_db;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ==================== M4：出库单主表 ====================
CREATE TABLE IF NOT EXISTS wms_outbound_order (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '出库单ID',
    outbound_no VARCHAR(64) NOT NULL COMMENT '出库单号',
    outbound_type VARCHAR(32) NOT NULL DEFAULT 'SALES' COMMENT '出库类型（SALES/TRANSFER/OTHER）',
    target_name VARCHAR(128) DEFAULT NULL COMMENT '目标客户/部门名称',
    warehouse_id BIGINT UNSIGNED NOT NULL COMMENT '仓库ID',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0草稿，1已提交，2已完成',
    remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
    created_by BIGINT UNSIGNED DEFAULT NULL COMMENT '创建人ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wms_outbound_order_no (outbound_no),
    KEY idx_wms_outbound_order_wh_id (warehouse_id),
    KEY idx_wms_outbound_order_status (status),
    CONSTRAINT fk_wms_outbound_order_wh_id FOREIGN KEY (warehouse_id) REFERENCES wms_warehouse (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='出库单主表';

-- ==================== M4：出库单明细表 ====================
CREATE TABLE IF NOT EXISTS wms_outbound_item (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '出库明细ID',
    outbound_order_id BIGINT UNSIGNED NOT NULL COMMENT '出库单ID',
    sku_id BIGINT UNSIGNED NOT NULL COMMENT 'SKU ID',
    location_id BIGINT UNSIGNED NOT NULL COMMENT '出库库位ID',
    plan_qty INT NOT NULL DEFAULT 0 COMMENT '计划出库数量',
    actual_qty INT NOT NULL DEFAULT 0 COMMENT '实出数量',
    remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wms_outbound_item_order_sku_loc (outbound_order_id, sku_id, location_id),
    KEY idx_wms_outbound_item_sku_id (sku_id),
    KEY idx_wms_outbound_item_location_id (location_id),
    CONSTRAINT fk_wms_outbound_item_order_id FOREIGN KEY (outbound_order_id) REFERENCES wms_outbound_order (id),
    CONSTRAINT fk_wms_outbound_item_sku_id FOREIGN KEY (sku_id) REFERENCES wms_sku (id),
    CONSTRAINT fk_wms_outbound_item_location_id FOREIGN KEY (location_id) REFERENCES wms_location (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='出库单明细表';

SET FOREIGN_KEY_CHECKS = 1;

