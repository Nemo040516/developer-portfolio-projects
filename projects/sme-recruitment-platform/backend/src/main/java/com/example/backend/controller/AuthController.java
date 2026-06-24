package com.example.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.backend.common.Result;
import com.example.backend.entity.SysUser;
import com.example.backend.entity.UserLoginLog;
import com.example.backend.service.AuthService;
import com.example.backend.service.UserLoginLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 认证控制器
 * 处理用户注册和登录请求
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    // 登录日志短窗口去重：同账号同设备同UA在窗口内仅记录一次
    private static final long LOGIN_LOG_DEDUP_WINDOW_SECONDS = 3L;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserLoginLogService userLoginLogService;

    /**
     * 用户注册接口
     * @param sysUser 包含用户名、密码、角色的用户实体
     * @return 注册成功后的用户信息
     */
    @PostMapping("/register")
    public Result<SysUser> register(@RequestBody SysUser sysUser) {
        return authService.register(sysUser);
    }

    /**
     * 用户登录接口
     * @param sysUser 包含用户名、密码的用户实体
     * @return 登录成功后的 Token 和 Role
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody SysUser sysUser, HttpServletRequest request) {
        Result<Map<String, Object>> result = authService.login(sysUser);
        if (result != null && result.getCode() != null && result.getCode() == 200 && result.getData() != null) {
            try {
                Object userIdObj = result.getData().get("userId");
                Long userId = userIdObj == null ? null : Long.valueOf(userIdObj.toString());
                if (userId != null) {
                    LocalDateTime now = LocalDateTime.now();
                    String ip = defaultString(trimToLength(resolveClientIp(request), 64));
                    UserLoginLog log = new UserLoginLog();
                    log.setUserId(userId);
                    log.setIp(ip);
                    String userAgent = request.getHeader("User-Agent");
                    String safeUserAgent = defaultString(trimToLength(userAgent, 500));
                    String safeDevice = defaultString(trimToLength(resolveDevice(userAgent), 100));
                    log.setUserAgent(safeUserAgent);
                    log.setDevice(safeDevice);
                    log.setLoginTime(now);
                    log.setCreateTime(now);
                    if (!isDuplicateLoginLog(userId, ip, safeDevice, safeUserAgent, now)) {
                        userLoginLogService.save(log);
                    }
                }
            } catch (Exception ignored) {
                // 登录日志失败不影响主流程
            }
        }
        return result;
    }

    /**
     * 用户注销接口
     * 由于是 JWT 无状态认证，后端其实不需要做太多操作，
     * 但为了符合 RESTful 规范，提供一个接口供前端调用。
     * 前端在调用此接口成功后，应清除本地存储的 Token。
     */
    @PostMapping("/logout")
    public Result<?> logout() {
        // 如果使用了 Redis 存储 Token 黑名单，可以在这里将 Token 加入黑名单
        // 目前简单实现，直接返回成功，由前端清除 Token
        return Result.success("注销成功");
    }

    /**
     * 获取客户端 IP（支持反向代理）
     */
    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String[] headers = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP"
        };
        for (String header : headers) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank() && !"unknown".equalsIgnoreCase(value)) {
                if (value.contains(",")) {
                    return value.split(",")[0].trim();
                }
                return value.trim();
            }
        }
        return request.getRemoteAddr();
    }

    /**
     * 根据 User-Agent 识别简单设备信息
     */
    private String resolveDevice(String userAgent) {
        if (userAgent == null) {
            return "未知设备";
        }
        String agent = userAgent.toLowerCase();
        if (agent.contains("windows")) {
            return "Windows";
        }
        if (agent.contains("mac os") || agent.contains("macos")) {
            return "macOS";
        }
        if (agent.contains("android")) {
            return "Android";
        }
        if (agent.contains("iphone") || agent.contains("ipad") || agent.contains("ios")) {
            return "iOS";
        }
        if (agent.contains("linux")) {
            return "Linux";
        }
        return "其他设备";
    }

    private String trimToLength(String value, int max) {
        if (value == null) {
            return null;
        }
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    /**
     * 判断是否为短时间重复登录日志
     */
    private boolean isDuplicateLoginLog(Long userId, String ip, String device, String userAgent, LocalDateTime now) {
        if (userId == null || now == null) {
            return false;
        }
        LambdaQueryWrapper<UserLoginLog> query = new LambdaQueryWrapper<UserLoginLog>()
                .eq(UserLoginLog::getUserId, userId)
                .eq(UserLoginLog::getIp, ip)
                .eq(UserLoginLog::getDevice, device)
                .eq(UserLoginLog::getUserAgent, userAgent)
                .ge(UserLoginLog::getLoginTime, now.minusSeconds(LOGIN_LOG_DEDUP_WINDOW_SECONDS));
        return userLoginLogService.count(query) > 0;
    }
}
