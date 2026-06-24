<!--
文件速览：
1. 文件职责：商家端主布局，负责侧栏导航、顶部工作区头部、统一的侧栏提醒徽标与内容承载骨架。
2. 页面入口：承载 `/merchant/*` 页面，服务商家工作台、职位、候选人与沟通链路。
3. 关键结构：merchant-shell、merchant-aside、merchant-header、受限账号只读菜单、main-area、统一菜单提醒徽标。
4. 阅读建议：先看模板中的菜单徽标结构，再看脚本里的治理/简历/沟通摘要轮询与侧栏宽度逻辑。
-->
<template>
  <div class="merchant-layout merchant-theme">
    <el-container class="merchant-shell">
      <el-aside :width="asideWidth" class="merchant-aside" :class="{ 'merchant-aside-collapsed': isSidebarCollapsed }">
        <div class="merchant-brand" :class="{ 'merchant-brand-collapsed': isSidebarCollapsed }">
          <template v-if="!isSidebarCollapsed">
            <div class="merchant-brand-content">
              <el-icon class="merchant-brand-mark"><OfficeBuilding /></el-icon>
              <span class="merchant-brand-name" :title="companyName">{{ companyName }}</span>
            </div>
          </template>
          <div v-else class="merchant-brand-badge">
            <el-icon size="20"><OfficeBuilding /></el-icon>
          </div>
        </div>

        <el-menu
          router
          :default-active="activePath"
          :collapse="isSidebarCollapsed"
          :collapse-transition="false"
          background-color="transparent"
          text-color="#5b616e"
          active-text-color="#0071e3"
          class="merchant-menu"
        >
          <template v-if="!isRestrictedMerchant">
            <el-menu-item index="/merchant/dashboard">
              <el-icon><Odometer /></el-icon>
              <template #title>工作台概览</template>
            </el-menu-item>

            <el-menu-item index="/merchant/jobs">
              <el-icon><Briefcase /></el-icon>
              <template #title>职位管理</template>
            </el-menu-item>

            <el-menu-item index="/merchant/resumes">
              <template v-if="isSidebarCollapsed">
                <el-badge
                  :hidden="!pendingResumeCount"
                  is-dot
                  class="merchant-menu-icon-badge"
                >
                  <el-icon><User /></el-icon>
                </el-badge>
              </template>
              <el-icon v-else><User /></el-icon>
              <template #title>
                <span class="merchant-menu-title-with-count">
                  <span>简历处理台</span>
                  <span v-if="pendingResumeCount" class="merchant-menu-count">{{ pendingResumeMenuCount }}</span>
                </span>
              </template>
            </el-menu-item>
          </template>

          <el-menu-item index="/merchant/governance">
            <template v-if="isSidebarCollapsed">
              <el-badge
                :hidden="!governanceStore.pendingTotal"
                is-dot
                class="merchant-menu-icon-badge"
              >
                <el-icon><Bell /></el-icon>
              </el-badge>
            </template>
            <el-icon v-else><Bell /></el-icon>
            <template #title>
              <span class="merchant-menu-title-with-count">
                <span>平台通知</span>
                <span v-if="governanceStore.pendingTotal" class="merchant-menu-count">{{ governanceMenuCount }}</span>
              </span>
            </template>
          </el-menu-item>

          <template v-if="!isRestrictedMerchant">
            <el-menu-item index="/merchant/interviews">
              <el-icon><Calendar /></el-icon>
              <template #title>面试日程</template>
            </el-menu-item>

            <el-menu-item index="/merchant/talent">
              <el-icon><UserFilled /></el-icon>
              <template #title>候选人库</template>
            </el-menu-item>

            <el-menu-item index="/merchant/chat">
              <template v-if="isSidebarCollapsed">
                <el-badge
                  :hidden="!chatStore.unreadCount"
                  is-dot
                  class="merchant-menu-icon-badge"
                >
                  <el-icon><ChatLineRound /></el-icon>
                </el-badge>
              </template>
              <el-icon v-else><ChatLineRound /></el-icon>
              <template #title>
                <span class="merchant-menu-title-with-count">
                  <span>在线沟通</span>
                  <span v-if="chatStore.unreadCount" class="merchant-menu-count">{{ chatMenuCount }}</span>
                </span>
              </template>
            </el-menu-item>

            <el-menu-item index="/merchant/company">
              <el-icon><School /></el-icon>
              <template #title>企业信息管理</template>
            </el-menu-item>
          </template>
        </el-menu>
      </el-aside>

      <el-container class="merchant-body">
        <el-header class="merchant-header">
          <div class="merchant-header-left">
            <el-button class="collapse-btn" text :disabled="isForcedCompact" @click="toggleCollapse">
              <el-icon>
                <component :is="isSidebarCollapsed ? Expand : Fold" />
              </el-icon>
            </el-button>
            <div class="merchant-header-copy">
              <span class="merchant-header-title">商家管理系统</span>
              <span class="merchant-header-desc">{{ headerDesc }}</span>
            </div>
          </div>

          <div class="merchant-header-right">
            <UserAvatar />
          </div>
        </el-header>

        <el-main class="main-area">
          <router-view />
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>

