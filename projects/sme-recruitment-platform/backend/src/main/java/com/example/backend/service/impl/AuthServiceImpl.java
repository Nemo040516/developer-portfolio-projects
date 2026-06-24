/*
 * 文件速览：
 * 1. 文件职责：处理注册、登录与登录返回扩展字段生成，并承接受限账号的只读提醒模式。
 * 2. 对外入口：AuthService#register、AuthService#login。
 * 3. 关键结构：账号校验、JWT 生成、运行时强制改密开关判断。
 * 4. 阅读建议：先看 login，再看 shouldForcePasswordChange 与角色规范化。
 */
package com.example.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.backend.common.Result;
import com.example.backend.config.RuntimeAuthSecuritySettings;
import com.example.backend.entity.AuditLog;
import com.example.backend.entity.SysUser;
import com.example.backend.mapper.SysUserMapper;
import com.example.backend.service.AuditLogService;
import com.example.backend.service.AuthService;
import com.example.backend.utils.AccountStatusUtils;
import com.example.backend.utils.JwtUtils;
import com.example.backend.utils.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 认证服务实现类
 * 处理注册和登录的具体业务逻辑
 */
@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private RuntimeAuthSecuritySettings runtimeAuthSecuritySettings;

    /**
     * 用户注册实现
     * 1. 检查用户名是否已存在
     * 2. 对密码进行加密
     * 3. 设置创建时间和更新时间
     * 4. 保存用户到数据库
     */
    @Override
    public Result<SysUser> register(SysUser sysUser) {
        // 构建查询条件，检查用户名是否存在
        QueryWrapper<SysUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", sysUser.getUsername());
        if (sysUserMapper.selectCount(queryWrapper) > 0) {
            return Result.error("用户名已存在");
        }

        // 注册入口只允许自助创建求职者或商家账号，管理员账号必须走平台内控流程。
        String role = normalizeRegisterRole(sysUser.getRole());
        if (role == null) {
            return Result.error(400, "注册仅支持求职者或商家角色");
        }
        sysUser.setRole(role);

        // 密码加密
        sysUser.setPassword(passwordEncoder.encode(sysUser.getPassword()));
        // 设置时间戳
        sysUser.setCreateTime(LocalDateTime.now());
        sysUser.setUpdateTime(LocalDateTime.now());
        // 插入数据库
        sysUserMapper.insert(sysUser);
        
        // 返回前清空密码，避免泄露
        sysUser.setPassword(null); 
        return Result.success(sysUser);
    }

    /**
     * 用户登录实现
     * 1. 根据用户名查询用户
     * 2. 验证密码是否匹配
     * 3. 生成并返回 JWT Token 以及角色信息
     */
    @Override
    public Result<Map<String, Object>> login(SysUser sysUser) {
        // 根据用户名查询用户
        QueryWrapper<SysUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", sysUser.getUsername());
        SysUser user = sysUserMapper.selectOne(queryWrapper);

        // 验证用户是否存在以及密码是否正确
        if (user == null || !passwordEncoder.matches(sysUser.getPassword(), user.getPassword())) {
            return Result.error("用户名或密码错误");
        }
        // 若存在过期限制，自动解除
        if (user.getBanStatus() != null && user.getBanStatus() == 1
                && user.getBanUntil() != null && user.getBanUntil().isBefore(LocalDateTime.now())) {
            user.setBanStatus(0);
            user.setBanReason(null);
            user.setBanUntil(null);
            user.setUpdateTime(LocalDateTime.now());
            sysUserMapper.updateById(user);
        }
        String blockedMsg = AccountStatusUtils.getBlockedMessage(user);
        boolean restrictedNoticeMode = blockedMsg != null && AccountStatusUtils.allowRestrictedNoticeMode(user);
        if (blockedMsg != null && !restrictedNoticeMode) {
            return Result.error(blockedMsg);
        }

        // 角色规范化（兼容旧角色 HR/STUDENT）
        String normalizedRole = normalizeSupportedRole(user.getRole());
        if (normalizedRole == null) {
            return Result.error("角色不合法");
        }

        // 生成 Token，包含用户 ID 和角色信息
        String token = jwtUtils.generateToken(user.getId(), normalizedRole);
        
        // 构建返回结果，包含 token 和 role
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("role", normalizedRole);
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        data.put("forceChangePassword", shouldForcePasswordChange(user.getId()));
        data.put("restrictedMode", restrictedNoticeMode);
        data.put("restrictedReason", restrictedNoticeMode ? blockedMsg : null);
        
        return Result.success(data);
    }

    /**
     * 判断当前账号是否需要在登录后强制修改密码。
     * 规则：仅当开关开启且最近一次相关审计日志为 RESET_PASSWORD 时返回 true。
     */
    private boolean shouldForcePasswordChange(Long userId) {
        if (userId == null || !runtimeAuthSecuritySettings.isForcePasswordChangeEnabled()) {
            return false;
        }
        AuditLog latestLog = auditLogService.getOne(new LambdaQueryWrapper<AuditLog>()
                .eq(AuditLog::getModule, "AUTH")
                .eq(AuditLog::getTargetId, userId)
                .in(AuditLog::getAction, "RESET_PASSWORD", "CHANGE_PASSWORD")
                .orderByDesc(AuditLog::getCreateTime)
                .orderByDesc(AuditLog::getId)
                .last("LIMIT 1"), false);
        return latestLog != null && "RESET_PASSWORD".equalsIgnoreCase(latestLog.getAction());
    }

    /**
     * 角色规范化：兼容旧角色 HR/STUDENT
     */
    private String normalizeSupportedRole(String role) {
        String normalizedRole = SecurityUtils.normalizeRole(role);
        if (normalizedRole == null) {
            return null;
        }
        if ("APPLICANT".equals(normalizedRole)
                || "MERCHANT".equals(normalizedRole)
                || "ADMIN".equals(normalizedRole)) {
            return normalizedRole;
        }
        return null;
    }

    /**
     * 注册角色规范化：仅允许求职者与商家自助注册。
     */
    private String normalizeRegisterRole(String role) {
        String normalizedRole = normalizeSupportedRole(role);
        if ("APPLICANT".equals(normalizedRole) || "MERCHANT".equals(normalizedRole)) {
            return normalizedRole;
        }
        return null;
    }
}
