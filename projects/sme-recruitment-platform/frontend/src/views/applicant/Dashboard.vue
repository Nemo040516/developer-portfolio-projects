<!--
文件速览：
1. 文件职责：求职者工作台首页，负责面试、沟通消息、竞争力洞察与职位推荐总览。
2. 页面入口：求职者路由 `/applicant/dashboard`。
3. 关键结构：dashboard-header、governance-banner、governance-stage-board、bento-grid、recommend-section。
4. 阅读建议：先看顶部工作台与平台提醒横幅的阶段分组，再看中部双核心卡片与洞察区，最后看推荐职位横向流。
-->
<template>
  <div class="dashboard-page">
    <!-- 1. 精简 Header (Apple Style) -->
    <header class="dashboard-header">
      <div class="greeting-box">
        <h1 class="welcome-text">{{ greeting }}, {{ displayName }}</h1>
        <p class="date-text">{{ currentDateText }}</p>
        <p class="lead-text">先跟进近期面试与沟通，再处理简历完善度和推荐职位。</p>
      </div>
      <el-button round class="quick-btn" @click="$router.push('/jobs')">
        <el-icon><Search /></el-icon> 探索新职位
      </el-button>
    </header>

    <section
      class="governance-banner"
      :class="{ 'governance-banner--active': governanceStore.pendingTotal > 0 }"
    >
      <div class="governance-banner__copy">
        <div class="governance-banner__eyebrow">平台提醒</div>
        <h2 class="governance-banner__title">{{ governanceBannerTitle }}</h2>
        <p class="governance-banner__desc">{{ governanceBannerDesc }}</p>
        <div class="governance-banner__stats">
          <span class="governance-banner__pill">待查看 {{ governanceStore.counts.unread || 0 }}</span>
          <span class="governance-banner__pill">待处理 {{ governanceStore.counts.pendingAction || 0 }}</span>
          <span class="governance-banner__pill">待复核 {{ governanceStore.counts.pendingReview || 0 }}</span>
        </div>
      </div>

      <div v-if="governancePrimaryNotice || governanceHasBuckets" class="governance-banner__focus">
        <article
          v-if="governancePrimaryNotice"
          class="governance-focus-card"
          :class="{ 'governance-focus-card--warning': governancePrimaryNotice.isOverdue || governancePrimaryNotice.severity === 'HIGH' }"
        >
          <div class="governance-focus-card__meta">
            <span>{{ noticeTypeText(governancePrimaryNotice.noticeType) }}</span>
            <span>{{ statusText(governancePrimaryNotice.status) }}</span>
          </div>
          <div class="governance-focus-card__title">{{ governancePrimaryNotice.title }}</div>
          <div class="governance-focus-card__summary">{{ governancePrimaryNotice.summary || '请进入平台提醒查看完整说明。' }}</div>
          <div class="governance-focus-card__footer">
            <span>{{ formatNoticeDeadline(governancePrimaryNotice) }}</span>
            <el-button link type="primary" @click="goToNotice(governancePrimaryNotice.id)">查看</el-button>
          </div>
        </article>

        <div class="governance-stage-board">
          <article
            v-for="group in governanceStageGroups"
            :key="group.key"
            class="governance-stage-card"
            :class="`governance-stage-card--${group.key}`"
          >
            <div class="governance-stage-card__head">
              <div>
                <div class="governance-stage-card__label">{{ group.label }}</div>
                <div class="governance-stage-card__hint">{{ group.hint }}</div>
              </div>
              <span class="governance-stage-card__count">{{ group.count }}</span>
            </div>

            <div v-if="group.items.length" class="governance-stage-card__list">
              <button
                v-for="item in group.items"
                :key="item.id"
                type="button"
                class="governance-stage-entry"
                @click="goToNotice(item.id)"
              >
                <span class="governance-stage-entry__title">{{ item.title }}</span>
                <span class="governance-stage-entry__meta">{{ formatStageItemMeta(item, group.key) }}</span>
              </button>
            </div>
            <div v-else class="governance-stage-card__empty">{{ group.emptyText }}</div>
          </article>
        </div>
      </div>

      <div v-else class="governance-banner__empty">
        <div class="governance-banner__empty-title">当前没有需要处理的平台提醒</div>
        <div class="governance-banner__empty-desc">如后续出现举报结果、账号提醒或封禁说明，系统会在这里优先提示你。</div>
        <el-button plain @click="$router.push('/applicant/notices')">进入平台提醒</el-button>
      </div>
    </section>

    <!-- 2. iCloud Bento Grid (双核心 + 洞察) -->
    <div class="bento-grid">
      <!-- 核心 A：面试日程 -->
      <div class="bento-item main-card interview-highlight">
        <div class="bento-header">
          <span class="bento-title">近期面试安排</span>
          <el-button link class="all-btn" @click="$router.push('/applicant/interviews')">
            日程 <el-icon><ArrowRight /></el-icon>
          </el-button>
        </div>
        <div v-loading="interviewLoading" class="card-body-wrap">
          <div v-if="upcomingInterviews.length" class="scroll-list">
            <div v-for="item in upcomingInterviews" :key="item.id" class="compact-pill">
              <span class="pill-time">{{ formatTimeOnly(item.scheduleTime) }}</span>
              <div class="pill-info">
                <div class="pill-job">{{ item.jobTitle }}</div>
                <div class="pill-company">{{ item.companyName }}</div>
              </div>
            </div>
          </div>
          <div v-else class="empty-compact">
            <el-icon size="32" color="#d1d1d6"><Calendar /></el-icon>
            <p>本周暂无面试</p>
          </div>
        </div>
      </div>

      <!-- 核心 B：最近沟通 -->
      <div class="bento-item main-card chat-highlight" @click="$router.push('/chat')">
        <div class="bento-header">
          <span class="bento-title">正在沟通消息</span>
          <el-button link class="all-btn">进入对话 <el-icon><ArrowRight /></el-icon></el-button>
        </div>
        <div v-loading="chatLoading" class="card-body-wrap">
          <div v-if="recentChats.length" class="scroll-list">
            <div v-for="chat in recentChats" :key="chat.id" class="compact-pill clickable-pill" @click.stop="goToChat(chat.peerId)">
              <el-avatar :size="32" :src="chat.peerAvatar || defaultAvatar" />
              <div class="pill-info">
                <div class="pill-job">{{ chat.peerName }}</div>
                <div class="pill-msg">{{ chat.lastMessage || '点击开始沟通' }}</div>
              </div>
            </div>
          </div>
          <div v-else class="empty-compact">
            <el-icon size="32" color="#d1d1d6"><ChatLineRound /></el-icon>
            <p>暂无新消息</p>
          </div>
        </div>
      </div>

      <!-- 中部：求职洞察 + 快捷工具 -->
      <div class="middle-insight-row">
        <!-- 竞争力周报 -->
        <div class="bento-item insight-card">
          <div class="insight-header">
            <div class="title-group">
              <span class="insight-title">求职竞争力报告</span>
              <div class="insight-status" v-if="insightData.totalViewed > 0">
                <span class="status-dot"></span>
                <span>表现活跃</span>
              </div>
            </div>
            <div class="insight-meta">
              被查看 <span class="highlight">{{ insightData.totalViewed }}</span> 次 · 排名提升 <span class="trend-up">↑{{ insightData.trend }}</span>
            </div>
          </div>
          
          <div class="insight-content">
            <div class="chart-grid-lines"><span></span><span></span><span></span></div>
            <div class="insight-chart">
              <div v-for="(val, index) in insightData.chartData" :key="index" class="chart-bar-wrap">
                <div class="bar-tooltip">{{ val }}</div>
                <div class="chart-bar" :style="{ height: getBarHeight(val) + '%' }" :class="{ top: val > 0 && val === Math.max(...insightData.chartData) }"></div>
                <span class="chart-day">{{ insightData.days[index] || '' }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 垂直统计磁贴 -->
        <div class="compact-tools">
          <div class="bento-item mini-pill applied" @click="$router.push('/applicant/applications')">
            <div class="tool-icon-box blue"><el-icon><Document /></el-icon></div>
            <div class="tool-info">
              <span class="tool-num">{{ dashboardStats.appliedCount || 0 }}</span>
              <span class="tool-txt">投递记录</span>
            </div>
          </div>
          <div class="bento-item mini-pill resume" @click="$router.push('/applicant/resume')">
            <div class="tool-icon-box blue is-soft"><el-icon><Edit /></el-icon></div>
            <div class="tool-info">
              <span class="tool-num">{{ resumeCompletionValue }}%</span>
              <span class="tool-txt">完善度</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 3. 横向推荐 -->
    <section class="recommend-section">
      <div class="section-bar">
        <h2>为你推荐的职位</h2>
        <div class="bar-line"></div>
        <div class="scroll-hint">横划探索 <el-icon><ArrowRight /></el-icon></div>
      </div>

      <div class="job-scroll-container" v-loading="loading">
        <div class="job-horizontal-list">
          <div v-for="job in recommendJobs" :key="job.id" class="job-mini-card" @click="toDetail(job.id)">
            <div class="job-header">
              <el-avatar :size="32" :src="formatFileUrl(job.companyLogo)" class="mini-logo" />
              <div class="job-info">
                <h3 class="job-name">{{ job.title }}</h3>
                <p class="job-company">{{ job.companyName }}</p>
              </div>
            </div>
            <div class="job-tags">
              <span>{{ job.workLocation }}</span>
              <span>{{ job.experience }}</span>
            </div>
            <div class="job-footer">
              <span class="job-salary">{{ formatSalaryK(job.minSalary, job.maxSalary) }}</span>
              <el-icon><ArrowRight /></el-icon>
            </div>
          </div>
          <div class="scroll-spacer"></div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
/*
文件速览：
1. 文件职责：承载求职者工作台的面试、沟通、竞争力、职位推荐与平台提醒聚焦展示。
2. 对外入口：fetchStats、fetchRecentChats、fetchRecommendJobs、fetchInterviewOverview、goToNotice。
3. 关键结构：governancePrimaryNotice、governanceStageGroups、upcomingInterviews、recentChats、insightData。
4. 阅读建议：先看平台提醒 banner 的聚焦事项与三段分组，再看面试 / 沟通 / 推荐职位数据拉取。
*/
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { getDashboardStats, getMyApplications, getInsightStats } from '@/api/applicant'
import { getInterviewList } from '@/api/interview'
import { searchJobs } from '@/api/job'
import { getChatSessionList } from '@/api/chat'
import { formatFileUrl } from '@/utils/file'
import { formatDateLongCN, formatSalaryK, formatTimeHM } from '@/utils/format'
import { useUserStore } from '@/stores/user'
import { useGovernanceStore } from '@/stores/governance'
import { 
  Document, ArrowRight, Search, Calendar, ChatLineRound, Edit 
} from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()
const governanceStore = useGovernanceStore()
const defaultAvatar = 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'

const dashboardStats = ref({})
const recommendJobs = ref([])
const recentChats = ref([])
const insightData = ref({ chartData: [0,0,0,0,0,0,0], totalViewed: 0, trend: '+0%', days: [] })
const loading = ref(false)
const interviewLoading = ref(false)
const chatLoading = ref(false)
const upcomingInterviews = ref([])

const displayName = computed(() => userStore.userInfo?.realName || userStore.userInfo?.nickname || userStore.userInfo?.username || '求职者')
const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '凌晨好'; if (h < 11) return '上午好'; if (h < 13) return '中午好'; if (h < 18) return '下午好'; return '晚上好'
})
const currentDateText = computed(() => formatDateLongCN(new Date()))
const resumeCompletionValue = computed(() => {
  const v = dashboardStats.value?.resumeCompleteness ?? dashboardStats.value?.resumeCompletion
  return Number.isFinite(v) ? v : 0
})
const governancePrimaryNotice = computed(() => governanceStore.primaryNotice)
const governanceReadCount = computed(() => Number(governanceStore.counts.unread || 0))
const governanceActionCount = computed(() =>
  Math.max(Number(governanceStore.counts.pendingAction || 0), governanceStore.workbenchBuckets.action.length)
)
const governanceReviewCount = computed(() => Number(governanceStore.counts.pendingReview || 0))
const governanceStageGroups = computed(() => {
  const primaryId = Number(governancePrimaryNotice.value?.id || 0)
  const filterPrimary = (items) => items.filter((item) => Number(item.id) !== primaryId).slice(0, 2)
  return [
    {
      key: 'read',
      label: '先读说明',
      hint: '先理解平台说明，再决定要不要继续动作。',
      count: governanceReadCount.value,
      items: filterPrimary(governanceStore.workbenchBuckets.read),
      emptyText: '当前没有新的待查看提醒'
    },
    {
      key: 'action',
      label: '需要你说明',
      hint: '这类事项需要补充说明、申诉或继续跟进。',
      count: governanceActionCount.value,
      items: filterPrimary(governanceStore.workbenchBuckets.action),
      emptyText: '当前没有需要你补充的事项'
    },
    {
      key: 'review',
      label: '平台处理中',
      hint: '你已反馈，当前只需留意平台后续结果。',
      count: governanceReviewCount.value,
      items: filterPrimary(governanceStore.workbenchBuckets.review),
      emptyText: '当前没有等待平台复核的事项'
    }
  ]
})
const governanceHasBuckets = computed(() =>
  governanceStageGroups.value.some((item) => item.items.length > 0 || item.count > 0)
)
const governanceBannerTitle = computed(() => {
  if (!governanceStore.pendingTotal) {
    return '平台提醒已清空，可以按节奏推进面试、沟通与简历优化。'
  }
  return `当前有 ${governanceStore.pendingTotal} 项平台提醒需要你优先查看`
})
const governanceBannerDesc = computed(() => {
  if (!governanceStore.pendingTotal) {
    return '系统仍会继续跟踪举报结果、账号状态和治理反馈，一旦有变更会第一时间同步。'
  }
  const unread = governanceReadCount.value
  const pendingAction = governanceActionCount.value
  const pendingReview = governanceReviewCount.value
  return `建议先确认 ${unread} 项待查看提醒，再处理 ${pendingAction} 项需要说明的事项；另有 ${pendingReview} 项正在等待平台复核。`
})

