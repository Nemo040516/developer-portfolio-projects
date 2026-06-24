/**
 * @file 速览索引
 * @summary SKU 业务服务，负责商品主数据的分页、创建、更新与状态维护。
 * @core 1. 校验 SKU 编码唯一性
 * @core 2. 处理 SKU 新增与编辑
 * @core 3. 维护 SKU 启停状态
 * @core 4. 保护多模块共享主数据的一致性
 * @entry 先看：page、create、update、updateStatus
 * @deps 关键依赖：SkuRepository
 * @risk 高风险修改点：SKU 编码口径、状态口径、被库存/单据/补货多模块共用
 * @link 相关文件：后端/src/main/java/com/wms/backend/sku/controller/SkuController.java、前端/src/components/SkuPanel.vue
 */
package com.wms.backend.sku.service;

import com.wms.backend.common.PageResult;
import com.wms.backend.exception.BusinessException;
import com.wms.backend.sku.dto.SkuCreateRequest;
import com.wms.backend.sku.dto.SkuResponse;
import com.wms.backend.sku.dto.SkuUpdateRequest;
import com.wms.backend.sku.repository.SkuRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SkuService {

    private final SkuRepository skuRepository;

    public SkuService(SkuRepository skuRepository) {
        this.skuRepository = skuRepository;
    }

    public PageResult<SkuResponse> page(String keyword, int pageNo, int pageSize) {
        String safeKeyword = keyword == null ? "" : keyword.trim();
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.max(1, Math.min(pageSize, 50));
        int offset = (safePageNo - 1) * safePageSize;
        long total = skuRepository.countByKeyword(safeKeyword);
        List<SkuResponse> records = skuRepository.pageByKeyword(safeKeyword, offset, safePageSize);
        return new PageResult<>(total, records);
    }

    public SkuResponse create(SkuCreateRequest request) {
        if (skuRepository.existsBySkuCode(request.skuCode())) {
            throw new BusinessException(4010, "SKU编码已存在");
        }
        int affected = skuRepository.insert(request);
        if (affected != 1) {
            throw new BusinessException(5100, "SKU新增失败");
        }
        return skuRepository.pageByKeyword(request.skuCode(), 0, 1).stream().findFirst()
                .orElseThrow(() -> new BusinessException(5101, "SKU新增后读取失败"));
    }

    public SkuResponse update(Long id, SkuUpdateRequest request) {
        ensureExists(id);
        int affected = skuRepository.update(id, request);
        if (affected != 1) {
            throw new BusinessException(5102, "SKU更新失败");
        }
        return skuRepository.findById(id).orElseThrow(() -> new BusinessException(5103, "SKU更新后读取失败"));
    }

    public SkuResponse updateStatus(Long id, Integer status) {
        ensureExists(id);
        int affected = skuRepository.updateStatus(id, status);
        if (affected != 1) {
            throw new BusinessException(5104, "SKU状态更新失败");
        }
        return skuRepository.findById(id).orElseThrow(() -> new BusinessException(5105, "SKU状态更新后读取失败"));
    }

    private void ensureExists(Long id) {
        if (skuRepository.findById(id).isEmpty()) {
            throw new BusinessException(4042, "SKU不存在");
        }
    }
}
