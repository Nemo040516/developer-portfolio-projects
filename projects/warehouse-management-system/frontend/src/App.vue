<!--
  @file 速览索引
  @summary 前端应用总壳层，负责登录恢复、菜单渲染、页面切换、管理员侧边栏分组折叠与首页快捷跳转联动。
  @core 1. 管理登录态与当前用户信息
  @core 2. 根据后端返回菜单渲染左侧导航
  @core 3. 负责各业务面板的按需加载与切换
  @core 4. 处理治理看板到具体业务页的快捷跳转
  @entry 先看：loadProfile、renderMenuGroups、loadCurrentMenuData、handleDashboardJump
  @deps 关键依赖：api/auth.js、DashboardPanel.vue、各业务 Panel 组件
  @state 关键状态：activeMenu、user、pendingDashboardPreset、menuGroupCollapsed
  @risk 高风险修改点：菜单码、管理员分组结构、快捷跳转预设、登录恢复逻辑
  @link 相关文件：前端/src/components/DashboardPanel.vue、前端/src/api/auth.js、后端/src/main/java/com/wms/backend/auth/service/MenuService.java
-->
<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import zhCn from "element-plus/es/locale/lang/zh-cn";
import { loginApi, meApi } from "./api/auth";
import DashboardPanel from "./components/DashboardPanel.vue";
import InboundPanel from "./components/InboundPanel.vue";
import InventoryAlertPanel from "./components/InventoryAlertPanel.vue";
import InventoryPanel from "./components/InventoryPanel.vue";
import LocationPanel from "./components/LocationPanel.vue";
import OutboundPanel from "./components/OutboundPanel.vue";
import PutawayPanel from "./components/PutawayPanel.vue";
import ReplenishmentPanel from "./components/ReplenishmentPanel.vue";
import SkuPanel from "./components/SkuPanel.vue";
import StocktakePanel from "./components/StocktakePanel.vue";
import SupplierPanel from "./components/SupplierPanel.vue";
import UserPanel from "./components/UserPanel.vue";
import WarehousePanel from "./components/WarehousePanel.vue";

const loading = ref(false);
const bootstrapping = ref(true);
const loggedIn = ref(false);
const activeMenu = ref("dashboard");
const user = reactive({
  username: "",
  roleCode: "",
  menus: []
});
const dashboardPanelRef = ref();
const warehousePanelRef = ref();
const locationPanelRef = ref();
const skuPanelRef = ref();
const supplierPanelRef = ref();
const userPanelRef = ref();
const inboundPanelRef = ref();
const putawayPanelRef = ref();
const outboundPanelRef = ref();
const inventoryPanelRef = ref();
const inventoryAlertPanelRef = ref();
const stocktakePanelRef = ref();
const replenishmentPanelRef = ref();
const pendingDashboardPreset = ref(null);
const sidebarRef = ref();
const sidebarCanScroll = ref(false);
const sidebarAtTop = ref(true);
const sidebarAtBottom = ref(false);
const menuGroupCollapsed = reactive({
  governance: false,
  execution: true
});

const loginForm = reactive({
  username: "",
  password: ""
});

const menuLabelMap = {
  dashboard: "首页看板",
  user: "用户管理",
  warehouse: "仓库管理",
  location: "库位管理",
  sku: "商品管理",
  supplier: "供应商管理",
  inbound: "采购入库",
  putaway: "上架管理",
  outbound: "出库管理",
  inventory: "库存台账",
  "inventory-alert": "库存预警",
  stocktake: "库存盘点",
  replenishment: "智能补货"
};

const roleLabelMap = {
  ADMIN: "管理员",
  WAREHOUSE: "仓库员",
  PURCHASER: "采购员"
};

const currentPanelTitle = computed(() => menuLabelMap[activeMenu.value] || "页面");

