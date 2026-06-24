/**
 * @file 速览索引
 * @summary 仓库业务服务，负责仓库分页、创建、更新、状态维护与仓库选项生成。
 * @core 1. 校验仓库编码唯一性
 * @core 2. 处理仓库新增与编辑
 * @core 3. 维护仓库启停状态
 * @core 4. 生成可用仓库选项供下游模块使用
 * @entry 先看：page、create、update、updateStatus、options
 * @deps 关键依赖：WarehouseRepository
 * @risk 高风险修改点：仓库状态口径、唯一性校验、选项是否只返回可用仓库
 * @link 相关文件：后端/src/main/java/com/wms/backend/warehouse/controller/WarehouseController.java、前端/src/components/WarehousePanel.vue
 */
package com.wms.backend.warehouse.service;

import com.wms.backend.common.PageResult;
import com.wms.backend.exception.BusinessException;
import com.wms.backend.warehouse.dto.WarehouseCreateRequest;
import com.wms.backend.warehouse.dto.WarehouseOptionResponse;
import com.wms.backend.warehouse.dto.WarehouseResponse;
import com.wms.backend.warehouse.dto.WarehouseUpdateRequest;
import com.wms.backend.warehouse.repository.WarehouseRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;

    public WarehouseService(WarehouseRepository warehouseRepository) {
        this.warehouseRepository = warehouseRepository;
    }

    public PageResult<WarehouseResponse> page(String keyword, int pageNo, int pageSize) {
        String safeKeyword = keyword == null ? "" : keyword.trim();
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.max(1, Math.min(pageSize, 50));
        int offset = (safePageNo - 1) * safePageSize;
        long total = warehouseRepository.countByKeyword(safeKeyword);
        List<WarehouseResponse> records = warehouseRepository.pageByKeyword(safeKeyword, offset, safePageSize);
        return new PageResult<>(total, records);
    }

    public WarehouseResponse create(WarehouseCreateRequest request) {
        if (warehouseRepository.existsByWarehouseCode(request.warehouseCode())) {
            throw new BusinessException(4006, "仓库编码已存在");
        }
        int affected = warehouseRepository.insert(request);
        if (affected != 1) {
            throw new BusinessException(5001, "仓库新增失败");
        }
        return warehouseRepository.pageByKeyword(request.warehouseCode(), 0, 1).stream().findFirst()
                .orElseThrow(() -> new BusinessException(5002, "仓库新增后读取失败"));
    }

    public WarehouseResponse update(Long id, WarehouseUpdateRequest request) {
        ensureExists(id);
        int affected = warehouseRepository.update(id, request);
        if (affected != 1) {
            throw new BusinessException(5003, "仓库更新失败");
        }
        return warehouseRepository.findById(id)
                .orElseThrow(() -> new BusinessException(5004, "仓库更新后读取失败"));
    }

    public WarehouseResponse updateStatus(Long id, Integer status) {
        ensureExists(id);
        int affected = warehouseRepository.updateStatus(id, status);
        if (affected != 1) {
            throw new BusinessException(5005, "仓库状态更新失败");
        }
        return warehouseRepository.findById(id)
                .orElseThrow(() -> new BusinessException(5006, "仓库状态更新后读取失败"));
    }

    public List<WarehouseOptionResponse> enabledOptions() {
        return warehouseRepository.listEnabledOptions();
    }

    private void ensureExists(Long id) {
        if (warehouseRepository.findById(id).isEmpty()) {
            throw new BusinessException(4040, "仓库不存在");
        }
    }
}
