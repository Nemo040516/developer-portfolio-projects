-- =========================================================
-- 智能仓库订货系统（M1）初始化数据脚本
-- 说明：
-- 1) 本脚本仅初始化最小演示数据，方便前后端联调。
-- 2) 新同学下载后请立即修改默认密码。
-- 3) 生产环境请禁用演示账号并改用安全密码策略。
-- =========================================================

USE wms_db;

-- 角色初始化
INSERT INTO sys_role (id, role_code, role_name, status, remark)
VALUES
    (1, 'ADMIN', '管理员', 1, '系统最高权限'),
    (2, 'WAREHOUSE', '仓库员', 1, '仓内作业角色'),
    (3, 'PURCHASER', '采购员', 1, '采购与补货角色')
ON DUPLICATE KEY UPDATE
    role_name = VALUES(role_name),
    status = VALUES(status),
    remark = VALUES(remark);

-- 用户初始化（开发演示账号）
INSERT INTO sys_user (id, username, password, real_name, status)
VALUES
    (1, 'admin', '{noop}12345', '系统管理员', 1),
    (2, 'warehouse', '{noop}12345', '仓库专员', 1),
    (3, 'purchaser', '{noop}12345', '采购专员', 1)
ON DUPLICATE KEY UPDATE
    password = VALUES(password),
    real_name = VALUES(real_name),
    status = VALUES(status);

-- 用户角色关联
INSERT INTO sys_user_role (id, user_id, role_id)
VALUES
    (1, 1, 1),
    (2, 2, 2),
    (3, 3, 3)
ON DUPLICATE KEY UPDATE
    user_id = VALUES(user_id),
    role_id = VALUES(role_id);

-- 基础资料初始化（最小样例）
INSERT INTO wms_warehouse (id, warehouse_code, warehouse_name, address, manager_name, contact_phone, status, remark)
VALUES
    (1, 'WH001', '一号主仓', '深圳市南山区示例路100号', '张三', '13800000001', 1, 'M1联调默认仓库')
ON DUPLICATE KEY UPDATE
    warehouse_name = VALUES(warehouse_name),
    address = VALUES(address),
    manager_name = VALUES(manager_name),
    contact_phone = VALUES(contact_phone),
    status = VALUES(status),
    remark = VALUES(remark);

INSERT INTO wms_location (id, warehouse_id, location_code, area_name, location_type, capacity, status, remark)
VALUES
    (1, 1, 'A01-01', 'A区', '标准货架位', 100.00, 1, 'M1联调默认库位')
ON DUPLICATE KEY UPDATE
    warehouse_id = VALUES(warehouse_id),
    area_name = VALUES(area_name),
    location_type = VALUES(location_type),
    capacity = VALUES(capacity),
    status = VALUES(status),
    remark = VALUES(remark);

INSERT INTO wms_sku (id, sku_code, sku_name, specification, unit, safe_stock, status, remark)
VALUES
    (1, 'SKU001', '演示商品-螺丝', 'M4*20', '盒', 50, 1, 'M1联调默认SKU')
ON DUPLICATE KEY UPDATE
    sku_name = VALUES(sku_name),
    specification = VALUES(specification),
    unit = VALUES(unit),
    safe_stock = VALUES(safe_stock),
    status = VALUES(status),
    remark = VALUES(remark);

INSERT INTO wms_supplier (id, supplier_code, supplier_name, contact_name, contact_phone, lead_time_days, status, remark)
VALUES
    (1, 'SUP001', '演示供应商A', '李四', '13900000002', 3, 1, 'M1联调默认供应商')
ON DUPLICATE KEY UPDATE
    supplier_name = VALUES(supplier_name),
    contact_name = VALUES(contact_name),
    contact_phone = VALUES(contact_phone),
    lead_time_days = VALUES(lead_time_days),
    status = VALUES(status),
    remark = VALUES(remark);
