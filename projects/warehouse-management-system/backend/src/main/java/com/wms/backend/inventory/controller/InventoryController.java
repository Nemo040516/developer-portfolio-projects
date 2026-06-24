/**
 * @file 速览索引
 * @summary 库存接口控制器，负责库存汇总、库存流水、库位库存、库位流水、预警规则与预警数据接口。
 * @core 1. 提供库存台账相关查询接口
 * @core 2. 提供库存预警规则维护接口
 * @core 3. 提供库存预警分页数据接口
 * @entry 先看：stocks、txns、locationStocks、locationTxns、alertRules、alerts
 * @deps 关键依赖：InventoryService 或相关查询服务
 * @risk 高风险修改点：查询口径、筛选参数、预警规则接口与预警列表联动
 * @link 相关文件：前端/src/components/InventoryPanel.vue、前端/src/components/InventoryAlertPanel.vue
 */
package com.wms.backend.inventory.controller;

import com.wms.backend.common.ApiResponse;
import com.wms.backend.common.PageResult;
import com.wms.backend.inventory.dto.InventoryAlertResponse;
import com.wms.backend.inventory.dto.InventoryAlertRuleResponse;
import com.wms.backend.inventory.dto.InventoryAlertRuleSaveRequest;
import com.wms.backend.inventory.dto.InventoryStockResponse;
import com.wms.backend.inventory.dto.InventoryTxnResponse;
import com.wms.backend.inventory.dto.LocationStockResponse;
import com.wms.backend.inventory.dto.LocationTxnResponse;
import com.wms.backend.inventory.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/stocks")
    public ApiResponse<PageResult<InventoryStockResponse>> pageStocks(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        return ApiResponse.success(inventoryService.pageStocks(keyword, pageNo, pageSize));
    }

    @GetMapping("/txns")
    public ApiResponse<PageResult<InventoryTxnResponse>> pageTxns(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        return ApiResponse.success(inventoryService.pageTxns(keyword, pageNo, pageSize));
    }

    @GetMapping("/location-stocks")
    public ApiResponse<PageResult<LocationStockResponse>> pageLocationStocks(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        return ApiResponse.success(inventoryService.pageLocationStocks(keyword, pageNo, pageSize));
    }

    @GetMapping("/location-txns")
    public ApiResponse<PageResult<LocationTxnResponse>> pageLocationTxns(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        return ApiResponse.success(inventoryService.pageLocationTxns(keyword, pageNo, pageSize));
    }

    @GetMapping("/alert-rules")
    public ApiResponse<PageResult<InventoryAlertRuleResponse>> pageAlertRules(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        return ApiResponse.success(inventoryService.pageAlertRules(keyword, pageNo, pageSize));
    }

    @PostMapping("/alert-rules")
    public ApiResponse<InventoryAlertRuleResponse> createAlertRule(
            @Valid @RequestBody InventoryAlertRuleSaveRequest request,
            Authentication authentication
    ) {
        String username = authentication == null ? "system" : authentication.getName();
        return ApiResponse.success(inventoryService.createAlertRule(request, username));
    }

    @PutMapping("/alert-rules/{id}")
    public ApiResponse<InventoryAlertRuleResponse> updateAlertRule(
            @PathVariable Long id,
            @Valid @RequestBody InventoryAlertRuleSaveRequest request
    ) {
        return ApiResponse.success(inventoryService.updateAlertRule(id, request));
    }

    @GetMapping("/alerts")
    public ApiResponse<PageResult<InventoryAlertResponse>> pageAlerts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String alertType,
            @RequestParam(required = false) String alertLevel,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        return ApiResponse.success(inventoryService.pageAlerts(keyword, alertType, alertLevel, pageNo, pageSize));
    }
}
