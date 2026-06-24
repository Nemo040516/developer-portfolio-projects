/*
 * 文件速览：
 * 1. 文件职责：统一控制器层登录态与角色校验，减少重复的 401/403 样板代码。
 * 2. 对外入口：requireLogin、requireApplicant、requireMerchant、requireAdminOrMerchant。
 * 3. 关键结构：基于 SecurityUtils 读取当前身份，校验失败时抛出 ApiException。
 * 4. 阅读建议：先看 requireLogin，再看各角色方法的错误消息参数。
 */
package com.example.backend.utils;

import com.example.backend.exception.ApiException;

/**
 * 控制器层访问校验工具。
 */
public final class ControllerAccessUtils {

    private ControllerAccessUtils() {
    }

    public static Long requireLogin() {
        return requireLogin("未登录");
    }

    public static Long requireLogin(String unauthorizedMessage) {
        Long userId = SecurityUtils.getUserId();
        if (userId == null) {
            throw new ApiException(401, unauthorizedMessage);
        }
        return userId;
    }

    public static Long requireApplicant(String forbiddenMessage) {
        Long userId = requireLogin();
        if (!SecurityUtils.isApplicantRole(SecurityUtils.getRole())) {
            throw new ApiException(403, forbiddenMessage);
        }
        return userId;
    }

    public static Long requireMerchant(String forbiddenMessage) {
        return requireMerchant("未登录", forbiddenMessage);
    }

    public static Long requireMerchant(String unauthorizedMessage, String forbiddenMessage) {
        Long userId = requireLogin(unauthorizedMessage);
        if (!SecurityUtils.isMerchantRole(SecurityUtils.getRole())) {
            throw new ApiException(403, forbiddenMessage);
        }
        return userId;
    }

    public static Long requireAdmin(String forbiddenMessage) {
        Long userId = requireLogin();
        if (!SecurityUtils.isAdminRole(SecurityUtils.getRole())) {
            throw new ApiException(403, forbiddenMessage);
        }
        return userId;
    }

    public static Long requireAdminOrMerchant(String unauthorizedMessage, String forbiddenMessage) {
        Long userId = requireLogin(unauthorizedMessage);
        String role = SecurityUtils.getRole();
        if (!SecurityUtils.isAdminRole(role) && !SecurityUtils.isMerchantRole(role)) {
            throw new ApiException(403, forbiddenMessage);
        }
        return userId;
    }
}
