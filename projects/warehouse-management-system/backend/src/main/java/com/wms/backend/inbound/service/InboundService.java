/**
 * @file 速览索引
 * @summary 入库单业务服务，负责入库单创建、编辑、提交流转与确认入库后的库存写入。
 * @core 1. 处理入库单新增与编辑
 * @core 2. 校验提交与确认的状态流转
 * @core 3. 在确认入库后写入库存相关数据
 * @entry 先看：create、update、submit、confirm
 * @deps 关键依赖：InboundRepository、库存相关仓储、仓库/SKU/供应商数据
 * @risk 高风险修改点：状态流转、确认后库存写入、明细字段口径
 * @link 相关文件：后端/src/main/java/com/wms/backend/inbound/controller/InboundController.java、前端/src/components/InboundPanel.vue
 */
package com.wms.backend.inbound.service;

import com.wms.backend.common.PageResult;
import com.wms.backend.exception.BusinessException;
import com.wms.backend.inbound.dto.InboundCreateRequest;
import com.wms.backend.inbound.dto.InboundDetailResponse;
import com.wms.backend.inbound.dto.InboundItemRequest;
import com.wms.backend.inbound.dto.InboundItemResponse;
import com.wms.backend.inbound.dto.InboundOrderResponse;
import com.wms.backend.inbound.dto.InboundUpdateRequest;
import com.wms.backend.inbound.repository.InboundRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
public class InboundService {

    private static final int STATUS_DRAFT = 0;
    private static final int STATUS_SUBMITTED = 1;
    private static final int STATUS_DONE = 2;
    private static final String INBOUND_NO_PREFIX = "in";
    private static final DateTimeFormatter INBOUND_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final InboundRepository inboundRepository;

    public InboundService(InboundRepository inboundRepository) {
        this.inboundRepository = inboundRepository;
    }

    public PageResult<InboundOrderResponse> page(String keyword, Integer status, int pageNo, int pageSize) {
        String safeKeyword = keyword == null ? "" : keyword.trim();
        Integer safeStatus = (status == null || status < 0 || status > 2) ? null : status;
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.max(1, Math.min(pageSize, 50));
        int offset = (safePageNo - 1) * safePageSize;
        long total = inboundRepository.countByKeyword(safeKeyword, safeStatus);
        List<InboundOrderResponse> records = inboundRepository.pageByKeyword(safeKeyword, safeStatus, offset, safePageSize);
        return new PageResult<>(total, records);
    }

