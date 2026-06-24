/**
 * @file 速览索引
 * @summary 盘点单接口控制器，负责盘点单分页、详情、新增、编辑、提交、确认与账面库存查询接口。
 * @core 1. 提供盘点单列表与详情接口
 * @core 2. 提供盘点单新增与编辑接口
 * @core 3. 提供提交与确认接口
 * @core 4. 提供账面库存查询接口
 * @entry 先看：page、detail、create、update、submit、confirm、bookStocks
 * @deps 关键依赖：StocktakeService、当前登录用户名获取
 * @risk 高风险修改点：账面库存查询、确认接口、状态流转与差异字段
 * @link 相关文件：后端/src/main/java/com/wms/backend/stocktake/service/StocktakeService.java、前端/src/components/StocktakePanel.vue
 */
package com.wms.backend.stocktake.controller;

import com.wms.backend.common.ApiResponse;
import com.wms.backend.common.PageResult;
import com.wms.backend.stocktake.dto.StocktakeBookStockResponse;
import com.wms.backend.stocktake.dto.StocktakeCreateRequest;
import com.wms.backend.stocktake.dto.StocktakeDetailResponse;
import com.wms.backend.stocktake.dto.StocktakeOrderResponse;
import com.wms.backend.stocktake.dto.StocktakeUpdateRequest;
import com.wms.backend.stocktake.service.StocktakeService;
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
@RequestMapping("/api/stocktakes")
public class StocktakeController {

    private final StocktakeService stocktakeService;

    public StocktakeController(StocktakeService stocktakeService) {
        this.stocktakeService = stocktakeService;
    }

    @GetMapping
    public ApiResponse<PageResult<StocktakeOrderResponse>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        return ApiResponse.success(stocktakeService.page(keyword, status, pageNo, pageSize));
    }

    @GetMapping("/{id}")
    public ApiResponse<StocktakeDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(stocktakeService.detail(id));
    }

    @GetMapping("/book-stocks")
    public ApiResponse<List<StocktakeBookStockResponse>> bookStocks(
            @RequestParam Long warehouseId,
            @RequestParam(required = false) Long skuId,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(stocktakeService.bookStocks(warehouseId, skuId, keyword));
    }

    @PostMapping
    public ApiResponse<StocktakeDetailResponse> create(
            @Valid @RequestBody StocktakeCreateRequest request,
            Authentication authentication
    ) {
        String username = authentication == null ? "system" : authentication.getName();
        return ApiResponse.success(stocktakeService.create(request, username));
    }

    @PutMapping("/{id}")
    public ApiResponse<StocktakeDetailResponse> update(@PathVariable Long id, @Valid @RequestBody StocktakeUpdateRequest request) {
        return ApiResponse.success(stocktakeService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        stocktakeService.delete(id);
        return ApiResponse.success(null);
    }

    @PutMapping("/{id}/submit")
    public ApiResponse<StocktakeDetailResponse> submit(@PathVariable Long id) {
        return ApiResponse.success(stocktakeService.submit(id));
    }

    @PutMapping("/{id}/confirm")
    public ApiResponse<StocktakeDetailResponse> confirm(@PathVariable Long id, Authentication authentication) {
        String username = authentication == null ? "system" : authentication.getName();
        return ApiResponse.success(stocktakeService.confirm(id, username));
    }
}
