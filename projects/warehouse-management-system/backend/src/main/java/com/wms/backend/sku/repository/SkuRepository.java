/**
 * @file 速览索引
 * @summary SKU 数据访问层，负责商品分页查询、主键读取、编码唯一校验及增改状态维护。
 * @core 1. 提供 SKU 按关键字分页查询
 * @core 2. 提供 SKU 主键读取与编码唯一性校验
 * @core 3. 提供 SKU 新增、更新与状态更新写入
 * @entry 先看：pageByKeyword、findById、existsBySkuCode、insert、update、updateStatus
 * @deps 关键依赖：JdbcTemplate、SkuCreateRequest、SkuUpdateRequest、SkuResponse
 * @state 关键数据：skuRowMapper、wms_sku 表、safe_stock 默认归零处理
 * @risk 高风险修改点：编码唯一校验、safe_stock 落库口径、状态更新 SQL
 * @link 相关文件：后端/src/main/java/com/wms/backend/sku/service/SkuService.java、后端/src/main/java/com/wms/backend/sku/controller/SkuController.java
 */
package com.wms.backend.sku.repository;

import com.wms.backend.sku.dto.SkuCreateRequest;
import com.wms.backend.sku.dto.SkuResponse;
import com.wms.backend.sku.dto.SkuUpdateRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class SkuRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<SkuResponse> skuRowMapper = (rs, rowNum) ->
            new SkuResponse(
                    rs.getLong("id"),
                    rs.getString("sku_code"),
                    rs.getString("sku_name"),
                    rs.getString("specification"),
                    rs.getString("unit"),
                    rs.getInt("safe_stock"),
                    rs.getInt("status"),
                    rs.getString("remark"),
                    toLocalDateTime(rs.getTimestamp("created_at")),
                    toLocalDateTime(rs.getTimestamp("updated_at"))
            );

    public SkuRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long countByKeyword(String keyword) {
        String sql = """
                SELECT COUNT(1)
                FROM wms_sku
                WHERE (? = '' OR sku_code LIKE ? OR sku_name LIKE ?)
                """;
        String like = "%" + keyword + "%";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, keyword, like, like);
        return count == null ? 0L : count;
    }

    public List<SkuResponse> pageByKeyword(String keyword, int offset, int size) {
        String sql = """
                SELECT id, sku_code, sku_name, specification, unit, safe_stock, status, remark, created_at, updated_at
                FROM wms_sku
                WHERE (? = '' OR sku_code LIKE ? OR sku_name LIKE ?)
                ORDER BY id DESC
                LIMIT ? OFFSET ?
                """;
        String like = "%" + keyword + "%";
        return jdbcTemplate.query(sql, skuRowMapper, keyword, like, like, size, offset);
    }

    public Optional<SkuResponse> findById(Long id) {
        String sql = """
                SELECT id, sku_code, sku_name, specification, unit, safe_stock, status, remark, created_at, updated_at
                FROM wms_sku
                WHERE id = ?
                """;
        return jdbcTemplate.query(sql, skuRowMapper, id).stream().findFirst();
    }

    public boolean existsBySkuCode(String skuCode) {
        String sql = "SELECT COUNT(1) FROM wms_sku WHERE sku_code = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, skuCode);
        return count != null && count > 0;
    }

    public int insert(SkuCreateRequest request) {
        String sql = """
                INSERT INTO wms_sku
                (sku_code, sku_name, specification, unit, safe_stock, status, remark)
                VALUES (?, ?, ?, ?, ?, 1, ?)
                """;
        return jdbcTemplate.update(sql,
                request.skuCode(),
                request.skuName(),
                request.specification(),
                request.unit(),
                request.safeStock() == null ? 0 : request.safeStock(),
                request.remark());
    }

    public int update(Long id, SkuUpdateRequest request) {
        String sql = """
                UPDATE wms_sku
                SET sku_name = ?, specification = ?, unit = ?, safe_stock = ?, remark = ?
                WHERE id = ?
                """;
        return jdbcTemplate.update(sql,
                request.skuName(),
                request.specification(),
                request.unit(),
                request.safeStock() == null ? 0 : request.safeStock(),
                request.remark(),
                id);
    }

    public int updateStatus(Long id, Integer status) {
        String sql = "UPDATE wms_sku SET status = ? WHERE id = ?";
        return jdbcTemplate.update(sql, status, id);
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
