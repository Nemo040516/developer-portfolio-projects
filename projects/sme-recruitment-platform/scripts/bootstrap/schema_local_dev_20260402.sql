
/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
DROP TABLE IF EXISTS `applicant_education`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `applicant_education` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '求职者ID',
  `school` varchar(100) DEFAULT NULL COMMENT '学校名称',
  `major` varchar(100) DEFAULT NULL COMMENT '专业',
  `degree` varchar(50) DEFAULT NULL COMMENT '学历',
  `start_date` varchar(20) DEFAULT NULL COMMENT '入学时间',
  `end_date` varchar(20) DEFAULT NULL COMMENT '毕业时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_edu_user_id` (`user_id`),
  CONSTRAINT `fk_app_edu_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=36 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='求职者-教育经历';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `applicant_experience`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `applicant_experience` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '求职者ID',
  `company` varchar(100) DEFAULT NULL COMMENT '公司名称',
  `position` varchar(100) DEFAULT NULL COMMENT '职位名称',
  `content` text COMMENT '工作内容',
  `start_date` varchar(20) DEFAULT NULL COMMENT '开始时间',
  `end_date` varchar(20) DEFAULT NULL COMMENT '结束时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `type` int DEFAULT '0' COMMENT '类型(0全职 1实习)',
  PRIMARY KEY (`id`),
  KEY `idx_exp_user_id` (`user_id`),
  CONSTRAINT `fk_app_exp_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=36 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='求职者-工作/实习经历';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `applicant_profile`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `applicant_profile` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '求职者ID (sys_user.id)',
  `current_identity` varchar(20) DEFAULT 'WORKER' COMMENT '当前身份: STUDENT-在校生(找实习), FRESH_GRAD-应届生(找全职), WORKER-职场人士',
  `real_name` varchar(50) DEFAULT NULL COMMENT '真实姓名',
  `gender` tinyint DEFAULT '0' COMMENT '性别(0保密 1男 2女)',
  `birthday` date DEFAULT NULL COMMENT '出生日期',
  `work_years` varchar(20) DEFAULT NULL COMMENT '工作年限 (如: 10年, 应届生, 在校生)',
  `phone` varchar(20) DEFAULT NULL COMMENT '联系手机 (可能与注册手机不同)',
  `email` varchar(100) DEFAULT NULL COMMENT '联系邮箱',
  `college` varchar(100) DEFAULT NULL COMMENT '毕业院校',
  `major` varchar(100) DEFAULT NULL COMMENT '主修专业',
  `degree` varchar(20) DEFAULT NULL COMMENT '最高学历',
  `grad_year` int DEFAULT NULL COMMENT '毕业年份 (如: 2026)',
  `current_status` varchar(50) DEFAULT NULL COMMENT '求职状态',
  `expect_city` varchar(50) DEFAULT NULL COMMENT '期望城市',
  `expect_salary` varchar(50) DEFAULT NULL COMMENT '期望薪资',
  `expect_salary_min` int DEFAULT NULL COMMENT '期望最低薪资(k)',
  `expect_salary_max` int DEFAULT NULL COMMENT '期望最高薪资(k)',
  `expect_job` varchar(100) DEFAULT NULL COMMENT '期望职位',
  `advantage` varchar(500) DEFAULT NULL COMMENT '个人优势',
  `skills` text COMMENT '技能标签 (JSON或逗号分隔, 如: Java, Vue, 英语六级)',
  `education_json` text COMMENT '教育经历(JSON数组)',
  `experience_json` text COMMENT '工作/实习经历(JSON数组)',
  `project_json` text COMMENT '项目经历(JSON数组)',
  `certificate_json` text COMMENT '证书(JSON数组)',
  `award_json` text COMMENT '奖项(JSON数组)',
  `language_json` text COMMENT '语言能力(JSON数组)',
  `resume_url` varchar(255) DEFAULT NULL COMMENT '简历附件URL',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_applicant_user` (`user_id`),
  KEY `idx_applicant_degree` (`degree`),
  KEY `idx_applicant_expect_city_job_time` (`expect_city`,`expect_job`,`update_time` DESC),
  CONSTRAINT `fk_applicant_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=33 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='求职者-档案主表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50003 TRIGGER `trg_applicant_profile_insert_salary_sync` BEFORE INSERT ON `applicant_profile` FOR EACH ROW BEGIN
    DECLARE clean_str VARCHAR(50);
    DECLARE sep_pos INT;
    DECLARE min_val INT;
    DECLARE max_val INT;

    
    IF NEW.expect_salary_min IS NOT NULL AND NEW.expect_salary_max IS NOT NULL THEN
        SET NEW.expect_salary = CONCAT(NEW.expect_salary_min, 'k-', NEW.expect_salary_max, 'k');

    
    
    ELSEIF NEW.expect_salary IS NOT NULL AND NEW.expect_salary != '' AND (NEW.expect_salary_min IS NULL OR NEW.expect_salary_max IS NULL) THEN
        
        SET clean_str = REPLACE(REPLACE(REPLACE(LOWER(NEW.expect_salary), 'k', ''), '?', ''), '?', '');
        SET clean_str = REPLACE(clean_str, '~', '-'); 
        SET clean_str = REPLACE(clean_str, '?', '-'); 

        
        SET sep_pos = LOCATE('-', clean_str);
        
        
        IF sep_pos > 1 THEN
            
            SET min_val = CAST(SUBSTRING(clean_str, 1, sep_pos - 1) AS UNSIGNED);
            
            SET max_val = CAST(SUBSTRING(clean_str, sep_pos + 1) AS UNSIGNED);
            
            
            IF min_val > 0 AND max_val > 0 THEN
                SET NEW.expect_salary_min = min_val;
                SET NEW.expect_salary_max = max_val;
                
                SET NEW.expect_salary = CONCAT(min_val, 'k-', max_val, 'k');
            END IF;
        END IF;
    END IF;