function sortMenusByPriority(codes, priorityOrder) {
  const orderMap = new Map(priorityOrder.map((code, index) => [code, index]));
  return [...(codes || [])].sort((a, b) => {
    const ai = orderMap.has(a) ? orderMap.get(a) : Number.MAX_SAFE_INTEGER;
    const bi = orderMap.has(b) ? orderMap.get(b) : Number.MAX_SAFE_INTEGER;
    if (ai !== bi) return ai - bi;
    return String(menuLabelMap[a] || a).localeCompare(String(menuLabelMap[b] || b), "zh-CN");
  });
}

const renderMenus = computed(() => {
  const codes = user.menus || [];
  if (user.roleCode !== "ADMIN") {
    return codes.map((code) => ({
      code,
      label: menuLabelMap[code] || code
    }));
  }
  const adminPriority = [
    "dashboard",
    "user",
    "inventory-alert",
    "replenishment",
    "inbound",
    "putaway",
    "outbound",
    "stocktake",
    "inventory",
    "warehouse",
    "location",
    "sku",
    "supplier"
  ];
  return sortMenusByPriority(codes, adminPriority).map((code) => ({
    code,
    label: code === "dashboard" ? "治理看板" : menuLabelMap[code] || code
  }));
});

const renderMenuGroups = computed(() => {
  if (user.roleCode !== "ADMIN") {
    return [
      {
        key: "default",
        title: "",
        sections: [
          {
            key: "default",
            title: "",
            items: renderMenus.value
          }
        ]
      }
    ];
  }
  const governanceCodes = new Set(["dashboard", "user", "inventory-alert", "replenishment"]);
  const governanceItems = renderMenus.value.filter((item) => governanceCodes.has(item.code));
  const executionItems = renderMenus.value.filter((item) => !governanceCodes.has(item.code));
  // 管理员“作业”区再细分为“单据作业 / 库存与资料”，降低长菜单的扫读成本。
  const executionSections = [
    {
      key: "document",
      title: "单据作业",
      items: executionItems.filter((item) => ["inbound", "putaway", "outbound", "stocktake"].includes(item.code))
    },
    {
      key: "data",
      title: "库存与资料",
      items: executionItems.filter((item) => ["inventory", "warehouse", "location", "sku", "supplier"].includes(item.code))
    }
  ].filter((section) => section.items.length > 0);
  return [
    {
      key: "governance",
      title: "治理",
      sections: [
        {
          key: "core",
          title: "",
          items: governanceItems
        }
      ]
    },
    {
      key: "execution",
      title: "作业",
      sections: executionSections
    }
  ].filter((group) => group.sections.some((section) => section.items.length > 0));
});

// 侧边栏滚动状态计算：用于控制阴影提示与“上移/下移/回顶”交互按钮。
function updateSidebarScrollState() {
  const el = sidebarRef.value;
  if (!el) return;
  const maxScrollTop = el.scrollHeight - el.clientHeight;
  sidebarCanScroll.value = maxScrollTop > 2;
  sidebarAtTop.value = el.scrollTop <= 2;
  sidebarAtBottom.value = maxScrollTop - el.scrollTop <= 2;
}

function handleSidebarScroll() {
  updateSidebarScrollState();
}

function handleWindowResize() {
  updateSidebarScrollState();
}

// 管理员分组折叠：用于减少侧边栏垂直占用，突出当前关注区块。
function isGroupCollapsed(groupKey) {
  return !!menuGroupCollapsed[groupKey];
}

function toggleMenuGroup(groupKey) {
  if (!groupKey || !Object.prototype.hasOwnProperty.call(menuGroupCollapsed, groupKey)) {
    return;
  }
  menuGroupCollapsed[groupKey] = !menuGroupCollapsed[groupKey];
  nextTick(() => {
    updateSidebarScrollState();
  });
}

function menuStorageKey(username) {
  return `wms_active_menu_${username || "anonymous"}`;
}