    public InboundDetailResponse detail(Long id) {
        InboundOrderResponse order = inboundRepository.findOrderById(id)
                .orElseThrow(() -> new BusinessException(4041, "入库单不存在"));
        List<InboundItemResponse> items = inboundRepository.listItemsByOrderId(id);
        return new InboundDetailResponse(
                order.id(),
                order.inboundNo(),
                order.supplierId(),
                order.supplierName(),
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
    public InboundDetailResponse create(InboundCreateRequest request, String username) {
        validateMasterData(request.supplierId(), request.warehouseId(), request.items());
        String inboundNo = generateInboundNo();
        Long createdBy = inboundRepository.findUserIdByUsername(username).orElse(null);
        int affected = inboundRepository.insertOrder(inboundNo, request.supplierId(), request.warehouseId(), request.remark(), createdBy);
        if (affected != 1) {
            throw new BusinessException(4101, "入库单新增失败");
        }
        Long orderId = inboundRepository.findOrderIdByInboundNo(inboundNo)
                .orElseThrow(() -> new BusinessException(4102, "入库单新增后读取失败"));
        for (InboundItemRequest item : request.items()) {
            inboundRepository.insertItem(orderId, item);
        }
        return detail(orderId);
    }

    @Transactional
    public InboundDetailResponse update(Long id, InboundUpdateRequest request) {
        InboundOrderResponse order = inboundRepository.findOrderById(id)
                .orElseThrow(() -> new BusinessException(4041, "入库单不存在"));
        if (order.status() != STATUS_DRAFT) {
            throw new BusinessException(4103, "仅草稿状态可编辑");
        }
        validateMasterData(request.supplierId(), request.warehouseId(), request.items());
        int affected = inboundRepository.updateOrder(id, request.supplierId(), request.warehouseId(), request.remark());
        if (affected != 1) {
            throw new BusinessException(4104, "入库单更新失败");
        }
        inboundRepository.deleteItemsByOrderId(id);
        for (InboundItemRequest item : request.items()) {
            inboundRepository.insertItem(id, item);
        }
        return detail(id);
    }

    @Transactional
    public void delete(Long id) {
        // 删除动作仅允许草稿单据，避免破坏已提交/已完成业务追溯链路。
        InboundOrderResponse order = inboundRepository.findOrderById(id)
                .orElseThrow(() -> new BusinessException(4041, "入库单不存在"));
        if (order.status() != STATUS_DRAFT) {
            throw new BusinessException(4114, "仅草稿状态可删除");
        }
        // 先删明细再删主单，满足外键约束。
        inboundRepository.deleteItemsByOrderId(id);
        int affected = inboundRepository.deleteOrderById(id);
        if (affected != 1) {
            throw new BusinessException(4115, "入库单删除失败");
        }
    }

    @Transactional
    public InboundDetailResponse submit(Long id, String username) {
        InboundOrderResponse order = inboundRepository.findOrderById(id)
                .orElseThrow(() -> new BusinessException(4041, "入库单不存在"));
        if (order.status() != STATUS_DRAFT) {
            throw new BusinessException(4105, "仅草稿状态可提交");
        }
        List<InboundItemResponse> items = inboundRepository.listItemsByOrderId(id);
        if (items.isEmpty()) {
            throw new BusinessException(4106, "入库明细不能为空");
        }
        Long operatorId = inboundRepository.findUserIdByUsername(username).orElse(null);
        for (InboundItemResponse item : items) {
            int beforeQty = inboundRepository.findStockQty(order.warehouseId(), item.skuId()).orElse(0);
            inboundRepository.insertInventoryTxn(
                    "INBOUND_SUBMIT",
                    order.inboundNo(),
                    order.id(),
                    order.warehouseId(),
                    item.skuId(),
                    0,
                    beforeQty,
                    beforeQty,
                    operatorId,
                    username,
                    "入库单已提交，待确认入库"
            );
        }
        int affected = inboundRepository.updateStatus(id, STATUS_SUBMITTED);
        if (affected != 1) {
            throw new BusinessException(4107, "入库单提交失败");
        }
        return detail(id);
    }

    @Transactional
    public InboundDetailResponse confirm(Long id, String username) {
        InboundOrderResponse order = inboundRepository.findOrderById(id)
                .orElseThrow(() -> new BusinessException(4041, "入库单不存在"));
        if (order.status() != STATUS_SUBMITTED) {
            throw new BusinessException(4108, "仅已提交状态可确认入库");
        }
        List<InboundItemResponse> items = inboundRepository.listItemsByOrderId(id);
        if (items.isEmpty()) {
            throw new BusinessException(4106, "入库明细不能为空");
        }
        Long operatorId = inboundRepository.findUserIdByUsername(username).orElse(null);
        for (InboundItemResponse item : items) {
            int effectiveQty = item.receivedQty() != null && item.receivedQty() > 0 ? item.receivedQty() : item.planQty();
            if (effectiveQty <= 0) {
                throw new BusinessException(4109, "实收数量必须大于0");
            }
            Optional<Integer> beforeQtyOptional = inboundRepository.findStockQty(order.warehouseId(), item.skuId());
            int beforeQty = beforeQtyOptional.orElse(0);
            int afterQty = beforeQty + effectiveQty;
            inboundRepository.upsertStock(order.warehouseId(), item.skuId(), afterQty);
            inboundRepository.insertInventoryTxn(
                    "INBOUND",
                    order.inboundNo(),
                    order.id(),
                    order.warehouseId(),
                    item.skuId(),
                    effectiveQty,
                    beforeQty,
                    afterQty,
                    operatorId,
                    username,
                    "入库确认自动生成"
            );
            if (item.receivedQty() == null || item.receivedQty() <= 0) {
                inboundRepository.updateReceivedQty(item.id(), effectiveQty);
            }
        }
        int affected = inboundRepository.updateStatus(id, STATUS_DONE);
        if (affected != 1) {
            throw new BusinessException(4110, "入库单确认失败");
        }
        return detail(id);
    }

    private void validateMasterData(Long supplierId, Long warehouseId, List<InboundItemRequest> items) {
        if (!inboundRepository.existsSupplierActive(supplierId)) {
            throw new BusinessException(4111, "供应商不存在或已停用");
        }
        if (!inboundRepository.existsWarehouseActive(warehouseId)) {
            throw new BusinessException(4112, "仓库不存在或已停用");
        }
        for (InboundItemRequest item : items) {
            if (!inboundRepository.existsSkuActive(item.skuId())) {
                throw new BusinessException(4113, "SKU不存在或已停用");
            }
        }
    }

    private String generateInboundNo() {
        String datePart = LocalDate.now().format(INBOUND_DATE_FORMATTER);
        String prefix = INBOUND_NO_PREFIX + datePart;
        String currentMax = inboundRepository.findMaxInboundNoByPrefix(prefix).orElse(null);
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