<script setup>
/*
文件速览：
1. 文件职责：根据当前路由和视口宽度控制商家端主布局的侧栏、统一提醒徽标与头部呈现。
2. 对外入口：companyName、activePath、isSidebarCollapsed、asideWidth、pendingResumeMenuCount、governanceMenuCount、chatMenuCount、toggleCollapse。
3. 关键结构：viewportWidth、isForcedCompact、isMediumDesktop、companyName、restrictedMode、治理/简历/沟通摘要轮询。
4. 阅读建议：先看 isRestrictedMerchant / headerDesc，再看统一徽标计数与轮询逻辑。
*/
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import {
  OfficeBuilding, Odometer, School, Briefcase, User, ChatLineRound, UserFilled,
  Calendar, Fold, Expand, Bell
} from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useChatStore } from '@/stores/chat'
import { useGovernanceStore } from '@/stores/governance'
import { getChatSessionList } from '@/api/chat'
import { getMerchantDashboardStats } from '@/api/merchant'
import UserAvatar from '@/components/common/UserAvatar.vue'

const route = useRoute()
const userStore = useUserStore()
const chatStore = useChatStore()
const governanceStore = useGovernanceStore()
const isRestrictedMerchant = computed(() =>
  userStore.restrictedMode && userStore.role === 'MERCHANT'
)

const isCollapse = ref(false)
const viewportWidth = ref(typeof window === 'undefined' ? 1440 : window.innerWidth)
const pendingResumeCount = ref(0)
const activePath = computed(() => route.path)

// 企业名称统一从用户 store 读取；store 初始化时已完成 sessionStorage 恢复。
const companyName = computed(() => userStore.userInfo?.companyName || '我的企业工作台')

const syncViewportWidth = () => {
  viewportWidth.value = window.innerWidth
}

const isForcedCompact = computed(() => viewportWidth.value < 960)
const isMediumDesktop = computed(() => viewportWidth.value < 1280)
const isSidebarCollapsed = computed(() => isCollapse.value || isForcedCompact.value)

const formatMenuCount = (count) => {
  const normalized = Number(count || 0)
  if (!normalized) return '0'
  return normalized > 99 ? '99+' : String(normalized)
}

const pendingResumeMenuCount = computed(() => formatMenuCount(pendingResumeCount.value))
const governanceMenuCount = computed(() => formatMenuCount(governanceStore.pendingTotal))
const chatMenuCount = computed(() => formatMenuCount(chatStore.unreadCount))
const headerDesc = computed(() =>
  isRestrictedMerchant.value
    ? '当前账号处于受限状态，仅可查看平台通知与处理说明'
    : '聚焦职位、候选人、面试与沟通的日常处理'
)

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
  isCollapse.value = !isCollapse.value
}

let governanceTimer = null
let sidebarAttentionTimer = null

const resolveChatSessionList = (payload) => {
  const raw = payload?.data ?? payload
  if (Array.isArray(raw?.records)) return raw.records
  return Array.isArray(raw) ? raw : []
}

