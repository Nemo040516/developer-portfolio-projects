/**
 * @file 速览索引
 * @summary 供应商业务服务，负责供应商主数据的分页、创建、更新与状态维护。
 * @core 1. 校验供应商编码唯一性
 * @core 2. 处理供应商新增与编辑
 * @core 3. 维护供应商启停状态
 * @core 4. 维护联系人、电话、交期等采购基础数据
 * @entry 先看：page、create、update、updateStatus
 * @deps 关键依赖：SupplierRepository
 * @risk 高风险修改点：联系方式校验、交期字段、状态口径、补货与采购下游依赖
 * @link 相关文件：后端/src/main/java/com/wms/backend/supplier/controller/SupplierController.java、前端/src/components/SupplierPanel.vue
 */
package com.wms.backend.supplier.service;

import com.wms.backend.common.PageResult;
import com.wms.backend.exception.BusinessException;
import com.wms.backend.supplier.dto.SupplierCreateRequest;
import com.wms.backend.supplier.dto.SupplierResponse;
import com.wms.backend.supplier.dto.SupplierUpdateRequest;
import com.wms.backend.supplier.repository.SupplierRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SupplierService {

    private final SupplierRepository supplierRepository;

    public SupplierService(SupplierRepository supplierRepository) {
        this.supplierRepository = supplierRepository;
    }

    public PageResult<SupplierResponse> page(String keyword, int pageNo, int pageSize) {
        String safeKeyword = keyword == null ? "" : keyword.trim();
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.max(1, Math.min(pageSize, 50));
        int offset = (safePageNo - 1) * safePageSize;
        long total = supplierRepository.countByKeyword(safeKeyword);
        List<SupplierResponse> records = supplierRepository.pageByKeyword(safeKeyword, offset, safePageSize);
        return new PageResult<>(total, records);
    }

    public SupplierResponse create(SupplierCreateRequest request) {
        if (supplierRepository.existsBySupplierCode(request.supplierCode())) {
            throw new BusinessException(4011, "供应商编码已存在");
        }
        int affected = supplierRepository.insert(request);
        if (affected != 1) {
            throw new BusinessException(5200, "供应商新增失败");
        }
        return supplierRepository.pageByKeyword(request.supplierCode(), 0, 1).stream().findFirst()
                .orElseThrow(() -> new BusinessException(5201, "供应商新增后读取失败"));
    }

    public SupplierResponse update(Long id, SupplierUpdateRequest request) {
        ensureExists(id);
        int affected = supplierRepository.update(id, request);
        if (affected != 1) {
            throw new BusinessException(5202, "供应商更新失败");
        }
        return supplierRepository.findById(id).orElseThrow(() -> new BusinessException(5203, "供应商更新后读取失败"));
    }

    public SupplierResponse updateStatus(Long id, Integer status) {
        ensureExists(id);
        int affected = supplierRepository.updateStatus(id, status);
        if (affected != 1) {
            throw new BusinessException(5204, "供应商状态更新失败");
        }
        return supplierRepository.findById(id).orElseThrow(() -> new BusinessException(5205, "供应商状态更新后读取失败"));
    }

    private void ensureExists(Long id) {
        if (supplierRepository.findById(id).isEmpty()) {
            throw new BusinessException(4043, "供应商不存在");
        }
    }
}
