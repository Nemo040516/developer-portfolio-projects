/**
 * @file 速览索引
 * @summary SKU 接口控制器，负责 SKU 分页、新增、更新与状态切换接口。
 * @core 1. 提供 SKU 列表接口
 * @core 2. 提供 SKU 新增与更新接口
 * @core 3. 提供 SKU 状态切换接口
 * @entry 先看：page、create、update、updateStatus
 * @deps 关键依赖：SkuService、SkuResponse
 * @risk 高风险修改点：SKU 状态接口、分页查询参数、字段与前端表单一致性
 * @link 相关文件：后端/src/main/java/com/wms/backend/sku/service/SkuService.java、前端/src/components/SkuPanel.vue
 */
package com.wms.backend.sku.controller;

import com.wms.backend.common.ApiResponse;
import com.wms.backend.common.PageResult;
import com.wms.backend.sku.dto.SkuCreateRequest;
import com.wms.backend.sku.dto.SkuResponse;
import com.wms.backend.sku.dto.SkuStatusUpdateRequest;
import com.wms.backend.sku.dto.SkuUpdateRequest;
import com.wms.backend.sku.service.SkuService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/skus")
public class SkuController {

    private final SkuService skuService;

    public SkuController(SkuService skuService) {
        this.skuService = skuService;
    }

    @GetMapping
    public ApiResponse<PageResult<SkuResponse>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        return ApiResponse.success(skuService.page(keyword, pageNo, pageSize));
    }

    @PostMapping
    public ApiResponse<SkuResponse> create(@Valid @RequestBody SkuCreateRequest request) {
        return ApiResponse.success(skuService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<SkuResponse> update(@PathVariable Long id, @Valid @RequestBody SkuUpdateRequest request) {
        return ApiResponse.success(skuService.update(id, request));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<SkuResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody SkuStatusUpdateRequest request
    ) {
        return ApiResponse.success(skuService.updateStatus(id, request.status()));
    }
}
