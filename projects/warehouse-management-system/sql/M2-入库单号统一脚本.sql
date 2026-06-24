-- M2-入库单号统一脚本
-- 目标：将历史测试数据统一为 inYYYYMMDDNNNN，并同步库存流水 biz_no
-- 作用库：wms_db
-- 注意：本脚本仅更新 wms_db，不涉及 sme_recruitment_db

USE wms_db;

START TRANSACTION;

-- 1) 统一入库单号：按“日期 + id顺序”生成 4 位流水号
UPDATE wms_inbound_order o
JOIN (
    SELECT
        id,
        CONCAT(
            'in',
            DATE_FORMAT(created_at, '%Y%m%d'),
            LPAD(ROW_NUMBER() OVER (PARTITION BY DATE(created_at) ORDER BY id), 4, '0')
        ) AS new_inbound_no
    FROM wms_inbound_order
) n ON n.id = o.id
SET o.inbound_no = n.new_inbound_no;

-- 2) 同步库存流水业务单号，确保与入库单一致
UPDATE wms_inventory_txn t
JOIN wms_inbound_order o ON o.id = t.biz_id
SET t.biz_no = o.inbound_no
WHERE t.biz_type IN ('INBOUND', 'INBOUND_SUBMIT');

COMMIT;
