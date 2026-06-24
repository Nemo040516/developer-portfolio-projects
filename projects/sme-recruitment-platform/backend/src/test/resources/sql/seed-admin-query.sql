SET NAMES utf8mb4;

DELETE FROM report_evidence;
DELETE FROM report_info;
DELETE FROM job_post;
DELETE FROM job_category;
DELETE FROM merchant_profile;
DELETE FROM sys_log_audit;
DELETE FROM applicant_profile;
DELETE FROM sys_user;

ALTER TABLE report_evidence AUTO_INCREMENT = 1;
ALTER TABLE report_info AUTO_INCREMENT = 1;
ALTER TABLE job_post AUTO_INCREMENT = 1;
ALTER TABLE job_category AUTO_INCREMENT = 1;
ALTER TABLE merchant_profile AUTO_INCREMENT = 1;
ALTER TABLE sys_log_audit AUTO_INCREMENT = 1;
ALTER TABLE applicant_profile AUTO_INCREMENT = 1;
ALTER TABLE sys_user AUTO_INCREMENT = 1;

INSERT INTO sys_user (
    id, username, password, nickname, phone, email, avatar, role, status, ban_status, ban_reason, ban_until, create_time, update_time
) VALUES
    (1, 'admin1', '12345', '管理员', '13800000001', 'admin1@example.com', NULL, 'ADMIN', 1, 0, NULL, NULL, DATE_SUB(TIMESTAMP(CURDATE(), '09:00:00'), INTERVAL 7 DAY), DATE_SUB(TIMESTAMP(CURDATE(), '09:00:00'), INTERVAL 7 DAY)),
    (2, 'boss_pending', '12345', '星辰负责人', '13800000002', 'boss_pending@example.com', NULL, 'MERCHANT', 1, 0, NULL, NULL, DATE_SUB(TIMESTAMP(CURDATE(), '10:00:00'), INTERVAL 5 DAY), TIMESTAMP(CURDATE(), '10:30:00')),
    (3, 'app_banned', '12345', '候选人甲', '13800000003', 'app_banned@example.com', NULL, 'APPLICANT', 1, 1, '历史封禁', DATE_ADD(TIMESTAMP(CURDATE(), '18:00:00'), INTERVAL 7 DAY), DATE_SUB(TIMESTAMP(CURDATE(), '11:00:00'), INTERVAL 2 DAY), TIMESTAMP(CURDATE(), '11:20:00')),
    (4, 'boss_approved', '12345', '云海招聘官', '13800000004', 'boss_approved@example.com', NULL, 'MERCHANT', 1, 0, NULL, NULL, DATE_SUB(TIMESTAMP(CURDATE(), '10:00:00'), INTERVAL 4 DAY), DATE_SUB(TIMESTAMP(CURDATE(), '09:00:00'), INTERVAL 1 DAY)),
    (5, 'app_active', '12345', '候选人乙', '13800000005', 'app_active@example.com', NULL, 'APPLICANT', 1, 0, NULL, NULL, DATE_SUB(TIMESTAMP(CURDATE(), '09:00:00'), INTERVAL 1 DAY), TIMESTAMP(CURDATE(), '11:40:00'));

INSERT INTO merchant_profile (
    id, user_id, company_name, company_logo, industry, scale, financing, description, province, city, district, address,
    contact_name, contact_phone, credit_code, legal_person, longitude, latitude, license_url, qualification_urls,
    audit_status, audit_reason, audit_time, publish_status, update_time
) VALUES
    (11, 2, '星辰科技', '/uploads/logo/star.png', '互联网', '50-99人', 'A轮', '智能招聘平台', '上海市', '上海市', '浦东新区', '张江科学城',
     NULL, NULL, '91310000123456789A', '张三', NULL, NULL, '/uploads/license/star-license.png', '["/uploads/license/star-qa.png"]',
     0, NULL, NULL, 1, TIMESTAMP(CURDATE(), '11:10:00')),
    (12, 4, '云海物流', '/uploads/logo/cloud.png', '物流', '100-499人', 'B轮', '城市配送服务', '杭州市', '杭州市', '滨江区', '物联网大道 18 号',
     '王主管', '13900000004', '91330100123456789B', '李四', NULL, NULL, '/uploads/license/cloud-license.png', '["/uploads/license/cloud-qa.png"]',
     1, NULL, DATE_SUB(TIMESTAMP(CURDATE(), '09:30:00'), INTERVAL 2 DAY), 0, DATE_SUB(TIMESTAMP(CURDATE(), '09:00:00'), INTERVAL 1 DAY));

INSERT INTO job_category (id, category_name) VALUES
    (1, '后端开发'),
    (2, '运营'),
    (3, '风控');

