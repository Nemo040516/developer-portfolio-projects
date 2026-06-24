/**
 * @file 速览索引
 * @summary 角色业务服务，负责角色分页、角色选项与角色状态维护，是管理员角色治理链路的核心服务。
 * @core 1. 提供角色分页查询
 * @core 2. 提供启用角色选项给用户创建页使用
 * @core 3. 维护角色启停状态
 * @core 4. 保护 ADMIN 角色不可被停用
 * @entry 先看：page、updateStatus、options
 * @deps 关键依赖：RoleRepository、用户登录鉴权逻辑、前端 UserPanel
 * @risk 高风险修改点：ADMIN 不可停用规则、角色状态对登录的影响、角色选项口径
 * @link 相关文件：后端/src/main/java/com/wms/backend/role/controller/RoleController.java、前端/src/components/UserPanel.vue
 */
package com.wms.backend.role.service;

import com.wms.backend.common.PageResult;
import com.wms.backend.exception.BusinessException;
import com.wms.backend.role.dto.RoleOptionResponse;
import com.wms.backend.role.dto.RoleResponse;
import com.wms.backend.role.repository.RoleRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RoleService {

    private final RoleRepository roleRepository;

    public RoleService(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    public PageResult<RoleResponse> page(String keyword, int pageNo, int pageSize) {
        String safeKeyword = keyword == null ? "" : keyword.trim();
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.max(1, Math.min(pageSize, 50));
        int offset = (safePageNo - 1) * safePageSize;
        long total = roleRepository.countByKeyword(safeKeyword);
        List<RoleResponse> records = roleRepository.pageByKeyword(safeKeyword, offset, safePageSize);
        return new PageResult<>(total, records);
    }

    public RoleResponse updateStatus(Long id, Integer status) {
        RoleResponse current = roleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(4044, "角色不存在"));
        // 管理员角色属于系统根角色，禁止停用，避免出现全局越权或无法登录的风险。
        if ("ADMIN".equals(current.roleCode()) && status != null && status == 0) {
            throw new BusinessException(4024, "管理员角色禁止停用");
        }
        int affected = roleRepository.updateStatus(id, status);
        if (affected != 1) {
            throw new BusinessException(5302, "角色状态更新失败");
        }
        return roleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(5303, "角色状态更新后读取失败"));
    }

    public List<RoleOptionResponse> options() {
        return roleRepository.listEnabledOptions();
    }

}
