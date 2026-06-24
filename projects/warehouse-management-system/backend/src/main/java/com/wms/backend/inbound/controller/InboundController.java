/**
 * @file 速览索引
 * @summary 入库单接口控制器，负责入库单分页、详情、新增、编辑、提交与确认接口。
 * @core 1. 提供入库单列表与详情接口
 * @core 2. 提供入库单新增与编辑接口
 * @core 3. 提供提交与确认接口
 * @entry 先看：page、detail、create、update、submit、confirm
 * @deps 关键依赖：InboundService、当前登录用户名获取
 * @risk 高风险修改点：状态流转接口路径、详情结构、提交确认权限
 * @link 相关文件：后端/src/main/java/com/wms/backend/inbound/service/InboundService.java、前端/src/components/InboundPanel.vue
 */
package com.wms.backend.inbound.controller;

import com.wms.backend.common.ApiResponse;
import com.wms.backend.common.PageResult;
import com.wms.backend.inbound.dto.InboundCreateRequest;
import com.wms.backend.inbound.dto.InboundDetailResponse;
import com.wms.backend.inbound.dto.InboundOrderResponse;
import com.wms.backend.inbound.dto.InboundUpdateRequest;
import com.wms.backend.inbound.service.InboundService;
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

@RestController
@RequestMapping("/api/inbounds")
public class InboundController {

    private final InboundService inboundService;

    public InboundController(InboundService inboundService) {
        this.inboundService = inboundService;
    }

    @GetMapping
    public ApiResponse<PageResult<InboundOrderResponse>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        return ApiResponse.success(inboundService.page(keyword, status, pageNo, pageSize));
    }

    @GetMapping("/{id}")
    public ApiResponse<InboundDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(inboundService.detail(id));
    }

    @PostMapping
    public ApiResponse<InboundDetailResponse> create(
            @Valid @RequestBody InboundCreateRequest request,
            Authentication authentication
    ) {
        String username = authentication == null ? "system" : authentication.getName();
        return ApiResponse.success(inboundService.create(request, username));
    }

    @PutMapping("/{id}")
    public ApiResponse<InboundDetailResponse> update(@PathVariable Long id, @Valid @RequestBody InboundUpdateRequest request) {
        return ApiResponse.success(inboundService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        inboundService.delete(id);
        return ApiResponse.success(null);
    }

    @PutMapping("/{id}/submit")
    public ApiResponse<InboundDetailResponse> submit(@PathVariable Long id, Authentication authentication) {
        String username = authentication == null ? "system" : authentication.getName();
        return ApiResponse.success(inboundService.submit(id, username));
    }

    @PutMapping("/{id}/confirm")
    public ApiResponse<InboundDetailResponse> confirm(@PathVariable Long id, Authentication authentication) {
        String username = authentication == null ? "system" : authentication.getName();
        return ApiResponse.success(inboundService.confirm(id, username));
    }
}
