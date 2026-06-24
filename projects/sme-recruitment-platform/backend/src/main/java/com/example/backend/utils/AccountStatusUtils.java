/*
 * 文件速览：
 * 1. 文件职责：集中判定账号受限状态，并为登录、JWT 鉴权和治理通知受限态提供统一判断。
 * 2. 对外入口：getBlockedMessage、allowRestrictedNoticeMode。
 * 3. 关键结构：status 禁用判断、banStatus 限制判断、封禁说明文案拼装。
 * 4. 阅读建议：先看 getBlockedMessage，再看 allowRestrictedNoticeMode 的只读放行规则。
 */
package com.example.backend.utils;

import com.example.backend.entity.SysUser;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 账号状态判定工具
 */
public class AccountStatusUtils {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private AccountStatusUtils() {
    }

    /**
     * 返回账号受限提示信息（正常则返回 null）
     */
    public static String getBlockedMessage(SysUser user) {
        if (user == null) {
            return null;
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            return "账号已被禁用，请联系管理员";
        }
        Integer banStatus = user.getBanStatus();
        if (banStatus == null || banStatus == 0) {
            return null;
        }
        if (banStatus == 2) {
            return buildMessage("账号已被封禁", user.getBanReason(), user.getBanUntil());
        }
        if (banStatus == 1) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime until = user.getBanUntil();
            if (until == null || until.isAfter(now)) {
                return buildMessage("账号已被限制使用", user.getBanReason(), until);
            }
        }
        return null;
    }

    /**
     * 当前账号是否允许进入“只读治理提醒模式”。
     * 说明：
     * 1. 仅对 banStatus=1/2 的受限账号开放。
     * 2. status=0 的彻底禁用账号仍不允许登录。
     */
    public static boolean allowRestrictedNoticeMode(SysUser user) {
        if (user == null) {
            return false;
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            return false;
        }
        Integer banStatus = user.getBanStatus();
        return banStatus != null && (banStatus == 1 || banStatus == 2);
    }

    private static String buildMessage(String base, String reason, LocalDateTime until) {
        StringBuilder builder = new StringBuilder(base);
        if (StringUtils.hasText(reason)) {
            builder.append("，原因：").append(reason);
        }
        if (until != null) {
            builder.append("，解封时间：").append(until.format(TIME_FORMATTER));
        }
        return builder.toString();
    }
}