const refreshPendingResumeCount = async () => {
  if (!userStore.token || userStore.role !== 'MERCHANT' || isRestrictedMerchant.value) {
    pendingResumeCount.value = 0
    return
  }
  try {
    const res = await getMerchantDashboardStats({ rangeDays: 7 })
    const data = res?.data || {}
    const deliveryCount = Number(data.deliveryCount || 0)
    const viewedCount = Number(data.funnelViewedCount || 0)
    pendingResumeCount.value = Math.max(deliveryCount - viewedCount, 0)
  } catch (error) {
    // 侧栏待处理简历数拉取失败时保持静默，避免影响主布局渲染。
  }
}

const refreshChatUnreadCount = async () => {
  if (!userStore.token || userStore.role !== 'MERCHANT' || isRestrictedMerchant.value) {
    chatStore.setUnreadCount(0)
    return
  }
  try {
    const res = await getChatSessionList()
    const list = resolveChatSessionList(res)
    const total = list.reduce((sum, item) => (
      sum + Number(item.unreadCount || item.unread || item.unreadNum || 0)
    ), 0)
    chatStore.setUnreadCount(total)
  } catch (error) {
    // 会话未读数拉取失败时保持静默，避免打断商家工作流。
  }
}

const refreshSidebarAttention = async () => {
  await Promise.all([
    refreshPendingResumeCount(),
    refreshChatUnreadCount()
  ])
}

const refreshGovernanceSummary = async (force = false) => {
  if (!userStore.token || userStore.role !== 'MERCHANT') {
    governanceStore.resetSummary()
    return
  }
  try {
    await governanceStore.fetchSummary('MERCHANT', {
      force,
      cacheMs: force ? 0 : 12000
    })
  } catch (error) {
    // 治理摘要拉取失败时保持静默，避免影响商家主流程。
  }
}

const startGovernancePolling = () => {
  if (governanceTimer) return
  governanceTimer = setInterval(() => {
    refreshGovernanceSummary()
  }, 45000)
}

const stopGovernancePolling = () => {
  if (governanceTimer) {
    clearInterval(governanceTimer)
    governanceTimer = null
  }
}

const startSidebarAttentionPolling = () => {
  if (sidebarAttentionTimer) return
  sidebarAttentionTimer = setInterval(() => {
    refreshSidebarAttention()
  }, 30000)
}

const stopSidebarAttentionPolling = () => {
  if (sidebarAttentionTimer) {
    clearInterval(sidebarAttentionTimer)
    sidebarAttentionTimer = null
  }
}

onMounted(() => {
  syncViewportWidth()
  window.addEventListener('resize', syncViewportWidth)
  refreshSidebarAttention()
  refreshGovernanceSummary(true)
  startSidebarAttentionPolling()
  startGovernancePolling()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncViewportWidth)
  stopSidebarAttentionPolling()
  stopGovernancePolling()
})
</script>

<style scoped>
.merchant-layout {
  min-height: 100vh;
  background: var(--ui-bg);
  overflow-x: clip;
}

.merchant-shell {
  min-height: 100vh;
  align-items: flex-start;
  padding: var(--ui-shell-gap);
  gap: var(--ui-shell-gap);
}

.merchant-aside {
  background: var(--ui-surface);
  border: 1px solid var(--ui-border);
  border-radius: var(--ui-radius-lg);
  box-shadow: var(--ui-shadow-sm);
  box-sizing: border-box;
  align-self: flex-start;
  height: calc(100vh - (var(--ui-shell-gap) * 2));
  position: sticky;
  top: var(--ui-shell-gap);
  z-index: 2;
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

.merchant-brand {
  padding: 14px 12px;
  border-bottom: 1px solid var(--ui-border);
  width: 100%;
  display: flex;
  justify-content: flex-start;
}

.merchant-brand-content {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.merchant-brand-mark {
  color: var(--ui-accent);
}

.merchant-brand-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 17px;
  font-weight: 700;
  color: var(--ui-text);
}

.merchant-brand-collapsed {
  justify-content: center;
  align-items: center;
  padding: 12px 0;
}

.merchant-brand-badge {
  width: 36px;
  height: 36px;
  border-radius: 999px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 113, 227, 0.12);
  color: #1f2937;
}

