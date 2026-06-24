package com.example.backend.utils;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.util.StringUtils;

public class SecurityUtils {

    /**
     * 获取当前登录用户 ID
     * @return 用户 ID
     */
    public static Long getUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() != null) {
            try {
                return Long.valueOf(authentication.getPrincipal().toString());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 获取当前登录用户角色（去除 ROLE_ 前缀）
     * @return 角色字符串（如 ADMIN/MERCHANT/APPLICANT），获取失败则返回 null
     */
    public static String getRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (authority == null || authority.getAuthority() == null) {
                continue;
            }
            String role = authority.getAuthority();
            if (role.startsWith("ROLE_")) {
                role = role.substring(5);
            }
            return normalizeRole(role);
        }
        return null;
    }

    /**
     * 角色规范化：兼容旧角色 HR/STUDENT
     */
    public static String normalizeRole(String role) {
        if (!StringUtils.hasText(role)) {
            return null;
        }
        String upper = role.trim().toUpperCase();
        if (upper.startsWith("ROLE_")) {
            upper = upper.substring(5);
        }
        if ("HR".equals(upper)) {
            return "MERCHANT";
        }
        if ("STUDENT".equals(upper)) {
            return "APPLICANT";
        }
        return upper;
    }

    public static boolean isApplicantRole(String role) {
        return "APPLICANT".equalsIgnoreCase(normalizeRole(role));
    }

    public static boolean isMerchantRole(String role) {
        return "MERCHANT".equalsIgnoreCase(normalizeRole(role));
    }

    public static boolean isAdminRole(String role) {
        return "ADMIN".equalsIgnoreCase(normalizeRole(role));
    }
}
