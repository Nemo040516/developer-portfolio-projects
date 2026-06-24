SET NAMES utf8mb4;

DELETE FROM sys_log_audit;
DELETE FROM sys_user;

ALTER TABLE sys_log_audit AUTO_INCREMENT = 1;
ALTER TABLE sys_user AUTO_INCREMENT = 1;

INSERT INTO sys_user (
    id, username, password, nickname, phone, email, avatar, role, status, ban_status, ban_reason, ban_until, create_time, update_time
) VALUES
    (1, 'admin1', '12345', '管理员', '13800000001', 'admin1@example.com', NULL, 'ADMIN', 1, 0, NULL, NULL, TIMESTAMP(CURDATE(), '08:00:00'), TIMESTAMP(CURDATE(), '08:00:00'));

INSERT INTO sys_log_audit (module, action, target_id, operator_id, operator_role, detail, create_time)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 35
)
SELECT
    'JOB',
    CASE WHEN MOD(n, 2) = 0 THEN 'AUDIT' ELSE 'REVOKE' END,
    101,
    1,
    'ADMIN',
    CONCAT('JOB-101 日志#', LPAD(n, 2, '0')),
    TIMESTAMPADD(MINUTE, n, TIMESTAMP(CURDATE(), '08:00:00'))
FROM seq;

INSERT INTO sys_log_audit (module, action, target_id, operator_id, operator_role, detail, create_time)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 4
)
SELECT
    'JOB',
    'AUDIT',
    102,
    1,
    'ADMIN',
    CONCAT('JOB-102 干扰日志#', LPAD(n, 2, '0')),
    TIMESTAMPADD(MINUTE, n, TIMESTAMP(CURDATE(), '09:00:00'))
FROM seq;

INSERT INTO sys_log_audit (module, action, target_id, operator_id, operator_role, detail, create_time)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 4
)
SELECT
    'MERCHANT',
    CASE WHEN MOD(n, 2) = 0 THEN 'STATUS' ELSE 'AUDIT' END,
    11,
    1,
    'ADMIN',
    CONCAT('MERCHANT-11 日志#', LPAD(n, 2, '0')),
    TIMESTAMPADD(MINUTE, n, TIMESTAMP(CURDATE(), '10:00:00'))
FROM seq;

INSERT INTO sys_log_audit (module, action, target_id, operator_id, operator_role, detail, create_time)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 2
)
SELECT
    'MERCHANT',
    'AUDIT',
    12,
    1,
    'ADMIN',
    CONCAT('MERCHANT-12 干扰日志#', LPAD(n, 2, '0')),
    TIMESTAMPADD(MINUTE, n, TIMESTAMP(CURDATE(), '10:30:00'))
FROM seq;

INSERT INTO sys_log_audit (module, action, target_id, operator_id, operator_role, detail, create_time)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 3
)
SELECT
    'REPORT',
    'HANDLE',
    201,
    1,
    'ADMIN',
    CONCAT('REPORT-201 日志#', LPAD(n, 2, '0')),
    TIMESTAMPADD(MINUTE, n, TIMESTAMP(CURDATE(), '11:00:00'))
FROM seq;

INSERT INTO sys_log_audit (module, action, target_id, operator_id, operator_role, detail, create_time)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 2
)
SELECT
    'REPORT',
    'HANDLE',
    202,
    1,
    'ADMIN',
    CONCAT('REPORT-202 干扰日志#', LPAD(n, 2, '0')),
    TIMESTAMPADD(MINUTE, n, TIMESTAMP(CURDATE(), '11:20:00'))
FROM seq;

INSERT INTO sys_log_audit (module, action, target_id, operator_id, operator_role, detail, create_time)
VALUES
    ('AUTH', 'BAN', 3, 1, 'ADMIN', 'AUTH 干扰日志', TIMESTAMP(CURDATE(), '12:00:00'));
