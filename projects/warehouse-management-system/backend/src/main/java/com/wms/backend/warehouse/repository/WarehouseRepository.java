/**
 * @file 速览索引
 * @summary 仓库数据访问层，负责仓库分页查询、主键读取、编码唯一校验与增改状态写入。
 * @core 1. 提供按关键字分页查询仓库
 * @core 2. 提供按主键读取与编码唯一性校验
 * @core 3. 提供新增、更新、状态更新写入
 * @core 4. 提供启用仓库下拉选项查询
 * @entry 先看：pageByKeyword、findById、existsByWarehouseCode、insert、update、updateStatus、listEnabledOptions
 * @deps 关键依赖：JdbcTemplate、WarehouseCreateRequest、WarehouseUpdateRequest、WarehouseResponse、WarehouseOptionResponse
 * @state 关键数据：warehouseRowMapper、wms_warehouse(status=1 视为可用)
 * @risk 高风险修改点：字段别名映射、状态过滤口径、更新 SQL 字段集合
 * @link 相关文件：后端/src/main/java/com/wms/backend/warehouse/service/WarehouseService.java、后端/src/main/java/com/wms/backend/warehouse/controller/WarehouseController.java
 */
package com.wms.backend.warehouse.repository;

import com.wms.backend.warehouse.dto.WarehouseCreateRequest;
import com.wms.backend.warehouse.dto.WarehouseOptionResponse;
import com.wms.backend.warehouse.dto.WarehouseResponse;
import com.wms.backend.warehouse.dto.WarehouseUpdateRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class WarehouseRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<WarehouseResponse> warehouseRowMapper = (rs, rowNum) ->
            new WarehouseResponse(
                    rs.getLong("id"),
                    rs.getString("warehouse_code"),
                    rs.getString("warehouse_name"),
                    rs.getString("address"),
                    rs.getString("manager_name"),
                    rs.getString("contact_phone"),
                    rs.getInt("status"),
                    rs.getString("remark"),
                    toLocalDateTime(rs.getTimestamp("created_at")),
                    toLocalDateTime(rs.getTimestamp("updated_at"))
            );

    public WarehouseRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long countByKeyword(String keyword) {
        String sql = """
                SELECT COUNT(1)
                FROM wms_warehouse
                WHERE (? = '' OR warehouse_code LIKE ? OR warehouse_name LIKE ?)
                """;
        String like = "%" + keyword + "%";
        Long total = jdbcTemplate.queryForObject(sql, Long.class, keyword, like, like);
        return total == null ? 0L : total;
    }

    public List<WarehouseResponse> pageByKeyword(String keyword, int offset, int size) {
        String sql = """
                SELECT id, warehouse_code, warehouse_name, address, manager_name, contact_phone, status, remark, created_at, updated_at
                FROM wms_warehouse
                WHERE (? = '' OR warehouse_code LIKE ? OR warehouse_name LIKE ?)
                ORDER BY id DESC
                LIMIT ? OFFSET ?
                """;
        String like = "%" + keyword + "%";
        return jdbcTemplate.query(sql, warehouseRowMapper, keyword, like, like, size, offset);
    }

    public Optional<WarehouseResponse> findById(Long id) {
        String sql = """
                SELECT id, warehouse_code, warehouse_name, address, manager_name, contact_phone, status, remark, created_at, updated_at
                FROM wms_warehouse
                WHERE id = ?
                """;
        List<WarehouseResponse> list = jdbcTemplate.query(sql, warehouseRowMapper, id);
        return list.stream().findFirst();
    }

    public boolean existsByWarehouseCode(String warehouseCode) {
        String sql = "SELECT COUNT(1) FROM wms_warehouse WHERE warehouse_code = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, warehouseCode);
        return count != null && count > 0;
    }

    public int insert(WarehouseCreateRequest request) {
        String sql = """
                INSERT INTO wms_warehouse
                (warehouse_code, warehouse_name, address, manager_name, contact_phone, status, remark)
                VALUES (?, ?, ?, ?, ?, 1, ?)
                """;
        return jdbcTemplate.update(sql,
                request.warehouseCode(),
                request.warehouseName(),
                request.address(),
                request.managerName(),
                request.contactPhone(),
                request.remark());
    }

    public int update(Long id, WarehouseUpdateRequest request) {
        String sql = """
                UPDATE wms_warehouse
                SET warehouse_name = ?, address = ?, manager_name = ?, contact_phone = ?, remark = ?
                WHERE id = ?
                """;
        return jdbcTemplate.update(sql,
                request.warehouseName(),
                request.address(),
                request.managerName(),
                request.contactPhone(),
                request.remark(),
                id);
    }

    public int updateStatus(Long id, Integer status) {
        String sql = "UPDATE wms_warehouse SET status = ? WHERE id = ?";
        return jdbcTemplate.update(sql, status, id);
    }

    public List<WarehouseOptionResponse> listEnabledOptions() {
        String sql = """
                SELECT id, warehouse_code, warehouse_name
                FROM wms_warehouse
                WHERE status = 1
                ORDER BY id DESC
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new WarehouseOptionResponse(
                rs.getLong("id"),
                rs.getString("warehouse_code"),
                rs.getString("warehouse_name")
        ));
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