const fetchStats = async () => {
  try {
    const res = await getDashboardStats()
    if (res.code === 200) dashboardStats.value = res.data
  } catch (e) {}
}

const fetchInsight = async () => {
  try {
    const res = await getInsightStats()
    if (res.code === 200 && res.data) insightData.value = res.data
  } catch (e) {}
}

const getBarHeight = (val) => {
  const max = Math.max(...insightData.value.chartData, 5)
  return (val / max) * 100
}

const fetchRecentChats = async () => {
  chatLoading.value = true
  try {
    const res = await getChatSessionList()
    const list = res.data?.records || res.data || []
    recentChats.value = list.slice(0, 10).map(s => ({
      id: s.id || s.peerId, peerId: s.peerId, peerName: s.peerName,
      peerAvatar: s.peerAvatar, lastMessage: s.lastMessage || '点击开始沟通'
    }))
  } catch (e) {} finally { chatLoading.value = false }
}

const goToChat = (peerId) => { router.push({ path: '/chat', query: { targetId: peerId } }) }
const goToNotice = (noticeId) => {
  router.push({
    path: '/applicant/notices',
    query: noticeId ? { noticeId: String(noticeId) } : undefined
  })
}

const noticeTypeText = (value) => {
  const map = {
    REPORT_RESULT: '举报结果',
    USER_WARNING: '用户警告',
    BAN_NOTICE: '封禁通知'
  }
  return map[value] || '平台提醒'
}

