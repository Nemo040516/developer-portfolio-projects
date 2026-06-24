/**
 * @file 速览索引
 * @summary 菜单生成服务，负责根据角色返回前端可见菜单，用于控制左侧导航与首页入口展示。
 * @core 1. 为 ADMIN 返回治理优先的菜单集合
 * @core 2. 为 PURCHASER / WAREHOUSE 返回各自可用菜单
 * @core 3. 保持菜单码与前端菜单渲染逻辑一致
 * @entry 先看：各角色菜单构造方法 / 菜单清单常量 / getMenus 之类主入口
 * @deps 关键依赖：角色编码、前端 App.vue、SecurityConfig
 * @risk 高风险修改点：菜单码改动、角色菜单缩放、前端菜单分组与首页跳转联动
 * @link 相关文件：前端/src/App.vue、后端/src/main/java/com/wms/backend/security/SecurityConfig.java
 */
package com.wms.backend.auth.service;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MenuService {

    public List<String> menusByRole(String roleCode) {
        if ("ADMIN".equals(roleCode)) {
            // 角色状态配置已并入用户管理页，管理员菜单不再单独展示“角色管理”。
            return List.of("dashboard", "user", "warehouse", "location", "sku", "supplier", "inbound", "putaway", "outbound", "inventory", "inventory-alert", "stocktake", "replenishment");
        }
        if ("WAREHOUSE".equals(roleCode)) {
            // 仓库员菜单采用“作业流优先”：
            // 先执行链路（入库->上架->出库->库存），再风险/周期任务（预警->盘点->补货），最后是低频主数据维护。
            return List.of(
                    "dashboard",
                    "inbound",
                    "putaway",
                    "outbound",
                    "inventory",
                    "inventory-alert",
                    "stocktake",
                    "replenishment",
                    "sku",
                    "location",
                    "warehouse"
            );
        }
        if ("PURCHASER".equals(roleCode)) {
            // 采购员菜单采用“执行优先”：
            // 看板保留首位，其次是高频操作（智能补货、库存预警），最后是基础资料。
            return List.of("dashboard", "replenishment", "inventory-alert", "supplier", "sku");
        }
        return List.of("dashboard");
    }
}
