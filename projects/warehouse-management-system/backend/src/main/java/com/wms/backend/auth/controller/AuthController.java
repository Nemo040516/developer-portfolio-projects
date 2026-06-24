/**
 * @file 速览索引
 * @summary 认证控制器，负责登录、当前用户信息回显与服务连通性探活。
 * @core 1. 提供 `/api/auth/login` 登录入口
 * @core 2. 提供 `/api/auth/me` 基于JWT解析用户与菜单信息
 * @core 3. 提供 `/api/ping` 健康探活接口
 * @entry 先看：login、me、ping
 * @deps 关键依赖：AuthService、JwtTokenService、MenuService
 * @state 关键数据：Authorization Bearer Token、JWT Claims（subject/role）
 * @risk 高风险修改点：Bearer 解析规则、role claim 键名、菜单回传口径
 * @link 相关文件：后端/src/main/java/com/wms/backend/auth/service/AuthService.java、后端/src/main/java/com/wms/backend/security/JwtTokenService.java
 */
package com.wms.backend.auth.controller;

import com.wms.backend.auth.dto.LoginRequest;
import com.wms.backend.auth.dto.LoginResponse;
import com.wms.backend.auth.service.AuthService;
import com.wms.backend.auth.service.MenuService;
import com.wms.backend.common.ApiResponse;
import com.wms.backend.security.JwtTokenService;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService authService;
    private final JwtTokenService jwtTokenService;
    private final MenuService menuService;

    public AuthController(AuthService authService, JwtTokenService jwtTokenService, MenuService menuService) {
        this.authService = authService;
        this.jwtTokenService = jwtTokenService;
        this.menuService = menuService;
    }

    @PostMapping("/auth/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @GetMapping("/auth/me")
    public ApiResponse<MeResponse> me(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        String token = authorization.replace("Bearer ", "");
        Claims claims = jwtTokenService.parse(token);
        String username = claims.getSubject();
        String roleCode = claims.get("role", String.class);
        return ApiResponse.success(new MeResponse(username, roleCode, menuService.menusByRole(roleCode)));
    }

    @GetMapping("/ping")
    public ApiResponse<String> ping() {
        return ApiResponse.success("pong");
    }

    public record MeResponse(String username, String roleCode, java.util.List<String> menus) {
    }
}
