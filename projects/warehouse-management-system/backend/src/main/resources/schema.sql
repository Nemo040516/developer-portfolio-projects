/*
 * @file 速览索引
 * @summary 后端统一建表脚本，定义 M1-M6 所需核心表结构、约束、索引与外键关系。
 * @core 1. 创建账号权限与主数据表（L0/L1）
 * @core 2. 创建入库/上架/出库/库存流水主链路表（L1/L2）
 * @core 3. 创建预警、盘点、补货建议与销量聚合扩展表（L2/L3）
 * @core 4. 通过主外键与联合索引保障状态机与查询性能
 * @entry 先看：`CREATE TABLE IF NOT EXISTS` 分段、`idx_*` 索引、`fk_*` 外键
 * @deps 依赖：application.yml 启动初始化、data.sql 初始化数据
 * @risk 高风险修改点：外键级联影响、唯一键冲突、索引删改导致查询退化
 * @link 相关文件：后端/src/main/resources/data.sql、SQL脚本/A3-数据库表分级备注优化.sql
 */
-- M1 建表脚本（后端启动自动执行）
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    username VARCHAR(64) NOT NULL COMMENT '登录账号',
    password VARCHAR(255) NOT NULL COMMENT '登录密码（开发环境允许明文前缀，生产建议哈希）',
    real_name VARCHAR(64) DEFAULT NULL COMMENT '真实姓名',
    mobile VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    email VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0禁用',
    last_login_at DATETIME DEFAULT NULL COMMENT '最后登录时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='L0-系统基础 | 系统用户表';

CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '角色ID',
    role_code VARCHAR(64) NOT NULL COMMENT '角色编码',
    role_name VARCHAR(64) NOT NULL COMMENT '角色名称',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0禁用',
    remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='L0-系统基础 | 系统角色表';

CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    user_id BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    role_id BIGINT UNSIGNED NOT NULL COMMENT '角色ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_role_user_role (user_id, role_id),
    KEY idx_sys_user_role_role_id (role_id),
    CONSTRAINT fk_sys_user_role_user_id FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_sys_user_role_role_id FOREIGN KEY (role_id) REFERENCES sys_role (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='L0-系统基础 | 用户角色关联表';

CREATE TABLE IF NOT EXISTS wms_warehouse (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '仓库ID',
    warehouse_code VARCHAR(64) NOT NULL COMMENT '仓库编码',
    warehouse_name VARCHAR(128) NOT NULL COMMENT '仓库名称',
    address VARCHAR(255) DEFAULT NULL COMMENT '仓库地址',
    manager_name VARCHAR(64) DEFAULT NULL COMMENT '负责人',
    contact_phone VARCHAR(20) DEFAULT NULL COMMENT '联系电话',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0停用',
    remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wms_warehouse_code (warehouse_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='L1-核心主链路 | 仓库主数据表';

CREATE TABLE IF NOT EXISTS wms_location (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '库位ID',
    warehouse_id BIGINT UNSIGNED NOT NULL COMMENT '所属仓库ID',
    location_code VARCHAR(64) NOT NULL COMMENT '库位编码',
    area_name VARCHAR(64) DEFAULT NULL COMMENT '库区名称',
    location_type VARCHAR(32) DEFAULT NULL COMMENT '库位类型',
    capacity DECIMAL(12,2) DEFAULT NULL COMMENT '容量上限',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0停用',
    remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wms_location_code (location_code),
    KEY idx_wms_location_warehouse_id (warehouse_id),
    CONSTRAINT fk_wms_location_warehouse_id FOREIGN KEY (warehouse_id) REFERENCES wms_warehouse (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='L1-核心主链路 | 库位主数据表';

CREATE TABLE IF NOT EXISTS wms_sku (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'SKU主键ID',
    sku_code VARCHAR(64) NOT NULL COMMENT 'SKU编码',
    sku_name VARCHAR(128) NOT NULL COMMENT 'SKU名称',
    specification VARCHAR(128) DEFAULT NULL COMMENT '规格型号',
    unit VARCHAR(32) DEFAULT NULL COMMENT '计量单位',
    safe_stock INT NOT NULL DEFAULT 0 COMMENT '安全库存',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0停用',
    remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wms_sku_code (sku_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='L1-核心主链路 | SKU主数据表';

CREATE TABLE IF NOT EXISTS wms_supplier (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '供应商ID',
    supplier_code VARCHAR(64) NOT NULL COMMENT '供应商编码',
    supplier_name VARCHAR(128) NOT NULL COMMENT '供应商名称',
    contact_name VARCHAR(64) DEFAULT NULL COMMENT '联系人',
    contact_phone VARCHAR(20) DEFAULT NULL COMMENT '联系电话',
    lead_time_days INT NOT NULL DEFAULT 0 COMMENT '平均交期（天）',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用，0停用',
    remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wms_supplier_code (supplier_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='L1-核心主链路 | 供应商主数据表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='L1-核心主链路 | 入库单主表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='L1-核心主链路 | 入库单明细表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='L1-核心主链路 | 库存汇总表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='L1-核心主链路 | 库存流水表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='L2-核心扩展 | 上架单主表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='L2-核心扩展 | 上架单明细表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='L2-核心扩展 | 库位库存汇总表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='L2-核心扩展 | 库位库存流水表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='L2-核心扩展 | 出库单主表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='L2-核心扩展 | 出库单明细表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='L2-核心扩展 | 库存预警规则表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='L2-核心扩展 | 盘点单主表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='L2-核心扩展 | 盘点单明细表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='L3-扩展能力 | 补货建议主表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='L3-扩展能力 | 补货建议明细表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='L3-扩展能力 | 销量日聚合表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='L3-扩展能力 | SKU关联推荐对';

SET FOREIGN_KEY_CHECKS = 1;
