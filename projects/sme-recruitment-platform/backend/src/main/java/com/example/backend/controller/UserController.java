/*
 * 文件速览：
 * 1. 文件职责：处理用户资料、隐私、密码、登录记录与认证状态相关接口。
 * 2. 对外入口：/user/info、/user/profile、/user/password、/user/login-logs、/user/verify。
 * 3. 关键结构：Token 解析、账号资料更新、密码修改、认证状态读写。
 * 4. 阅读建议：先看 getUserInfo / changePassword，再看其余资料类接口。
 */
package com.example.backend.controller;

import com.example.backend.common.Result;
import com.example.backend.dto.ApplicantVerifyDTO;
import com.example.backend.dto.PasswordChangeDTO;
import com.example.backend.dto.UserPrivacyUpdateDTO;
import com.example.backend.dto.UserProfileUpdateDTO;
import com.example.backend.entity.ApplicantVerification;
import com.example.backend.entity.MerchantInfo;
import com.example.backend.entity.SysUser;
import com.example.backend.entity.UserLoginLog;
import com.example.backend.entity.UserPrivacySetting;
import com.example.backend.service.ApplicantVerificationService;
import com.example.backend.service.AuditLogService;
import com.example.backend.service.MerchantInfoService;
import com.example.backend.service.SysUserService;
import com.example.backend.service.UserLoginLogService;
import com.example.backend.service.UserPrivacySettingService;
import com.example.backend.utils.ControllerAccessUtils;
import com.example.backend.utils.JwtUtils;
import com.example.backend.utils.SecurityUtils;
import com.example.backend.utils.UploadFileUtils;
import com.example.backend.vo.UserInfoVO;
import com.example.backend.vo.UserLoginLogVO;
import com.example.backend.vo.UserVerifyStatusVO;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户控制器
 * 处理用户相关信息的获取
 */
@RestController
@Validated
@RequestMapping("/user")
public class UserController {

    private static final DateTimeFormatter MINUTE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Autowired
    private SysUserService sysUserService;

    @Autowired
    private MerchantInfoService merchantInfoService;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserPrivacySettingService userPrivacySettingService;

    @Autowired
    private UserLoginLogService userLoginLogService;

    @Autowired
    private ApplicantVerificationService applicantVerificationService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuditLogService auditLogService;

    private Long requireLogin() {
        return ControllerAccessUtils.requireLogin();
    }

    private Long requireApplicant(String forbiddenMessage) {
        return ControllerAccessUtils.requireApplicant(forbiddenMessage);
    }

    /**
     * 获取当前登录用户的信息
     * 从请求头中解析 Token，获取用户 ID 并查询数据库
     * @param request HTTP 请求对象
     * @return 用户详细信息
     */
    @GetMapping("/info")
    public Result<SysUser> getUserInfo(HttpServletRequest request) {
        // 从请求头中获取 Token（兼容 Bearer 与旧格式）
        String token = extractToken(request);
        if (StringUtils.hasText(token)) {
            try {
                // 解析 Token 获取 Claims
                Claims claims = jwtUtils.getClaimsByToken(token);
                // 获取用户 ID (Subject)
                String userId = claims.getSubject();
                // 根据 ID 查询用户信息
                SysUser user = sysUserService.getById(userId);
                if (user != null) {
                    // 处于安全考虑，将密码置空
                    user.setPassword(null);
                    // 角色规范化（兼容旧角色 HR/STUDENT）
                    user.setRole(SecurityUtils.normalizeRole(user.getRole()));
                    return Result.success(user);
                }
            } catch (Exception e) {
                return Result.error(401, "Token无效");
            }
        }
        return Result.error(401, "未登录");
    }

    /**
     * 获取当前用户的简要信息（用于前端头部展示）
     * 包含：昵称、头像、角色、认证状态
     */
    @GetMapping("/me")
    public Result<UserInfoVO> getCurrentUser(HttpServletRequest request) {
        String token = extractToken(request);
        if (StringUtils.hasText(token)) {
            try {
                Claims claims = jwtUtils.getClaimsByToken(token);
                String userId = claims.getSubject();
                SysUser user = sysUserService.getById(userId);
                
                if (user != null) {
                    UserInfoVO userInfoVO = new UserInfoVO();
                    BeanUtils.copyProperties(user, userInfoVO);
                    userInfoVO.setRole(SecurityUtils.normalizeRole(userInfoVO.getRole()));
                    
                    // 如果是商家，查询认证状态（兼容旧角色 HR）
                    if ("MERCHANT".equals(userInfoVO.getRole())) {
                        MerchantInfo merchantInfo = merchantInfoService.getByUserId(user.getId());
                        if (merchantInfo != null) {
                            userInfoVO.setAuditStatus(merchantInfo.getAuditStatus());
                        } else {
                            // 未提交过资料
                            userInfoVO.setAuditStatus(3); 
                        }
                    } else {
                        // 求职者/管理员默认已认证
                        userInfoVO.setAuditStatus(1);
                    }
                    
                    return Result.success(userInfoVO);
                }
            } catch (Exception e) {
                // Token 无效时，不返回 401，而是返回 null，让前端知道未登录
                return Result.success(null);
            }
        }
        // 未携带 Token，也返回 null
        return Result.success(null);
    }

