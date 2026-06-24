/**
 * @file 速览索引
 * @summary JWT 令牌服务，负责签发登录令牌与解析令牌声明。
 * @core 1. 基于配置 secret 与 expireMs 构建签名能力
 * @core 2. 生成包含 uid/role 的 HS256 JWT
 * @core 3. 解析并校验 token，返回 claims
 * @entry 先看：createToken、parse
 * @deps 关键依赖：application.yml(app.jwt.secret/expire-ms)、AuthService、JwtAuthenticationFilter
 * @state 关键字段：claims(subject=username, uid, role)、expireAt
 * @risk 高风险修改点：claims 键名变更会联动鉴权过滤器；secret 长度或算法调整会导致历史 token 失效
 * @link 相关文件：后端/src/main/java/com/wms/backend/security/JwtAuthenticationFilter.java、后端/src/main/java/com/wms/backend/auth/service/AuthService.java
 */
package com.wms.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtTokenService {

    private final byte[] secret;
    private final long expireMs;

    public JwtTokenService(@Value("${app.jwt.secret}") String secret, @Value("${app.jwt.expire-ms}") long expireMs) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.expireMs = expireMs;
    }

    public String createToken(Long userId, String username, String roleCode) {
        Date now = new Date();
        Date expireAt = new Date(now.getTime() + expireMs);
        return Jwts.builder()
                .setSubject(username)
                .claim("uid", userId)
                .claim("role", roleCode)
                .setIssuedAt(now)
                .setExpiration(expireAt)
                .signWith(Keys.hmacShaKeyFor(secret), SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(secret))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
