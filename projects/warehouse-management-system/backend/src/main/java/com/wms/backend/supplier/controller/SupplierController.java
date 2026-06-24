/**
 * @file 速览索引
 * @summary 供应商接口控制器，负责供应商分页、新增、更新与状态切换接口。
 * @core 1. 提供供应商列表接口
 * @core 2. 提供供应商新增与更新接口
 * @core 3. 提供供应商状态切换接口
 * @entry 先看：page、create、update、updateStatus
 * @deps 关键依赖：SupplierService、SupplierResponse
 * @risk 高风险修改点：联系方式字段、状态接口、分页查询与前端对齐
 * @link 相关文件：后端/src/main/java/com/wms/backend/supplier/service/SupplierService.java、前端/src/components/SupplierPanel.vue
 */
package com.wms.backend.supplier.controller;

import com.wms.backend.common.ApiResponse;
import com.wms.backend.common.PageResult;
import com.wms.backend.supplier.dto.SupplierCreateRequest;
import com.wms.backend.supplier.dto.SupplierResponse;
import com.wms.backend.supplier.dto.SupplierStatusUpdateRequest;
import com.wms.backend.supplier.dto.SupplierUpdateRequest;
import com.wms.backend.supplier.service.SupplierService;
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
@RequestMapping("/api/suppliers")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @GetMapping
    public ApiResponse<PageResult<SupplierResponse>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        return ApiResponse.success(supplierService.page(keyword, pageNo, pageSize));
    }

    @PostMapping
    public ApiResponse<SupplierResponse> create(@Valid @RequestBody SupplierCreateRequest request) {
        return ApiResponse.success(supplierService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<SupplierResponse> update(@PathVariable Long id, @Valid @RequestBody SupplierUpdateRequest request) {
        return ApiResponse.success(supplierService.update(id, request));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<SupplierResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody SupplierStatusUpdateRequest request
    ) {
        return ApiResponse.success(supplierService.updateStatus(id, request.status()));
    }
}
