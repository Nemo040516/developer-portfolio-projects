/*
 * 文件速览：
 * 1. 文件职责：统一生成与解析 JWT，并在启动阶段拒绝使用缺失或过短的密钥配置。
 * 2. 对外入口：generateToken、getClaimsByToken。
 * 3. 关键结构：app.jwt.secret / app.jwt.expire-ms 配置注入、密钥长度校验、HS256 签名。
 * 4. 阅读建议：先看构造方法里的密钥与有效期校验，再看生成与解析方法。
 */
package com.example.backend.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

/**
 * JWT 工具类
 * 用于生成和解析 JSON Web Token
 */
@Component
public class JwtUtils {

    private static final long DEFAULT_EXPIRE_MS = 30L * 60 * 1000;

    private final Key key;
    private final long expireMs;

    public JwtUtils(@Value("${app.jwt.secret:}") String secret,
                    @Value("${app.jwt.expire-ms:" + DEFAULT_EXPIRE_MS + "}") long expireMs) {
        this.key = buildKey(secret);
        this.expireMs = validateExpireMs(expireMs);
    }

    private Key buildKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("缺少 app.jwt.secret / APP_JWT_SECRET 配置，系统拒绝使用仓库内默认 JWT 密钥");
        }
        if (secret.length() < 32) {
            throw new IllegalStateException("app.jwt.secret 至少需要 32 个字符");
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private long validateExpireMs(long expireMs) {
        if (expireMs <= 0) {
            throw new IllegalStateException("app.jwt.expire-ms 必须为正整数毫秒值");
        }
        return expireMs;
    }

    /**
     * 生成 Token
     * @param userId 用户 ID
     * @param role 用户角色
     * @return 加密后的 Token 字符串
     */
    public String generateToken(Long userId, String role) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expireMs);

        return Jwts.builder()
                .setSubject(String.valueOf(userId)) // 设置主题（通常是用户ID）
                .claim("role", role) // 添加自定义载荷：角色
                .setIssuedAt(now) // 签发时间
                .setExpiration(expiration) // 过期时间
                .signWith(key, SignatureAlgorithm.HS256) // 签名算法和密钥
                .compact();
    }

    long getExpireMs() {
        return expireMs;
    }

    /**
     * 解析 Token
     * @param token Token 字符串
     * @return 包含 Token 信息的 Claims 对象
     */
    public Claims getClaimsByToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key) // 设置验签密钥
                .build()
                .parseClaimsJws(token) // 解析 Token
                .getBody();
    }
}