function resolveActiveMenu(username, menus) {
  const availableMenus = menus || [];
  if (!availableMenus.length) {
    return "dashboard";
  }
  const lastMenu = sessionStorage.getItem(menuStorageKey(username));
  if (lastMenu && availableMenus.includes(lastMenu)) {
    return lastMenu;
  }
  return availableMenus[0];
}

async function loadProfile() {
  const profile = await meApi();
  user.username = profile.username;
  user.roleCode = profile.roleCode;
  user.menus = profile.menus || [];
  activeMenu.value = resolveActiveMenu(user.username, user.menus);
  loggedIn.value = true;
  await loadCurrentMenuData();
}

async function handleLogin() {
  if (!loginForm.username || !loginForm.password) {
    ElMessage.warning("请输入账号和密码");
    return;
  }
  loading.value = true;
  try {
    const data = await loginApi(loginForm);
    sessionStorage.setItem("wms_token", data.token);
    localStorage.removeItem("wms_token");
    user.username = data.username;
    user.roleCode = data.roleCode;
    user.menus = data.menus || [];
    activeMenu.value = resolveActiveMenu(user.username, user.menus);
    loggedIn.value = true;
    await loadCurrentMenuData();
    ElMessage.success("登录成功");
  } catch (error) {
    ElMessage.error(error?.message || "登录失败");
  } finally {
    loading.value = false;
  }
}

function handleLogout() {
  if (user.username) {
    sessionStorage.removeItem(menuStorageKey(user.username));
  }
  sessionStorage.removeItem("wms_token");
  localStorage.removeItem("wms_token");
  loggedIn.value = false;
  user.username = "";
  user.roleCode = "";
  user.menus = [];
  loginForm.username = "";
  loginForm.password = "";
}

function normalizeDashboardJumpTarget(target) {
  if (typeof target === "string") {
    return { menuCode: target, preset: null };
  }
  if (target && typeof target === "object") {
    return {
      menuCode: target.menuCode || target.code || "",
      preset: target.preset || null
    };
  }
  return { menuCode: "", preset: null };
}

async function applyMenuQuickPreset(menuCode, preset) {
  if (!preset) return;
  if (menuCode === "replenishment") {
    await replenishmentPanelRef.value?.applyQuickFilter?.(preset);
    return;
  }
  if (menuCode === "inventory-alert") {
    await inventoryAlertPanelRef.value?.applyQuickFilter?.(preset);
  }
}

async function handleDashboardJump(target) {
  const { menuCode, preset } = normalizeDashboardJumpTarget(target);
  if (!menuCode || !user.menus.includes(menuCode)) {
    ElMessage.warning("当前账号无该模块访问权限");
    return;
  }
  if (activeMenu.value === menuCode) {
    await applyMenuQuickPreset(menuCode, preset);
    return;
  }
  pendingDashboardPreset.value = preset ? { menuCode, preset } : null;
  activeMenu.value = menuCode;
}

async function loadCurrentMenuData() {
  if (activeMenu.value === "dashboard") {
    await nextTick();
    await dashboardPanelRef.value?.loadData();
  }
  // 页面首次切到仓库管理时，自动触发列表加载，减少手工操作。
  if (activeMenu.value === "warehouse") {
    await nextTick();
    await warehousePanelRef.value?.loadData();
  }
  if (activeMenu.value === "location") {
    await nextTick();
    await locationPanelRef.value?.loadData();
  }
  if (activeMenu.value === "sku") {
    await nextTick();
    await skuPanelRef.value?.loadData();
  }
  if (activeMenu.value === "supplier") {
    await nextTick();
    await supplierPanelRef.value?.loadData();
  }
  if (activeMenu.value === "user") {
    await nextTick();
    await userPanelRef.value?.loadData();
  }
  if (activeMenu.value === "inbound") {
    await nextTick();
    await inboundPanelRef.value?.loadData();
  }
  if (activeMenu.value === "inventory") {
    await nextTick();
    await inventoryPanelRef.value?.loadData();
  }
  if (activeMenu.value === "putaway") {
    await nextTick();
    await putawayPanelRef.value?.loadData();
  }
  if (activeMenu.value === "outbound") {
    await nextTick();
    await outboundPanelRef.value?.loadData();
  }
  if (activeMenu.value === "inventory-alert") {
    await nextTick();
    const pending = pendingDashboardPreset.value;
    if (pending && pending.menuCode === "inventory-alert") {
      pendingDashboardPreset.value = null;
      await applyMenuQuickPreset("inventory-alert", pending.preset);
    } else {
      await inventoryAlertPanelRef.value?.loadData();
    }
  }
  if (activeMenu.value === "stocktake") {
    await nextTick();
    await stocktakePanelRef.value?.loadData();
  }
  if (activeMenu.value === "replenishment") {
    await nextTick();
    const pending = pendingDashboardPreset.value;
    if (pending && pending.menuCode === "replenishment") {
      pendingDashboardPreset.value = null;
      await applyMenuQuickPreset("replenishment", pending.preset);
    } else {
      await replenishmentPanelRef.value?.loadData();
    }
  }
}

