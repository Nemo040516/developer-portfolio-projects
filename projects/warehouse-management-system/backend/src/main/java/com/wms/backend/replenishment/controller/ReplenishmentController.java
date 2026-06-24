/**
 * @file 速览索引
 * @summary 补货接口控制器，负责补货计划查询、统计指标、详情、重算、确认、调整最终数量与转采购草稿接口。
 * @core 1. 提供补货计划列表与详情接口
 * @core 2. 提供E1统计指标接口
 * @core 3. 提供补货计算与重算接口
 * @core 4. 提供确认与调整最终数量接口
 * @core 5. 提供转采购草稿接口
 * @entry 先看：page、metrics、detail、calculate、recalculate、confirm、updateFinalQty、toPurchaseDraft
 * @deps 关键依赖：ReplenishmentService
 * @risk 高风险修改点：状态流转、最终数量调整、转采购草稿接口路径
 * @link 相关文件：后端/src/main/java/com/wms/backend/replenishment/service/ReplenishmentService.java、前端/src/components/ReplenishmentPanel.vue
 */
package com.wms.backend.replenishment.controller;

import com.wms.backend.common.ApiResponse;
import com.wms.backend.common.PageResult;
import com.wms.backend.replenishment.dto.ReplenishmentCalculateRequest;
import com.wms.backend.replenishment.dto.ReplenishmentDetailResponse;
import com.wms.backend.replenishment.dto.ReplenishmentFinalQtyUpdateRequest;
import com.wms.backend.replenishment.dto.ReplenishmentMetricsResponse;
import com.wms.backend.replenishment.dto.ReplenishmentPlanResponse;
import com.wms.backend.replenishment.dto.ReplenishmentRecalculateRequest;
import com.wms.backend.replenishment.service.ReplenishmentService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * M6 接口入口：
 * 1) 提供补货建议分页与详情；
 * 2) 提供生成、重算、确认、转采购草稿能力。
 */
@RestController
@RequestMapping("/api/replenishments")
public class ReplenishmentController {

    private final ReplenishmentService replenishmentService;

    public ReplenishmentController(ReplenishmentService replenishmentService) {
        this.replenishmentService = replenishmentService;
    }

    @GetMapping
    public ApiResponse<PageResult<ReplenishmentPlanResponse>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate generatedDateStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate generatedDateEnd,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        return ApiResponse.success(replenishmentService.page(keyword, status, generatedDateStart, generatedDateEnd, pageNo, pageSize));
    }

    @GetMapping("/metrics")
    public ApiResponse<ReplenishmentMetricsResponse> metrics(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ApiResponse.success(replenishmentService.metrics(warehouseId, startDate, endDate));
    }

    @GetMapping("/{id}")
    public ApiResponse<ReplenishmentDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(replenishmentService.detail(id));
    }

    @PostMapping("/calculate")
    public ApiResponse<ReplenishmentDetailResponse> calculate(
            @Valid @RequestBody ReplenishmentCalculateRequest request,
            Authentication authentication
    ) {
        String username = authentication == null ? "system" : authentication.getName();
        return ApiResponse.success(replenishmentService.calculate(request, username));
    }

    @PutMapping("/{id}/recalculate")
    public ApiResponse<ReplenishmentDetailResponse> recalculate(
            @PathVariable Long id,
            @RequestBody(required = false) ReplenishmentRecalculateRequest request,
            Authentication authentication
    ) {
        return ApiResponse.success(replenishmentService.recalculate(id, request, resolveOperator(authentication)));
    }

    @PutMapping("/{id}/confirm")
    public ApiResponse<ReplenishmentDetailResponse> confirm(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.success(replenishmentService.confirm(id, resolveOperator(authentication)));
    }

    @PutMapping("/{id}/items/{itemId}/final-qty")
    public ApiResponse<ReplenishmentDetailResponse> updateFinalQty(
            @PathVariable Long id,
            @PathVariable Long itemId,
            Authentication authentication,
            @Valid @RequestBody ReplenishmentFinalQtyUpdateRequest request
    ) {
        return ApiResponse.success(replenishmentService.updateFinalQty(id, itemId, request.finalQty(), resolveOperator(authentication)));
    }

    @PostMapping("/{id}/to-purchase-draft")
    public ApiResponse<ReplenishmentDetailResponse> toPurchaseDraft(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.success(replenishmentService.toPurchaseDraft(id, resolveOperator(authentication)));
    }

    /**
     * 统一提取操作者账号，用于业务日志追踪。
     */
    private String resolveOperator(Authentication authentication) {
        return authentication == null ? "system" : authentication.getName();
    }
}
