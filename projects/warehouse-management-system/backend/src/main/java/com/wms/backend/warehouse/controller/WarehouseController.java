/**
 * @file 速览索引
 * @summary 仓库接口控制器，负责仓库分页、新增、更新、状态切换与仓库选项接口。
 * @core 1. 提供仓库列表接口
 * @core 2. 提供仓库新增与更新接口
 * @core 3. 提供仓库状态切换接口
 * @core 4. 提供下游模块使用的仓库选项接口
 * @entry 先看：page、create、update、updateStatus、options
 * @deps 关键依赖：WarehouseService、WarehouseResponse
 * @risk 高风险修改点：接口路径、状态接口、选项口径、分页参数与前端一致性
 * @link 相关文件：后端/src/main/java/com/wms/backend/warehouse/service/WarehouseService.java、前端/src/components/WarehousePanel.vue
 */
package com.wms.backend.warehouse.controller;

import com.wms.backend.common.ApiResponse;
import com.wms.backend.common.PageResult;
import com.wms.backend.warehouse.dto.WarehouseCreateRequest;
import com.wms.backend.warehouse.dto.WarehouseOptionResponse;
import com.wms.backend.warehouse.dto.WarehouseResponse;
import com.wms.backend.warehouse.dto.WarehouseStatusUpdateRequest;
import com.wms.backend.warehouse.dto.WarehouseUpdateRequest;
import com.wms.backend.warehouse.service.WarehouseService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/warehouses")
public class WarehouseController {

    private final WarehouseService warehouseService;

    public WarehouseController(WarehouseService warehouseService) {
        this.warehouseService = warehouseService;
    }

    @GetMapping
    public ApiResponse<PageResult<WarehouseResponse>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        return ApiResponse.success(warehouseService.page(keyword, pageNo, pageSize));
    }

    @GetMapping("/options")
    public ApiResponse<List<WarehouseOptionResponse>> options() {
        return ApiResponse.success(warehouseService.enabledOptions());
    }

    @PostMapping
    public ApiResponse<WarehouseResponse> create(@Valid @RequestBody WarehouseCreateRequest request) {
        return ApiResponse.success(warehouseService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<WarehouseResponse> update(@PathVariable Long id, @Valid @RequestBody WarehouseUpdateRequest request) {
        return ApiResponse.success(warehouseService.update(id, request));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<WarehouseResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody WarehouseStatusUpdateRequest request
    ) {
        return ApiResponse.success(warehouseService.updateStatus(id, request.status()));
    }
}
