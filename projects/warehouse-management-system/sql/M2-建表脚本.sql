-- =========================================================
-- 智能仓库订货系统（M2）建表脚本
-- 说明：
-- 1) 本脚本仅操作 wms_db，不会改动其他数据库。
-- 2) 本脚本用于入库管理与库存变动追溯的最小闭环。
-- 3) 严禁在 其他独立项目库执行本脚本。
-- =========================================================

USE wms_db;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ==================== M2：入库单主表 ====================
CREATE TABLE IF NOT EXISTS wms_inbound_order (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '入库单ID',
    inbound_no VARCHAR(64) NOT NULL COMMENT '入库单号',
    supplier_id BIGINT UNSIGNED NOT NULL COMMENT '供应商ID',
    warehouse_id BIGINT UNSIGNED NOT NULL COMMENT '仓库ID',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0草稿，1已提交，2已完成',
    remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
    created_by BIGINT UNSIGNED DEFAULT NULL COMMENT '创建人ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wms_inbound_order_no (inbound_no),
    KEY idx_wms_inbound_order_supplier_id (supplier_id),
    KEY idx_wms_inbound_order_warehouse_id (warehouse_id),
    KEY idx_wms_inbound_order_status (status),
    CONSTRAINT fk_wms_inbound_order_supplier_id FOREIGN KEY (supplier_id) REFERENCES wms_supplier (id),
    CONSTRAINT fk_wms_inbound_order_warehouse_id FOREIGN KEY (warehouse_id) REFERENCES wms_warehouse (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='入库单主表';

-- ==================== M2：入库单明细表 ====================
CREATE TABLE IF NOT EXISTS wms_inbound_item (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '入库明细ID',
    inbound_order_id BIGINT UNSIGNED NOT NULL COMMENT '入库单ID',
    sku_id BIGINT UNSIGNED NOT NULL COMMENT 'SKU ID',
    plan_qty INT NOT NULL DEFAULT 0 COMMENT '计划入库数量',
    received_qty INT NOT NULL DEFAULT 0 COMMENT '实收数量',
    remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_wms_inbound_item_order_id (inbound_order_id),
    KEY idx_wms_inbound_item_sku_id (sku_id),
    CONSTRAINT fk_wms_inbound_item_order_id FOREIGN KEY (inbound_order_id) REFERENCES wms_inbound_order (id),
    CONSTRAINT fk_wms_inbound_item_sku_id FOREIGN KEY (sku_id) REFERENCES wms_sku (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='入库单明细表';

-- ==================== M2：库存汇总表 ====================
CREATE TABLE IF NOT EXISTS wms_inventory_stock (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '库存主键ID',
    warehouse_id BIGINT UNSIGNED NOT NULL COMMENT '仓库ID',
    sku_id BIGINT UNSIGNED NOT NULL COMMENT 'SKU ID',
    on_hand_qty INT NOT NULL DEFAULT 0 COMMENT '现存数量',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wms_inventory_stock_wh_sku (warehouse_id, sku_id),
    KEY idx_wms_inventory_stock_sku_id (sku_id),
    CONSTRAINT fk_wms_inventory_stock_wh_id FOREIGN KEY (warehouse_id) REFERENCES wms_warehouse (id),
    CONSTRAINT fk_wms_inventory_stock_sku_id FOREIGN KEY (sku_id) REFERENCES wms_sku (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存汇总表';

-- ==================== M2：库存流水表 ====================
CREATE TABLE IF NOT EXISTS wms_inventory_txn (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '库存流水ID',
    biz_type VARCHAR(32) NOT NULL COMMENT '业务类型（INBOUND/OUTBOUND/ADJUST等）',
    biz_no VARCHAR(64) NOT NULL COMMENT '业务单号',
    biz_id BIGINT UNSIGNED DEFAULT NULL COMMENT '业务单ID',
    warehouse_id BIGINT UNSIGNED NOT NULL COMMENT '仓库ID',
    sku_id BIGINT UNSIGNED NOT NULL COMMENT 'SKU ID',
    qty_change INT NOT NULL COMMENT '变动数量（入库为正，出库为负）',
    before_qty INT NOT NULL COMMENT '变动前数量',
    after_qty INT NOT NULL COMMENT '变动后数量',
    operator_id BIGINT UNSIGNED DEFAULT NULL COMMENT '操作人ID',
    operator_name VARCHAR(64) DEFAULT NULL COMMENT '操作人姓名',
    occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    KEY idx_wms_inventory_txn_biz_no (biz_no),
    KEY idx_wms_inventory_txn_wh_sku (warehouse_id, sku_id),
    KEY idx_wms_inventory_txn_occurred_at (occurred_at),
    CONSTRAINT fk_wms_inventory_txn_wh_id FOREIGN KEY (warehouse_id) REFERENCES wms_warehouse (id),
    CONSTRAINT fk_wms_inventory_txn_sku_id FOREIGN KEY (sku_id) REFERENCES wms_sku (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存流水表';

SET FOREIGN_KEY_CHECKS = 1;
