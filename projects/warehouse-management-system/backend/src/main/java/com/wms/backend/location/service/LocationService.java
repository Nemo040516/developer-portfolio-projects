/**
 * @file 速览索引
 * @summary 库位业务服务，负责库位创建、更新、状态维护与仓库关联校验。
 * @core 1. 校验库位编码唯一性
 * @core 2. 校验仓库存在性与库位归属
 * @core 3. 处理库位新增与编辑
 * @core 4. 维护库位状态
 * @entry 先看：page、create、update、updateStatus
 * @deps 关键依赖：LocationRepository、WarehouseRepository
 * @risk 高风险修改点：仓库-库位关系、状态口径、容量与类型字段
 * @link 相关文件：后端/src/main/java/com/wms/backend/location/controller/LocationController.java、前端/src/components/LocationPanel.vue
 */
package com.wms.backend.location.service;

import com.wms.backend.common.PageResult;
import com.wms.backend.exception.BusinessException;
import com.wms.backend.location.dto.LocationCreateRequest;
import com.wms.backend.location.dto.LocationResponse;
import com.wms.backend.location.dto.LocationUpdateRequest;
import com.wms.backend.location.repository.LocationRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LocationService {

    private final LocationRepository locationRepository;

    public LocationService(LocationRepository locationRepository) {
        this.locationRepository = locationRepository;
    }

    public PageResult<LocationResponse> page(String keyword, Long warehouseId, int pageNo, int pageSize) {
        String safeKeyword = keyword == null ? "" : keyword.trim();
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.max(1, Math.min(pageSize, 50));
        int offset = (safePageNo - 1) * safePageSize;
        long total = locationRepository.countByCondition(safeKeyword, warehouseId);
        List<LocationResponse> records = locationRepository.pageByCondition(safeKeyword, warehouseId, offset, safePageSize);
        return new PageResult<>(total, records);
    }

    public LocationResponse create(LocationCreateRequest request) {
        if (!locationRepository.existsWarehouseById(request.warehouseId())) {
            throw new BusinessException(4007, "所属仓库不存在");
        }
        if (locationRepository.existsByLocationCode(request.locationCode())) {
            throw new BusinessException(4008, "库位编码已存在");
        }
        int affected = locationRepository.insert(request);
        if (affected != 1) {
            throw new BusinessException(5007, "库位新增失败");
        }
        return locationRepository.pageByCondition(request.locationCode(), null, 0, 1).stream().findFirst()
                .orElseThrow(() -> new BusinessException(5008, "库位新增后读取失败"));
    }

    public LocationResponse update(Long id, LocationUpdateRequest request) {
        ensureExists(id);
        if (!locationRepository.existsWarehouseById(request.warehouseId())) {
            throw new BusinessException(4007, "所属仓库不存在");
        }
        int affected = locationRepository.update(id, request);
        if (affected != 1) {
            throw new BusinessException(5009, "库位更新失败");
        }
        return locationRepository.findById(id)
                .orElseThrow(() -> new BusinessException(5010, "库位更新后读取失败"));
    }

    public LocationResponse updateStatus(Long id, Integer status) {
        ensureExists(id);
        int affected = locationRepository.updateStatus(id, status);
        if (affected != 1) {
            throw new BusinessException(5011, "库位状态更新失败");
        }
        return locationRepository.findById(id)
                .orElseThrow(() -> new BusinessException(5012, "库位状态更新后读取失败"));
    }

    private void ensureExists(Long id) {
        if (locationRepository.findById(id).isEmpty()) {
            throw new BusinessException(4041, "库位不存在");
        }
    }
}
