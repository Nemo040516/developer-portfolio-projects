<!--
文件速览：
1. 文件职责：管理员端主布局，负责侧栏、头部、主内容区和窄宽度下的骨架稳定性。
2. 页面入口：承载 `/admin/*` 管理后台页面。
3. 关键结构：admin-shell、admin-aside、admin-header、admin-main、handleMenuSelect。
4. 阅读建议：先看脚本里的视口宽度与菜单跳转控制，再看样式中的中等宽度与窄宽度收口。
-->
<template>
  <div class="admin-layout admin-theme">
    <el-container class="admin-shell">
      <el-aside :width="asideWidth" class="admin-aside" :class="{ 'admin-aside-collapsed': isSidebarCollapsed }">
        <div class="brand" :class="{ 'brand-collapsed': isSidebarCollapsed }">
          <div v-if="!isSidebarCollapsed" class="brand-content">
            <el-icon class="brand-mark"><Grid /></el-icon>
            <span class="brand-title">管理后台</span>
          </div>
          <div v-else class="brand-icon">
            <el-icon><Grid /></el-icon>
          </div>
        </div>
        <el-menu
          :default-active="activePath"
          class="admin-menu"
          :collapse="isSidebarCollapsed"
          :collapse-transition="false"
          @select="handleMenuSelect"
        >
          <el-menu-item index="/admin/dashboard">
            <el-icon><Odometer /></el-icon>
            <span>数据看板</span>
          </el-menu-item>
          <el-menu-item index="/admin/jobs">
            <el-icon><Suitcase /></el-icon>
            <span>职位审核</span>
          </el-menu-item>
          <el-menu-item index="/admin/merchants">
            <el-icon><OfficeBuilding /></el-icon>
            <span>商家审核</span>
          </el-menu-item>
          <el-menu-item index="/admin/reports">
            <el-icon><WarningFilled /></el-icon>
            <span>举报处理</span>
          </el-menu-item>
          <el-menu-item index="/admin/governance">
            <el-icon><Bell /></el-icon>
            <span>治理通知</span>
          </el-menu-item>
          <el-menu-item index="/admin/users">
            <el-icon><UserFilled /></el-icon>
            <span>账号风控</span>
          </el-menu-item>
        </el-menu>
      </el-aside>

      <el-container class="admin-content">
        <el-header class="admin-header">
          <div class="header-left">
            <el-button class="collapse-btn" text :disabled="isForcedCompact" @click="toggleCollapse">
              <el-icon>
                <Fold v-if="!isSidebarCollapsed" />
                <Expand v-else />
              </el-icon>
            </el-button>
            <div class="header-copy">
              <span class="header-title">管理员后台</span>
              <span class="header-desc">审核、风控与运行级配置统一入口</span>
            </div>
          </div>
          <div class="header-right">
            <el-button size="small" type="danger" class="logout-btn" @click="handleLogout">退出登录</el-button>
          </div>
        </el-header>
        <el-main class="admin-main">
          <router-view />
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>

<script setup>
/*
文件速览：
1. 文件职责：根据当前路由和视口宽度控制管理员端骨架布局与菜单跳转。
2. 对外入口：isSidebarCollapsed、asideWidth、toggleCollapse、handleMenuSelect。
3. 关键结构：viewportWidth、isForcedCompact、asideWidth、handleMenuSelect、handleLogout。
4. 阅读建议：先看 resize 监听与菜单选择，再看侧栏宽度与折叠计算。
*/
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessageBox } from 'element-plus'
import { Fold, Expand, Odometer, Suitcase, OfficeBuilding, WarningFilled, Grid, UserFilled, Bell } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const activePath = computed(() => route.path)

// 侧边栏折叠状态：保留人工控制，并在极窄桌面下自动进入紧凑态。
const isCollapsed = ref(false)
const viewportWidth = ref(typeof window === 'undefined' ? 1440 : window.innerWidth)

