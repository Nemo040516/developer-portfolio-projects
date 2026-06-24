INSERT INTO sys_user (
    id, username, password, nickname, phone, email, avatar, role, status, ban_status, ban_reason, ban_until, create_time, update_time
) VALUES
    (1, 'admin1', '12345', '管理员', '13800000001', 'admin1@example.com', NULL, 'ADMIN', 1, 0, NULL, NULL, '2026-03-01 10:00:00', '2026-03-01 10:00:00'),
    (2, 'boss1', '12345', '招聘方', '13800000002', 'boss1@example.com', NULL, 'MERCHANT', 1, 0, NULL, NULL, '2026-03-01 10:00:00', '2026-03-01 10:00:00'),
    (3, 'app1', '12345', '候选人甲', '13800000003', 'app1@example.com', NULL, 'APPLICANT', 1, 0, NULL, NULL, '2026-03-01 10:00:00', '2026-03-01 10:00:00');
