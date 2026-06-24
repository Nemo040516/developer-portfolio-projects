/**
 * @file 速览索引
 * @summary 盘点单业务服务，负责盘点单创建、编辑、提交、确认以及账实差异回写库存。
 * @core 1. 处理盘点单新增与编辑
 * @core 2. 校验提交与确认状态流转
 * @core 3. 提供账面库存查询能力
 * @core 4. 在确认盘点后回写库存修正
 * @entry 先看：create、update、submit、confirm、bookStocks
 * @deps 关键依赖：StocktakeRepository、库存相关仓储
 * @risk 高风险修改点：差异数量口径、确认后库存修正、状态流转
 * @link 相关文件：后端/src/main/java/com/wms/backend/stocktake/controller/StocktakeController.java、前端/src/components/StocktakePanel.vue
 */
package com.wms.backend.stocktake.service;

import com.wms.backend.common.PageResult;
import com.wms.backend.exception.BusinessException;
import com.wms.backend.stocktake.dto.StocktakeBookStockResponse;
import com.wms.backend.stocktake.dto.StocktakeCreateRequest;
import com.wms.backend.stocktake.dto.StocktakeDetailResponse;
import com.wms.backend.stocktake.dto.StocktakeItemRequest;
import com.wms.backend.stocktake.dto.StocktakeItemResponse;
import com.wms.backend.stocktake.dto.StocktakeOrderResponse;
import com.wms.backend.stocktake.dto.StocktakeUpdateRequest;
import com.wms.backend.stocktake.repository.StocktakeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class StocktakeService {

    private static final int STATUS_DRAFT = 0;
    private static final int STATUS_SUBMITTED = 1;
    private static final int STATUS_DONE = 2;
    private static final String STOCKTAKE_NO_PREFIX = "st";
    private static final DateTimeFormatter STOCKTAKE_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final StocktakeRepository stocktakeRepository;

    public StocktakeService(StocktakeRepository stocktakeRepository) {
        this.stocktakeRepository = stocktakeRepository;
    }

    public PageResult<StocktakeOrderResponse> page(String keyword, Integer status, int pageNo, int pageSize) {
        String safeKeyword = keyword == null ? "" : keyword.trim();
        Integer safeStatus = (status == null || status < 0 || status > 2) ? null : status;
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.max(1, Math.min(pageSize, 50));
        int offset = (safePageNo - 1) * safePageSize;
        long total = stocktakeRepository.countByKeyword(safeKeyword, safeStatus);
        List<StocktakeOrderResponse> records = stocktakeRepository.pageByKeyword(safeKeyword, safeStatus, offset, safePageSize);
        return new PageResult<>(total, records);
    }

    public StocktakeDetailResponse detail(Long id) {
        StocktakeOrderResponse order = stocktakeRepository.findOrderById(id)
                .orElseThrow(() -> new BusinessException(4401, "盘点单不存在"));
        List<StocktakeItemResponse> items = stocktakeRepository.listItemsByOrderId(id);
        return new StocktakeDetailResponse(
                order.id(),
                order.stocktakeNo(),
                order.warehouseId(),
                order.warehouseName(),
                order.scopeType(),
                order.status(),
                order.remark(),
                order.createdBy(),
                order.createdAt(),
                order.updatedAt(),
                items
        );
    }

    public List<StocktakeBookStockResponse> bookStocks(Long warehouseId, Long skuId, String keyword) {
        if (warehouseId == null) {
            throw new BusinessException(4001, "仓库不能为空");
        }
        if (!stocktakeRepository.existsWarehouseActive(warehouseId)) {
            throw new BusinessException(4411, "仓库不存在或已停用");
        }
        if (skuId != null && !stocktakeRepository.existsSkuActive(skuId)) {
            throw new BusinessException(4412, "SKU不存在或已停用");
        }
        return stocktakeRepository.listBookStocks(warehouseId, skuId, keyword, 200);
    }

    @Transactional
    public StocktakeDetailResponse create(StocktakeCreateRequest request, String username) {
        validateMasterData(request.warehouseId(), request.items());
        String scopeType = resolveScopeType(request.scopeType());
        String stocktakeNo = generateStocktakeNo();
        Long createdBy = stocktakeRepository.findUserIdByUsername(username).orElse(null);
        int affected = stocktakeRepository.insertOrder(stocktakeNo, request.warehouseId(), scopeType, request.remark(), createdBy);
        if (affected != 1) {
            throw new BusinessException(4402, "盘点单新增失败");
        }
        Long orderId = stocktakeRepository.findOrderIdByStocktakeNo(stocktakeNo)
                .orElseThrow(() -> new BusinessException(4403, "盘点单新增后读取失败"));
        insertItems(orderId, request.warehouseId(), request.items());
        return detail(orderId);
    }

    @Transactional
    public StocktakeDetailResponse update(Long id, StocktakeUpdateRequest request) {
        StocktakeOrderResponse order = stocktakeRepository.findOrderById(id)
                .orElseThrow(() -> new BusinessException(4401, "盘点单不存在"));
        if (order.status() != STATUS_DRAFT) {
            throw new BusinessException(4404, "仅草稿状态可编辑");
        }
        validateMasterData(request.warehouseId(), request.items());
        String scopeType = resolveScopeType(request.scopeType());
        int affected = stocktakeRepository.updateOrder(id, request.warehouseId(), scopeType, request.remark());
        if (affected != 1) {
            throw new BusinessException(4405, "盘点单更新失败");
        }
        stocktakeRepository.deleteItemsByOrderId(id);
        insertItems(id, request.warehouseId(), request.items());
        return detail(id);
    }

    @Transactional
    public void delete(Long id) {
        // 盘点删除仅允许草稿态，避免丢失已提交/已完成的库存修正依据。
        StocktakeOrderResponse order = stocktakeRepository.findOrderById(id)
                .orElseThrow(() -> new BusinessException(4401, "盘点单不存在"));
        if (order.status() != STATUS_DRAFT) {
            throw new BusinessException(4418, "仅草稿状态可删除");
        }
        // 先清理明细，再删除主单据，保证外键约束安全。
        stocktakeRepository.deleteItemsByOrderId(id);
        int affected = stocktakeRepository.deleteOrderById(id);
        if (affected != 1) {
            throw new BusinessException(4419, "盘点单删除失败");
        }
    }

    @Transactional
    public StocktakeDetailResponse submit(Long id) {
        StocktakeOrderResponse order = stocktakeRepository.findOrderById(id)
                .orElseThrow(() -> new BusinessException(4401, "盘点单不存在"));
        if (order.status() != STATUS_DRAFT) {
            throw new BusinessException(4406, "仅草稿状态可提交");
        }
        List<StocktakeItemResponse> items = stocktakeRepository.listItemsByOrderId(id);
        if (items.isEmpty()) {
            throw new BusinessException(4407, "盘点明细不能为空");
        }
        int affected = stocktakeRepository.updateStatus(id, STATUS_SUBMITTED);
        if (affected != 1) {
            throw new BusinessException(4408, "盘点单提交失败");
        }
        return detail(id);
    }

    @Transactional
    public StocktakeDetailResponse confirm(Long id, String username) {
        StocktakeOrderResponse order = stocktakeRepository.findOrderById(id)
                .orElseThrow(() -> new BusinessException(4401, "盘点单不存在"));
        if (order.status() != STATUS_SUBMITTED) {
            throw new BusinessException(4409, "仅已提交状态可确认盘点");
        }
        List<StocktakeItemResponse> items = stocktakeRepository.listItemsByOrderId(id);
        if (items.isEmpty()) {
            throw new BusinessException(4407, "盘点明细不能为空");
        }

        Map<Long, Integer> skuDelta = new HashMap<>();
        List<LocationAdjust> adjustments = new ArrayList<>();
        for (StocktakeItemResponse item : items) {
            int before = stocktakeRepository.lockLocationStockQty(order.warehouseId(), item.locationId(), item.skuId()).orElse(0);
            int countQty = item.countQty() == null ? 0 : item.countQty();
            if (countQty < 0) {
                throw new BusinessException(4416, "实盘数量不能小于0");
            }
            int delta = countQty - before;
            adjustments.add(new LocationAdjust(item.id(), item.skuId(), item.locationId(), before, countQty, delta));
            skuDelta.merge(item.skuId(), delta, Integer::sum);
        }

        Long operatorId = stocktakeRepository.findUserIdByUsername(username).orElse(null);
        for (LocationAdjust adjust : adjustments) {
            stocktakeRepository.upsertLocationStock(order.warehouseId(), adjust.locationId(), adjust.skuId(), adjust.afterQty());
            stocktakeRepository.updateItemSnapshot(adjust.itemId(), adjust.beforeQty(), adjust.afterQty(), adjust.deltaQty());
            stocktakeRepository.insertLocationTxn(
                    "STOCKTAKE_ADJUST",
                    order.stocktakeNo(),
                    order.id(),
                    order.warehouseId(),
                    adjust.locationId(),
                    adjust.skuId(),
                    adjust.deltaQty(),
                    adjust.beforeQty(),
                    adjust.afterQty(),
                    operatorId,
                    username,
                    "盘点确认自动生成"
            );
        }

        for (Map.Entry<Long, Integer> entry : skuDelta.entrySet()) {
            Long skuId = entry.getKey();
            int delta = entry.getValue();
            int before = stocktakeRepository.lockInventoryStockQty(order.warehouseId(), skuId).orElse(0);
            int after = before + delta;
            if (after < 0) {
                throw new BusinessException(4417, "盘点后仓库库存不能为负，SKU=" + skuId);
            }
            stocktakeRepository.upsertInventoryStock(order.warehouseId(), skuId, after);
            stocktakeRepository.insertInventoryTxn(
                    "STOCKTAKE_ADJUST",
                    order.stocktakeNo(),
                    order.id(),
                    order.warehouseId(),
                    skuId,
                    delta,
                    before,
                    after,
                    operatorId,
                    username,
                    "盘点确认自动生成"
            );
        }

        int affected = stocktakeRepository.updateStatus(id, STATUS_DONE);
        if (affected != 1) {
            throw new BusinessException(4410, "盘点单确认失败");
        }
        return detail(id);
    }

    private void insertItems(Long orderId, Long warehouseId, List<StocktakeItemRequest> items) {
        for (StocktakeItemRequest item : items) {
            int bookQty = stocktakeRepository.findLocationStockQty(warehouseId, item.locationId(), item.skuId()).orElse(0);
            stocktakeRepository.insertItem(orderId, item, bookQty);
        }
    }

    private void validateMasterData(Long warehouseId, List<StocktakeItemRequest> items) {
        if (!stocktakeRepository.existsWarehouseActive(warehouseId)) {
            throw new BusinessException(4411, "仓库不存在或已停用");
        }
        for (StocktakeItemRequest item : items) {
            if (!stocktakeRepository.existsSkuActive(item.skuId())) {
                throw new BusinessException(4412, "SKU不存在或已停用");
            }
            if (!stocktakeRepository.existsLocationActive(item.locationId())) {
                throw new BusinessException(4413, "库位不存在或已停用");
            }
            if (!stocktakeRepository.existsLocationInWarehouse(item.locationId(), warehouseId)) {
                throw new BusinessException(4414, "库位不属于当前仓库");
            }
        }
    }

    private String resolveScopeType(String scopeType) {
        if (scopeType == null || scopeType.isBlank()) {
            return "BY_WAREHOUSE";
        }
        return scopeType.trim().toUpperCase();
    }

    private String generateStocktakeNo() {
        String datePart = LocalDate.now().format(STOCKTAKE_DATE_FORMATTER);
        String prefix = STOCKTAKE_NO_PREFIX + datePart;
        String currentMax = stocktakeRepository.findMaxStocktakeNoByPrefix(prefix).orElse(null);
        int nextSeq = 1;
        if (currentMax != null && currentMax.length() >= prefix.length() + 4) {
            String suffix = currentMax.substring(currentMax.length() - 4);
            if (suffix.chars().allMatch(Character::isDigit)) {
                nextSeq = Integer.parseInt(suffix) + 1;
            }
        }
        return prefix + String.format("%04d", nextSeq);
    }

    private record LocationAdjust(Long itemId, Long skuId, Long locationId, int beforeQty, int afterQty, int deltaQty) {
    }
}