const syncViewportWidth = () => {
  viewportWidth.value = window.innerWidth
}

const isForcedCompact = computed(() => viewportWidth.value < 960)
const isMediumDesktop = computed(() => viewportWidth.value < 1280)
const isSidebarCollapsed = computed(() => isCollapsed.value || isForcedCompact.value)

const asideWidth = computed(() => {
  if (isSidebarCollapsed.value) {
    return 'var(--ui-layout-rail-collapsed-width)'
  }
  if (isMediumDesktop.value) {
    return 'var(--ui-layout-rail-width-compact)'
  }
  return 'var(--ui-layout-rail-width)'
})

const toggleCollapse = () => {
  if (isForcedCompact.value) return
  isCollapsed.value = !isCollapsed.value
}

// 侧栏统一走显式路由跳转，避免 el-menu 的 router 模式在后台页切换时出现不稳定行为。
const handleMenuSelect = async (path) => {
  const targetPath = typeof path === 'string' ? path.trim() : ''
  if (!targetPath || targetPath === route.path) return
  try {
    await router.push(targetPath)
  } catch (error) {
    console.error('管理员侧栏跳转失败', error)
  }
}

const handleLogout = async () => {
  try {
    await ElMessageBox.confirm(
      '确认退出登录吗？',
      '退出确认',
      {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch (error) {
    return
  }
  await userStore.logout()
  router.push('/login')
}

onMounted(() => {
  syncViewportWidth()
  window.addEventListener('resize', syncViewportWidth)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncViewportWidth)
})
</script>

<style scoped>
.admin-layout {
  min-height: 100vh;
  background: var(--ui-bg);
  font-family: var(--ui-font-family);
  color: #1d1d1f;
  overflow-x: clip;
}

.admin-shell {
  min-height: 100vh;
  align-items: flex-start;
  padding: var(--ui-shell-gap);
  gap: var(--ui-shell-gap);
}

.admin-aside {
  background: var(--ui-surface);
  color: #1f2937;
  border: 1px solid var(--ui-border);
  border-radius: var(--ui-radius-lg);
  box-shadow: var(--ui-shadow-sm);
  align-self: flex-start;
  height: calc(100vh - (var(--ui-shell-gap) * 2));
  position: sticky;
  top: var(--ui-shell-gap);
  padding: 12px 8px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  flex-shrink: 0;
  transition:
    width var(--ui-motion-slow) var(--ui-ease-standard),
    box-shadow var(--ui-motion-slow) var(--ui-ease-standard);
}

.brand {
  padding: 14px 12px;
  border-bottom: 1px solid var(--ui-border);
  width: 100%;
  display: flex;
  justify-content: flex-start;
}

.brand-content {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.brand-mark {
  color: var(--ui-accent);
}

.brand-title {
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0.4px;
  color: #111827;
  white-space: nowrap;
}

.brand-collapsed {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 12px 0;
}

.brand-icon {
  width: 36px;
  height: 36px;
  border-radius: 999px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 113, 227, 0.12);
  color: #1f2937;
}

.admin-menu {
  border-right: none;
  background: transparent;
  flex: 1;
  overflow: auto;
  display: block;
}

.admin-menu :deep(.el-menu--collapse) {
  width: var(--ui-layout-rail-collapsed-width);
}

.admin-menu :deep(.el-menu--collapse .el-menu-item) {
  width: 48px;
  height: 48px;
  margin: 8px auto;
  border-radius: 12px;
  justify-content: center;
  padding: 0;
}

.admin-menu :deep(.el-menu--collapse .el-menu-item .el-icon) {
  margin-right: 0;
}

.admin-menu :deep(.el-menu-item) {
  color: #5b616e;
  margin: 6px 4px;
  border-radius: 12px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  padding-left: 12px;
  gap: 8px;
}

.admin-menu :deep(.el-menu-item.is-active) {
  background: var(--ui-accent-light);
  color: var(--ui-accent);
  font-weight: 600;
  box-shadow: inset 0 0 0 1px rgba(0, 113, 227, 0.2);
}

.admin-menu :deep(.el-menu-item:hover) {
  background: rgba(0, 0, 0, 0.04);
}

.admin-content {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: var(--ui-shell-gap);
}

.admin-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  background: var(--ui-surface);
  border: 1px solid var(--ui-border);
  border-radius: var(--ui-radius-lg);
  box-shadow: var(--ui-shadow-sm);
  min-height: var(--ui-header-height);
  padding: 10px 16px;
  position: sticky;
  top: var(--ui-shell-gap);
  z-index: 10;
}

.admin-main {
  min-width: 0;
  padding: 8px 4px 20px;
  background: var(--ui-bg);
}

.header-left {
  font-weight: 600;
  color: #111827;
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.header-copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 2px;
}

.header-title {
  font-weight: 600;
}

.header-desc {
  color: var(--ui-muted);
  font-size: 12px;
  line-height: 1.4;
  white-space: nowrap;
}

.header-right {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-shrink: 0;
}

.collapse-btn {
  border-radius: 10px;
  color: #111827;
}

.logout-btn {
  border-radius: 10px;
}

/* 管理员端统一轻量风格（与商家端保持一致） */
.admin-theme :deep(.el-button) {
  border-radius: 12px;
  height: 32px;
  padding: 0 16px;
  font-weight: 500;
  letter-spacing: 0.2px;
}

.admin-theme :deep(.el-button--small) {
  height: 28px;
  padding: 0 12px;
  border-radius: 10px;
}

.admin-theme :deep(.el-button--large) {
  height: 36px;
  padding: 0 20px;
  border-radius: 14px;
}

.admin-theme :deep(.el-button--primary) {
  background-color: #0071e3;
  border-color: #0071e3;
}

.admin-theme :deep(.el-button--primary:hover),
.admin-theme :deep(.el-button--primary:focus) {
  background-color: #005bb5;
  border-color: #005bb5;
}

.admin-theme :deep(.el-input__wrapper),
.admin-theme :deep(.el-textarea__inner),
.admin-theme :deep(.el-select .el-input__wrapper) {
  border-radius: 12px;
  box-shadow: inset 0 0 0 1px #e5e7eb;
}

.admin-theme :deep(.el-input__wrapper.is-focus),
.admin-theme :deep(.el-textarea__inner:focus) {
  box-shadow: inset 0 0 0 1px #0071e3;
}

.admin-theme :deep(.el-card) {
  border-radius: 16px;
  border: 1px solid #ebebef;
  box-shadow: none;
}

.admin-theme :deep(.el-card__header) {
  background: #fafafa;
  border-bottom: 1px solid #f0f0f2;
}

.admin-theme :deep(.el-table) {
  border-radius: 14px;
  overflow: hidden;
}

.admin-theme :deep(.el-table th.el-table__cell) {
  background: #fafafa;
  color: #1d1d1f;
}

.admin-theme :deep(.el-tag) {
  border-radius: 999px;
}

.admin-theme :deep(.el-dialog),
.admin-theme :deep(.el-drawer) {
  border-radius: 16px;
}

@media (max-width: 1280px) {
  .admin-aside {
    padding: 10px 6px;
  }

  .brand {
    padding: 12px 10px;
  }

  .admin-menu :deep(.el-menu-item) {
    margin: 5px 4px;
    padding-left: 10px;
  }

  .admin-header {
    padding: 10px 14px;
  }
}

@media (max-width: 900px) {
  .admin-shell {
    padding: 10px;
    gap: 10px;
  }

  .admin-header {
    padding: 10px 12px;
  }

  .header-desc {
    display: none;
  }

  .header-right .logout-btn {
    padding-inline: 10px;
  }
}

@media (max-width: 640px) {
  .admin-main {
    padding: 6px 0 16px;
  }
}
</style>
