/**
 * @file 速览索引
 * @summary 出库单接口控制器，负责出库单分页、详情、新增、编辑、提交、确认与可用库存查询接口。
 * @core 1. 提供出库单列表与详情接口
 * @core 2. 提供出库单新增与编辑接口
 * @core 3. 提供提交与确认接口
 * @core 4. 提供可用库存查询接口给前端联动
 * @entry 先看：page、detail、create、update、submit、confirm、availableStocks
 * @deps 关键依赖：OutboundService、当前登录用户名获取
 * @risk 高风险修改点：可用库存接口、确认接口、详情与明细结构
 * @link 相关文件：后端/src/main/java/com/wms/backend/outbound/service/OutboundService.java、前端/src/components/OutboundPanel.vue
 */
package com.wms.backend.outbound.controller;

import com.wms.backend.common.ApiResponse;
import com.wms.backend.common.PageResult;
import com.wms.backend.outbound.dto.OutboundAvailableStockResponse;
import com.wms.backend.outbound.dto.OutboundCreateRequest;
import com.wms.backend.outbound.dto.OutboundDetailResponse;
import com.wms.backend.outbound.dto.OutboundOrderResponse;
import com.wms.backend.outbound.dto.OutboundUpdateRequest;
import com.wms.backend.outbound.service.OutboundService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/outbounds")
public class OutboundController {

    private final OutboundService outboundService;

    public OutboundController(OutboundService outboundService) {
        this.outboundService = outboundService;
    }

    @GetMapping
    public ApiResponse<PageResult<OutboundOrderResponse>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        return ApiResponse.success(outboundService.page(keyword, status, pageNo, pageSize));
    }

    @GetMapping("/{id}")
    public ApiResponse<OutboundDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(outboundService.detail(id));
    }

    @GetMapping("/available-stocks")
    public ApiResponse<List<OutboundAvailableStockResponse>> availableStocks(
            @RequestParam Long warehouseId,
            @RequestParam Long skuId,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(outboundService.availableStocks(warehouseId, skuId, keyword));
    }

    @PostMapping
    public ApiResponse<OutboundDetailResponse> create(
            @Valid @RequestBody OutboundCreateRequest request,
            Authentication authentication
    ) {
        String username = authentication == null ? "system" : authentication.getName();
        return ApiResponse.success(outboundService.create(request, username));
    }

    @PutMapping("/{id}")
    public ApiResponse<OutboundDetailResponse> update(@PathVariable Long id, @Valid @RequestBody OutboundUpdateRequest request) {
        return ApiResponse.success(outboundService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        outboundService.delete(id);
        return ApiResponse.success(null);
    }

    @PutMapping("/{id}/submit")
    public ApiResponse<OutboundDetailResponse> submit(@PathVariable Long id, Authentication authentication) {
        String username = authentication == null ? "system" : authentication.getName();
        return ApiResponse.success(outboundService.submit(id, username));
    }

    @PutMapping("/{id}/confirm")
    public ApiResponse<OutboundDetailResponse> confirm(@PathVariable Long id, Authentication authentication) {
        String username = authentication == null ? "system" : authentication.getName();
        return ApiResponse.success(outboundService.confirm(id, username));
    }
}
