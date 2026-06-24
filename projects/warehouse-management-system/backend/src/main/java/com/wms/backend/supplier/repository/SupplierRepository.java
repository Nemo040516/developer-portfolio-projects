/**
 * @file 速览索引
 * @summary 供应商数据访问层，负责供应商分页查询、主键读取、编码唯一校验及增改状态写入。
 * @core 1. 提供供应商关键字分页查询
 * @core 2. 提供供应商主键读取与编码唯一校验
 * @core 3. 提供新增、更新与状态更新写入
 * @entry 先看：pageByKeyword、findById、existsBySupplierCode、insert、update、updateStatus
 * @deps 关键依赖：JdbcTemplate、SupplierCreateRequest、SupplierUpdateRequest、SupplierResponse
 * @state 关键数据：supplierRowMapper、wms_supplier 表、lead_time_days 空值归零
 * @risk 高风险修改点：编码唯一性校验、手机号/交期字段落库口径、状态更新 SQL
 * @link 相关文件：后端/src/main/java/com/wms/backend/supplier/service/SupplierService.java、后端/src/main/java/com/wms/backend/supplier/controller/SupplierController.java
 */
package com.wms.backend.supplier.repository;

import com.wms.backend.supplier.dto.SupplierCreateRequest;
import com.wms.backend.supplier.dto.SupplierResponse;
import com.wms.backend.supplier.dto.SupplierUpdateRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class SupplierRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<SupplierResponse> supplierRowMapper = (rs, rowNum) ->
            new SupplierResponse(
                    rs.getLong("id"),
                    rs.getString("supplier_code"),
                    rs.getString("supplier_name"),
                    rs.getString("contact_name"),
                    rs.getString("contact_phone"),
                    rs.getInt("lead_time_days"),
                    rs.getInt("status"),
                    rs.getString("remark"),
                    toLocalDateTime(rs.getTimestamp("created_at")),
                    toLocalDateTime(rs.getTimestamp("updated_at"))
            );

    public SupplierRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long countByKeyword(String keyword) {
        String sql = """
                SELECT COUNT(1)
                FROM wms_supplier
                WHERE (? = '' OR supplier_code LIKE ? OR supplier_name LIKE ?)
                """;
        String like = "%" + keyword + "%";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, keyword, like, like);
        return count == null ? 0L : count;
    }

    public List<SupplierResponse> pageByKeyword(String keyword, int offset, int size) {
        String sql = """
                SELECT id, supplier_code, supplier_name, contact_name, contact_phone, lead_time_days, status, remark, created_at, updated_at
                FROM wms_supplier
                WHERE (? = '' OR supplier_code LIKE ? OR supplier_name LIKE ?)
                ORDER BY id DESC
                LIMIT ? OFFSET ?
                """;
        String like = "%" + keyword + "%";
        return jdbcTemplate.query(sql, supplierRowMapper, keyword, like, like, size, offset);
    }

    public Optional<SupplierResponse> findById(Long id) {
        String sql = """
                SELECT id, supplier_code, supplier_name, contact_name, contact_phone, lead_time_days, status, remark, created_at, updated_at
                FROM wms_supplier
                WHERE id = ?
                """;
        return jdbcTemplate.query(sql, supplierRowMapper, id).stream().findFirst();
    }

    public boolean existsBySupplierCode(String supplierCode) {
        String sql = "SELECT COUNT(1) FROM wms_supplier WHERE supplier_code = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, supplierCode);
        return count != null && count > 0;
    }

    public int insert(SupplierCreateRequest request) {
        String sql = """
                INSERT INTO wms_supplier
                (supplier_code, supplier_name, contact_name, contact_phone, lead_time_days, status, remark)
                VALUES (?, ?, ?, ?, ?, 1, ?)
                """;
        return jdbcTemplate.update(sql,
                request.supplierCode(),
                request.supplierName(),
                request.contactName(),
                request.contactPhone(),
                request.leadTimeDays() == null ? 0 : request.leadTimeDays(),
                request.remark());
    }

    public int update(Long id, SupplierUpdateRequest request) {
        String sql = """
                UPDATE wms_supplier
                SET supplier_name = ?, contact_name = ?, contact_phone = ?, lead_time_days = ?, remark = ?
                WHERE id = ?
                """;
        return jdbcTemplate.update(sql,
                request.supplierName(),
                request.contactName(),
                request.contactPhone(),
                request.leadTimeDays() == null ? 0 : request.leadTimeDays(),
                request.remark(),
                id);
    }

    public int updateStatus(Long id, Integer status) {
        String sql = "UPDATE wms_supplier SET status = ? WHERE id = ?";
        return jdbcTemplate.update(sql, status, id);
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
