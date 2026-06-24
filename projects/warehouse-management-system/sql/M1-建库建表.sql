-- =========================================================
-- 智能仓库订货系统（M1）建库建表脚本
-- 说明：
-- 1) 本脚本仅操作 wms_db，不会改动其他数据库。
-- 2) 表命名遵循 snake_case，主键统一为 BIGINT。
-- 3) 所有核心字段保留中文注释，便于教学与汇报。
-- =========================================================

CREATE DATABASE IF NOT EXISTS wms_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE wms_db;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ==================== M1：账号与权限 ====================
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统角色表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- ==================== M1：基础资料 ====================
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='仓库主数据表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库位主数据表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SKU主数据表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商主数据表';

SET FOREIGN_KEY_CHECKS = 1;
