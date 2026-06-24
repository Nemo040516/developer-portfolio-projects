/**
 * @file 速览索引
 * @summary 角色接口控制器，负责角色分页、角色选项与角色状态维护接口，是管理员角色治理入口。
 * @core 1. 提供角色分页接口
 * @core 2. 提供可用角色选项接口
 * @core 3. 提供角色状态切换接口
 * @entry 先看：page、options、updateStatus
 * @deps 关键依赖：RoleService、RoleResponse、RoleStatusUpdateRequest
 * @risk 高风险修改点：角色状态接口路径、角色选项口径、角色新增接口已删除这一现状
 * @link 相关文件：后端/src/main/java/com/wms/backend/role/service/RoleService.java、前端/src/components/UserPanel.vue
 */
package com.wms.backend.role.controller;

import com.wms.backend.common.ApiResponse;
import com.wms.backend.common.PageResult;
import com.wms.backend.role.dto.RoleOptionResponse;
import com.wms.backend.role.dto.RoleResponse;
import com.wms.backend.role.dto.RoleStatusUpdateRequest;
import com.wms.backend.role.service.RoleService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    public ApiResponse<PageResult<RoleResponse>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        return ApiResponse.success(roleService.page(keyword, pageNo, pageSize));
    }

    @GetMapping("/options")
    public ApiResponse<List<RoleOptionResponse>> options() {
        return ApiResponse.success(roleService.options());
    }

    @PutMapping("/{id}/status")
    public ApiResponse<RoleResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody RoleStatusUpdateRequest request
    ) {
        return ApiResponse.success(roleService.updateStatus(id, request.status()));
    }
}
