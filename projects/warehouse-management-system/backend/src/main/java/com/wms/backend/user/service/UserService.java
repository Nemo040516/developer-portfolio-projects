/**
 * @file 速览索引
 * @summary 用户业务服务，负责用户分页、新增、状态维护、密码重置与管理员端账号规则控制。
 * @core 1. 校验用户创建规则与角色范围
 * @core 2. 维护用户启停状态
 * @core 3. 提供密码重置能力
 * @core 4. 保证管理员账号不通过前台页面随意新增
 * @entry 先看：page、create、updateStatus、resetPassword
 * @deps 关键依赖：UserRepository、RoleRepository、PasswordEncoder
 * @risk 高风险修改点：管理员创建限制、采购员/仓库员角色范围、默认密码口径、角色状态联动
 * @link 相关文件：后端/src/main/java/com/wms/backend/user/controller/UserController.java、前端/src/components/UserPanel.vue
 */
package com.wms.backend.user.service;

import com.wms.backend.common.PageResult;
import com.wms.backend.exception.BusinessException;
import com.wms.backend.user.dto.UserCreateRequest;
import com.wms.backend.user.dto.UserResponse;
import com.wms.backend.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public PageResult<UserResponse> page(String keyword, Long roleId, int pageNo, int pageSize) {
        String safeKeyword = keyword == null ? "" : keyword.trim();
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.max(1, Math.min(pageSize, 50));
        int offset = (safePageNo - 1) * safePageSize;
        long total = userRepository.countByKeyword(safeKeyword, roleId);
        List<UserResponse> records = userRepository.pageByKeyword(safeKeyword, roleId, offset, safePageSize);
        return new PageResult<>(total, records);
    }

    @Transactional
    public UserResponse create(UserCreateRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new BusinessException(4030, "账号已存在");
        }
        if (!userRepository.existsRoleById(request.roleId())) {
            throw new BusinessException(4031, "角色不存在");
        }
        // 管理端新增账号仅允许“仓库员/采购员”，管理员账号统一走数据库受控维护。
        String roleCode = userRepository.findRoleCodeById(request.roleId())
                .orElseThrow(() -> new BusinessException(4031, "角色不存在"));
        if (!"WAREHOUSE".equals(roleCode) && !"PURCHASER".equals(roleCode)) {
            throw new BusinessException(4032, "仅允许新增仓库员或采购员账号");
        }

        // 支持管理员指定初始密码；未填写时默认12345，兼顾易用性。
        String rawPassword = request.password() == null || request.password().isBlank()
                ? "12345"
                : request.password().trim();
        String encodedPassword = passwordEncoder.encode(rawPassword);

        int userAffected = userRepository.insertUser(request, encodedPassword);
        if (userAffected != 1) {
            throw new BusinessException(5400, "用户新增失败");
        }
        Long userId = userRepository.findUserIdByUsername(request.username());
        int roleAffected = userRepository.bindUserRole(userId, request.roleId());
        if (roleAffected != 1) {
            throw new BusinessException(5401, "用户角色绑定失败");
        }
        return userRepository.findById(userId).orElseThrow(() -> new BusinessException(5402, "用户新增后读取失败"));
    }

    public UserResponse updateStatus(Long id, Integer status) {
        ensureExists(id);
        int affected = userRepository.updateStatus(id, status);
        if (affected != 1) {
            throw new BusinessException(5403, "用户状态更新失败");
        }
        return userRepository.findById(id).orElseThrow(() -> new BusinessException(5404, "用户状态更新后读取失败"));
    }

    public UserResponse resetPassword(Long id) {
        ensureExists(id);
        int affected = userRepository.resetPassword(id);
        if (affected != 1) {
            throw new BusinessException(5405, "用户密码重置失败");
        }
        return userRepository.findById(id).orElseThrow(() -> new BusinessException(5406, "用户密码重置后读取失败"));
    }

    private void ensureExists(Long id) {
        if (userRepository.findById(id).isEmpty()) {
            throw new BusinessException(4045, "用户不存在");
        }
    }
}
