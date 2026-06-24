/**
 * @file 速览索引
 * @summary 角色数据访问层，负责角色分页查询、详情读取、状态更新与启用角色选项查询。
 * @core 1. 提供角色关键字分页查询
 * @core 2. 提供角色主键查询与状态更新
 * @core 3. 提供仅启用角色的下拉选项查询
 * @entry 先看：pageByKeyword、findById、updateStatus、listEnabledOptions
 * @deps 关键依赖：JdbcTemplate、RoleResponse、RoleOptionResponse
 * @state 关键数据：roleRowMapper、sys_role 表、status=1 选项过滤
 * @risk 高风险修改点：角色查询口径、状态更新 SQL、options 接口的启用过滤条件
 * @link 相关文件：后端/src/main/java/com/wms/backend/role/service/RoleService.java、后端/src/main/java/com/wms/backend/role/controller/RoleController.java
 */
package com.wms.backend.role.repository;

import com.wms.backend.role.dto.RoleOptionResponse;
import com.wms.backend.role.dto.RoleResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class RoleRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<RoleResponse> roleRowMapper = (rs, rowNum) ->
            new RoleResponse(
                    rs.getLong("id"),
                    rs.getString("role_code"),
                    rs.getString("role_name"),
                    rs.getInt("status"),
                    rs.getString("remark"),
                    toLocalDateTime(rs.getTimestamp("created_at")),
                    toLocalDateTime(rs.getTimestamp("updated_at"))
            );

    public RoleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long countByKeyword(String keyword) {
        String sql = """
                SELECT COUNT(1)
                FROM sys_role
                WHERE (? = '' OR role_code LIKE ? OR role_name LIKE ?)
                """;
        String like = "%" + keyword + "%";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, keyword, like, like);
        return count == null ? 0L : count;
    }

    public List<RoleResponse> pageByKeyword(String keyword, int offset, int size) {
        String sql = """
                SELECT id, role_code, role_name, status, remark, created_at, updated_at
                FROM sys_role
                WHERE (? = '' OR role_code LIKE ? OR role_name LIKE ?)
                ORDER BY id DESC
                LIMIT ? OFFSET ?
                """;
        String like = "%" + keyword + "%";
        return jdbcTemplate.query(sql, roleRowMapper, keyword, like, like, size, offset);
    }

    public Optional<RoleResponse> findById(Long id) {
        String sql = """
                SELECT id, role_code, role_name, status, remark, created_at, updated_at
                FROM sys_role
                WHERE id = ?
                """;
        return jdbcTemplate.query(sql, roleRowMapper, id).stream().findFirst();
    }

    public int updateStatus(Long id, Integer status) {
        String sql = "UPDATE sys_role SET status = ? WHERE id = ?";
        return jdbcTemplate.update(sql, status, id);
    }

    public List<RoleOptionResponse> listEnabledOptions() {
        String sql = """
                SELECT id, role_code, role_name
                FROM sys_role
                WHERE status = 1
                ORDER BY id DESC
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new RoleOptionResponse(
                rs.getLong("id"),
                rs.getString("role_code"),
                rs.getString("role_name")
        ));
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