watch(activeMenu, async () => {
  if (loggedIn.value && user.username && activeMenu.value) {
    sessionStorage.setItem(menuStorageKey(user.username), activeMenu.value);
  }
  await loadCurrentMenuData();
});

onMounted(async () => {
  // 历史版本使用 localStorage，这里做一次兼容迁移，后续统一用 sessionStorage。
  const legacyToken = localStorage.getItem("wms_token");
  if (legacyToken && !sessionStorage.getItem("wms_token")) {
    sessionStorage.setItem("wms_token", legacyToken);
    localStorage.removeItem("wms_token");
  }
  const token = sessionStorage.getItem("wms_token");
  if (!token) {
    bootstrapping.value = false;
    return;
  }
  try {
    await loadProfile();
  } catch (error) {
    sessionStorage.removeItem("wms_token");
  } finally {
    bootstrapping.value = false;
    await nextTick();
    updateSidebarScrollState();
  }
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", handleWindowResize);
});

watch([() => loggedIn.value, renderMenuGroups], async () => {
  await nextTick();
  updateSidebarScrollState();
});

onMounted(() => {
  window.addEventListener("resize", handleWindowResize);
});
</script>

<template>
  <el-config-provider :locale="zhCn">
    <div v-if="bootstrapping" class="login-page">
      <div class="login-card">
        <h2>智能仓库订货系统</h2>
        <p>正在恢复登录状态...</p>
      </div>
    </div>

    <div v-else-if="!loggedIn" class="login-page">
      <div class="login-card">
        <h2>智能仓库订货系统</h2>
        <p>模块 M1：账号权限与基础资料</p>
        <el-form label-position="top" autocomplete="off">
          <el-form-item label="账号">
            <el-input v-model="loginForm.username" autocomplete="off" placeholder="请输入账号" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input
              v-model="loginForm.password"
              type="password"
              show-password
              autocomplete="new-password"
              placeholder="请输入密码"
            />
          </el-form-item>
          <el-button type="primary" :loading="loading" style="width: 100%" @click="handleLogin">
            登录
          </el-button>
        </el-form>
      </div>
    </div>

    <div v-else class="layout-page">
      <aside
        ref="sidebarRef"
        class="sidebar"
        :class="{
          'sidebar-has-top-shadow': !sidebarAtTop,
          'sidebar-has-bottom-shadow': sidebarCanScroll && !sidebarAtBottom
        }"
        @scroll.passive="handleSidebarScroll"
      >
        <div class="logo">
          <div class="logo-title">智能仓储系统</div>
          <div class="logo-subtitle">仓储管理平台</div>
        </div>
        <el-menu :default-active="activeMenu" class="menu" @select="(key) => (activeMenu = key)">
          <template v-for="group in renderMenuGroups" :key="group.key">
            <div
              v-if="group.title"
              class="menu-group-title menu-group-toggle"
              role="button"
              tabindex="0"
              @click="toggleMenuGroup(group.key)"
              @keydown.enter.prevent="toggleMenuGroup(group.key)"
            >
              <span>{{ group.title }}</span>
              <span class="menu-group-arrow" :class="{ 'menu-group-arrow-collapsed': isGroupCollapsed(group.key) }">▾</span>
            </div>
            <template v-if="!group.title || !isGroupCollapsed(group.key)">
              <template v-for="section in group.sections" :key="`${group.key}-${section.key}`">
                <div v-if="section.title" class="menu-subgroup-title">{{ section.title }}</div>
                <el-menu-item v-for="item in section.items" :key="item.code" :index="item.code">
                  {{ item.label }}
                </el-menu-item>
              </template>
            </template>
          </template>
        </el-menu>
      </aside>
      <main class="main">
        <header class="header">
          <div>当前角色：{{ roleLabelMap[user.roleCode] || user.roleCode }}</div>
          <div>
            <span class="username">{{ user.username }}</span>
            <el-button size="small" @click="handleLogout">退出</el-button>
          </div>
        </header>
        <section class="content">
          <dashboard-panel
            v-if="activeMenu === 'dashboard'"
            ref="dashboardPanelRef"
            :role-code="user.roleCode"
            :menus="user.menus"
            @jump-menu="handleDashboardJump"
          />
          <warehouse-panel v-else-if="activeMenu === 'warehouse'" ref="warehousePanelRef" />
          <location-panel v-else-if="activeMenu === 'location'" ref="locationPanelRef" />
          <sku-panel v-else-if="activeMenu === 'sku'" ref="skuPanelRef" :role-code="user.roleCode" />
          <supplier-panel v-else-if="activeMenu === 'supplier'" ref="supplierPanelRef" :role-code="user.roleCode" />
          <user-panel v-else-if="activeMenu === 'user'" ref="userPanelRef" />
          <inbound-panel v-else-if="activeMenu === 'inbound'" ref="inboundPanelRef" />
          <putaway-panel v-else-if="activeMenu === 'putaway'" ref="putawayPanelRef" />
          <outbound-panel v-else-if="activeMenu === 'outbound'" ref="outboundPanelRef" />
          <inventory-panel v-else-if="activeMenu === 'inventory'" ref="inventoryPanelRef" />
          <inventory-alert-panel
            v-else-if="activeMenu === 'inventory-alert'"
            ref="inventoryAlertPanelRef"
            :role-code="user.roleCode"
          />
          <stocktake-panel v-else-if="activeMenu === 'stocktake'" ref="stocktakePanelRef" />
          <replenishment-panel
            v-else-if="activeMenu === 'replenishment'"
            ref="replenishmentPanelRef"
            :role-code="user.roleCode"
          />
          <p v-else>当前菜单暂未配置页面，请检查权限与菜单配置。</p>
        </section>
      </main>
    </div>
  </el-config-provider>
