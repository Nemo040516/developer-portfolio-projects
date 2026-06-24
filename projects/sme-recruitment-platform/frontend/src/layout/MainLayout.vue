<!--
文件速览：
1. 文件职责：求职者与游客共用主布局，负责顶部导航、个人菜单联动的提醒轮询、主内容容器与页脚骨架。
2. 页面入口：承载 `/jobs`、`/applicant/*`、`/chat` 等前台主链路页面。
3. 关键结构：header-content、nav-menu、受限账号只读导航、user-area、main-content、footer、提醒轮询。
4. 阅读建议：先看模板中的 restrictedMode 分支，再看脚本里的治理轮询与受限态兜底逻辑。
-->
<template>
  <div class="main-layout">
    <!-- 顶部导航栏 -->
    <header class="header">
      <div class="container header-content">
        <!-- Logo -->
        <div class="logo" @click="goHome">
          <el-icon size="28" class="logo-icon"><Briefcase /></el-icon>
          <span class="logo-text">中小商家招聘平台</span>
        </div>

        <!-- 菜单 -->
        <nav class="nav-menu">
          <template v-if="isRestrictedApplicant">
            <span class="nav-restricted-tip">只读受限模式</span>
          </template>
          <template v-else>
            <router-link to="/applicant/dashboard" class="nav-item" :class="{ active: $route.path.startsWith('/applicant/dashboard') }">工作台</router-link>
            <router-link to="/jobs" class="nav-item" :class="{ active: $route.path.startsWith('/jobs') || $route.path.startsWith('/job/detail') }">职位大厅</router-link>
            <router-link to="/applicant/applications" class="nav-item" :class="{ active: $route.path.startsWith('/applicant/applications') }">投递记录</router-link>
            <router-link to="/applicant/interviews" class="nav-item" :class="{ active: $route.path.startsWith('/applicant/interviews') }">面试日程</router-link>
            <el-badge :value="chatStore.unreadCount" :hidden="!chatStore.unreadCount" class="nav-badge">
              <router-link to="/chat" class="nav-item" :class="{ active: $route.path.startsWith('/chat') }">沟通消息</router-link>
            </el-badge>
          </template>
        </nav>

        <!-- 用户区域 -->
        <div class="user-area">
          <template v-if="userStore.token">
            <UserAvatar />
          </template>
          <template v-else>
            <el-button type="primary" link @click="$router.push('/login')">登录</el-button>
            <el-divider direction="vertical" />
            <el-button type="primary" @click="$router.push('/register')">注册</el-button>
          </template>
        </div>
      </div>
    </header>

    <!-- 内容区 -->
    <main class="main-content">
      <router-view />
    </main>

    <!-- 页脚 -->
    <footer class="footer">
      <div class="container">
        <p class="copyright">© 2024 中小商家招聘与投递管理平台 - 毕业设计项目</p>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { useUserStore } from '@/stores/user'
import { useChatStore } from '@/stores/chat'
import { useGovernanceStore } from '@/stores/governance'
import { useRouter } from 'vue-router'
import { computed, onMounted, onBeforeUnmount } from 'vue'
import { Briefcase } from '@element-plus/icons-vue'
import { getMyResume } from '@/api/applicant'
import { getChatSessionList } from '@/api/chat'
import UserAvatar from '@/components/common/UserAvatar.vue'

const userStore = useUserStore()
const chatStore = useChatStore()
const governanceStore = useGovernanceStore()
const router = useRouter()
const isRestrictedApplicant = computed(() =>
  userStore.restrictedMode && userStore.role === 'APPLICANT'
)

// 仅求职者端同步简历姓名（避免商家端误用）
const syncResumeName = async () => {
  if (!userStore.token || userStore.role !== 'APPLICANT' || isRestrictedApplicant.value) return
  if (userStore.userInfo?.realName) return
  try {
    const res = await getMyResume()
    const resumeName = res?.data?.basicInfo?.name
    if (resumeName) {
      userStore.setUserInfo({
        ...userStore.userInfo,
        realName: resumeName
      })
    }
  } catch (error) {
    console.warn('同步简历姓名失败:', error)
  }
}

const goHome = () => {
  if (!userStore.token) {
    router.push('/login')
    return
  }
  if (isRestrictedApplicant.value) {
    router.push(userStore.resolveRestrictedPath())
    return
  }
  if (userStore.role === 'MERCHANT') {
    router.push('/merchant/dashboard')
  } else if (userStore.role === 'APPLICANT') {
    router.push('/applicant/dashboard')
  } else if (userStore.role === 'ADMIN') {
    router.push('/admin')
  } else {
    router.push('/login')
  }
}

onMounted(() => {
  syncResumeName()
  refreshUnreadCount()
  startUnreadPolling()
  refreshGovernanceSummary(true)
  startGovernancePolling()
})

onBeforeUnmount(() => {
  stopUnreadPolling()
  stopGovernancePolling()
})

let unreadTimer = null
let governanceTimer = null

