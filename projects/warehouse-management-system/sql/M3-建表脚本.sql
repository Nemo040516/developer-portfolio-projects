-- =========================================================
-- 智能仓库订货系统（M3）建表脚本
-- 说明：
-- 1) 本脚本仅操作 wms_db，不会改动其他数据库。
-- 2) 本脚本用于上架管理与库位库存追溯的最小闭环。
-- 3) 严禁在 sme_recruitment_db 等独立项目库执行本脚本。
-- =========================================================

USE wms_db;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ==================== M3：上架单主表 ====================
CREATE TABLE IF NOT EXISTS wms_putaway_order (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '上架单ID',
    putaway_no VARCHAR(64) NOT NULL COMMENT '上架单号',
    source_type VARCHAR(32) NOT NULL DEFAULT 'INBOUND' COMMENT '来源类型（INBOUND/OTHER）',
    source_order_id BIGINT UNSIGNED DEFAULT NULL COMMENT '来源单据ID',
    source_order_no VARCHAR(64) DEFAULT NULL COMMENT '来源单号',
    warehouse_id BIGINT UNSIGNED NOT NULL COMMENT '仓库ID',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0草稿，1已提交，2已完成',
    remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
    created_by BIGINT UNSIGNED DEFAULT NULL COMMENT '创建人ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wms_putaway_order_no (putaway_no),
    KEY idx_wms_putaway_order_wh_id (warehouse_id),
    KEY idx_wms_putaway_order_status (status),
    KEY idx_wms_putaway_order_source (source_type, source_order_id),
    CONSTRAINT fk_wms_putaway_order_wh_id FOREIGN KEY (warehouse_id) REFERENCES wms_warehouse (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='上架单主表';

-- ==================== M3：上架单明细表 ====================
CREATE TABLE IF NOT EXISTS wms_putaway_item (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '上架明细ID',
    putaway_order_id BIGINT UNSIGNED NOT NULL COMMENT '上架单ID',
    sku_id BIGINT UNSIGNED NOT NULL COMMENT 'SKU ID',
    location_id BIGINT UNSIGNED NOT NULL COMMENT '目标库位ID',
    plan_qty INT NOT NULL DEFAULT 0 COMMENT '计划上架数量',
    actual_qty INT NOT NULL DEFAULT 0 COMMENT '实上数量',
    remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_wms_putaway_item_order_id (putaway_order_id),
    KEY idx_wms_putaway_item_sku_id (sku_id),
    KEY idx_wms_putaway_item_location_id (location_id),
    CONSTRAINT fk_wms_putaway_item_order_id FOREIGN KEY (putaway_order_id) REFERENCES wms_putaway_order (id),
    CONSTRAINT fk_wms_putaway_item_sku_id FOREIGN KEY (sku_id) REFERENCES wms_sku (id),
    CONSTRAINT fk_wms_putaway_item_location_id FOREIGN KEY (location_id) REFERENCES wms_location (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='上架单明细表';

-- ==================== M3：库位库存汇总表 ====================
CREATE TABLE IF NOT EXISTS wms_location_stock (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '库位库存主键ID',
    warehouse_id BIGINT UNSIGNED NOT NULL COMMENT '仓库ID',
    location_id BIGINT UNSIGNED NOT NULL COMMENT '库位ID',
    sku_id BIGINT UNSIGNED NOT NULL COMMENT 'SKU ID',
    on_hand_qty INT NOT NULL DEFAULT 0 COMMENT '现存数量',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wms_location_stock_wh_loc_sku (warehouse_id, location_id, sku_id),
    KEY idx_wms_location_stock_sku_id (sku_id),
    KEY idx_wms_location_stock_loc_id (location_id),
    CONSTRAINT fk_wms_location_stock_wh_id FOREIGN KEY (warehouse_id) REFERENCES wms_warehouse (id),
    CONSTRAINT fk_wms_location_stock_loc_id FOREIGN KEY (location_id) REFERENCES wms_location (id),
    CONSTRAINT fk_wms_location_stock_sku_id FOREIGN KEY (sku_id) REFERENCES wms_sku (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库位库存汇总表';

-- ==================== M3：库位库存流水表 ====================
CREATE TABLE IF NOT EXISTS wms_location_txn (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '库位流水ID',
    biz_type VARCHAR(32) NOT NULL COMMENT '业务类型（PUTAWAY/PUTAWAY_SUBMIT等）',
    biz_no VARCHAR(64) NOT NULL COMMENT '业务单号',
    biz_id BIGINT UNSIGNED DEFAULT NULL COMMENT '业务单ID',
    warehouse_id BIGINT UNSIGNED NOT NULL COMMENT '仓库ID',
    location_id BIGINT UNSIGNED NOT NULL COMMENT '库位ID',
    sku_id BIGINT UNSIGNED NOT NULL COMMENT 'SKU ID',
    qty_change INT NOT NULL COMMENT '变动数量（上架为正，下架为负）',
    before_qty INT NOT NULL COMMENT '变动前数量',
    after_qty INT NOT NULL COMMENT '变动后数量',
    operator_id BIGINT UNSIGNED DEFAULT NULL COMMENT '操作人ID',
    operator_name VARCHAR(64) DEFAULT NULL COMMENT '操作人姓名',
    occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    KEY idx_wms_location_txn_biz_no (biz_no),
    KEY idx_wms_location_txn_wh_loc_sku (warehouse_id, location_id, sku_id),
    KEY idx_wms_location_txn_occurred_at (occurred_at),
    CONSTRAINT fk_wms_location_txn_wh_id FOREIGN KEY (warehouse_id) REFERENCES wms_warehouse (id),
    CONSTRAINT fk_wms_location_txn_loc_id FOREIGN KEY (location_id) REFERENCES wms_location (id),
    CONSTRAINT fk_wms_location_txn_sku_id FOREIGN KEY (sku_id) REFERENCES wms_sku (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库位库存流水表';

SET FOREIGN_KEY_CHECKS = 1;