END */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50003 TRIGGER `trg_applicant_profile_update_salary_sync` BEFORE UPDATE ON `applicant_profile` FOR EACH ROW BEGIN
    DECLARE clean_str VARCHAR(50);
    DECLARE sep_pos INT;
    DECLARE min_val INT;
    DECLARE max_val INT;

    
    
    IF NEW.expect_salary_min IS NOT NULL AND NEW.expect_salary_max IS NOT NULL THEN
        SET NEW.expect_salary = CONCAT(NEW.expect_salary_min, 'k-', NEW.expect_salary_max, 'k');

    
    
    ELSEIF NEW.expect_salary != OLD.expect_salary AND NEW.expect_salary IS NOT NULL AND NEW.expect_salary != '' THEN
        
        IF (NEW.expect_salary_min IS NULL OR NEW.expect_salary_min = OLD.expect_salary_min) AND 
           (NEW.expect_salary_max IS NULL OR NEW.expect_salary_max = OLD.expect_salary_max) THEN

            
            SET clean_str = REPLACE(REPLACE(REPLACE(LOWER(NEW.expect_salary), 'k', ''), '?', ''), '?', '');
            SET clean_str = REPLACE(clean_str, '~', '-');
            SET clean_str = REPLACE(clean_str, '?', '-');

            
            SET sep_pos = LOCATE('-', clean_str);
            
            
            IF sep_pos > 1 THEN
                SET min_val = CAST(SUBSTRING(clean_str, 1, sep_pos - 1) AS UNSIGNED);
                SET max_val = CAST(SUBSTRING(clean_str, sep_pos + 1) AS UNSIGNED);
                
                IF min_val > 0 AND max_val > 0 THEN
                    SET NEW.expect_salary_min = min_val;
                    SET NEW.expect_salary_max = max_val;
                    SET NEW.expect_salary = CONCAT(min_val, 'k-', max_val, 'k');
                END IF;
            END IF;
        END IF;
    END IF;