const statusText = (value) => {
  const map = {
    PENDING_READ: '待查看',
    PENDING_ACTION: '待处理',
    PENDING_REVIEW: '待复核',
    FINISHED: '已完成',
    REJECTED: '已驳回',
    EXPIRED: '已失效',
    CLOSED: '已关闭'
  }
  return map[value] || '处理中'
}

const formatNoticeDeadline = (item) => {
  if (item?.isOverdue) return '已超过处理时限'
  if (!item?.dueTime) return '暂无截止时间'
  return `截止 ${String(item.dueTime).slice(0, 16).replace('T', ' ')}`
}

const formatStageItemMeta = (item, groupKey) => {
  if (!item) return '请进入平台提醒查看详情'
  if (groupKey === 'review') {
    return item.latestActionTime
      ? `最近更新 ${String(item.latestActionTime).slice(5, 16).replace('T', ' ')}`
      : '等待平台继续处理'
  }
  if (item.isOverdue) return '已超过处理时限'
  if (item.dueTime) return `截止 ${String(item.dueTime).slice(5, 16).replace('T', ' ')}`
  return item.summary || '请进入平台提醒查看详情'
}

const fetchRecommendJobs = async () => {
  loading.value = true
  try {
    const res = await searchJobs({ current: 1, size: 20 })
    const records = res.data.records || []
    recommendJobs.value = records.sort(() => Math.random() - 0.5).slice(0, 8)
  } catch (e) {} finally { loading.value = false }
}