    /**
     * 更新账号基础资料（昵称/手机号/邮箱/头像）
     */
    @PutMapping("/profile")
    public Result<SysUser> updateProfile(@RequestBody @Valid UserProfileUpdateDTO dto) {
        Long userId = requireLogin();
        UpdateWrapper<SysUser> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", userId);
        if (dto.getNickname() != null) {
            wrapper.set("nickname", trimToNull(dto.getNickname()));
        }
        if (dto.getPhone() != null) {
            wrapper.set("phone", trimToNull(dto.getPhone()));
        }
        if (dto.getEmail() != null) {
            wrapper.set("email", trimToNull(dto.getEmail()));
        }
        if (dto.getAvatar() != null) {
            wrapper.set("avatar", trimToNull(dto.getAvatar()));
        }
        sysUserService.update(wrapper);

        SysUser user = sysUserService.getById(userId);
        if (user != null) {
            user.setPassword(null);
            user.setRole(SecurityUtils.normalizeRole(user.getRole()));
        }
        return Result.success(user);
    }

    /**
     * 上传并更新头像
     */
    @PostMapping("/avatar")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        Long userId = requireLogin();
        if (file == null || file.isEmpty()) {
            return Result.error(400, "文件不能为空");
        }

        long maxSize = 2 * 1024 * 1024;
        if (file.getSize() > maxSize) {
            return Result.error(400, "头像大小不能超过2MB");
        }

        String extension = UploadFileUtils.extractExtension(file.getOriginalFilename());
        if (!UploadFileUtils.isAllowedExtension(extension, "jpg", "jpeg", "png")) {
            return Result.error(400, "仅支持 JPG/PNG 图片格式");
        }

