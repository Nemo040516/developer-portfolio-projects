/**
 * @file 速览索引
 * @summary 上架单业务服务，负责上架单创建、编辑、提交、确认以及库位库存落位逻辑。
 * @core 1. 处理上架单新增与编辑
 * @core 2. 校验提交与确认状态流转
 * @core 3. 在确认上架后更新库位库存与库存流水
 * @entry 先看：create、update、submit、confirm
 * @deps 关键依赖：PutawayRepository、库位/库存相关仓储
 * @risk 高风险修改点：库位分配、确认后库存落位、状态流转
 * @link 相关文件：后端/src/main/java/com/wms/backend/putaway/controller/PutawayController.java、前端/src/components/PutawayPanel.vue
 */
package com.wms.backend.putaway.service;

import com.wms.backend.common.PageResult;
import com.wms.backend.exception.BusinessException;
import com.wms.backend.putaway.dto.PutawayCreateRequest;
import com.wms.backend.putaway.dto.PutawayDetailResponse;
import com.wms.backend.putaway.dto.PutawayItemRequest;
import com.wms.backend.putaway.dto.PutawayItemResponse;
import com.wms.backend.putaway.dto.PutawayOrderResponse;
import com.wms.backend.putaway.dto.PutawayUpdateRequest;
import com.wms.backend.putaway.repository.PutawayRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PutawayService {

    private static final int STATUS_DRAFT = 0;
    private static final int STATUS_SUBMITTED = 1;
    private static final int STATUS_DONE = 2;
    private static final String PUTAWAY_NO_PREFIX = "pa";
    private static final DateTimeFormatter PUTAWAY_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final PutawayRepository putawayRepository;

    public PutawayService(PutawayRepository putawayRepository) {
        this.putawayRepository = putawayRepository;
    }

    public PageResult<PutawayOrderResponse> page(String keyword, Integer status, int pageNo, int pageSize) {
        String safeKeyword = keyword == null ? "" : keyword.trim();
        Integer safeStatus = (status == null || status < 0 || status > 2) ? null : status;
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.max(1, Math.min(pageSize, 50));
        int offset = (safePageNo - 1) * safePageSize;
        long total = putawayRepository.countByKeyword(safeKeyword, safeStatus);
        List<PutawayOrderResponse> records = putawayRepository.pageByKeyword(safeKeyword, safeStatus, offset, safePageSize);
        return new PageResult<>(total, records);
    }

    public PutawayDetailResponse detail(Long id) {
        PutawayOrderResponse order = putawayRepository.findOrderById(id)
                .orElseThrow(() -> new BusinessException(4201, "上架单不存在"));
        List<PutawayItemResponse> items = putawayRepository.listItemsByOrderId(id);
        return new PutawayDetailResponse(
                order.id(),
                order.putawayNo(),
                order.sourceType(),
                order.sourceOrderId(),
                order.sourceOrderNo(),
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

    @Transactional
    public PutawayDetailResponse create(PutawayCreateRequest request, String username) {
        String safeSourceType = resolveSourceType(request.sourceType());
        validateMasterData(request.warehouseId(), request.items());
        String putawayNo = generatePutawayNo();
        Long createdBy = putawayRepository.findUserIdByUsername(username).orElse(null);
        int affected = putawayRepository.insertOrder(
                putawayNo, safeSourceType, request.sourceOrderId(), request.sourceOrderNo(), request.warehouseId(), request.remark(), createdBy
        );
        if (affected != 1) {
            throw new BusinessException(4202, "上架单新增失败");
        }
        Long orderId = putawayRepository.findOrderIdByPutawayNo(putawayNo)
                .orElseThrow(() -> new BusinessException(4203, "上架单新增后读取失败"));
        for (PutawayItemRequest item : request.items()) {
            putawayRepository.insertItem(orderId, item);
        }
        return detail(orderId);
    }

    @Transactional
    public PutawayDetailResponse update(Long id, PutawayUpdateRequest request) {
        PutawayOrderResponse order = putawayRepository.findOrderById(id)
                .orElseThrow(() -> new BusinessException(4201, "上架单不存在"));
        if (order.status() != STATUS_DRAFT) {
            throw new BusinessException(4204, "仅草稿状态可编辑");
        }
        String safeSourceType = resolveSourceType(request.sourceType());
        validateMasterData(request.warehouseId(), request.items());
        int affected = putawayRepository.updateOrder(
                id, safeSourceType, request.sourceOrderId(), request.sourceOrderNo(), request.warehouseId(), request.remark()
        );
        if (affected != 1) {
            throw new BusinessException(4205, "上架单更新失败");
        }
        putawayRepository.deleteItemsByOrderId(id);
        for (PutawayItemRequest item : request.items()) {
            putawayRepository.insertItem(id, item);
        }
        return detail(id);
    }

    @Transactional
    public void delete(Long id) {
        // 仅允许删除草稿单据，避免已提交/已完成上架单丢失追溯信息。
        PutawayOrderResponse order = putawayRepository.findOrderById(id)
                .orElseThrow(() -> new BusinessException(4201, "上架单不存在"));
        if (order.status() != STATUS_DRAFT) {
            throw new BusinessException(4217, "仅草稿状态可删除");
        }
        // 删除顺序固定为“明细 -> 主表”，确保外键删除安全。
        putawayRepository.deleteItemsByOrderId(id);
        int affected = putawayRepository.deleteOrderById(id);
        if (affected != 1) {
            throw new BusinessException(4218, "上架单删除失败");
        }
    }

    @Transactional
    public PutawayDetailResponse submit(Long id, String username) {
        PutawayOrderResponse order = putawayRepository.findOrderById(id)
                .orElseThrow(() -> new BusinessException(4201, "上架单不存在"));
        if (order.status() != STATUS_DRAFT) {
            throw new BusinessException(4206, "仅草稿状态可提交");
        }
        List<PutawayItemResponse> items = putawayRepository.listItemsByOrderId(id);
        if (items.isEmpty()) {
            throw new BusinessException(4207, "上架明细不能为空");
        }
        // 提交前校验“可上架量”能覆盖本单明细，提前阻断超量上架。
        assertAvailableQty(order.warehouseId(), items, false);

        Long operatorId = putawayRepository.findUserIdByUsername(username).orElse(null);
        for (PutawayItemResponse item : items) {
            int beforeQty = putawayRepository.findLocationStockQty(order.warehouseId(), item.locationId(), item.skuId()).orElse(0);
            putawayRepository.insertLocationTxn(
                    "PUTAWAY_SUBMIT",
                    order.putawayNo(),
                    order.id(),
                    order.warehouseId(),
                    item.locationId(),
                    item.skuId(),
                    0,
                    beforeQty,
                    beforeQty,
                    operatorId,
                    username,
                    "上架单已提交，待确认上架"
            );
        }
        int affected = putawayRepository.updateStatus(id, STATUS_SUBMITTED);
        if (affected != 1) {
            throw new BusinessException(4208, "上架单提交失败");
        }
        return detail(id);
    }

    @Transactional
    public PutawayDetailResponse confirm(Long id, String username) {
        PutawayOrderResponse order = putawayRepository.findOrderById(id)
                .orElseThrow(() -> new BusinessException(4201, "上架单不存在"));
        if (order.status() != STATUS_SUBMITTED) {
            throw new BusinessException(4209, "仅已提交状态可确认上架");
        }
        List<PutawayItemResponse> items = putawayRepository.listItemsByOrderId(id);
        if (items.isEmpty()) {
            throw new BusinessException(4207, "上架明细不能为空");
        }
        // 确认前再次校验可上架量，避免并发场景下超分配。
        assertAvailableQty(order.warehouseId(), items, true);

        Long operatorId = putawayRepository.findUserIdByUsername(username).orElse(null);
        for (PutawayItemResponse item : items) {
            int effectiveQty = item.actualQty() != null && item.actualQty() > 0 ? item.actualQty() : item.planQty();
            if (effectiveQty <= 0) {
                throw new BusinessException(4210, "实上数量必须大于0");
            }
            int beforeQty = putawayRepository.findLocationStockQty(order.warehouseId(), item.locationId(), item.skuId()).orElse(0);
            int afterQty = beforeQty + effectiveQty;
            putawayRepository.upsertLocationStock(order.warehouseId(), item.locationId(), item.skuId(), afterQty);
            putawayRepository.insertLocationTxn(
                    "PUTAWAY",
                    order.putawayNo(),
                    order.id(),
                    order.warehouseId(),
                    item.locationId(),
                    item.skuId(),
                    effectiveQty,
                    beforeQty,
                    afterQty,
                    operatorId,
                    username,
                    "上架确认自动生成"
            );
            if (item.actualQty() == null || item.actualQty() <= 0) {
                putawayRepository.updateActualQty(item.id(), effectiveQty);
            }
        }
        int affected = putawayRepository.updateStatus(id, STATUS_DONE);
        if (affected != 1) {
            throw new BusinessException(4211, "上架单确认失败");
        }
        return detail(id);
    }

    private void validateMasterData(Long warehouseId, List<PutawayItemRequest> items) {
        if (!putawayRepository.existsWarehouseActive(warehouseId)) {
            throw new BusinessException(4212, "仓库不存在或已停用");
        }
        for (PutawayItemRequest item : items) {
            if (!putawayRepository.existsSkuActive(item.skuId())) {
                throw new BusinessException(4213, "SKU不存在或已停用");
            }
            if (!putawayRepository.existsLocationActive(item.locationId())) {
                throw new BusinessException(4214, "库位不存在或已停用");
            }
            if (!putawayRepository.existsLocationInWarehouse(item.locationId(), warehouseId)) {
                throw new BusinessException(4215, "库位不属于当前仓库");
            }
        }
    }

    private void assertAvailableQty(Long warehouseId, List<PutawayItemResponse> items, boolean useActualQty) {
        Map<Long, Integer> requestedBySku = new HashMap<>();
        for (PutawayItemResponse item : items) {
            int effectiveQty;
            if (useActualQty) {
                effectiveQty = item.actualQty() != null && item.actualQty() > 0 ? item.actualQty() : item.planQty();
            } else {
                effectiveQty = item.planQty();
            }
            requestedBySku.merge(item.skuId(), effectiveQty, Integer::sum);
        }
        for (Map.Entry<Long, Integer> entry : requestedBySku.entrySet()) {
            Long skuId = entry.getKey();
            int requested = entry.getValue();
            int totalQty = putawayRepository.findTotalStockQty(warehouseId, skuId).orElse(0);
            int allocatedQty = putawayRepository.sumAllocatedQty(warehouseId, skuId);
            int availableQty = totalQty - allocatedQty;
            if (requested > availableQty) {
                throw new BusinessException(4216, "可上架数量不足，SKU=" + skuId + "，可上架=" + availableQty + "，请求=" + requested);
            }
        }
    }

    private String resolveSourceType(String sourceType) {
        if (sourceType == null || sourceType.isBlank()) {
            return "INBOUND";
        }
        return sourceType.trim().toUpperCase();
    }

    private String generatePutawayNo() {
        String datePart = LocalDate.now().format(PUTAWAY_DATE_FORMATTER);
        String prefix = PUTAWAY_NO_PREFIX + datePart;
        String currentMax = putawayRepository.findMaxPutawayNoByPrefix(prefix).orElse(null);
        int nextSeq = 1;
        if (currentMax != null && currentMax.length() >= prefix.length() + 4) {
            String suffix = currentMax.substring(currentMax.length() - 4);
            if (suffix.chars().allMatch(Character::isDigit)) {
                nextSeq = Integer.parseInt(suffix) + 1;
            }
        }
        return prefix + String.format("%04d", nextSeq);
    }
}
