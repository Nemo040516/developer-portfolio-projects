/*
 * 文件速览：
 * 1. 文件职责：集中维护平台治理通知模块的状态字典、类型字典与动作字典。
 * 2. 对外入口：供治理通知实体、服务、控制器和前端对接文档统一引用。
 * 3. 关键结构：通知类型、严重级别、来源模块、状态、动作类型五组常量。
 * 4. 阅读建议：先看 Phase 1 会用到的状态，再看后续扩展类型。
 */
package com.example.backend.common;

/**
 * 平台治理通知模块常量
 */
public final class GovernanceNoticeConstants {

    private GovernanceNoticeConstants() {
    }

    /**
     * 目标角色
     */
    public static final class TargetRole {
        public static final String MERCHANT = "MERCHANT";
        public static final String APPLICANT = "APPLICANT";

        private TargetRole() {
        }
    }

    /**
     * 通知类型
     */
    public static final class NoticeType {
        public static final String JOB_RECTIFY = "JOB_RECTIFY";
        public static final String MERCHANT_RECTIFY = "MERCHANT_RECTIFY";
        public static final String REPORT_RESULT = "REPORT_RESULT";
        public static final String USER_WARNING = "USER_WARNING";
        public static final String BAN_NOTICE = "BAN_NOTICE";

        private NoticeType() {
        }
    }

    /**
     * 严重级别
     */
    public static final class Severity {
        public static final String INFO = "INFO";
        public static final String WARNING = "WARNING";
        public static final String HIGH = "HIGH";

        private Severity() {
        }
    }

    /**
     * 来源模块
     */
    public static final class SourceModule {
        public static final String JOB_AUDIT = "JOB_AUDIT";
        public static final String MERCHANT_AUDIT = "MERCHANT_AUDIT";
        public static final String REPORT = "REPORT";
        public static final String RISK_CONTROL = "RISK_CONTROL";

        private SourceModule() {
        }
    }

    /**
     * 通知状态
     */
    public static final class Status {
        public static final String PENDING_READ = "PENDING_READ";
        public static final String PENDING_ACTION = "PENDING_ACTION";
        public static final String PENDING_REVIEW = "PENDING_REVIEW";
        public static final String FINISHED = "FINISHED";
        public static final String REJECTED = "REJECTED";
        public static final String EXPIRED = "EXPIRED";
        public static final String CLOSED = "CLOSED";

        private Status() {
        }
    }

    /**
     * 动作类型
     */
    public static final class ActionType {
        public static final String READ = "READ";
        public static final String SUBMIT_FIX = "SUBMIT_FIX";
        public static final String REPLY = "REPLY";
        public static final String APPEAL = "APPEAL";
        public static final String APPROVE = "APPROVE";
        public static final String REJECT = "REJECT";
        public static final String CLOSE = "CLOSE";

        private ActionType() {
        }
    }
}
