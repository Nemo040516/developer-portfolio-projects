/**
 * @file 速览索引
 * @summary 用户数据访问层，负责用户分页查询、账号创建落库、角色绑定、状态更新与密码重置。
 * @core 1. 提供按关键字与角色筛选的用户分页查询
 * @core 2. 提供账号唯一性与角色存在性校验查询
 * @core 3. 提供用户新增与用户角色绑定写入
 * @core 4. 提供状态更新与密码重置写入
 * @entry 先看：pageByKeyword、insertUser、bindUserRole、updateStatus、resetPassword
 * @deps 关键依赖：JdbcTemplate、UserCreateRequest、UserResponse、UserService
 * @state 关键数据：userRowMapper、sys_user、sys_user_role、sys_role
 * @risk 高风险修改点：联表字段别名、角色绑定流程顺序、resetPassword 默认密码口径
 * @link 相关文件：后端/src/main/java/com/wms/backend/user/service/UserService.java、后端/src/main/java/com/wms/backend/user/controller/UserController.java
 */
package com.wms.backend.user.repository;

import com.wms.backend.user.dto.UserCreateRequest;
import com.wms.backend.user.dto.UserResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Repository
public class UserRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<UserResponse> userRowMapper = (rs, rowNum) ->
            new UserResponse(
                    rs.getLong("id"),
                    rs.getString("username"),
                    rs.getString("real_name"),
                    rs.getString("mobile"),
                    rs.getString("email"),
                    rs.getInt("status"),
                    rs.getLong("role_id"),
                    rs.getString("role_code"),
                    rs.getString("role_name"),
                    toLocalDateTime(rs.getTimestamp("created_at")),
                    toLocalDateTime(rs.getTimestamp("updated_at"))
            );

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long countByKeyword(String keyword, Long roleId) {
        String sql = """
                SELECT COUNT(1)
                FROM sys_user u
                LEFT JOIN sys_user_role ur ON u.id = ur.user_id
                WHERE (? = '' OR u.username LIKE ? OR u.real_name LIKE ?)
                  AND (? IS NULL OR ur.role_id = ?)
                """;
        String like = "%" + keyword + "%";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, keyword, like, like, roleId, roleId);
        return count == null ? 0L : count;
    }

    public List<UserResponse> pageByKeyword(String keyword, Long roleId, int offset, int size) {
        String sql = """
                SELECT u.id, u.username, u.real_name, u.mobile, u.email, u.status, u.created_at, u.updated_at,
                       r.id AS role_id, r.role_code, r.role_name
                FROM sys_user u
                LEFT JOIN sys_user_role ur ON u.id = ur.user_id
                LEFT JOIN sys_role r ON ur.role_id = r.id
                WHERE (? = '' OR u.username LIKE ? OR u.real_name LIKE ?)
                  AND (? IS NULL OR ur.role_id = ?)
                ORDER BY u.id DESC
                LIMIT ? OFFSET ?
                """;
        String like = "%" + keyword + "%";
        return jdbcTemplate.query(sql, userRowMapper, keyword, like, like, roleId, roleId, size, offset);
    }

    public Optional<UserResponse> findById(Long id) {
        String sql = """
                SELECT u.id, u.username, u.real_name, u.mobile, u.email, u.status, u.created_at, u.updated_at,
                       r.id AS role_id, r.role_code, r.role_name
                FROM sys_user u
                LEFT JOIN sys_user_role ur ON u.id = ur.user_id
                LEFT JOIN sys_role r ON ur.role_id = r.id
                WHERE u.id = ?
                """;
        return jdbcTemplate.query(sql, userRowMapper, id).stream().findFirst();
    }

    public boolean existsByUsername(String username) {
        String sql = "SELECT COUNT(1) FROM sys_user WHERE username = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, username);
        return count != null && count > 0;
    }

    public boolean existsRoleById(Long roleId) {
        String sql = "SELECT COUNT(1) FROM sys_role WHERE id = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, roleId);
        return count != null && count > 0;
    }

    public int insertUser(UserCreateRequest request, String encodedPassword) {
        String sql = """
                INSERT INTO sys_user (username, password, real_name, mobile, email, status)
                VALUES (?, ?, ?, ?, ?, 1)
                """;
        return jdbcTemplate.update(sql, request.username(), encodedPassword, request.realName(), request.mobile(), request.email());
    }

    public Optional<String> findRoleCodeById(Long roleId) {
        String sql = "SELECT role_code FROM sys_role WHERE id = ?";
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("role_code"), roleId)
                .stream()
                .findFirst()
                .map(code -> code == null ? "" : code.trim().toUpperCase(Locale.ROOT));
    }

    public Long findUserIdByUsername(String username) {
        String sql = "SELECT id FROM sys_user WHERE username = ?";
        return jdbcTemplate.queryForObject(sql, Long.class, username);
    }

    public int bindUserRole(Long userId, Long roleId) {
        String sql = "INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)";
        return jdbcTemplate.update(sql, userId, roleId);
    }

    public int updateStatus(Long id, Integer status) {
        String sql = "UPDATE sys_user SET status = ? WHERE id = ?";
        return jdbcTemplate.update(sql, status, id);
    }

    public int resetPassword(Long id) {
        String sql = "UPDATE sys_user SET password = '{noop}12345' WHERE id = ?";
        return jdbcTemplate.update(sql, id);
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