END */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
DROP TABLE IF EXISTS `applicant_project`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `applicant_project` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `name` varchar(100) DEFAULT NULL COMMENT '项目名称',
  `role` varchar(100) DEFAULT NULL COMMENT '担任角色',
  `description` text COMMENT '项目描述',
  `start_date` varchar(20) DEFAULT NULL COMMENT '开始时间',
  `end_date` varchar(20) DEFAULT NULL COMMENT '结束时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  CONSTRAINT `fk_app_proj_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=41 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='求职者-项目经验';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `applicant_verification`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `applicant_verification` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '关联sys_user.id',
  `real_name` varchar(50) DEFAULT NULL COMMENT '真实姓名',
  `cert_type` varchar(20) DEFAULT NULL COMMENT '证件类型',
  `cert_no` varchar(50) DEFAULT NULL COMMENT '证件号码',
  `remark` varchar(500) DEFAULT NULL COMMENT '补充说明',
  `status` varchar(20) DEFAULT 'PENDING' COMMENT '状态: PENDING/APPROVED/REJECTED',
  `submit_time` datetime DEFAULT NULL COMMENT '提交时间',
  `audit_time` datetime DEFAULT NULL COMMENT '审核时间',
  `audit_user_id` bigint DEFAULT NULL COMMENT '审核人',
  `audit_reason` varchar(255) DEFAULT NULL COMMENT '驳回原因',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_verify_user` (`user_id`),
  KEY `idx_verify_status_time` (`status`,`submit_time`),
  KEY `idx_verify_audit_user_id` (`audit_user_id`),
  CONSTRAINT `fk_verify_audit_user` FOREIGN KEY (`audit_user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_verify_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chk_verify_status` CHECK ((`status` in (_utf8mb4'PENDING',_utf8mb4'APPROVED',_utf8mb4'REJECTED')))
) ENGINE=InnoDB AUTO_INCREMENT=33 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='求职者认证申请表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `chat_message`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `chat_message` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `from_user_id` bigint NOT NULL COMMENT '发送者ID',
  `to_user_id` bigint NOT NULL COMMENT '接收者ID',
  `content` text NOT NULL COMMENT '消息内容',
  `is_read` tinyint DEFAULT '0' COMMENT '是否已读: 0-未读, 1-已读',
  `job_id` bigint DEFAULT NULL COMMENT '沟通岗位ID',
  `job_key` varchar(100) DEFAULT NULL COMMENT '沟通岗位关键字',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_chat_session` (`from_user_id`,`to_user_id`),
  KEY `idx_chat_to_read_time` (`to_user_id`,`is_read`,`create_time`),
  KEY `idx_chat_from_to_time` (`from_user_id`,`to_user_id`,`create_time`),
  KEY `idx_chat_job_id` (`job_id`),
  CONSTRAINT `fk_chat_msg_from_user` FOREIGN KEY (`from_user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_chat_msg_job` FOREIGN KEY (`job_id`) REFERENCES `job_post` (`id`),
  CONSTRAINT `fk_chat_msg_to_user` FOREIGN KEY (`to_user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=143 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='即时通讯聊天记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `chat_session`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `chat_session` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `applicant_id` bigint NOT NULL COMMENT '求职者ID',
  `merchant_id` bigint NOT NULL COMMENT '商家ID',
  `job_id` bigint DEFAULT NULL COMMENT '沟通岗位ID',
  `job_title` varchar(100) DEFAULT NULL COMMENT '沟通岗位名称',
  `job_key` varchar(100) DEFAULT NULL COMMENT '沟通岗位关键字',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_chat_session_pair` (`applicant_id`,`merchant_id`),
  KEY `idx_chat_session_job` (`merchant_id`,`job_id`),
  KEY `fk_chat_session_job` (`job_id`),
  CONSTRAINT `fk_chat_session_applicant` FOREIGN KEY (`applicant_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_chat_session_job` FOREIGN KEY (`job_id`) REFERENCES `job_post` (`id`),
  CONSTRAINT `fk_chat_session_merchant` FOREIGN KEY (`merchant_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=79 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='会话上下文（岗位信息）';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `governance_notice`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `governance_notice` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `notice_no` varchar(32) NOT NULL COMMENT '业务编号',
  `target_role` varchar(20) NOT NULL COMMENT '目标角色：MERCHANT/APPLICANT',
  `target_user_id` bigint NOT NULL COMMENT '目标用户ID(sys_user.id)',
  `notice_type` varchar(32) NOT NULL COMMENT '通知类型：JOB_RECTIFY/MERCHANT_RECTIFY/REPORT_RESULT/USER_WARNING/BAN_NOTICE',
  `severity` varchar(16) NOT NULL DEFAULT 'INFO' COMMENT '严重级别：INFO/WARNING/HIGH',
  `source_module` varchar(32) NOT NULL COMMENT '来源模块：JOB_AUDIT/MERCHANT_AUDIT/REPORT/RISK_CONTROL',
  `source_id` bigint DEFAULT NULL COMMENT '来源业务ID',
  `related_job_id` bigint DEFAULT NULL COMMENT '关联职位ID(job_post.id)',
  `related_merchant_id` bigint DEFAULT NULL COMMENT '关联商家ID(sys_user.id)',
  `title` varchar(120) NOT NULL COMMENT '通知标题',
  `summary` varchar(255) DEFAULT NULL COMMENT '通知摘要',
  `detail` text COMMENT '详细说明',
  `required_action` varchar(255) DEFAULT NULL COMMENT '平台要求动作',
  `due_time` datetime DEFAULT NULL COMMENT '截止时间',
  `status` varchar(32) NOT NULL COMMENT '当前状态',
  `need_ack` tinyint NOT NULL DEFAULT '1' COMMENT '是否要求确认已读：0-否，1-是',
  `need_reply` tinyint NOT NULL DEFAULT '0' COMMENT '是否要求用户提交反馈：0-否，1-是',
  `created_by` bigint NOT NULL COMMENT '创建管理员ID(sys_user.id)',
  `read_time` datetime DEFAULT NULL COMMENT '首次已读时间',
  `closed_time` datetime DEFAULT NULL COMMENT '关闭时间',
  `latest_action_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '最近动作时间',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0-否，1-是',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_governance_notice_no` (`notice_no`),
  KEY `idx_gov_notice_target_status_time` (`target_user_id`,`status`,`latest_action_time`),
  KEY `idx_gov_notice_module_source` (`source_module`,`source_id`),
  KEY `idx_gov_notice_type_due` (`notice_type`,`due_time`),
  KEY `idx_gov_notice_created_by_time` (`created_by`,`create_time`),
  KEY `fk_gov_notice_related_job` (`related_job_id`),
  KEY `fk_gov_notice_related_merchant` (`related_merchant_id`),
  CONSTRAINT `fk_gov_notice_created_by` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_gov_notice_related_job` FOREIGN KEY (`related_job_id`) REFERENCES `job_post` (`id`),
  CONSTRAINT `fk_gov_notice_related_merchant` FOREIGN KEY (`related_merchant_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_gov_notice_target_user` FOREIGN KEY (`target_user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=606 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台治理通知与整改单主表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `governance_notice_action`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `governance_notice_action` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `notice_id` bigint NOT NULL COMMENT '所属治理通知ID(governance_notice.id)',
  `actor_role` varchar(20) NOT NULL COMMENT '操作人角色：ADMIN/MERCHANT/APPLICANT',
  `actor_user_id` bigint NOT NULL COMMENT '操作人ID(sys_user.id)',
  `action_type` varchar(32) NOT NULL COMMENT '动作类型：READ/SUBMIT_FIX/REPLY/APPEAL/APPROVE/REJECT/CLOSE',
  `content` text COMMENT '动作说明',
  `attachment_json` json DEFAULT NULL COMMENT '附件列表(JSON数组)',
  `extra_json` json DEFAULT NULL COMMENT '扩展字段(JSON对象)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_gov_action_notice_time` (`notice_id`,`create_time`),
  KEY `idx_gov_action_actor_time` (`actor_user_id`,`create_time`),
  KEY `idx_gov_action_type_time` (`action_type`,`create_time`),
  CONSTRAINT `fk_gov_action_actor` FOREIGN KEY (`actor_user_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_gov_action_notice` FOREIGN KEY (`notice_id`) REFERENCES `governance_notice` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=605 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台治理通知与整改单动作记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `job_apply`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `job_apply` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `job_id` bigint NOT NULL COMMENT '岗位ID',
  `applicant_id` bigint NOT NULL COMMENT '求职者ID',
  `resume_url` varchar(255) DEFAULT NULL COMMENT '投递简历附件URL',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '投递状态: 0-已投递, 1-已初筛(被查看), 2-面试邀约, 3-不合适',
  `feedback` varchar(500) DEFAULT NULL COMMENT '商家反馈意见',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '投递时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_job_delivery_unique` (`job_id`,`applicant_id`),
  KEY `idx_delivery_applicant_status_time` (`applicant_id`,`status`,`create_time`),
  KEY `idx_delivery_job_status_time` (`job_id`,`status`,`create_time`),
  CONSTRAINT `job_apply_ibfk_1` FOREIGN KEY (`job_id`) REFERENCES `job_post` (`id`),
  CONSTRAINT `job_apply_ibfk_2` FOREIGN KEY (`applicant_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `chk_job_apply_status` CHECK ((`status` in (0,1,2,3)))
) ENGINE=InnoDB AUTO_INCREMENT=77 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='岗位投递记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `job_category`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `job_category` (
  `id` int NOT NULL AUTO_INCREMENT,
  `category_name` varchar(50) NOT NULL COMMENT '分类名称',
  `sort` int DEFAULT '0' COMMENT '排序字段',
  `parent_id` int DEFAULT NULL COMMENT '父分类ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_category_parent_id` (`parent_id`),
  CONSTRAINT `fk_job_category_parent` FOREIGN KEY (`parent_id`) REFERENCES `job_category` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=75 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='职位分类表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `job_interview`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `job_interview` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `delivery_id` bigint NOT NULL COMMENT '投递记录ID (job_apply.id)',
  `round_no` int DEFAULT '1' COMMENT '面试轮次',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '面试状态: 0-待确认, 1-已确认, 2-已拒绝, 3-已取消, 4-已完成',
  `schedule_time` datetime DEFAULT NULL COMMENT '面试时间',
  `location` varchar(255) DEFAULT NULL COMMENT '面试地点/链接',
  `method` varchar(50) DEFAULT NULL COMMENT '面试形式',
  `remark` varchar(500) DEFAULT NULL COMMENT '面试备注',
  `creator_id` bigint DEFAULT NULL COMMENT '创建人(商家ID)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_interview_round` (`delivery_id`,`round_no`),
  KEY `idx_interview_delivery` (`delivery_id`),
  KEY `idx_interview_creator_id` (`creator_id`),
  CONSTRAINT `fk_interview_creator` FOREIGN KEY (`creator_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_interview_delivery` FOREIGN KEY (`delivery_id`) REFERENCES `job_apply` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chk_job_interview_status` CHECK ((`status` in (0,1,2,3,4)))
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='岗位面试记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `job_post`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `job_post` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `merchant_id` bigint NOT NULL COMMENT '发布商家ID(关联sys_user)',
  `category_id` int NOT NULL COMMENT '职位分类ID(关联job_category)',
  `title` varchar(100) NOT NULL COMMENT '岗位标题',
  `description` text NOT NULL COMMENT '岗位描述 (HTML富文本)',
  `requirement` text COMMENT '任职要求(富文本)',
  `work_location` varchar(50) DEFAULT NULL COMMENT '工作地点',
  `district` varchar(50) DEFAULT NULL COMMENT '工作区域(如: 虎丘区) - 用于细粒度筛选',
  `min_salary` int DEFAULT NULL COMMENT '最低薪资 (单位: k)',
  `max_salary` int DEFAULT NULL COMMENT '最高薪资 (单位: k)',
  `headcount` int DEFAULT '1' COMMENT '招聘人数',
  `experience` varchar(20) DEFAULT '不限' COMMENT '经验要求: 应届生, 1-3年, 3-5年, 5-10年',
  `degree` varchar(20) DEFAULT '不限' COMMENT '学历要求: 大专, 本科, 硕士',
  `tags` varchar(255) DEFAULT NULL COMMENT '技能/福利标签(逗号分隔): 如 Java,Vue,双休,五险一金',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '职位状态: 0-已下架, 1-招聘中, 2-已归档',
  `audit_status` tinyint NOT NULL DEFAULT '0' COMMENT '审核状态: 0-待审核, 1-通过, 2-驳回',
  `audit_reason` varchar(255) DEFAULT NULL COMMENT '审核驳回原因',
  `audit_time` datetime DEFAULT NULL COMMENT '审核时间',
  `last_edit_summary` varchar(500) DEFAULT NULL COMMENT '最近修改摘要',
  `last_edit_time` datetime DEFAULT NULL COMMENT '最近修改时间',
  `view_count` int DEFAULT '0' COMMENT '浏览量(热度排序用)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `job_info_ibfk_1` (`merchant_id`),
  KEY `idx_job_query` (`status`,`work_location`,`category_id`),
  KEY `idx_job_salary` (`min_salary`),
  KEY `fk_job_post_category` (`category_id`),
  KEY `idx_job_post_audit_time` (`audit_status`,`create_time` DESC),
  CONSTRAINT `fk_job_post_category` FOREIGN KEY (`category_id`) REFERENCES `job_category` (`id`),
  CONSTRAINT `job_post_ibfk_1` FOREIGN KEY (`merchant_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `chk_job_post_audit_status` CHECK ((`audit_status` in (0,1,2))),
  CONSTRAINT `chk_job_post_status` CHECK ((`status` in (0,1,2)))
) ENGINE=InnoDB AUTO_INCREMENT=161 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='岗位发布信息表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `job_view_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `job_view_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `job_id` bigint NOT NULL COMMENT '职位ID',
  `merchant_id` bigint NOT NULL COMMENT '商家ID',
  `viewer_id` bigint DEFAULT NULL COMMENT '浏览者ID(未登录可为空)',
  `view_time` datetime NOT NULL COMMENT '浏览时间',
  PRIMARY KEY (`id`),
  KEY `idx_job_view_merchant_time` (`merchant_id`,`view_time`),
  KEY `idx_job_view_job` (`job_id`),
  KEY `idx_job_view_viewer_id` (`viewer_id`),
  CONSTRAINT `fk_job_view_log_job` FOREIGN KEY (`job_id`) REFERENCES `job_post` (`id`),
  CONSTRAINT `fk_job_view_log_merchant` FOREIGN KEY (`merchant_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_job_view_log_viewer` FOREIGN KEY (`viewer_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=110 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='职位浏览记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `merchant_license`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `merchant_license` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `merchant_id` bigint NOT NULL,
  `image_url` varchar(255) NOT NULL,
  `name` varchar(50) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_qual_merchant` (`merchant_id`),
  CONSTRAINT `fk_merchant_license_merchant` FOREIGN KEY (`merchant_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `merchant_profile`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `merchant_profile` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '关联sys_user.id',
  `company_name` varchar(100) NOT NULL COMMENT '企业/店铺名称',
  `company_logo` varchar(255) DEFAULT NULL COMMENT '企业Logo',
  `industry` varchar(50) DEFAULT NULL COMMENT '所属行业 (如: 餐饮, 互联网, 零售)',
  `scale` varchar(50) DEFAULT NULL COMMENT '人员规模 (如: 0-20人, 20-99人)',
  `financing` varchar(50) DEFAULT NULL COMMENT '融资阶段 (如: 个体户/不融资, 天使轮, 已上市)',
  `description` text COMMENT '企业介绍/品牌故事 (富文本)',
  `province` varchar(20) DEFAULT NULL COMMENT '省份',
  `city` varchar(20) DEFAULT NULL COMMENT '城市',
  `district` varchar(20) DEFAULT NULL COMMENT '区/县',
  `address` varchar(200) DEFAULT NULL COMMENT '详细地址 (门牌号)',
  `contact_name` varchar(50) DEFAULT NULL COMMENT '联系人姓名',
  `contact_phone` varchar(20) DEFAULT NULL COMMENT '联系人电话',
  `credit_code` varchar(50) DEFAULT NULL COMMENT '统一社会信用代码',
  `legal_person` varchar(50) DEFAULT NULL COMMENT '法人姓名',
  `longitude` decimal(10,6) DEFAULT NULL COMMENT '经度',
  `latitude` decimal(10,6) DEFAULT NULL COMMENT '纬度',
  `license_url` varchar(255) DEFAULT NULL COMMENT '营业执照图片',
  `qualification_urls` text COMMENT '资质材料(JSON数组)',
  `audit_status` tinyint DEFAULT '0' COMMENT '审核状态: 0-待审核, 1-通过, 2-驳回',
  `audit_reason` varchar(255) DEFAULT NULL COMMENT '审核驳回原因',
  `audit_time` datetime DEFAULT NULL COMMENT '审核时间',
  `publish_status` tinyint DEFAULT '1' COMMENT '发布状态: 0-限制发布,1-正常,2-封禁',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_user` (`user_id`),
  KEY `idx_merchant_profile_audit_time` (`audit_status`,`update_time` DESC),
  CONSTRAINT `fk_merchant_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=70 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商家企业信息扩展表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `report_evidence`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `report_evidence` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `report_id` bigint NOT NULL COMMENT '举报ID(report_info.id)',
  `file_url` varchar(500) NOT NULL COMMENT '证据文件地址',
  `file_type` varchar(20) NOT NULL DEFAULT 'FILE' COMMENT '证据类型: IMAGE/PDF/FILE',
  `sort_order` tinyint NOT NULL DEFAULT '1' COMMENT '顺序（从1开始）',
  `uploader_id` bigint DEFAULT NULL COMMENT '上传者ID(sys_user.id)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_report_evidence_report_sort` (`report_id`,`sort_order`),
  KEY `idx_report_evidence_uploader_time` (`uploader_id`,`create_time`),
  CONSTRAINT `fk_report_evidence_report` FOREIGN KEY (`report_id`) REFERENCES `report_info` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_report_evidence_uploader` FOREIGN KEY (`uploader_id`) REFERENCES `sys_user` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='举报证据附件表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `report_info`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `report_info` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `type` varchar(20) NOT NULL COMMENT '举报类型: JOB/MERCHANT/USER',
  `target_id` bigint NOT NULL COMMENT '被举报对象ID(职位ID或商家账号ID)',
  `reporter_id` bigint NOT NULL COMMENT '举报人ID(sys_user.id)',
  `reason` varchar(500) NOT NULL COMMENT '举报原因',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '处理状态: 0-待处理, 1-已处理, 2-已驳回',
  `result` varchar(500) DEFAULT NULL COMMENT '处理结果说明',
  `action_code` varchar(64) DEFAULT NULL COMMENT '处理动作代码（如 JOB_OFFLINE/USER_BAN/REJECT）',
  `handled_by` bigint DEFAULT NULL COMMENT '处理人ID(sys_user.id)',
  `handled_time` datetime DEFAULT NULL COMMENT '处理时间',
  `evidence` varchar(1000) DEFAULT NULL COMMENT '举报证据（文件地址列表，逗号分隔）',
  `target_snapshot` json DEFAULT NULL COMMENT '被举报对象快照（JSON）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_report_status_type_time` (`status`,`type`,`create_time`),
  KEY `idx_report_target_id` (`target_id`),
  KEY `idx_report_reporter_id` (`reporter_id`),
  KEY `idx_report_action_code` (`action_code`),
  KEY `idx_report_handled_by_time` (`handled_by`,`handled_time`),
  KEY `idx_report_status_handled_time` (`status`,`handled_time`),
  CONSTRAINT `fk_report_handled_by` FOREIGN KEY (`handled_by`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `fk_report_reporter` FOREIGN KEY (`reporter_id`) REFERENCES `sys_user` (`id`),
  CONSTRAINT `chk_report_info_status` CHECK ((`status` in (0,1,2))),
  CONSTRAINT `chk_report_info_type` CHECK ((`type` in (_utf8mb4'JOB',_utf8mb4'MERCHANT',_utf8mb4'USER')))
) ENGINE=InnoDB AUTO_INCREMENT=23 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='举报记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `resume_attachment_permission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `resume_attachment_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `applicant_id` bigint NOT NULL COMMENT '授权人(求职者ID)',
  `merchant_id` bigint NOT NULL COMMENT '被授权人(商家ID)',
  `attachment_type` varchar(20) DEFAULT 'RESUME' COMMENT '简历类型',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '授权状态: 0-待同意, 1-已授权, 2-已拒绝',
  `expire_time` datetime DEFAULT NULL COMMENT '授权有效期',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_resume_attach_permission` (`applicant_id`,`merchant_id`,`attachment_type`),
  KEY `fk_resume_attach_merchant` (`merchant_id`),
  CONSTRAINT `fk_resume_attach_applicant` FOREIGN KEY (`applicant_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_resume_attach_merchant` FOREIGN KEY (`merchant_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chk_resume_attach_status` CHECK ((`status` in (0,1,2)))
) ENGINE=InnoDB AUTO_INCREMENT=31 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='附件简历授权记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sys_log_audit`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_log_audit` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `module` varchar(20) NOT NULL COMMENT '模块: JOB/MERCHANT/REPORT/AUTH',
  `action` varchar(30) NOT NULL COMMENT '动作: CREATE/UPDATE/AUDIT/RESUBMIT/STATUS/HANDLE',
  `target_id` bigint DEFAULT NULL COMMENT '目标ID(职位/商家/举报等)',
  `operator_id` bigint DEFAULT NULL COMMENT '操作者ID(sys_user.id)',
  `operator_role` varchar(20) DEFAULT NULL COMMENT '操作者角色',
  `detail` varchar(1000) DEFAULT NULL COMMENT '操作详情(摘要/JSON)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_audit_module_target_time` (`module`,`target_id`,`create_time`),
  KEY `idx_audit_operator_time` (`operator_id`,`create_time`),
  CONSTRAINT `fk_audit_operator` FOREIGN KEY (`operator_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1564 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='敏感操作审计日志';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sys_log_login`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_log_login` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '关联sys_user.id',
  `ip` varchar(64) DEFAULT NULL COMMENT '登录IP',
  `device` varchar(100) DEFAULT NULL COMMENT '设备/系统',
  `user_agent` varchar(500) DEFAULT NULL COMMENT 'User-Agent',
  `login_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_login_user_time` (`user_id`,`login_time`),
  CONSTRAINT `fk_login_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=845 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户登录日志表';
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `sys_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` varchar(50) NOT NULL COMMENT '登录账号 (唯一)',
  `password` varchar(100) NOT NULL COMMENT '加密密码 (BCrypt存储)',
  `nickname` varchar(50) DEFAULT NULL COMMENT '对外昵称 (HR称呼/求职者姓名)',
  `role` enum('APPLICANT','MERCHANT','ADMIN') NOT NULL COMMENT '角色: APPLICANT-求职者, MERCHANT-商家, ADMIN-管理员',
  `role_sort` tinyint GENERATED ALWAYS AS ((case `role` when _utf8mb4'ADMIN' then 1 when _utf8mb4'MERCHANT' then 2 when _utf8mb4'APPLICANT' then 3 else 9 end)) STORED COMMENT '角色排序(ADMIN->MERCHANT->APPLICANT)',
  `phone` varchar(20) DEFAULT NULL COMMENT '联系电话',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `avatar` varchar(255) DEFAULT NULL COMMENT '头像链接 (OSS/本地路径)',
  `status` tinyint DEFAULT '1' COMMENT '帐号状态: 0-禁用, 1-正常',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `ban_status` tinyint DEFAULT '0' COMMENT '封禁状态: 0-正常,1-限制,2-封禁/拉黑',
  `ban_reason` varchar(255) DEFAULT NULL COMMENT '封禁原因',
  `ban_until` datetime DEFAULT NULL COMMENT '封禁截止时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_sys_user_role_sort` (`role_sort`,`id`)
) ENGINE=InnoDB AUTO_INCREMENT=321 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='核心用户账号表 (Authentication)';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50003 TRIGGER `trg_sys_user_sync_contact_insert` AFTER INSERT ON `sys_user` FOR EACH ROW BEGIN
    UPDATE applicant_profile
    SET phone = NEW.phone,
        email = NEW.email
    WHERE user_id = NEW.id;
END */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
/*!50003 SET @saved_cs_client      = @@character_set_client */ ;
/*!50003 SET @saved_cs_results     = @@character_set_results */ ;
/*!50003 SET @saved_col_connection = @@collation_connection */ ;
/*!50003 SET character_set_client  = utf8mb4 */ ;
/*!50003 SET character_set_results = utf8mb4 */ ;
/*!50003 SET collation_connection  = utf8mb4_0900_ai_ci */ ;
/*!50003 SET @saved_sql_mode       = @@sql_mode */ ;
/*!50003 SET sql_mode              = 'ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION' */ ;
DELIMITER ;;
/*!50003 CREATE*/ /*!50003 TRIGGER `trg_sys_user_sync_contact_update` AFTER UPDATE ON `sys_user` FOR EACH ROW BEGIN
    UPDATE applicant_profile
    SET phone = NEW.phone,
        email = NEW.email
    WHERE user_id = NEW.id
      AND ((NOT (NEW.phone <=> OLD.phone)) OR (NOT (NEW.email <=> OLD.email)));
END */;;
DELIMITER ;
/*!50003 SET sql_mode              = @saved_sql_mode */ ;
/*!50003 SET character_set_client  = @saved_cs_client */ ;
/*!50003 SET character_set_results = @saved_cs_results */ ;
/*!50003 SET collation_connection  = @saved_col_connection */ ;
DROP TABLE IF EXISTS `user_privacy_setting`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_privacy_setting` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '关联sys_user.id',
  `contact_visibility` varchar(20) DEFAULT 'DELIVERY' COMMENT '联系方式可见范围: PUBLIC/DELIVERY/AUTH',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_privacy_user` (`user_id`),
  CONSTRAINT `fk_privacy_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chk_privacy_visibility` CHECK ((`contact_visibility` in (_utf8mb4'PUBLIC',_utf8mb4'DELIVERY',_utf8mb4'AUTH')))
) ENGINE=InnoDB AUTO_INCREMENT=49 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户隐私设置表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;


