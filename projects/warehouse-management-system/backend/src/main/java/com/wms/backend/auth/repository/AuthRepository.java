/**
 * @file 速览索引
 * @summary 认证仓储层，负责按用户名读取登录所需账号与角色信息。
 * @core 1. 查询用户基础字段（id/username/password/status）
 * @core 2. 关联用户角色并过滤启用角色
 * @core 3. 输出 `UserLoginInfo` 供服务层鉴权
 * @entry 先看：findByUsername
 * @deps 关键依赖：JdbcTemplate、sys_user、sys_user_role、sys_role
 * @risk 高风险修改点：`LEFT JOIN` 与 `r.status=1` 条件、`ORDER BY ur.id` 角色选择顺序
 * @link 相关文件：后端/src/main/java/com/wms/backend/auth/service/AuthService.java
 */
package com.wms.backend.auth.repository;

import com.wms.backend.auth.model.UserLoginInfo;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class AuthRepository {

    private final JdbcTemplate jdbcTemplate;

    public AuthRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<UserLoginInfo> findByUsername(String username) {
        String sql = """
                SELECT u.id, u.username, u.password, u.status, r.role_code
                FROM sys_user u
                LEFT JOIN sys_user_role ur ON u.id = ur.user_id
                LEFT JOIN sys_role r ON ur.role_id = r.id AND r.status = 1
                WHERE u.username = ?
                ORDER BY ur.id ASC
                LIMIT 1
                """;
        return jdbcTemplate.query(sql, rs -> {
            if (!rs.next()) {
                return Optional.empty();
            }
            UserLoginInfo info = new UserLoginInfo(
                    rs.getLong("id"),
                    rs.getString("username"),
                    rs.getString("password"),
                    rs.getInt("status"),
                    rs.getString("role_code")
            );
            return Optional.of(info);
        }, username);
    }
}
