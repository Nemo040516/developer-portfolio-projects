-- 文件速览：
-- 1. 文件职责：为真实 MySQL 专项测试提供可重复初始化的最小基础表结构。
-- 2. 关键范围：统一身份、求职者、商家、职位、举报、审计、治理通知。
-- 3. 阅读建议：先看 DROP 顺序，再看治理通知相关外键，避免后续补表时破坏可重复执行性。

SET NAMES utf8mb4;

DROP TABLE IF EXISTS governance_notice_action;
DROP TABLE IF EXISTS governance_notice;
DROP TABLE IF EXISTS report_evidence;
DROP TABLE IF EXISTS report_info;
DROP TABLE IF EXISTS job_post;
DROP TABLE IF EXISTS job_category;
DROP TABLE IF EXISTS merchant_profile;
DROP TABLE IF EXISTS applicant_profile;
DROP TABLE IF EXISTS sys_log_audit;
DROP TABLE IF EXISTS sys_user;

CREATE TABLE sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    password VARCHAR(255),
    nickname VARCHAR(64),
    phone VARCHAR(32),
    email VARCHAR(128),
    avatar VARCHAR(255),
    role VARCHAR(32) NOT NULL,
    role_sort INT GENERATED ALWAYS AS (
        CASE
            WHEN role = 'ADMIN' THEN 1
            WHEN role = 'MERCHANT' THEN 2
            WHEN role = 'APPLICANT' THEN 3
            ELSE 99
        END
    ) STORED,
    status INT DEFAULT 1,
    ban_status INT DEFAULT 0,
    ban_reason VARCHAR(255),
    ban_until DATETIME NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sys_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE applicant_profile (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    current_identity VARCHAR(64),
    real_name VARCHAR(64),
    gender INT,
    birthday DATE,
    work_years VARCHAR(64),
    phone VARCHAR(32),
    email VARCHAR(128),
    college VARCHAR(128),
    major VARCHAR(128),
    degree VARCHAR(64),
    grad_year INT,
    current_status VARCHAR(64),
    expect_city VARCHAR(64),
    expect_salary_min INT,
    expect_salary_max INT,
    expect_salary VARCHAR(64),
    expect_job VARCHAR(128),
    advantage TEXT,
    skills TEXT,
    education_json LONGTEXT,
    experience_json LONGTEXT,
    project_json LONGTEXT,
    certificate_json LONGTEXT,
    award_json LONGTEXT,
    language_json LONGTEXT,
    resume_url VARCHAR(255),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_applicant_profile_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE merchant_profile (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    company_name VARCHAR(128),
    company_logo VARCHAR(255),
    industry VARCHAR(64),
    scale VARCHAR(64),
    financing VARCHAR(64),
    description TEXT,
    province VARCHAR(64),
    city VARCHAR(64),
    district VARCHAR(64),
    address VARCHAR(255),
    contact_name VARCHAR(64),
    contact_phone VARCHAR(32),
    credit_code VARCHAR(64),
    legal_person VARCHAR(64),
    longitude DECIMAL(10, 6),
    latitude DECIMAL(10, 6),
    license_url VARCHAR(255),
    qualification_urls LONGTEXT,
    audit_status INT DEFAULT 0,
    audit_reason VARCHAR(255),
    audit_time DATETIME NULL,
    publish_status INT DEFAULT 1,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_merchant_profile_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE job_category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    category_name VARCHAR(128) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE job_post (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    merchant_id BIGINT NOT NULL,
    category_id BIGINT,
    title VARCHAR(128) NOT NULL,
    description LONGTEXT,
    requirement LONGTEXT,
    work_location VARCHAR(128),
    district VARCHAR(128),
    min_salary INT,
    max_salary INT,
    headcount INT,
    experience VARCHAR(64),
    degree VARCHAR(64),
    tags VARCHAR(255),
    status INT DEFAULT 1,
    audit_status INT DEFAULT 0,
    audit_reason VARCHAR(255),
    audit_time DATETIME NULL,
    last_edit_summary VARCHAR(255),
    last_edit_time DATETIME NULL,
    view_count INT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_job_post_merchant (merchant_id),
    KEY idx_job_post_audit_status (audit_status),
    KEY idx_job_post_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sys_log_audit (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    module VARCHAR(32) NOT NULL,
    action VARCHAR(32) NOT NULL,
    target_id BIGINT,
    operator_id BIGINT,
    operator_role VARCHAR(32),
    detail TEXT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_sys_log_audit_module_target (module, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE report_info (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    type VARCHAR(32) NOT NULL,
    target_id BIGINT NOT NULL,
    reporter_id BIGINT NOT NULL,
    reason TEXT,
    status INT NOT NULL,
    result TEXT,
    evidence TEXT,
    action_code VARCHAR(64),
    handled_by BIGINT,
    handled_time DATETIME NULL,
    target_snapshot LONGTEXT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_report_target (target_id),
    KEY idx_report_reporter (reporter_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE report_evidence (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    report_id BIGINT NOT NULL,
    file_url VARCHAR(255) NOT NULL,
    file_type VARCHAR(32) NOT NULL,
    sort_order INT NOT NULL,
    uploader_id BIGINT NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_report_evidence_report (report_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE governance_notice (
    id BIGINT NOT NULL AUTO_INCREMENT,
    notice_no VARCHAR(32) NOT NULL,
    target_role VARCHAR(20) NOT NULL,
    target_user_id BIGINT NOT NULL,
    notice_type VARCHAR(32) NOT NULL,
    severity VARCHAR(16) NOT NULL DEFAULT 'INFO',
    source_module VARCHAR(32) NOT NULL,
    source_id BIGINT DEFAULT NULL,
    related_job_id BIGINT DEFAULT NULL,
    related_merchant_id BIGINT DEFAULT NULL,
    title VARCHAR(120) NOT NULL,
    summary VARCHAR(255) DEFAULT NULL,
    detail TEXT,
    required_action VARCHAR(255) DEFAULT NULL,
    due_time DATETIME DEFAULT NULL,
    status VARCHAR(32) NOT NULL,
    need_ack TINYINT NOT NULL DEFAULT 1,
    need_reply TINYINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    read_time DATETIME DEFAULT NULL,
    closed_time DATETIME DEFAULT NULL,
    latest_action_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_governance_notice_no (notice_no),
    KEY idx_gov_notice_target_status_time (target_user_id, status, latest_action_time),
    KEY idx_gov_notice_module_source (source_module, source_id),
    KEY idx_gov_notice_type_due (notice_type, due_time),
    KEY idx_gov_notice_created_by_time (created_by, create_time),
    CONSTRAINT fk_gov_notice_target_user FOREIGN KEY (target_user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_gov_notice_created_by FOREIGN KEY (created_by) REFERENCES sys_user(id),
    CONSTRAINT fk_gov_notice_related_job FOREIGN KEY (related_job_id) REFERENCES job_post(id),
    CONSTRAINT fk_gov_notice_related_merchant FOREIGN KEY (related_merchant_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE governance_notice_action (
    id BIGINT NOT NULL AUTO_INCREMENT,
    notice_id BIGINT NOT NULL,
    actor_role VARCHAR(20) NOT NULL,
    actor_user_id BIGINT NOT NULL,
    action_type VARCHAR(32) NOT NULL,
    content TEXT,
    attachment_json JSON DEFAULT NULL,
    extra_json JSON DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_gov_action_notice_time (notice_id, create_time),
    KEY idx_gov_action_actor_time (actor_user_id, create_time),
    KEY idx_gov_action_type_time (action_type, create_time),
    CONSTRAINT fk_gov_action_notice FOREIGN KEY (notice_id) REFERENCES governance_notice(id) ON DELETE CASCADE,
    CONSTRAINT fk_gov_action_actor FOREIGN KEY (actor_user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