</template>

<style scoped>
.login-page,
.layout-page {
  --app-surface: #f5f7fb;
  --app-card-bg: #ffffff;
  --app-card-border: #e7ebf2;
  --app-radius-lg: 12px;
  --app-radius-md: 10px;
  --app-radius-sm: 8px;
  --app-text-main: #101828;
  --app-text-sub: #667085;
  --app-brand-deep: #18345c;
  --app-brand-soft: #8cb5ff;
  --app-sidebar-width: 180px;
  --app-sidebar-width-md: 172px;
}

.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #eaf2ff 0%, #f6f9ff 55%, #f9fbff 100%);
}

.login-card {
  width: 380px;
  padding: 28px;
  background: var(--app-card-bg);
  border-radius: var(--app-radius-lg);
  border: 1px solid var(--app-card-border);
  box-shadow: 0 10px 30px rgba(17, 24, 39, 0.08);
}

.login-card h2 {
  margin: 0 0 6px;
  color: var(--app-text-main);
}

.login-card p {
  margin: 0 0 14px;
  color: var(--app-text-sub);
}

.layout-page {
  height: 100vh;
  min-height: 100vh;
  display: flex;
  background: var(--app-surface);
  overflow: hidden;
}

.sidebar {
  position: relative;
  width: var(--app-sidebar-width);
  height: 100vh;
  flex: 0 0 var(--app-sidebar-width);
  background: linear-gradient(180deg, #18345c 0%, #1d3f6f 100%);
  color: #fff;
  box-shadow: inset -1px 0 0 rgba(255, 255, 255, 0.08);
  overflow-y: auto;
  overflow-x: hidden;
  scroll-behavior: smooth;
  -webkit-overflow-scrolling: touch;
  padding-bottom: 8px;
}

.sidebar::-webkit-scrollbar {
  width: 6px;
}

.sidebar::-webkit-scrollbar-thumb {
  background: rgba(219, 233, 255, 0.35);
  border-radius: 999px;
}

.sidebar::-webkit-scrollbar-track {
  background: transparent;
}

.sidebar-has-top-shadow::before,
.sidebar-has-bottom-shadow::after {
  content: "";
  position: sticky;
  display: block;
  left: 0;
  width: 100%;
  height: 14px;
  pointer-events: none;
  z-index: 2;
}

.sidebar-has-top-shadow::before {
  top: 0;
  background: linear-gradient(180deg, rgba(12, 35, 66, 0.52) 0%, rgba(12, 35, 66, 0) 100%);
}

.sidebar-has-bottom-shadow::after {
  bottom: 0;
  background: linear-gradient(0deg, rgba(12, 35, 66, 0.52) 0%, rgba(12, 35, 66, 0) 100%);
}

.logo {
  height: 64px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 8px 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.12);
}