.merchant-menu {
  display: block;
  border-right: none;
  background: transparent;
  flex: 1;
  overflow: auto;
}

.merchant-menu :deep(.el-menu--collapse) {
  width: var(--ui-layout-rail-collapsed-width);
}

.merchant-menu :deep(.el-menu-item) {
  margin: 6px 4px;
  border-radius: 12px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  padding-left: 12px;
  gap: 8px;
}

.merchant-menu :deep(.el-menu-item .el-icon) {
  margin-right: 0;
}

.merchant-menu-icon-badge :deep(.el-badge__content.is-dot) {
  right: 8px;
  top: 10px;
}

.merchant-menu-title-with-count {
  width: 100%;
  display: inline-flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.merchant-menu-count {
  min-width: 22px;
  height: 20px;
  padding: 0 7px;
  border-radius: 999px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 113, 227, 0.12);
  color: var(--ui-accent);
  font-size: 11px;
  font-weight: 700;
  line-height: 1;
}

.merchant-menu :deep(.el-menu-item.is-active) {
  background: var(--ui-accent-light) !important;
  color: var(--ui-accent) !important;
  font-weight: 600;
  box-shadow: inset 0 0 0 1px rgba(0, 113, 227, 0.2);
}

.merchant-menu :deep(.el-menu-item:hover) {
  background: rgba(0, 0, 0, 0.04);
}

.merchant-menu :deep(.el-menu--collapse .el-menu-item) {
  width: 48px;
  height: 48px;
  margin: 8px auto;
  padding: 0;
  justify-content: center;
  border-radius: 12px;
}

.merchant-menu :deep(.el-menu--collapse .el-menu-item .el-icon) {
  margin-right: 0;
}

.merchant-menu :deep(.el-menu--collapse .merchant-menu-icon-badge .el-badge__content) {
  transform: translate(2px, -2px);
}

.merchant-body {
  min-width: 0;
  position: relative;
  z-index: 1;
  background: transparent;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: var(--ui-shell-gap);
}

.merchant-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  min-height: var(--ui-header-height);
  padding: 10px 16px;
  background: var(--ui-surface);
  border: 1px solid var(--ui-border);
  border-radius: var(--ui-radius-lg);
  box-shadow: var(--ui-shadow-sm);
  position: sticky;
  top: var(--ui-shell-gap);
  z-index: 5;
}

.merchant-header-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.merchant-header-copy {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.merchant-header-title {
  color: var(--ui-text);
  font-size: 15px;
  font-weight: 600;
}

.merchant-header-desc {
  color: var(--ui-muted);
  font-size: 12px;
  line-height: 1.4;
  white-space: nowrap;
}

.merchant-header-right {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-shrink: 0;
}

.collapse-btn {
  border-radius: 10px;
  color: var(--ui-text);
}

.main-area {
  min-width: 0;
  flex: 1;
  padding: 8px 4px 20px;
  overflow-y: auto;
  background: var(--ui-bg);
}

@media (max-width: 1280px) {
  .merchant-aside {
    padding: 10px 6px;
  }

  .merchant-brand {
    padding: 12px 10px;
  }

  .merchant-menu :deep(.el-menu-item) {
    margin: 5px 4px;
    padding-left: 10px;
  }

  .merchant-header {
    padding: 10px 14px;
  }
}

@media (max-width: 1024px) {
  .merchant-header-desc {
    display: none;
  }

  .merchant-header-right :deep(.user-info-meta),
  .merchant-header-right :deep(.arrow-icon) {
    display: none;
  }

  .merchant-header-right :deep(.avatar-wrapper) {
    padding: 6px;
    border-radius: 14px;
  }
}

@media (max-width: 900px) {
  .merchant-shell {
    padding: 10px;
    gap: 10px;
  }

  .merchant-header {
    padding: 10px 12px;
  }
}

@media (max-width: 640px) {
  .main-area {
    padding: 6px 0 16px;
  }
}
</style>
