SET NAMES utf8mb4;

-- Public demo seed data only.
-- The BCrypt hash below is for the demo password: 12345.
-- All names, phones, emails, companies, and profile records are synthetic.

INSERT INTO `sys_user`
  (`id`, `username`, `password`, `nickname`, `role`, `phone`, `email`, `avatar`, `status`, `create_time`, `update_time`, `ban_status`, `ban_reason`, `ban_until`)
VALUES
  (1, 'admin1', '$2a$10$7NTxOaT0ni2AomUhwZZ/guRpgDiQWUNKsVhS.uGZnbagThWZKPcqO', 'Demo Admin', 'ADMIN', '13800000001', 'admin1@example.com', NULL, 1, NOW(), NOW(), 0, NULL, NULL),
  (4, 'boss1', '$2a$10$7NTxOaT0ni2AomUhwZZ/guRpgDiQWUNKsVhS.uGZnbagThWZKPcqO', 'Demo Merchant', 'MERCHANT', '13900000001', 'boss1@example.com', NULL, 1, NOW(), NOW(), 0, NULL, NULL),
  (9, 'app1', '$2a$10$7NTxOaT0ni2AomUhwZZ/guRpgDiQWUNKsVhS.uGZnbagThWZKPcqO', 'Demo Applicant', 'APPLICANT', '13700000001', 'app1@example.com', NULL, 1, NOW(), NOW(), 0, NULL, NULL)
ON DUPLICATE KEY UPDATE
  nickname = VALUES(nickname),
  phone = VALUES(phone),
  email = VALUES(email),
  status = VALUES(status),
  ban_status = VALUES(ban_status),
  update_time = NOW();

INSERT INTO `job_category`
  (`id`, `category_name`, `sort`, `parent_id`, `create_time`, `update_time`)
VALUES
  (1, 'Technology', 1, NULL, NOW(), NOW()),
  (2, 'Operations', 2, NULL, NOW(), NOW()),
  (6, 'Java Backend Developer', 1, 1, NOW(), NOW()),
  (7, 'Web Frontend Developer', 2, 1, NOW(), NOW()),
  (21, 'HR and Recruiting', 1, 2, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  category_name = VALUES(category_name),
  sort = VALUES(sort),
  parent_id = VALUES(parent_id),
  update_time = NOW();

INSERT INTO `merchant_profile`
  (`id`, `user_id`, `company_name`, `company_logo`, `industry`, `scale`, `financing`, `description`, `province`, `city`, `district`, `address`, `contact_name`, `contact_phone`, `credit_code`, `legal_person`, `longitude`, `latitude`, `license_url`, `qualification_urls`, `audit_status`, `audit_reason`, `audit_time`, `publish_status`, `create_time`, `update_time`)
VALUES
  (1, 4, 'Demo Local Retail Studio', NULL, 'Retail', '20-99', 'Demo', 'Synthetic merchant profile for local portfolio demonstration.', 'Demo Province', 'Demo City', 'Demo District', 'Demo Road 88', 'Demo Contact', '13900000001', 'DEMO-CREDIT-CODE-001', 'Demo Legal Person', NULL, NULL, NULL, '[]', 1, NULL, NOW(), 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  company_name = VALUES(company_name),
  industry = VALUES(industry),
  scale = VALUES(scale),
  description = VALUES(description),
  contact_name = VALUES(contact_name),
  contact_phone = VALUES(contact_phone),
  audit_status = VALUES(audit_status),
  publish_status = VALUES(publish_status),
  update_time = NOW();

INSERT INTO `applicant_profile`
  (`id`, `user_id`, `current_identity`, `real_name`, `gender`, `birthday`, `work_years`, `phone`, `email`, `college`, `major`, `degree`, `grad_year`, `current_status`, `expect_city`, `expect_salary`, `expect_salary_min`, `expect_salary_max`, `expect_job`, `advantage`, `skills`, `education_json`, `experience_json`, `project_json`, `certificate_json`, `award_json`, `language_json`, `resume_url`, `create_time`, `update_time`)
VALUES
  (1, 9, 'FRESH_GRAD', 'Demo Applicant', 0, '2003-01-01', 'Fresh graduate', '13700000001', 'app1@example.com', 'Demo University', 'Computer Science', 'Bachelor', 2026, 'Open to opportunities', 'Demo City', '8k-12k', 8, 12, 'Java Backend Developer', 'Synthetic applicant profile used for local portfolio walkthroughs.', 'Java,Spring Boot,Vue,MySQL', '[]', '[]', '[]', '[]', '[]', '[]', NULL, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  real_name = VALUES(real_name),
  phone = VALUES(phone),
  email = VALUES(email),
  college = VALUES(college),
  major = VALUES(major),
  expect_job = VALUES(expect_job),
  advantage = VALUES(advantage),
  skills = VALUES(skills),
  update_time = NOW();

INSERT INTO `user_privacy_setting`
  (`id`, `user_id`, `contact_visibility`, `create_time`, `update_time`)
VALUES
  (1, 9, 'DELIVERY', NOW(), NOW())
ON DUPLICATE KEY UPDATE
  contact_visibility = VALUES(contact_visibility),
  update_time = NOW();

INSERT INTO `job_post`
  (`id`, `merchant_id`, `category_id`, `title`, `description`, `requirement`, `work_location`, `district`, `min_salary`, `max_salary`, `headcount`, `experience`, `degree`, `tags`, `status`, `audit_status`, `audit_reason`, `audit_time`, `last_edit_summary`, `last_edit_time`, `view_count`, `create_time`, `update_time`)
VALUES
  (1, 4, 6, 'Java Backend Developer', 'Develop recruitment and application management APIs for local merchants.', 'Spring Boot, MyBatis-Plus, MySQL, REST API experience.', 'Demo City', 'Demo District', 8, 12, 2, 'Fresh graduate', 'Bachelor', 'Java,Spring Boot,MySQL', 1, 1, NULL, NOW(), 'Initial demo job', NOW(), 12, NOW(), NOW()),
  (2, 4, 7, 'Web Frontend Developer', 'Build role-based dashboard pages for applicants, merchants, and administrators.', 'Vue 3, Element Plus, routing and API integration.', 'Demo City', 'Demo District', 8, 12, 1, 'Fresh graduate', 'Bachelor', 'Vue,Vite,Element Plus', 1, 1, NULL, NOW(), 'Initial demo job', NOW(), 8, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  title = VALUES(title),
  description = VALUES(description),
  requirement = VALUES(requirement),
  status = VALUES(status),
  audit_status = VALUES(audit_status),
  update_time = NOW();

INSERT INTO `job_apply`
  (`id`, `job_id`, `applicant_id`, `resume_url`, `status`, `feedback`, `create_time`, `update_time`)
VALUES
  (1, 1, 9, NULL, 2, 'Synthetic application record for interview workflow demonstration.', NOW(), NOW())
ON DUPLICATE KEY UPDATE
  status = VALUES(status),
  feedback = VALUES(feedback),
  update_time = NOW();
