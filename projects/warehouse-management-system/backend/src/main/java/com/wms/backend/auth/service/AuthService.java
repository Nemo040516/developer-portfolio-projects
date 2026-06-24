/**
 * @file 速览索引
 * @summary 认证服务层，负责登录主流程：账号校验、状态校验、密码校验、令牌签发与菜单下发。
 * @core 1. 读取账号并校验是否存在
 * @core 2. 校验账号启用状态与角色绑定
 * @core 3. 校验密码并生成 JWT
 * @core 4. 返回角色菜单与登录响应
 * @entry 先看：login
 * @deps 关键依赖：AuthRepository、PasswordEncoder、JwtTokenService、MenuService
 * @risk 高风险修改点：错误码口径（4004/4005/4009）、密码匹配策略、token 载荷字段
 * @link 相关文件：后端/src/main/java/com/wms/backend/auth/repository/AuthRepository.java
 */
package com.wms.backend.auth.service;

import com.wms.backend.auth.dto.LoginRequest;
import com.wms.backend.auth.dto.LoginResponse;
import com.wms.backend.auth.model.UserLoginInfo;
import com.wms.backend.auth.repository.AuthRepository;
import com.wms.backend.exception.BusinessException;
import com.wms.backend.security.JwtTokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuthService {

    private final AuthRepository authRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final MenuService menuService;

    public AuthService(
            AuthRepository authRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            MenuService menuService
    ) {
        this.authRepository = authRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.menuService = menuService;
    }

    public LoginResponse login(LoginRequest request) {
        UserLoginInfo user = authRepository.findByUsername(request.username())
                .orElseThrow(() -> new BusinessException(4004, "账号或密码错误"));
        if (user.status() == null || user.status() != 1) {
            throw new BusinessException(4005, "账号已被禁用");
        }
        if (user.roleCode() == null || user.roleCode().isBlank()) {
            throw new BusinessException(4009, "账号未绑定有效角色");
        }
        if (!passwordEncoder.matches(request.password(), user.password())) {
            throw new BusinessException(4004, "账号或密码错误");
        }
        List<String> menus = menuService.menusByRole(user.roleCode());
        String token = jwtTokenService.createToken(user.userId(), user.username(), user.roleCode());
        return new LoginResponse(token, user.username(), user.roleCode(), menus);
    }
}