const toDetail = (id) => {
  const routeData = router.resolve({ path: `/job/detail/${id}` })
  window.open(routeData.href, '_blank')
}

const formatTimeOnly = (v) => formatTimeHM(v)

const fetchInterviewOverview = async () => {
  interviewLoading.value = true
  try {
    const res = await getMyApplications({ current: 1, size: 10, status: 2 })
    const records = res.data?.records || []
    const tasks = records.map(async (row) => {
      try {
        const response = await getInterviewList(row.id)
        return (response.data || []).map(i => ({ ...i, jobTitle: row.jobTitle, companyName: row.companyName }))
      } catch (e) { return [] }
    })
    const result = await Promise.all(tasks)
    upcomingInterviews.value = result.flat()
      .filter(i => new Date(i.scheduleTime.replace('T', ' ')).getTime() >= Date.now())
      .sort((a, b) => new Date(a.scheduleTime).getTime() - new Date(b.scheduleTime).getTime())
      .slice(0, 5)
  } catch (e) {} finally { interviewLoading.value = false }
}

onMounted(() => { fetchStats(); fetchRecommendJobs(); fetchInterviewOverview(); fetchRecentChats(); fetchInsight() })
</script>

<style scoped>
.dashboard-page {
  width: min(calc(100% - (var(--ui-shell-gutter) * 2)), var(--ui-main-shell-max-width-wide));
  margin: 0 auto;
  padding: 24px 0 60px;
}

