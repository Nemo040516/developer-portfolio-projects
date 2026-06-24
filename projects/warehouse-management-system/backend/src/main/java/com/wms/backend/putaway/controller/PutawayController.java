/**
 * @file 速览索引
 * @summary 上架单接口控制器，负责上架单分页、详情、新增、编辑、提交与确认接口。
 * @core 1. 提供上架单列表与详情接口
 * @core 2. 提供上架单新增与编辑接口
 * @core 3. 提供提交与确认接口
 * @entry 先看：page、detail、create、update、submit、confirm
 * @deps 关键依赖：PutawayService、当前登录用户名获取
 * @risk 高风险修改点：上架状态流转、详情结构、确认接口路径
 * @link 相关文件：后端/src/main/java/com/wms/backend/putaway/service/PutawayService.java、前端/src/components/PutawayPanel.vue
 */
package com.wms.backend.putaway.controller;

import com.wms.backend.common.ApiResponse;
import com.wms.backend.common.PageResult;
import com.wms.backend.putaway.dto.PutawayCreateRequest;
import com.wms.backend.putaway.dto.PutawayDetailResponse;
import com.wms.backend.putaway.dto.PutawayOrderResponse;
import com.wms.backend.putaway.dto.PutawayUpdateRequest;
import com.wms.backend.putaway.service.PutawayService;
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
@RequestMapping("/api/putaways")
public class PutawayController {

    private final PutawayService putawayService;

    public PutawayController(PutawayService putawayService) {
        this.putawayService = putawayService;
    }

    @GetMapping
    public ApiResponse<PageResult<PutawayOrderResponse>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        return ApiResponse.success(putawayService.page(keyword, status, pageNo, pageSize));
    }

    @GetMapping("/{id}")
    public ApiResponse<PutawayDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(putawayService.detail(id));
    }

    @PostMapping
    public ApiResponse<PutawayDetailResponse> create(
            @Valid @RequestBody PutawayCreateRequest request,
            Authentication authentication
    ) {
        String username = authentication == null ? "system" : authentication.getName();
        return ApiResponse.success(putawayService.create(request, username));
    }

    @PutMapping("/{id}")
    public ApiResponse<PutawayDetailResponse> update(@PathVariable Long id, @Valid @RequestBody PutawayUpdateRequest request) {
        return ApiResponse.success(putawayService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        putawayService.delete(id);
        return ApiResponse.success(null);
    }

    @PutMapping("/{id}/submit")
    public ApiResponse<PutawayDetailResponse> submit(@PathVariable Long id, Authentication authentication) {
        String username = authentication == null ? "system" : authentication.getName();
        return ApiResponse.success(putawayService.submit(id, username));
    }

    @PutMapping("/{id}/confirm")
    public ApiResponse<PutawayDetailResponse> confirm(@PathVariable Long id, Authentication authentication) {
        String username = authentication == null ? "system" : authentication.getName();
        return ApiResponse.success(putawayService.confirm(id, username));
    }
}
