-- =========================================================
-- 智能仓库订货系统（M6）建表脚本
-- 说明：
-- 1) 本脚本仅操作 wms_db，不会改动其他数据库。
-- 2) 本脚本用于智能补货建议最小闭环（建议主表、明细、销量日聚合、关联推荐对）。
-- 3) 严禁在 其他独立项目库执行本脚本。
-- =========================================================

USE wms_db;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ==================== M6：补货建议主表 ====================
CREATE TABLE IF NOT EXISTS wms_replenishment_plan (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '补货建议计划ID',
    plan_no VARCHAR(64) NOT NULL COMMENT '补货建议计划号',
    warehouse_id BIGINT UNSIGNED NOT NULL COMMENT '仓库ID',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0草稿，1已确认，2已转采购草稿',
    calc_days INT NOT NULL DEFAULT 15 COMMENT '销量计算窗口天数',
    lead_time_days INT NOT NULL DEFAULT 3 COMMENT '供应交期天数',
    safety_days INT NOT NULL DEFAULT 2 COMMENT '安全覆盖天数',
    purchase_draft_no VARCHAR(64) DEFAULT NULL COMMENT '采购草稿号（V1）',
    generated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
    created_by BIGINT UNSIGNED DEFAULT NULL COMMENT '创建人ID',
    remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wms_replenishment_plan_no (plan_no),
    UNIQUE KEY uk_wms_replenishment_plan_purchase_no (purchase_draft_no),
    KEY idx_wms_replenishment_plan_wh_id (warehouse_id),
    KEY idx_wms_replenishment_plan_status (status),
    KEY idx_wms_replenishment_plan_status_id (status, id),
    KEY idx_wms_replenishment_plan_generated_at (generated_at),
    KEY idx_wms_replenishment_plan_wh_status_id (warehouse_id, status, id),
    CONSTRAINT fk_wms_replenishment_plan_wh_id FOREIGN KEY (warehouse_id) REFERENCES wms_warehouse (id),
    CONSTRAINT fk_wms_replenishment_plan_created_by FOREIGN KEY (created_by) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='补货建议主表';

-- ==================== M6：补货建议明细表 ====================
CREATE TABLE IF NOT EXISTS wms_replenishment_item (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '补货建议明细ID',
    plan_id BIGINT UNSIGNED NOT NULL COMMENT '补货建议计划ID',
    sku_id BIGINT UNSIGNED NOT NULL COMMENT 'SKU ID',
    current_qty INT NOT NULL DEFAULT 0 COMMENT '当前库存',
    safe_qty INT NOT NULL DEFAULT 0 COMMENT '安全库存基线',
    predicted_daily_sales DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '预测日均销量',
    predicted_total_qty INT NOT NULL DEFAULT 0 COMMENT '预测周期总需求',
    shortage_qty INT NOT NULL DEFAULT 0 COMMENT '库存缺口',
    suggested_qty INT NOT NULL DEFAULT 0 COMMENT '系统建议补货量',
    final_qty INT NOT NULL DEFAULT 0 COMMENT '最终补货量（可人工调整）',
    reco_source VARCHAR(64) DEFAULT NULL COMMENT '协同推荐来源',
    confidence DECIMAL(6,4) DEFAULT NULL COMMENT '推荐置信度',
    reason VARCHAR(255) DEFAULT NULL COMMENT '建议理由',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wms_replenishment_item_plan_sku (plan_id, sku_id),
    KEY idx_wms_replenishment_item_sku_id (sku_id),
    KEY idx_wms_replenishment_item_plan_id_id (plan_id, id),
    CONSTRAINT fk_wms_replenishment_item_plan_id FOREIGN KEY (plan_id) REFERENCES wms_replenishment_plan (id),
    CONSTRAINT fk_wms_replenishment_item_sku_id FOREIGN KEY (sku_id) REFERENCES wms_sku (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='补货建议明细表';

-- ==================== M6：销量日聚合表（轻量预测输入） ====================
CREATE TABLE IF NOT EXISTS wms_sales_daily (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '销量日聚合ID',
    stat_date DATE NOT NULL COMMENT '统计日期',
    warehouse_id BIGINT UNSIGNED NOT NULL COMMENT '仓库ID',
    sku_id BIGINT UNSIGNED NOT NULL COMMENT 'SKU ID',
    outbound_qty INT NOT NULL DEFAULT 0 COMMENT '当日出库销量',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wms_sales_daily_date_wh_sku (stat_date, warehouse_id, sku_id),
    KEY idx_wms_sales_daily_wh_sku (warehouse_id, sku_id),
    KEY idx_wms_sales_daily_wh_date_sku (warehouse_id, stat_date, sku_id),
    CONSTRAINT fk_wms_sales_daily_wh_id FOREIGN KEY (warehouse_id) REFERENCES wms_warehouse (id),
    CONSTRAINT fk_wms_sales_daily_sku_id FOREIGN KEY (sku_id) REFERENCES wms_sku (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销量日聚合表';

-- ==================== M6：SKU 关联推荐对（轻量协同输入） ====================
CREATE TABLE IF NOT EXISTS wms_reco_pair (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '推荐对ID',
    sku_id BIGINT UNSIGNED NOT NULL COMMENT '主SKU ID',
    related_sku_id BIGINT UNSIGNED NOT NULL COMMENT '关联SKU ID',
    support_count INT NOT NULL DEFAULT 0 COMMENT '共同出现次数',
    confidence DECIMAL(6,4) NOT NULL DEFAULT 0.0000 COMMENT '置信度',
    lift DECIMAL(8,4) DEFAULT NULL COMMENT '提升度（预留）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wms_reco_pair_sku_related (sku_id, related_sku_id),
    KEY idx_wms_reco_pair_related (related_sku_id),
    KEY idx_wms_reco_pair_sku_conf_support_id (sku_id, confidence, support_count, id),
    CONSTRAINT fk_wms_reco_pair_sku_id FOREIGN KEY (sku_id) REFERENCES wms_sku (id),
    CONSTRAINT fk_wms_reco_pair_related_sku_id FOREIGN KEY (related_sku_id) REFERENCES wms_sku (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SKU关联推荐对';

SET FOREIGN_KEY_CHECKS = 1;
