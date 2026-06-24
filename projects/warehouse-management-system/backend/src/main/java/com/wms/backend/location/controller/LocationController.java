/**
 * @file 速览索引
 * @summary 库位接口控制器，负责库位分页、新增、更新与状态维护接口。
 * @core 1. 提供库位列表接口
 * @core 2. 提供库位新增与更新接口
 * @core 3. 提供库位状态切换接口
 * @entry 先看：page、create、update、updateStatus
 * @deps 关键依赖：LocationService、LocationResponse
 * @risk 高风险修改点：库位与仓库绑定参数、状态路径、分页查询参数
 * @link 相关文件：后端/src/main/java/com/wms/backend/location/service/LocationService.java、前端/src/components/LocationPanel.vue
 */
package com.wms.backend.location.controller;

import com.wms.backend.common.ApiResponse;
import com.wms.backend.common.PageResult;
import com.wms.backend.location.dto.LocationCreateRequest;
import com.wms.backend.location.dto.LocationResponse;
import com.wms.backend.location.dto.LocationStatusUpdateRequest;
import com.wms.backend.location.dto.LocationUpdateRequest;
import com.wms.backend.location.service.LocationService;
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
@RequestMapping("/api/locations")
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @GetMapping
    public ApiResponse<PageResult<LocationResponse>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        return ApiResponse.success(locationService.page(keyword, warehouseId, pageNo, pageSize));
    }

    @PostMapping
    public ApiResponse<LocationResponse> create(@Valid @RequestBody LocationCreateRequest request) {
        return ApiResponse.success(locationService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<LocationResponse> update(@PathVariable Long id, @Valid @RequestBody LocationUpdateRequest request) {
        return ApiResponse.success(locationService.update(id, request));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<LocationResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody LocationStatusUpdateRequest request
    ) {
        return ApiResponse.success(locationService.updateStatus(id, request.status()));
    }
}