.logo-title {
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0.5px;
  color: #f8fbff;
}

.logo-subtitle {
  margin-top: 2px;
  font-size: 11px;
  color: #dbe9ff;
}

.menu {
  border-right: none;
  background: transparent;
}

.menu :deep(.el-menu) {
  border-right: none;
  background: transparent;
}

.menu :deep(.el-menu-item) {
  color: #eef4ff;
  padding: 0 14px !important;
}

.menu :deep(.el-menu-item:hover) {
  background: rgba(140, 181, 255, 0.24);
  color: #ffffff;
}

.menu :deep(.el-menu-item.is-active) {
  background: rgba(140, 181, 255, 0.34);
  color: #ffffff;
  font-weight: 600;
}

.menu-group-title {
  padding: 10px 14px 6px;
  font-size: 11px;
  color: rgba(219, 233, 255, 0.78);
  letter-spacing: 0.8px;
  font-weight: 700;
}

.menu-group-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
  user-select: none;
}

.menu-group-arrow {
  font-size: 12px;
  color: rgba(219, 233, 255, 0.82);
  transition: transform 0.15s ease;
}

.menu-group-arrow-collapsed {
  transform: rotate(-90deg);
}

.menu-subgroup-title {
  padding: 4px 14px 2px 22px;
  font-size: 10px;
  color: rgba(219, 233, 255, 0.6);
  letter-spacing: 0.6px;
}

.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  height: 100vh;
  overflow: hidden;
}

.header {
  height: 56px;
  padding: 0 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--app-card-bg);
  border-bottom: 1px solid var(--app-card-border);
  color: #344054;
}

.username {
  margin-right: 10px;
  color: var(--app-text-main);
  font-weight: 600;
}

.content {
  flex: 1;
  min-height: 0;
  padding: 14px;
  overflow: auto;
}

.login-card :deep(.el-button),
.header :deep(.el-button) {
  border-radius: var(--app-radius-sm);
}

@media (max-width: 960px) {
  .sidebar {
    width: var(--app-sidebar-width-md);
    flex-basis: var(--app-sidebar-width-md);
  }

  .content {
    padding: 12px;
  }
}
</style>