INSERT INTO job_post (
    id, merchant_id, category_id, title, description, requirement, work_location, district, min_salary, max_salary, headcount,
    experience, degree, tags, status, audit_status, audit_reason, audit_time, last_edit_summary, last_edit_time, view_count, create_time, update_time
) VALUES
    (101, 2, 1, 'Java 后端工程师', '负责招聘系统后端开发', '熟悉 Spring Boot', '上海', '浦东', 15, 25, 3,
     '3-5年', '本科', 'Java,Spring', 1, 0, NULL, NULL, '新增岗位', TIMESTAMP(CURDATE(), '10:50:00'), 23, TIMESTAMP(CURDATE(), '10:00:00'), TIMESTAMP(CURDATE(), '10:00:00')),
    (102, 4, 2, '运营专员', '负责履约运营', '具备运营经验', '杭州', '滨江', 8, 12, 2,
     '1-3年', '大专', '运营,数据', 1, 1, NULL, DATE_SUB(TIMESTAMP(CURDATE(), '09:00:00'), INTERVAL 2 DAY), '更新岗位说明', DATE_SUB(TIMESTAMP(CURDATE(), '09:20:00'), INTERVAL 2 DAY), 11, DATE_SUB(TIMESTAMP(CURDATE(), '09:00:00'), INTERVAL 2 DAY), DATE_SUB(TIMESTAMP(CURDATE(), '09:00:00'), INTERVAL 2 DAY)),
    (103, 2, 3, '风控专员', '负责招聘风控', '具备审核经验', '上海', '徐汇', 10, 15, 1,
     '1-3年', '本科', '风控,审核', 0, 2, '描述不完整', DATE_SUB(TIMESTAMP(CURDATE(), '09:00:00'), INTERVAL 3 DAY), '补充岗位信息', DATE_SUB(TIMESTAMP(CURDATE(), '09:10:00'), INTERVAL 3 DAY), 5, DATE_SUB(TIMESTAMP(CURDATE(), '09:00:00'), INTERVAL 3 DAY), DATE_SUB(TIMESTAMP(CURDATE(), '09:00:00'), INTERVAL 3 DAY)),
    (104, 2, 1, 'Java 架构师', '负责平台架构设计', '具备高并发经验', '上海', '浦东', 25, 35, 1,
     '5-10年', '本科', 'Java,架构', 1, 0, NULL, NULL, '薪资区间调整', TIMESTAMP(CURDATE(), '09:40:00'), 39, TIMESTAMP(CURDATE(), '09:00:00'), TIMESTAMP(CURDATE(), '09:00:00'));

INSERT INTO report_info (
    id, type, target_id, reporter_id, reason, status, result, evidence, action_code, handled_by, handled_time, target_snapshot, create_time, update_time
) VALUES
    (201, 'MERCHANT', 2, 3, '企业宣传与实际不符', 0, NULL, NULL, NULL, NULL, NULL,
     '{"type":"MERCHANT","targetId":2,"companyName":"星辰科技"}', TIMESTAMP(CURDATE(), '11:00:00'), TIMESTAMP(CURDATE(), '11:00:00')),
    (202, 'MERCHANT', 2, 5, '企业存在骚扰行为', 1, '限制发布', 'legacy-merchant-evidence.png', 'MERCHANT_LIMIT', 1, DATE_SUB(NOW(), INTERVAL 1 DAY),
     '{"type":"MERCHANT","targetId":2,"companyName":"星辰科技"}', DATE_SUB(TIMESTAMP(CURDATE(), '10:00:00'), INTERVAL 1 DAY), DATE_SUB(TIMESTAMP(CURDATE(), '10:00:00'), INTERVAL 1 DAY)),
    (203, 'USER', 3, 2, '求职者恶意骚扰', 0, NULL, '/uploads/reports/2/user-evidence-fallback.png', NULL, NULL, NULL,
     '{"type":"USER","targetId":3,"username":"app_banned"}', TIMESTAMP(CURDATE(), '11:30:00'), TIMESTAMP(CURDATE(), '11:30:00'));

INSERT INTO report_evidence (
    id, report_id, file_url, file_type, sort_order, uploader_id, create_time
) VALUES
    (301, 203, '/uploads/reports/2/user-chat-1.png', 'IMAGE', 1, 2, TIMESTAMP(CURDATE(), '11:31:00')),
    (302, 203, '/uploads/reports/2/user-chat-2.pdf', 'PDF', 2, 2, TIMESTAMP(CURDATE(), '11:32:00'));

INSERT INTO sys_log_audit (
    id, module, action, target_id, operator_id, operator_role, detail, create_time
) VALUES
    (401, 'MERCHANT', 'AUDIT', 11, 1, 'ADMIN', '待审核企业入库', TIMESTAMP(CURDATE(), '11:15:00')),
    (402, 'REPORT', 'HANDLE', 202, 1, 'ADMIN', '已处理企业举报', DATE_SUB(TIMESTAMP(CURDATE(), '10:00:00'), INTERVAL 1 DAY));
