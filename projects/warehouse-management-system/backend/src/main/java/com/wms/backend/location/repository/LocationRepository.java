/**
 * @file 速览索引
 * @summary 库位数据访问层，负责库位分页查询、详情读取、新增、编辑与状态更新。
 * @core 1. 提供库位按关键字与仓库条件分页查询
 * @core 2. 提供库位存在性与仓库存在性校验查询
 * @core 3. 提供库位新增、更新与状态更新写入
 * @entry 先看：pageByCondition、findById、insert、update、updateStatus
 * @deps 关键依赖：JdbcTemplate、LocationCreateRequest、LocationUpdateRequest、LocationResponse
 * @state 关键数据：locationRowMapper、wms_location 与 wms_warehouse 联表查询
 * @risk 高风险修改点：查询条件 SQL、编码唯一性校验、更新字段与 DTO 字段映射
 * @link 相关文件：后端/src/main/java/com/wms/backend/location/service/LocationService.java、后端/src/main/java/com/wms/backend/location/dto/LocationResponse.java
 */
package com.wms.backend.location.repository;

import com.wms.backend.location.dto.LocationCreateRequest;
import com.wms.backend.location.dto.LocationResponse;
import com.wms.backend.location.dto.LocationUpdateRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class LocationRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<LocationResponse> locationRowMapper = (rs, rowNum) ->
            new LocationResponse(
                    rs.getLong("id"),
                    rs.getLong("warehouse_id"),
                    rs.getString("warehouse_code"),
                    rs.getString("warehouse_name"),
                    rs.getString("location_code"),
                    rs.getString("area_name"),
                    rs.getString("location_type"),
                    rs.getBigDecimal("capacity"),
                    rs.getInt("status"),
                    rs.getString("remark"),
                    toLocalDateTime(rs.getTimestamp("created_at")),
                    toLocalDateTime(rs.getTimestamp("updated_at"))
            );

    public LocationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long countByCondition(String keyword, Long warehouseId) {
        String sql = """
                SELECT COUNT(1)
                FROM wms_location l
                JOIN wms_warehouse w ON l.warehouse_id = w.id
                WHERE (? = '' OR l.location_code LIKE ? OR l.area_name LIKE ?)
                  AND (? IS NULL OR l.warehouse_id = ?)
                """;
        String like = "%" + keyword + "%";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, keyword, like, like, warehouseId, warehouseId);
        return count == null ? 0L : count;
    }

    public List<LocationResponse> pageByCondition(String keyword, Long warehouseId, int offset, int size) {
        String sql = """
                SELECT l.id, l.warehouse_id, w.warehouse_code, w.warehouse_name, l.location_code, l.area_name, l.location_type,
                       l.capacity, l.status, l.remark, l.created_at, l.updated_at
                FROM wms_location l
                JOIN wms_warehouse w ON l.warehouse_id = w.id
                WHERE (? = '' OR l.location_code LIKE ? OR l.area_name LIKE ?)
                  AND (? IS NULL OR l.warehouse_id = ?)
                ORDER BY l.id DESC
                LIMIT ? OFFSET ?
                """;
        String like = "%" + keyword + "%";
        return jdbcTemplate.query(sql, locationRowMapper, keyword, like, like, warehouseId, warehouseId, size, offset);
    }

    public Optional<LocationResponse> findById(Long id) {
        String sql = """
                SELECT l.id, l.warehouse_id, w.warehouse_code, w.warehouse_name, l.location_code, l.area_name, l.location_type,
                       l.capacity, l.status, l.remark, l.created_at, l.updated_at
                FROM wms_location l
                JOIN wms_warehouse w ON l.warehouse_id = w.id
                WHERE l.id = ?
                """;
        return jdbcTemplate.query(sql, locationRowMapper, id).stream().findFirst();
    }

    public boolean existsByLocationCode(String locationCode) {
        String sql = "SELECT COUNT(1) FROM wms_location WHERE location_code = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, locationCode);
        return count != null && count > 0;
    }

    public boolean existsWarehouseById(Long warehouseId) {
        String sql = "SELECT COUNT(1) FROM wms_warehouse WHERE id = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, warehouseId);
        return count != null && count > 0;
    }

    public int insert(LocationCreateRequest request) {
        String sql = """
                INSERT INTO wms_location
                (warehouse_id, location_code, area_name, location_type, capacity, status, remark)
                VALUES (?, ?, ?, ?, ?, 1, ?)
                """;
        return jdbcTemplate.update(sql,
                request.warehouseId(),
                request.locationCode(),
                request.areaName(),
                request.locationType(),
                request.capacity(),
                request.remark());
    }

    public int update(Long id, LocationUpdateRequest request) {
        String sql = """
                UPDATE wms_location
                SET warehouse_id = ?, area_name = ?, location_type = ?, capacity = ?, remark = ?
                WHERE id = ?
                """;
        return jdbcTemplate.update(sql,
                request.warehouseId(),
                request.areaName(),
                request.locationType(),
                request.capacity(),
                request.remark(),
                id);
    }

    public int updateStatus(Long id, Integer status) {
        String sql = "UPDATE wms_location SET status = ? WHERE id = ?";
        return jdbcTemplate.update(sql, status, id);
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
