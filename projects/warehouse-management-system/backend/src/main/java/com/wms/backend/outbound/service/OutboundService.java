/**
 * @file 速览索引
 * @summary 出库单业务服务，负责出库单创建、编辑、提交、确认以及库存扣减逻辑。
 * @core 1. 处理出库单新增与编辑
 * @core 2. 校验提交与确认状态流转
 * @core 3. 提供可用库存查询能力
 * @core 4. 在确认出库后扣减库存并写入流水
 * @entry 先看：create、update、submit、confirm、availableStocks
 * @deps 关键依赖：OutboundRepository、库存相关仓储、仓库/SKU 数据
 * @risk 高风险修改点：可用库存口径、确认扣减事务、状态流转
 * @link 相关文件：后端/src/main/java/com/wms/backend/outbound/controller/OutboundController.java、前端/src/components/OutboundPanel.vue
 */
package com.wms.backend.outbound.service;

import com.wms.backend.common.PageResult;
import com.wms.backend.exception.BusinessException;
import com.wms.backend.outbound.dto.OutboundCreateRequest;
import com.wms.backend.outbound.dto.OutboundDetailResponse;
import com.wms.backend.outbound.dto.OutboundAvailableStockResponse;
import com.wms.backend.outbound.dto.OutboundItemRequest;
import com.wms.backend.outbound.dto.OutboundItemResponse;
import com.wms.backend.outbound.dto.OutboundOrderResponse;
import com.wms.backend.outbound.dto.OutboundUpdateRequest;
import com.wms.backend.outbound.repository.OutboundRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OutboundService {

    private static final int STATUS_DRAFT = 0;
    private static final int STATUS_SUBMITTED = 1;
    private static final int STATUS_DONE = 2;
    private static final String OUTBOUND_NO_PREFIX = "out";
    private static final DateTimeFormatter OUTBOUND_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final OutboundRepository outboundRepository;

    public OutboundService(OutboundRepository outboundRepository) {
        this.outboundRepository = outboundRepository;
    }

    public PageResult<OutboundOrderResponse> page(String keyword, Integer status, int pageNo, int pageSize) {
        String safeKeyword = keyword == null ? "" : keyword.trim();
        Integer safeStatus = (status == null || status < 0 || status > 2) ? null : status;
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.max(1, Math.min(pageSize, 50));
        int offset = (safePageNo - 1) * safePageSize;
        long total = outboundRepository.countByKeyword(safeKeyword, safeStatus);
        List<OutboundOrderResponse> records = outboundRepository.pageByKeyword(safeKeyword, safeStatus, offset, safePageSize);
        return new PageResult<>(total, records);
    }

    public OutboundDetailResponse detail(Long id) {
        OutboundOrderResponse order = outboundRepository.findOrderById(id)
                .orElseThrow(() -> new BusinessException(4301, "出库单不存在"));
        List<OutboundItemResponse> items = outboundRepository.listItemsByOrderId(id);
        return new OutboundDetailResponse(
                order.id(),
                order.outboundNo(),
                order.outboundType(),
                order.targetName(),
                order.warehouseId(),
                order.warehouseName(),
                order.status(),
                order.remark(),
                order.createdBy(),
                order.createdAt(),
                order.updatedAt(),
                items
        );
    }

    public List<OutboundAvailableStockResponse> availableStocks(Long warehouseId, Long skuId, String keyword) {
        if (warehouseId == null) {
            throw new BusinessException(4001, "仓库不能为空");
        }
        if (skuId == null) {
            throw new BusinessException(4001, "SKU不能为空");
        }
        if (!outboundRepository.existsWarehouseActive(warehouseId)) {
            throw new BusinessException(4311, "仓库不存在或已停用");
        }
        if (!outboundRepository.existsSkuActive(skuId)) {
            throw new BusinessException(4312, "SKU不存在或已停用");
        }
        return outboundRepository.listAvailableLocationStocks(warehouseId, skuId, keyword, 200);
    }

    @Transactional
    public OutboundDetailResponse create(OutboundCreateRequest request, String username) {
        String safeOutboundType = resolveOutboundType(request.outboundType());
        validateMasterData(request.warehouseId(), request.items());
        String outboundNo = generateOutboundNo();
        Long createdBy = outboundRepository.findUserIdByUsername(username).orElse(null);
        int affected = outboundRepository.insertOrder(
                outboundNo, safeOutboundType, request.targetName(), request.warehouseId(), request.remark(), createdBy
        );
        if (affected != 1) {
            throw new BusinessException(4302, "出库单新增失败");
        }
        Long orderId = outboundRepository.findOrderIdByOutboundNo(outboundNo)
                .orElseThrow(() -> new BusinessException(4303, "出库单新增后读取失败"));
        for (OutboundItemRequest item : request.items()) {
            outboundRepository.insertItem(orderId, item);
        }
        return detail(orderId);
    }

    @Transactional
    public OutboundDetailResponse update(Long id, OutboundUpdateRequest request) {
        OutboundOrderResponse order = outboundRepository.findOrderById(id)
                .orElseThrow(() -> new BusinessException(4301, "出库单不存在"));
        if (order.status() != STATUS_DRAFT) {
            throw new BusinessException(4304, "仅草稿状态可编辑");
        }
        String safeOutboundType = resolveOutboundType(request.outboundType());
        validateMasterData(request.warehouseId(), request.items());
        int affected = outboundRepository.updateOrder(
                id, safeOutboundType, request.targetName(), request.warehouseId(), request.remark()
        );
        if (affected != 1) {
            throw new BusinessException(4305, "出库单更新失败");
        }
        outboundRepository.deleteItemsByOrderId(id);
        for (OutboundItemRequest item : request.items()) {
            outboundRepository.insertItem(id, item);
        }
        return detail(id);
    }

    @Transactional
    public void delete(Long id) {
        // 出库单删除仅放开草稿态，确保已提交/已完成单据仍可被审计回溯。
        OutboundOrderResponse order = outboundRepository.findOrderById(id)
                .orElseThrow(() -> new BusinessException(4301, "出库单不存在"));
        if (order.status() != STATUS_DRAFT) {
            throw new BusinessException(4319, "仅草稿状态可删除");
        }
        // 明细先删除，主表后删除，避免触发外键约束错误。
        outboundRepository.deleteItemsByOrderId(id);
        int affected = outboundRepository.deleteOrderById(id);
        if (affected != 1) {
            throw new BusinessException(4320, "出库单删除失败");
        }
    }

    @Transactional
    public OutboundDetailResponse submit(Long id, String username) {
        OutboundOrderResponse order = outboundRepository.findOrderById(id)
                .orElseThrow(() -> new BusinessException(4301, "出库单不存在"));
        if (order.status() != STATUS_DRAFT) {
            throw new BusinessException(4306, "仅草稿状态可提交");
        }
        List<OutboundItemResponse> items = outboundRepository.listItemsByOrderId(id);
        if (items.isEmpty()) {
            throw new BusinessException(4307, "出库明细不能为空");
        }
        Long operatorId = outboundRepository.findUserIdByUsername(username).orElse(null);
        for (OutboundItemResponse item : items) {
            int beforeQty = outboundRepository.findLocationStockQty(order.warehouseId(), item.locationId(), item.skuId()).orElse(0);
            outboundRepository.insertLocationTxn(
                    "OUTBOUND_SUBMIT",
                    order.outboundNo(),
                    order.id(),
                    order.warehouseId(),
                    item.locationId(),
                    item.skuId(),
                    0,
                    beforeQty,
                    beforeQty,
                    operatorId,
                    username,
                    "出库单已提交，待确认出库"
            );
        }
        int affected = outboundRepository.updateStatus(id, STATUS_SUBMITTED);
        if (affected != 1) {
            throw new BusinessException(4308, "出库单提交失败");
        }
        return detail(id);
    }

    @Transactional
    public OutboundDetailResponse confirm(Long id, String username) {
        OutboundOrderResponse order = outboundRepository.findOrderById(id)
                .orElseThrow(() -> new BusinessException(4301, "出库单不存在"));
        if (order.status() != STATUS_SUBMITTED) {
            throw new BusinessException(4309, "仅已提交状态可确认出库");
        }
        List<OutboundItemResponse> items = outboundRepository.listItemsByOrderId(id);
        if (items.isEmpty()) {
            throw new BusinessException(4307, "出库明细不能为空");
        }
        List<ResolvedItem> resolvedItems = resolveItems(items);
        Map<LocationSkuKey, Integer> requestByLocation = sumRequestByLocation(resolvedItems);
        Map<Long, Integer> requestBySku = sumRequestBySku(resolvedItems);

        Map<LocationSkuKey, Integer> locationQtySnapshot = new HashMap<>();
        requestByLocation.keySet().stream()
                .sorted(Comparator.comparing(LocationSkuKey::locationId).thenComparing(LocationSkuKey::skuId))
                .forEach(key -> {
                    int beforeQty = outboundRepository.lockLocationStockQty(order.warehouseId(), key.locationId(), key.skuId()).orElse(0);
                    int requested = requestByLocation.getOrDefault(key, 0);
                    if (beforeQty < requested) {
                        throw new BusinessException(4316, "库位库存不足，SKU=" + key.skuId() + "，库位=" + key.locationId());
                    }
                    locationQtySnapshot.put(key, beforeQty);
                });

        Map<Long, Integer> inventoryQtySnapshot = new HashMap<>();
        requestBySku.keySet().stream()
                .sorted()
                .forEach(skuId -> {
                    int beforeQty = outboundRepository.lockInventoryStockQty(order.warehouseId(), skuId).orElse(0);
                    int requested = requestBySku.getOrDefault(skuId, 0);
                    if (beforeQty < requested) {
                        throw new BusinessException(4317, "仓库总库存不足，SKU=" + skuId);
                    }
                    inventoryQtySnapshot.put(skuId, beforeQty);
                });

        Long operatorId = outboundRepository.findUserIdByUsername(username).orElse(null);
        for (ResolvedItem item : resolvedItems) {
            LocationSkuKey locationKey = new LocationSkuKey(item.locationId(), item.skuId());
            int beforeLocationQty = locationQtySnapshot.getOrDefault(locationKey, 0);
            int afterLocationQty = beforeLocationQty - item.effectiveQty();
            locationQtySnapshot.put(locationKey, afterLocationQty);
            outboundRepository.upsertLocationStock(order.warehouseId(), item.locationId(), item.skuId(), afterLocationQty);
            outboundRepository.insertLocationTxn(
                    "OUTBOUND",
                    order.outboundNo(),
                    order.id(),
                    order.warehouseId(),
                    item.locationId(),
                    item.skuId(),
                    -item.effectiveQty(),
                    beforeLocationQty,
                    afterLocationQty,
                    operatorId,
                    username,
                    "出库确认自动生成"
            );

            int beforeInventoryQty = inventoryQtySnapshot.getOrDefault(item.skuId(), 0);
            int afterInventoryQty = beforeInventoryQty - item.effectiveQty();
            inventoryQtySnapshot.put(item.skuId(), afterInventoryQty);
            outboundRepository.upsertInventoryStock(order.warehouseId(), item.skuId(), afterInventoryQty);
            outboundRepository.insertInventoryTxn(
                    "OUTBOUND",
                    order.outboundNo(),
                    order.id(),
                    order.warehouseId(),
                    item.skuId(),
                    -item.effectiveQty(),
                    beforeInventoryQty,
                    afterInventoryQty,
                    operatorId,
                    username,
                    "出库确认自动生成"
            );

            if (!item.useActualQty()) {
                outboundRepository.updateActualQty(item.itemId(), item.effectiveQty());
            }
        }
        int affected = outboundRepository.updateStatus(id, STATUS_DONE);
        if (affected != 1) {
            throw new BusinessException(4310, "出库单确认失败");
        }
        return detail(id);
    }

    private void validateMasterData(Long warehouseId, List<OutboundItemRequest> items) {
        if (!outboundRepository.existsWarehouseActive(warehouseId)) {
            throw new BusinessException(4311, "仓库不存在或已停用");
        }
        Map<LocationSkuKey, Integer> duplicatedCheck = new HashMap<>();
        for (OutboundItemRequest item : items) {
            if (!outboundRepository.existsSkuActive(item.skuId())) {
                throw new BusinessException(4312, "SKU不存在或已停用");
            }
            if (!outboundRepository.existsLocationActive(item.locationId())) {
                throw new BusinessException(4313, "库位不存在或已停用");
            }
            if (!outboundRepository.existsLocationInWarehouse(item.locationId(), warehouseId)) {
                throw new BusinessException(4314, "库位不属于当前仓库");
            }
            LocationSkuKey key = new LocationSkuKey(item.locationId(), item.skuId());
            duplicatedCheck.merge(key, 1, Integer::sum);
            if (duplicatedCheck.get(key) > 1) {
                throw new BusinessException(4318, "同一出库单中不允许重复的“SKU+库位”明细");
            }
        }
    }

    private String resolveOutboundType(String outboundType) {
        if (outboundType == null || outboundType.isBlank()) {
            return "SALES";
        }
        return outboundType.trim().toUpperCase();
    }

    private String generateOutboundNo() {
        String datePart = LocalDate.now().format(OUTBOUND_DATE_FORMATTER);
        String prefix = OUTBOUND_NO_PREFIX + datePart;
        String currentMax = outboundRepository.findMaxOutboundNoByPrefix(prefix).orElse(null);
        int nextSeq = 1;
        if (currentMax != null && currentMax.length() >= prefix.length() + 4) {
            String suffix = currentMax.substring(currentMax.length() - 4);
            if (suffix.chars().allMatch(Character::isDigit)) {
                nextSeq = Integer.parseInt(suffix) + 1;
            }
        }
        return prefix + String.format("%04d", nextSeq);
    }

    private List<ResolvedItem> resolveItems(List<OutboundItemResponse> items) {
        List<ResolvedItem> resolved = new ArrayList<>();
        for (OutboundItemResponse item : items) {
            int effectiveQty = item.actualQty() != null && item.actualQty() > 0 ? item.actualQty() : item.planQty();
            if (effectiveQty <= 0) {
                throw new BusinessException(4315, "实出数量必须大于0");
            }
            boolean useActual = item.actualQty() != null && item.actualQty() > 0;
            resolved.add(new ResolvedItem(item.id(), item.skuId(), item.locationId(), effectiveQty, useActual));
        }
        return resolved;
    }

    private Map<LocationSkuKey, Integer> sumRequestByLocation(List<ResolvedItem> items) {
        Map<LocationSkuKey, Integer> map = new HashMap<>();
        for (ResolvedItem item : items) {
            LocationSkuKey key = new LocationSkuKey(item.locationId(), item.skuId());
            map.merge(key, item.effectiveQty(), Integer::sum);
        }
        return map;
    }

    private Map<Long, Integer> sumRequestBySku(List<ResolvedItem> items) {
        Map<Long, Integer> map = new HashMap<>();
        for (ResolvedItem item : items) {
            map.merge(item.skuId(), item.effectiveQty(), Integer::sum);
        }
        return map;
    }

    private record LocationSkuKey(Long locationId, Long skuId) {
    }

    private record ResolvedItem(Long itemId, Long skuId, Long locationId, int effectiveQty, boolean useActualQty) {
    }
}
