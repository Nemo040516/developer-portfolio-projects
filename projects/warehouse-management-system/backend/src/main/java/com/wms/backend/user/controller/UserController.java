/**
 * @file 速览索引
 * @summary 用户接口控制器，负责把前端用户治理请求映射到用户服务层。
 * @core 1. 提供用户分页查询接口
 * @core 2. 提供用户新增接口
 * @core 3. 提供用户状态更新接口
 * @core 4. 提供密码重置接口
 * @entry 先看：page、create、updateStatus、resetPassword
 * @deps 关键依赖：UserService、UserCreateRequest、UserResponse
 * @risk 高风险修改点：分页筛选参数、状态接口路径、密码重置接口路径与前端一致性
 * @link 相关文件：后端/src/main/java/com/wms/backend/user/service/UserService.java、前端/src/components/UserPanel.vue
 */
package com.wms.backend.user.controller;

import com.wms.backend.common.ApiResponse;
import com.wms.backend.common.PageResult;
import com.wms.backend.user.dto.UserCreateRequest;
import com.wms.backend.user.dto.UserResponse;
import com.wms.backend.user.dto.UserStatusUpdateRequest;
import com.wms.backend.user.service.UserService;
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
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ApiResponse<PageResult<UserResponse>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long roleId,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        return ApiResponse.success(userService.page(keyword, roleId, pageNo, pageSize));
    }

    @PostMapping
    public ApiResponse<UserResponse> create(@Valid @RequestBody UserCreateRequest request) {
        return ApiResponse.success(userService.create(request));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<UserResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UserStatusUpdateRequest request
    ) {
        return ApiResponse.success(userService.updateStatus(id, request.status()));
    }

    @PutMapping("/{id}/reset-password")
    public ApiResponse<UserResponse> resetPassword(@PathVariable Long id) {
        return ApiResponse.success(userService.resetPassword(id));
    }
}