        try {
            String fileUrl = UploadFileUtils.storeUnderUploads(file, userId, "avatar", "avatars");
            SysUser user = sysUserService.getById(userId);
            if (user == null) {
                return Result.error(404, "用户不存在");
            }
            user.setAvatar(fileUrl);
            sysUserService.updateById(user);
            return Result.success(fileUrl);
        } catch (Exception e) {
            return Result.error(500, "上传失败");
        }
    }

    /**
     * 获取隐私设置（联系方式可见范围）
     */
    @GetMapping("/privacy")
    public Result<UserPrivacySetting> getPrivacySetting() {
        Long userId = requireLogin();
        QueryWrapper<UserPrivacySetting> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        UserPrivacySetting setting = userPrivacySettingService.getOne(wrapper);
        if (setting == null) {
            setting = new UserPrivacySetting();
            setting.setUserId(userId);
            setting.setContactVisibility("DELIVERY");
        }
        return Result.success(setting);
    }

    /**
     * 更新隐私设置
     */
    @PutMapping("/privacy")
    public Result<UserPrivacySetting> updatePrivacySetting(@RequestBody @Valid UserPrivacyUpdateDTO dto) {
        Long userId = requireLogin();
        String visibility = normalizeVisibility(dto.getContactVisibility());
        if (visibility == null) {
            return Result.error(400, "联系方式可见范围不合法");
        }

        QueryWrapper<UserPrivacySetting> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        UserPrivacySetting setting = userPrivacySettingService.getOne(wrapper);
        LocalDateTime now = LocalDateTime.now();
        if (setting == null) {
            setting = new UserPrivacySetting();
            setting.setUserId(userId);
            setting.setContactVisibility(visibility);
            setting.setCreateTime(now);
            setting.setUpdateTime(now);
            userPrivacySettingService.save(setting);
        } else {
            setting.setContactVisibility(visibility);
            setting.setUpdateTime(now);
            userPrivacySettingService.updateById(setting);
        }
        auditLogService.record("AUTH", "UPDATE_PRIVACY", userId, "contactVisibility=" + visibility);
        return Result.success(setting);
    }

    /**
     * 修改密码
     */
    @PostMapping("/password")
    public Result<?> changePassword(@RequestBody @Valid PasswordChangeDTO dto) {
        Long userId = requireLogin();
        SysUser user = sysUserService.getById(userId);
        if (user == null) {
            return Result.error(404, "用户不存在");
        }
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            return Result.error(400, "原密码不正确");
        }
        if (dto.getOldPassword().equals(dto.getNewPassword())) {
            return Result.error(400, "新密码不能与原密码一致");
        }
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        user.setUpdateTime(LocalDateTime.now());
        sysUserService.updateById(user);
        auditLogService.record("AUTH", "CHANGE_PASSWORD", userId, "用户主动修改密码");
        return Result.success("密码已更新");
    }

    /**
     * 获取最近登录记录
     */
    @GetMapping("/login-logs")
    public Result<List<UserLoginLogVO>> getLoginLogs(
            @RequestParam(value = "limit", required = false)
            @Positive(message = "查询条数必须为正数") Integer limit) {
        Long userId = requireLogin();
        int safeLimit = limit == null ? 3 : Math.min(Math.max(limit, 1), 20);
        QueryWrapper<UserLoginLog> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId)
            .orderByDesc("login_time")
            .last("limit " + safeLimit);
        List<UserLoginLog> logs = userLoginLogService.list(wrapper);
        List<UserLoginLogVO> result = new ArrayList<>();
        for (UserLoginLog log : logs) {
            UserLoginLogVO vo = new UserLoginLogVO();
            if (log.getLoginTime() != null) {
                vo.setTime(log.getLoginTime().format(MINUTE_FORMATTER));
            }
            vo.setIp(log.getIp());
            vo.setDevice(log.getDevice());
            result.add(vo);
        }
        return Result.success(result);
    }

    /**
     * 获取求职者认证状态
     */
    @GetMapping("/verify")
    public Result<UserVerifyStatusVO> getVerifyStatus() {
        Long userId = requireLogin();
        QueryWrapper<ApplicantVerification> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        ApplicantVerification record = applicantVerificationService.getOne(wrapper);
        UserVerifyStatusVO vo = new UserVerifyStatusVO();
        if (record == null) {
            vo.setStatus("UNVERIFIED");
            return Result.success(vo);
        }
        vo.setStatus(record.getStatus());
        if (record.getSubmitTime() != null) {
            vo.setSubmittedAt(record.getSubmitTime().format(MINUTE_FORMATTER));
        }
        if (record.getAuditTime() != null) {
            vo.setAuditTime(record.getAuditTime().format(MINUTE_FORMATTER));
        }
        if (record.getAuditUserId() != null) {
            vo.setAuditUserId(record.getAuditUserId());
            SysUser auditor = sysUserService.getById(record.getAuditUserId());
            if (auditor != null) {
                String name = StringUtils.hasText(auditor.getNickname())
                        ? auditor.getNickname()
                        : auditor.getUsername();
                vo.setAuditUserName(name);
            }
        }
        vo.setAuditReason(record.getAuditReason());
        return Result.success(vo);
    }

    /**
     * 提交求职者认证
     */
    @PostMapping("/verify")
    public Result<UserVerifyStatusVO> submitVerify(@RequestBody @Valid ApplicantVerifyDTO dto) {
        Long userId = requireApplicant("仅求职者可提交认证");

        QueryWrapper<ApplicantVerification> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        ApplicantVerification record = applicantVerificationService.getOne(wrapper);
        LocalDateTime now = LocalDateTime.now();
        if (record == null) {
            record = new ApplicantVerification();
            record.setUserId(userId);
            record.setCreateTime(now);
        }
        record.setRealName(dto.getRealName());
        record.setCertType(dto.getCertType());
        record.setCertNo(dto.getCertNo());
        record.setRemark(dto.getRemark());
        record.setStatus("PENDING");
        record.setSubmitTime(now);
        record.setAuditTime(null);
        record.setAuditUserId(null);
        record.setAuditReason(null);
        record.setUpdateTime(now);

        if (record.getId() == null) {
            applicantVerificationService.save(record);
        } else {
            applicantVerificationService.updateById(record);
        }

        auditLogService.record("AUTH", "SUBMIT_VERIFY", userId, "status=PENDING,certType=" + dto.getCertType());
        UserVerifyStatusVO vo = new UserVerifyStatusVO();
        vo.setStatus("PENDING");
        vo.setSubmittedAt(now.format(MINUTE_FORMATTER));
        return Result.success(vo);
    }

    /**
     * 提取 Token（兼容 Bearer 前缀与旧格式）
     */
    private String extractToken(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (!StringUtils.hasText(token)) {
            return null;
        }
        return token.startsWith("Bearer ") ? token.substring(7) : token;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeVisibility(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String upper = value.trim().toUpperCase();
        if ("PUBLIC".equals(upper) || "DELIVERY".equals(upper) || "AUTH".equals(upper)) {
            return upper;
        }
        return null;
    }
}