/* --- 1. Header --- */
.dashboard-header { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; flex-wrap: wrap; margin-bottom: 24px; }
.greeting-box { display: flex; flex-direction: column; gap: 4px; max-width: min(720px, 100%); }
.welcome-text { font-size: 26px; font-weight: 700; color: #1d1d1f; letter-spacing: -0.6px; margin: 0; }
.date-text { font-size: 13px; color: #86868b; margin-top: 2px; }
.lead-text {
  margin: 4px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: #6e6e73;
}
.quick-btn {
  background: #fff;
  border: 1px solid #d2d2d7;
  font-weight: 500;
  transition: all var(--ui-motion-base) var(--ui-ease-standard);
}
.quick-btn:hover { border-color: var(--ui-accent); color: var(--ui-accent); }

.governance-banner {
  display: grid;
  grid-template-columns: minmax(320px, 1.05fr) minmax(0, 1fr);
  gap: 18px;
  margin-bottom: 26px;
  padding: 20px 22px;
  border-radius: 26px;
  border: 1px solid rgba(15, 23, 42, 0.06);
  background:
    radial-gradient(circle at top left, rgba(10, 132, 255, 0.08), transparent 30%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(249, 250, 251, 0.94));
  box-shadow: 0 16px 34px rgba(15, 23, 42, 0.05);
}

.governance-banner--active {
  border-color: rgba(10, 132, 255, 0.16);
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.08);
}

.governance-banner__copy {
  display: grid;
  gap: 12px;
  align-content: start;
}

.governance-banner__eyebrow {
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #6b7280;
  font-weight: 700;
}

.governance-banner__title {
  margin: 0;
  font-size: 22px;
  line-height: 1.4;
  color: #1d1d1f;
}

.governance-banner__desc {
  margin: 0;
  color: #6e6e73;
  line-height: 1.75;
}

.governance-banner__stats {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.governance-banner__pill {
  padding: 8px 12px;
  border-radius: 999px;
  border: 1px solid rgba(10, 132, 255, 0.12);
  background: rgba(255, 255, 255, 0.88);
  font-size: 13px;
  font-weight: 600;
  color: #1d1d1f;
}

.governance-banner__focus {
  display: grid;
  gap: 12px;
}

.governance-stage-board {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.governance-focus-card {
  display: grid;
  gap: 8px;
  padding: 15px 16px;
  border-radius: 18px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  background: rgba(255, 255, 255, 0.9);
}

.governance-focus-card--warning {
  border-color: rgba(245, 158, 11, 0.18);
  background:
    radial-gradient(circle at top left, rgba(245, 158, 11, 0.07), transparent 28%),
    rgba(255, 255, 255, 0.96);
}

.governance-focus-card__meta,
.governance-focus-card__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  font-size: 12px;
  color: #8e8e93;
}

.governance-focus-card__title {
  font-size: 15px;
  font-weight: 700;
  color: #1d1d1f;
}

.governance-focus-card__summary {
  color: #6e6e73;
  line-height: 1.65;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.governance-stage-card {
  display: grid;
  gap: 10px;
  padding: 14px 15px;
  border-radius: 18px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  background: rgba(255, 255, 255, 0.9);
}

.governance-stage-card--read {
  background:
    radial-gradient(circle at top left, rgba(59, 130, 246, 0.08), transparent 24%),
    rgba(255, 255, 255, 0.94);
}

.governance-stage-card--action {
  background:
    radial-gradient(circle at top left, rgba(245, 158, 11, 0.1), transparent 24%),
    rgba(255, 255, 255, 0.94);
}

.governance-stage-card--review {
  background:
    radial-gradient(circle at top left, rgba(16, 185, 129, 0.08), transparent 24%),
    rgba(255, 255, 255, 0.94);
}

.governance-stage-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.governance-stage-card__label {
  font-size: 13px;
  font-weight: 700;
  color: #1d1d1f;
}

.governance-stage-card__hint {
  margin-top: 2px;
  font-size: 11px;
  line-height: 1.55;
  color: #8e8e93;
}

.governance-stage-card__count {
  min-width: 28px;
  height: 28px;
  display: inline-grid;
  place-items: center;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.84);
  border: 1px solid rgba(148, 163, 184, 0.16);
  font-size: 12px;
  font-weight: 700;
  color: #1d1d1f;
}

.governance-stage-card__list {
  display: grid;
  gap: 8px;
}

.governance-stage-entry {
  width: 100%;
  display: grid;
  gap: 3px;
  padding: 10px 12px;
  text-align: left;
  border-radius: 14px;
  border: 1px solid rgba(148, 163, 184, 0.14);
  background: rgba(255, 255, 255, 0.84);
  cursor: pointer;
  transition: transform 0.22s ease, border-color 0.22s ease;
}

.governance-stage-entry:hover {
  transform: translateY(-1px);
  border-color: rgba(0, 113, 227, 0.18);
}

.governance-stage-entry__title {
  font-size: 13px;
  font-weight: 700;
  color: #1d1d1f;
  line-height: 1.45;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.governance-stage-entry__meta {
  font-size: 11px;
  color: #8e8e93;
  line-height: 1.55;
}

.governance-stage-card__empty {
  min-height: 72px;
  display: grid;
  place-content: center;
  text-align: center;
  padding: 12px;
  border-radius: 14px;
  border: 1px dashed rgba(148, 163, 184, 0.18);
  color: #9ca3af;
  font-size: 11px;
  line-height: 1.6;
}

.governance-banner__empty {
  min-height: 150px;
  border-radius: 20px;
  border: 1px dashed rgba(148, 163, 184, 0.24);
  background: rgba(255, 255, 255, 0.72);
  display: grid;
  place-content: center;
  gap: 8px;
  text-align: center;
  padding: 18px;
}

.governance-banner__empty-title {
  font-size: 16px;
  font-weight: 700;
  color: #1d1d1f;
}

.governance-banner__empty-desc {
  color: #8e8e93;
  line-height: 1.7;
}

/* --- 2. Bento Grid --- */
.bento-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(0, 0.92fr);
  gap: clamp(18px, 1.6vw, 24px);
  margin-bottom: 40px;
  align-items: stretch;
}

.bento-item {
  background: #ffffff;
  border-radius: 24px;
  border: 1px solid rgba(0, 0, 0, 0.03);
  box-shadow: 0 2px 4px rgba(0,0,0,0.02), 0 10px 20px rgba(0,0,0,0.04);
  transition: all var(--ui-motion-slow) var(--ui-ease-decelerate);
  display: flex;
  overflow: hidden;
  position: relative;
}

.bento-item::after {
  content: ''; position: absolute; width: 120px; height: 120px;
  background: radial-gradient(circle, rgba(0, 113, 227, 0.03) 0%, transparent 70%);
  bottom: -40px; right: -40px; pointer-events: none;
}

.bento-item:hover { transform: translateY(-2px); box-shadow: 0 12px 32px rgba(0, 0, 0, 0.06); }

.main-card { min-height: 236px; padding: 20px 24px; flex-direction: column; }
.bento-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.bento-title { font-size: 13px; font-weight: 700; color: #86868b; text-transform: uppercase; letter-spacing: 0.8px; }
.all-btn { font-size: 12px; }

.card-body-wrap { flex: 1; overflow-y: auto; padding-right: 4px; }
.card-body-wrap::-webkit-scrollbar { width: 4px; }
.card-body-wrap::-webkit-scrollbar-thumb { background: rgba(0,0,0,0.05); border-radius: 10px; }

.compact-pill {
  display: flex; align-items: center; gap: 14px; padding: 12px 16px;
  background: #fbfbfd; border-radius: 16px; margin-bottom: 10px;
  border: 1px solid rgba(0,0,0,0.02); transition: all var(--ui-motion-fast) var(--ui-ease-standard);
}
.clickable-pill { cursor: pointer; }
.clickable-pill:hover { background: #f2f2f7; transform: translateX(2px); }

.pill-time { font-size: 13px; font-weight: 700; color: #007aff; }
.pill-info { flex: 1; min-width: 0; }
.pill-job { font-size: 14px; font-weight: 600; color: #1d1d1f; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.pill-company, .pill-msg { font-size: 12px; color: #86868b; margin-top: 2px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

/* 洞察与工具 */
.middle-insight-row {
  grid-column: 1 / -1;
  display: grid;
  grid-template-columns: minmax(0, 1.7fr) minmax(280px, 0.92fr);
  gap: clamp(18px, 1.6vw, 24px);
  height: 180px;
}

.insight-card { padding: 16px 24px; flex-direction: column; background: linear-gradient(160deg, #ffffff 0%, #f9f9fb 100%); }
.title-group { display: flex; align-items: center; gap: 8px; }
.insight-status { display: flex; align-items: center; gap: 4px; background: rgba(52, 199, 89, 0.08); padding: 2px 8px; border-radius: 999px; font-size: 10px; color: #34c759; font-weight: 600; }
.status-dot { width: 4px; height: 4px; border-radius: 50%; background: #34c759; }

.insight-meta { font-size: 12px; color: #86868b; }
.highlight { color: #007aff; font-weight: 700; }
.trend-up { color: #34c759; font-weight: 600; }

.insight-content { flex: 1; position: relative; margin-top: 12px; display: flex; flex-direction: column; }
.chart-grid-lines { position: absolute; top: 0; left: 0; right: 0; bottom: 20px; display: flex; flex-direction: column; justify-content: space-between; }
.chart-grid-lines span { width: 100%; height: 1px; background: rgba(0,0,0,0.03); }

.insight-chart { height: 80px; display: flex; align-items: flex-end; justify-content: space-between; z-index: 1; padding: 0 4px; }
.chart-bar-wrap { display: flex; flex-direction: column; align-items: center; justify-content: flex-end; height: 100%; flex: 1; position: relative; }
.chart-bar { width: 18px; background: linear-gradient(180deg, #007aff 0%, #5ac8fa 100%); border-radius: 4px 4px 2px 2px; opacity: 0.9; margin-bottom: 20px; }
.chart-bar.top { background: linear-gradient(180deg, #2563eb 0%, #7dd3fc 100%); opacity: 1; }
.chart-day { position: absolute; bottom: 0; font-size: 10px; color: #aeaeb2; font-weight: 600; }

.bar-tooltip { position: absolute; top: -10px; background: #1d1d1f; color: #fff; font-size: 10px; padding: 2px 6px; border-radius: 4px; opacity: 0; transition: all var(--ui-motion-fast) var(--ui-ease-standard); }
.chart-bar-wrap:hover .bar-tooltip { opacity: 1; transform: translateY(-4px); }

/* 侧边工具 */
.compact-tools { display: flex; flex-direction: column; gap: 16px; }
.mini-pill { flex: 1; padding: 0 20px; align-items: center; gap: 16px; }
.tool-icon-box { width: 40px; height: 40px; border-radius: 12px; display: flex; align-items: center; justify-content: center; font-size: 20px; }
.tool-icon-box.blue { background: rgba(0, 122, 255, 0.08); color: #007aff; }
.tool-icon-box.is-soft { background: rgba(10, 132, 255, 0.06); color: #2563eb; }
.tool-num { font-size: 20px; font-weight: 700; color: #1d1d1f; display: block; line-height: 1; }
.tool-txt { font-size: 11px; color: #86868b; margin-top: 2px; }

/* 推荐 */
.recommend-section { margin-top: 28px; }
.section-bar { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.section-bar h2 { font-size: 18px; font-weight: 700; color: #1d1d1f; margin: 0; letter-spacing: -0.3px; }
.bar-line { flex: 1; height: 1px; background: #f2f2f7; }
.scroll-hint { font-size: 11px; color: #aeaeb2; }

.job-scroll-container { width: 100%; overflow-x: auto; padding-bottom: 10px; }
.job-scroll-container::-webkit-scrollbar { display: none; }
.job-horizontal-list { display: flex; gap: 16px; scroll-snap-type: x mandatory; padding: 4px 2px; }
.job-mini-card { flex: 0 0 252px; scroll-snap-align: start; background: #fff; border-radius: 20px; padding: 16px; box-shadow: 0 2px 4px rgba(0,0,0,0.02), 0 8px 16px rgba(0,0,0,0.04); cursor: pointer; transition: all var(--ui-motion-slow) var(--ui-ease-standard); display: flex; flex-direction: column; gap: 12px; }
.job-mini-card:hover { transform: translateY(-4px) scale(1.02); border-color: #007aff; }
.job-header { display: flex; gap: 10px; align-items: center; }
.mini-logo { border-radius: 8px; border: 1px solid #f2f2f7; }
.job-name { font-size: 15px; font-weight: 600; margin: 0; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.job-company { font-size: 12px; color: #86868b; margin-top: 1px; }
.job-tags { display: flex; gap: 6px; }
.job-tags span { font-size: 10px; color: #86868b; background: #f5f5f7; padding: 3px 8px; border-radius: 6px; }
.job-footer { display: flex; justify-content: space-between; align-items: center; margin-top: auto; padding-top: 10px; border-top: 1px solid #fbfbfd; }
.job-salary { font-size: 16px; font-weight: 700; color: #0a84ff; }

.empty-compact { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; color: #aeaeb2; font-size: 13px; gap: 8px; }

@media (min-width: 1480px) {
  .bento-grid {
    grid-template-columns: minmax(0, 1.04fr) minmax(0, 1.04fr) minmax(300px, 0.9fr);
    grid-auto-flow: dense;
  }

  .interview-highlight,
  .chat-highlight {
    min-height: 296px;
  }

  .middle-insight-row {
    grid-column: 3;
    grid-row: 1;
    grid-template-columns: 1fr;
    grid-template-rows: minmax(0, 1fr) auto;
    height: auto;
    align-self: stretch;
  }

  .insight-card {
    min-height: 296px;
  }

  .compact-tools {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 14px;
  }

  .mini-pill {
    min-height: 112px;
  }

  .recommend-section {
    margin-top: 18px;
  }

  .section-bar {
    margin-bottom: 18px;
  }

  .scroll-hint {
    display: none;
  }

  .job-scroll-container {
    overflow: visible;
    padding-bottom: 0;
  }

  .job-horizontal-list {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 18px;
    padding: 0;
    scroll-snap-type: none;
  }

  .job-mini-card {
    min-width: 0;
    min-height: 200px;
    flex: none;
  }

  .scroll-spacer {
    display: none;
  }
}

@media (max-width: 1100px) {
  .governance-banner {
    grid-template-columns: 1fr;
  }

  .governance-stage-board {
    grid-template-columns: 1fr;
  }

  .middle-insight-row {
    grid-template-columns: 1fr;
    height: auto;
  }

  .compact-tools {
    flex-direction: row;
  }

  .mini-pill {
    min-height: 88px;
  }
}

@media (max-width: 800px) {
  .dashboard-header {
    align-items: stretch;
  }

  .governance-banner {
    padding: 18px;
  }

  .governance-stage-board {
    grid-template-columns: 1fr;
  }

  .bento-grid {
    grid-template-columns: 1fr;
  }

  .middle-insight-row {
    grid-template-columns: 1fr;
  }

  .compact-tools {
    flex-direction: column;
  }

  .job-horizontal-list {
    gap: 12px;
  }
}
</style>