// 拉取会话列表，统计未读数量（用于导航栏红点）
const refreshUnreadCount = async () => {
  if (!userStore.token || userStore.role !== 'APPLICANT' || isRestrictedApplicant.value) return
  try {
    const res = await getChatSessionList()
    const raw = res?.data ?? res
    const list = Array.isArray(raw?.records) ? raw.records : (Array.isArray(raw) ? raw : [])
    const total = list.reduce((sum, item) => sum + Number(item.unreadCount || item.unread || item.unreadNum || 0), 0)
    chatStore.setUnreadCount(total)
  } catch (error) {
    // 忽略拉取失败
  }
}

const startUnreadPolling = () => {
  if (unreadTimer) return
  unreadTimer = setInterval(refreshUnreadCount, 30000)
}

const stopUnreadPolling = () => {
  if (unreadTimer) {
    clearInterval(unreadTimer)
    unreadTimer = null
  }
}

// 拉取平台提醒摘要，用于导航红点与工作台联动
const refreshGovernanceSummary = async (force = false) => {
  if (!userStore.token || userStore.role !== 'APPLICANT') {
    governanceStore.resetSummary()
    return
  }
  try {
    await governanceStore.fetchSummary('APPLICANT', {
      force,
      cacheMs: force ? 0 : 12000
    })
  } catch (error) {
    // 治理摘要失败时不打断前台主链路。
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
</script>

<style scoped>
.main-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background:
    radial-gradient(circle at 12% 0%, rgba(10, 132, 255, 0.1), transparent 32%),
    radial-gradient(circle at 88% 12%, rgba(52, 199, 89, 0.07), transparent 24%),
    radial-gradient(circle at 18% 100%, rgba(255, 159, 10, 0.06), transparent 28%),
    linear-gradient(180deg, #f7f7fb 0%, #f5f5f7 44%, #f7f6f2 100%);
  overflow-x: clip;
}

.header {
  background: transparent;
  position: sticky;
  top: 0;
  z-index: 100;
  padding-top: 12px;
}

.container {
  width: min(calc(100% - (var(--ui-shell-gutter) * 2)), var(--ui-main-shell-max-width));
  margin: 0 auto;
}

.header-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: var(--ui-header-height);
  gap: 12px 18px;
  padding: 10px 16px;
  border: 1px solid rgba(255, 255, 255, 0.74);
  border-radius: var(--ui-radius-lg);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.9), rgba(255, 255, 255, 0.8));
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.06);
  backdrop-filter: blur(16px);
  flex-wrap: wrap;
}

.logo-icon {
  color: var(--ui-accent);
}

.logo {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  font-weight: bold;
  font-size: 20px;
  color: var(--ui-text);
  flex: 0 1 auto;
  min-width: 0;
}

.logo-text {
  white-space: nowrap;
}

.nav-menu {
  display: flex;
  gap: 8px;
  flex: 1 1 520px;
  min-width: 0;
  flex-wrap: wrap;
  justify-content: center;
}

.nav-item {
  text-decoration: none;
  color: var(--ui-muted-strong);
  font-size: 16px;
  padding: 6px 14px;
  border-radius: 999px;
  position: relative;
  transition: color var(--ui-motion-slow) var(--ui-ease-standard);
  white-space: nowrap;
}

.nav-item:hover, .nav-item.active {
  color: var(--ui-accent);
  background: var(--ui-accent-light);
}

.nav-badge {
  display: inline-flex;
  align-items: center;
}

.nav-restricted-tip {
  display: inline-flex;
  align-items: center;
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(245, 158, 11, 0.12);
  color: #9a3412;
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
}

.nav-badge :deep(.el-badge__content) {
  transform: translate(4px, -6px);
}

.nav-item.active::after {
  content: none;
}

.user-area {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex: 0 1 auto;
  min-width: 0;
  margin-left: auto;
}

.main-content {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 16px 0 24px;
}

.footer {
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.74), rgba(255, 255, 255, 0.9));
  padding: 20px 0 24px;
  margin-top: auto;
  border-top: 1px solid rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(10px);
}

.copyright {
  text-align: center;
  color: var(--ui-muted);
  font-size: 14px;
}

@media (max-width: 1180px) {
  .header-content {
    gap: 10px 14px;
    padding: 12px 14px;
  }

  .nav-menu {
    order: 3;
    flex-basis: 100%;
    justify-content: flex-start;
  }

  .user-area {
    margin-left: 0;
  }
}

@media (max-width: 900px) {
  .logo-text {
    display: none;
  }

  .nav-menu {
    gap: 6px;
  }

  .nav-item {
    font-size: 14px;
    padding: 6px 12px;
  }

  .user-area :deep(.user-info-meta),
  .user-area :deep(.arrow-icon) {
    display: none;
  }

  .user-area :deep(.avatar-wrapper) {
    padding: 6px;
    border-radius: 14px;
  }
}

@media (max-width: 640px) {
  .header {
    padding-top: 10px;
  }

  .header-content {
    padding: 10px 12px;
    gap: 10px;
  }

  .nav-menu {
    justify-content: center;
  }

  .nav-item {
    padding: 6px 10px;
  }

  .main-content {
    padding: 14px 0 20px;
  }

  .footer {
    padding: 16px 0 20px;
  }
}
</style>

