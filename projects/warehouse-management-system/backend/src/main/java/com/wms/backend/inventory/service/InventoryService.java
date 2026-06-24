/**
 * @file 速览索引
 * @summary 库存业务服务，负责库存/库位分页查询、预警规则维护与预警筛选参数规范化。
 * @core 1. 统一库存与库位相关分页查询参数处理
 * @core 2. 处理预警规则新增/更新与业务校验
 * @core 3. 处理预警类型与预警级别参数规范化
 * @entry 先看：pageStocks、pageLocationStocks、pageAlerts、createAlertRule、updateAlertRule
 * @deps 关键依赖：InventoryRepository、BusinessException、InventoryAlertRuleSaveRequest
 * @state 关键规则：分页 pageNo/pageSize 安全化、alertType(LOW/HIGH)、alertLevel(CRITICAL/WARN/INFO)
 * @risk 高风险修改点：阈值关系校验(min<=safe<=max)、仓库SKU有效性校验、重复规则校验
 * @link 相关文件：后端/src/main/java/com/wms/backend/inventory/repository/InventoryRepository.java、后端/src/main/java/com/wms/backend/inventory/controller/InventoryController.java
 */
package com.wms.backend.inventory.service;

import com.wms.backend.common.PageResult;
import com.wms.backend.exception.BusinessException;
import com.wms.backend.inventory.dto.InventoryAlertResponse;
import com.wms.backend.inventory.dto.InventoryAlertRuleResponse;
import com.wms.backend.inventory.dto.InventoryAlertRuleSaveRequest;
import com.wms.backend.inventory.dto.InventoryStockResponse;
import com.wms.backend.inventory.dto.InventoryTxnResponse;
import com.wms.backend.inventory.dto.LocationStockResponse;
import com.wms.backend.inventory.dto.LocationTxnResponse;
import com.wms.backend.inventory.repository.InventoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    public InventoryService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    public PageResult<InventoryStockResponse> pageStocks(String keyword, int pageNo, int pageSize) {
        String safeKeyword = keyword == null ? "" : keyword.trim();
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.max(1, Math.min(pageSize, 50));
        int offset = (safePageNo - 1) * safePageSize;
        long total = inventoryRepository.countStockByKeyword(safeKeyword);
        List<InventoryStockResponse> records = inventoryRepository.pageStockByKeyword(safeKeyword, offset, safePageSize);
        return new PageResult<>(total, records);
    }

    public PageResult<InventoryTxnResponse> pageTxns(String keyword, int pageNo, int pageSize) {
        String safeKeyword = keyword == null ? "" : keyword.trim();
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.max(1, Math.min(pageSize, 50));
        int offset = (safePageNo - 1) * safePageSize;
        long total = inventoryRepository.countTxnByKeyword(safeKeyword);
        List<InventoryTxnResponse> records = inventoryRepository.pageTxnByKeyword(safeKeyword, offset, safePageSize);
        return new PageResult<>(total, records);
    }

    public PageResult<LocationStockResponse> pageLocationStocks(String keyword, int pageNo, int pageSize) {
        String safeKeyword = keyword == null ? "" : keyword.trim();
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.max(1, Math.min(pageSize, 50));
        int offset = (safePageNo - 1) * safePageSize;
        long total = inventoryRepository.countLocationStockByKeyword(safeKeyword);
        List<LocationStockResponse> records = inventoryRepository.pageLocationStockByKeyword(safeKeyword, offset, safePageSize);
        return new PageResult<>(total, records);
    }

    public PageResult<LocationTxnResponse> pageLocationTxns(String keyword, int pageNo, int pageSize) {
        String safeKeyword = keyword == null ? "" : keyword.trim();
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.max(1, Math.min(pageSize, 50));
        int offset = (safePageNo - 1) * safePageSize;
        long total = inventoryRepository.countLocationTxnByKeyword(safeKeyword);
        List<LocationTxnResponse> records = inventoryRepository.pageLocationTxnByKeyword(safeKeyword, offset, safePageSize);
        return new PageResult<>(total, records);
    }

    public PageResult<InventoryAlertRuleResponse> pageAlertRules(String keyword, int pageNo, int pageSize) {
        String safeKeyword = keyword == null ? "" : keyword.trim();
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.max(1, Math.min(pageSize, 50));
        int offset = (safePageNo - 1) * safePageSize;
        long total = inventoryRepository.countAlertRuleByKeyword(safeKeyword);
        List<InventoryAlertRuleResponse> records = inventoryRepository.pageAlertRuleByKeyword(safeKeyword, offset, safePageSize);
        return new PageResult<>(total, records);
    }

    public InventoryAlertRuleResponse createAlertRule(InventoryAlertRuleSaveRequest request, String username) {
        validateAlertRuleRequest(request, null);
        Long createdBy = inventoryRepository.findUserIdByUsername(username).orElse(null);
        int affected = inventoryRepository.insertAlertRule(
                request.warehouseId(),
                request.skuId(),
                request.minQty(),
                request.safeQty(),
                request.maxQty(),
                request.status(),
                request.remark(),
                createdBy
        );
        if (affected != 1) {
            throw new BusinessException(4501, "预警规则新增失败");
        }
        Long id = inventoryRepository.findAlertRuleIdByWarehouseSku(request.warehouseId(), request.skuId())
                .orElseThrow(() -> new BusinessException(4502, "预警规则新增后读取失败"));
        return inventoryRepository.findAlertRuleById(id)
                .orElseThrow(() -> new BusinessException(4503, "预警规则不存在"));
    }

    public InventoryAlertRuleResponse updateAlertRule(Long id, InventoryAlertRuleSaveRequest request) {
        InventoryAlertRuleResponse existing = inventoryRepository.findAlertRuleById(id)
                .orElseThrow(() -> new BusinessException(4503, "预警规则不存在"));
        validateAlertRuleRequest(request, id);
        int affected = inventoryRepository.updateAlertRule(
                existing.id(),
                request.warehouseId(),
                request.skuId(),
                request.minQty(),
                request.safeQty(),
                request.maxQty(),
                request.status(),
                request.remark()
        );
        if (affected != 1) {
            throw new BusinessException(4504, "预警规则更新失败");
        }
        return inventoryRepository.findAlertRuleById(id)
                .orElseThrow(() -> new BusinessException(4503, "预警规则不存在"));
    }

    public PageResult<InventoryAlertResponse> pageAlerts(String keyword, String alertType, String alertLevel, int pageNo, int pageSize) {
        String safeKeyword = keyword == null ? "" : keyword.trim();
        String safeAlertType = normalizeAlertType(alertType);
        String safeAlertLevel = normalizeAlertLevel(alertLevel);
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.max(1, Math.min(pageSize, 50));
        int offset = (safePageNo - 1) * safePageSize;
        long total = inventoryRepository.countAlertsByKeywordTypeAndLevel(safeKeyword, safeAlertType, safeAlertLevel);
        List<InventoryAlertResponse> records =
                inventoryRepository.pageAlertsByKeywordTypeAndLevel(safeKeyword, safeAlertType, safeAlertLevel, offset, safePageSize);
        return new PageResult<>(total, records);
    }

    private String normalizeAlertType(String alertType) {
        if (alertType == null || alertType.isBlank()) {
            return "";
        }
        String upper = alertType.trim().toUpperCase();
        if (!"LOW".equals(upper) && !"HIGH".equals(upper)) {
            throw new BusinessException(4001, "预警类型仅支持 LOW/HIGH");
        }
        return upper;
    }

    private String normalizeAlertLevel(String alertLevel) {
        if (alertLevel == null || alertLevel.isBlank()) {
            return "";
        }
        String upper = alertLevel.trim().toUpperCase();
        if (!"CRITICAL".equals(upper) && !"WARN".equals(upper) && !"INFO".equals(upper)) {
            throw new BusinessException(4001, "预警级别仅支持 CRITICAL/WARN/INFO");
        }
        return upper;
    }

    private void validateAlertRuleRequest(InventoryAlertRuleSaveRequest request, Long currentId) {
        if (request.status() != 0 && request.status() != 1) {
            throw new BusinessException(4001, "状态仅支持 0/1");
        }
        if (request.minQty() > request.safeQty() || request.safeQty() > request.maxQty()) {
            throw new BusinessException(4505, "阈值关系不合法，需满足 预警下限<=安全库存<=预警上限");
        }
        if (!inventoryRepository.existsWarehouseActive(request.warehouseId())) {
            throw new BusinessException(4506, "仓库不存在或已停用");
        }
        if (!inventoryRepository.existsSkuActive(request.skuId())) {
            throw new BusinessException(4507, "SKU不存在或已停用");
        }
        Long duplicatedId = inventoryRepository.findAlertRuleIdByWarehouseSku(request.warehouseId(), request.skuId()).orElse(null);
        if (duplicatedId != null && (currentId == null || !duplicatedId.equals(currentId))) {
            throw new BusinessException(4508, "同一仓库+SKU仅允许一条预警规则");
        }
    }
}
