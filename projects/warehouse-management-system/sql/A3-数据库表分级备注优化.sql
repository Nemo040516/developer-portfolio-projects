-- A3 数据库表分级备注优化脚本
-- 目标：在不改字段/索引/约束的前提下，仅通过表注释提升可读性。
-- 约束：仅允许操作 wms_db。

USE wms_db;

-- L0：系统基础（登录与权限）
ALTER TABLE sys_user COMMENT = 'L0-系统基础 | 系统用户表';
ALTER TABLE sys_role COMMENT = 'L0-系统基础 | 系统角色表';
ALTER TABLE sys_user_role COMMENT = 'L0-系统基础 | 用户角色关联表';

-- L1：核心主链路（主数据 + 入库 + 总库存）
ALTER TABLE wms_warehouse COMMENT = 'L1-核心主链路 | 仓库主数据表';
ALTER TABLE wms_location COMMENT = 'L1-核心主链路 | 库位主数据表';
ALTER TABLE wms_sku COMMENT = 'L1-核心主链路 | SKU主数据表';
ALTER TABLE wms_supplier COMMENT = 'L1-核心主链路 | 供应商主数据表';
ALTER TABLE wms_inbound_order COMMENT = 'L1-核心主链路 | 入库单主表';
ALTER TABLE wms_inbound_item COMMENT = 'L1-核心主链路 | 入库单明细表';
ALTER TABLE wms_inventory_stock COMMENT = 'L1-核心主链路 | 库存汇总表';
ALTER TABLE wms_inventory_txn COMMENT = 'L1-核心主链路 | 库存流水表';

-- L2：核心扩展（上架/出库/盘点/预警）
ALTER TABLE wms_putaway_order COMMENT = 'L2-核心扩展 | 上架单主表';
ALTER TABLE wms_putaway_item COMMENT = 'L2-核心扩展 | 上架单明细表';
ALTER TABLE wms_location_stock COMMENT = 'L2-核心扩展 | 库位库存汇总表';
ALTER TABLE wms_location_txn COMMENT = 'L2-核心扩展 | 库位库存流水表';
ALTER TABLE wms_outbound_order COMMENT = 'L2-核心扩展 | 出库单主表';
ALTER TABLE wms_outbound_item COMMENT = 'L2-核心扩展 | 出库单明细表';
ALTER TABLE wms_stock_alert_rule COMMENT = 'L2-核心扩展 | 库存预警规则表';
ALTER TABLE wms_stocktake_order COMMENT = 'L2-核心扩展 | 盘点单主表';
ALTER TABLE wms_stocktake_item COMMENT = 'L2-核心扩展 | 盘点单明细表';

-- L3：扩展能力（智能订货）
ALTER TABLE wms_replenishment_plan COMMENT = 'L3-扩展能力 | 补货建议主表';
ALTER TABLE wms_replenishment_item COMMENT = 'L3-扩展能力 | 补货建议明细表';
ALTER TABLE wms_sales_daily COMMENT = 'L3-扩展能力 | 销量日聚合表';
ALTER TABLE wms_reco_pair COMMENT = 'L3-扩展能力 | SKU关联推荐对';

-- 快速核验
SELECT table_name, table_comment
FROM information_schema.tables
WHERE table_schema = 'wms_db'
ORDER BY table_name;
