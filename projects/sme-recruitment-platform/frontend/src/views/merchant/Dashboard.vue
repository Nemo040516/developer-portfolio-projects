<!--
文件速览：
1. 文件职责：商家工作台首页，负责招聘漏斗、任务中心、风险预警、面试概览与治理提醒聚焦。
2. 页面入口：商家路由 `/merchant/dashboard`。
3. 关键结构：onboarding-wrapper、governance-spotlight、merchant-rectify-focus、governance-lane-board、task-center-card。
4. 阅读建议：先看新手引导与治理提醒区的阶段分组，再看企业资料整改聚焦卡、任务中心、风险预警和面试概览。
-->
<template>
  <div class="dashboard-container" v-loading="loading">

    <!-- 场景 A: 新手引导 (未完善资料) -->
    <div v-if="merchantStatus.isNew" class="onboarding-wrapper">
      <div class="welcome-box">
        <div class="text-center mb-8">
          <h1 class="text-3xl font-bold text-gray-800 mb-2">欢迎加入，开启招聘管理</h1>
          <p class="text-gray-500">完成 3 步即可发布职位并获取候选人。</p>
        </div>

        <el-steps :active="1" finish-status="success" align-center class="mb-12">
          <el-step title="注册账号" description="已完成" />
          <el-step title="完善企业信息" description="当前步骤" />
          <el-step title="发布首个职位" description="待进行" />
        </el-steps>

        <el-card shadow="hover" class="task-card border-none">
          <div class="flex items-center gap-6">
            <div class="icon-box bg-blue-50 text-blue-500">
              <el-icon size="32"><School /></el-icon>
            </div>
            <div class="flex-1">
              <h3 class="text-lg font-bold">第一步：完善企业资料</h3>
              <p class="text-gray-500 text-sm mt-1">
                求职者需要了解您的公司。请完善公司名称、规模、Logo 等基础信息，以便通过平台审核。
              </p>
            </div>
            <el-button type="primary" size="large" round @click="$router.push('/merchant/company')">
              去完善资料 <el-icon class="el-icon--right"><ArrowRight /></el-icon>
            </el-button>
          </div>
        </el-card>

        <div class="mt-8 text-center">
          <el-alert
            title="温馨提示：未完善企业信息的账号无法发布职位。"
            type="warning"
            center
            show-icon
            :closable="false"
          />
        </div>
      </div>
    </div>

    <!-- 场景 B: 正常工作台 -->
    <div v-else>
      <el-alert
        v-if="merchantStatus.auditStatus === 0"
        title="您的企业资料正在审核中，审核通过后职位将对外可见。"
        type="info" show-icon class="mb-4" />

      <section
        class="governance-spotlight mb-6"
        :class="{ 'governance-spotlight--active': governancePendingTotal > 0 }"
      >
        <div class="governance-spotlight__hero">
          <div class="governance-spotlight__hero-main">
            <div class="governance-spotlight__eyebrow">平台治理提醒</div>
            <h2 class="governance-spotlight__title">{{ governanceHeroTitle }}</h2>
            <p class="governance-spotlight__desc">{{ governanceHeroDesc }}</p>
            <div class="governance-spotlight__stats">
              <span class="governance-stat-pill">待查看 {{ governanceCounts.unread || 0 }}</span>
              <span class="governance-stat-pill">待处理 {{ governanceCounts.pendingAction || 0 }}</span>
              <span class="governance-stat-pill">待复核 {{ governanceCounts.pendingReview || 0 }}</span>
            </div>
            <div class="governance-spotlight__guide">
              <span class="governance-guide-step governance-guide-step--read">1. 先读平台说明</span>
              <span class="governance-guide-step governance-guide-step--action">2. 处理整改动作</span>
              <span class="governance-guide-step governance-guide-step--review">3. 等待平台复核</span>
            </div>
          </div>

          <div class="governance-spotlight__hero-side">
            <div class="governance-overview-card">
              <div class="governance-overview-card__eyebrow">处理建议</div>
              <div class="governance-overview-card__title">{{ governanceQuickFocus.title }}</div>
              <div class="governance-overview-card__desc">{{ governanceQuickFocus.desc }}</div>

              <div class="governance-overview-card__metrics">
                <div class="governance-overview-metric">
                  <span class="governance-overview-metric__label">待查看</span>
                  <strong class="governance-overview-metric__value">{{ governanceReadCount }}</strong>
                </div>
                <div class="governance-overview-metric">
                  <span class="governance-overview-metric__label">待处理</span>
                  <strong class="governance-overview-metric__value">{{ governanceActionCount }}</strong>
                </div>
                <div class="governance-overview-metric">
                  <span class="governance-overview-metric__label">待复核</span>
                  <strong class="governance-overview-metric__value">{{ governanceReviewCount }}</strong>
                </div>
              </div>

              <div class="governance-overview-card__actions">
                <el-button type="primary" @click="goTo('/merchant/governance')">进入通知中心</el-button>
                <el-button v-if="merchantRectifyFocus" plain @click="goToCompanyRectify">处理重点项</el-button>
              </div>
            </div>
          </div>
        </div>

        <div
          v-if="merchantRectifyFocus || governanceHasLaneItems"
          class="governance-spotlight__panel"
          :class="{ 'governance-spotlight__panel--lanes-only': !merchantRectifyFocus }"
        >
          <article
            v-if="merchantRectifyFocus"
            class="governance-focus-card"
            :class="`governance-focus-card--${merchantRectifyTone}`"
          >
            <div class="governance-focus-card__head">
              <div class="governance-focus-card__meta">
                <span class="governance-focus-card__eyebrow">企业资料整改</span>
                <span class="governance-focus-card__stage">{{ merchantRectifyStageText }}</span>
              </div>
              <el-tag :type="statusTag(merchantRectifyFocus.status)" round>{{ statusText(merchantRectifyFocus.status) }}</el-tag>
            </div>
            <div class="governance-focus-card__title">{{ merchantRectifyFocus.title }}</div>
            <div class="governance-focus-card__desc">{{ merchantRectifyDetailText }}</div>
            <div class="governance-focus-card__grid">
              <div class="governance-focus-card__fact">
                <span class="governance-focus-card__fact-label">当前重点</span>
                <span class="governance-focus-card__fact-value">{{ merchantRectifyNextStep }}</span>
              </div>
              <div class="governance-focus-card__fact">
                <span class="governance-focus-card__fact-label">处理时限</span>
                <span class="governance-focus-card__fact-value">{{ merchantRectifyDeadlineText }}</span>
              </div>
            </div>
            <div class="governance-focus-card__footer">
              <span class="governance-focus-card__hint">{{ merchantRectifyFooterHint }}</span>
              <div class="governance-focus-card__actions">
                <el-button type="primary" @click="goToCompanyRectify">去修改企业资料</el-button>
                <el-button plain @click="goToGovernanceNotice(merchantRectifyFocus.id)">查看通知</el-button>
              </div>
            </div>
          </article>

          <div class="governance-lane-board">
            <article
              v-for="lane in governanceLaneGroups"
              :key="lane.key"
              class="governance-lane-card"
              :class="`governance-lane-card--${lane.key}`"
            >
              <div class="governance-lane-card__head">
                <div class="governance-lane-card__summary">
                  <div class="governance-lane-card__label">{{ lane.label }}</div>
                  <div class="governance-lane-card__hint">{{ lane.hint }}</div>
                </div>
                <span class="governance-lane-card__count">{{ lane.count }}</span>
              </div>

              <div v-if="lane.items.length" class="governance-lane-card__list">
                <button
                  v-for="item in lane.items"
                  :key="item.id"
                  type="button"
                  class="governance-lane-entry"
                  @click="goToGovernanceNotice(item.id)"
                >
                  <span class="governance-lane-entry__type">{{ noticeTypeText(item.noticeType) }}</span>
                  <span class="governance-lane-entry__title">{{ item.title }}</span>
                  <span class="governance-lane-entry__meta">{{ formatGovernanceLaneMeta(item, lane.key) }}</span>
                </button>
              </div>
              <div v-else class="governance-lane-card__empty">{{ lane.emptyText }}</div>

              <div class="governance-lane-card__footer">
                <span class="governance-lane-card__footer-copy">{{ lane.footer }}</span>
                <el-button
                  link
                  type="primary"
                  class="governance-lane-card__footer-action"
                  @click="goToGovernanceNotice(lane.items[0]?.id)"
                >
                  {{ lane.actionText }}
                </el-button>
              </div>
            </article>
          </div>
        </div>

        <div v-else class="governance-spotlight__empty">
          <div class="governance-spotlight__empty-title">当前没有新的平台整改事项</div>
          <div class="governance-spotlight__empty-desc">工作台会在商家资料整改、职位整改或举报结果处理后自动同步提醒。</div>
          <el-button plain @click="$router.push('/merchant/governance')">查看通知中心</el-button>
        </div>
      </section>

      <el-card shadow="never" class="quick-action-card mb-6">
        <template #header>
          <div class="flex justify-between items-center">
            <span class="font-bold text-gray-700">快捷操作</span>
            <el-tag size="small" type="info">高频入口</el-tag>
          </div>
        </template>
        <div class="quick-action-grid">
          <el-button class="quick-action-btn" type="primary" @click="$router.push('/merchant/jobs')">发布职位</el-button>
          <el-button class="quick-action-btn" @click="$router.push('/merchant/resumes')">查看新投递</el-button>
          <el-button class="quick-action-btn" @click="$router.push('/merchant/interviews')">发起面试</el-button>
          <el-button class="quick-action-btn" @click="$router.push('/merchant/chat')">进入沟通</el-button>
        </div>
      </el-card>

      <el-row :gutter="20" class="mb-6" v-loading="statsLoading">
        <el-col :span="6" v-for="(item, index) in coreMetrics" :key="index">
          <el-card shadow="hover" class="stat-card" :class="item.colorClass">
            <div class="flex justify-between items-start">
              <div>
                <div class="text-white/80 text-sm">{{ item.label }}</div>
                <div v-if="item.subLabel" class="text-white/60 text-xs mt-1">{{ item.subLabel }}</div>
                <div class="text-3xl font-bold text-white mt-2">{{ formatNumber(item.value) }}</div>
              </div>
              <el-icon class="text-white/50 text-4xl"><component :is="item.icon" /></el-icon>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="16">
          <el-card shadow="never" class="h-full task-center-card" v-loading="statsLoading">
            <template #header>
              <div class="flex justify-between items-center">
                <span class="font-bold text-gray-700">今日任务中心</span>
                <el-radio-group v-model="funnelRange" size="small">
                  <el-radio-button value="7天">7天</el-radio-button>
                  <el-radio-button value="30天">30天</el-radio-button>
                </el-radio-group>
              </div>
            </template>
            <div class="task-summary">
              <div class="summary-main">
                当前待优先处理事项：
                <span class="summary-count">{{ formatNumber(urgentTaskCount) }}</span>
                项
              </div>
              <el-button size="small" @click="$router.push('/merchant/resumes')">进入候选人处理台</el-button>
            </div>

            <div class="todo-list">
              <div v-for="item in todoItems" :key="item.key" class="todo-item">
                <div class="todo-main">
                  <div class="todo-title-line">
                    <div class="todo-title">{{ item.title }}</div>
                    <el-tag size="small" :type="item.priorityTag">{{ item.priorityLabel }}</el-tag>
                  </div>
                  <div class="todo-desc">{{ item.desc }}</div>
                </div>
                <div class="todo-right">
                  <span class="todo-eta">约 {{ item.etaText }}</span>
                  <el-tag :type="item.count > 0 ? 'danger' : 'info'">{{ formatNumber(item.count) }}</el-tag>
                  <el-button size="small" :type="item.count > 0 ? 'primary' : 'default'" @click="goTo(item.route)">
                    {{ item.actionText }}
                  </el-button>
                </div>
              </div>
            </div>
          </el-card>
        </el-col>

        <el-col :span="8">
          <div class="right-stack">
            <el-card shadow="never" class="job-status-card" v-loading="statsLoading">
              <template #header>
                <div class="flex justify-between items-center">
                  <span class="font-bold text-gray-700">风险预警</span>
                  <el-button size="small" @click="$router.push('/merchant/jobs')">处理职位</el-button>
                </div>
              </template>
              <div class="warning-list">
                <div v-for="warning in warningItems" :key="warning.key" class="warning-item">
                  <div class="warning-main">
                    <div class="warning-title">{{ warning.title }}</div>
                    <div class="warning-desc">{{ warning.desc }}</div>
                  </div>
                  <div class="warning-right">
                    <el-tag :type="warning.count > 0 ? 'warning' : 'info'">{{ formatNumber(warning.count) }}</el-tag>
                    <el-button size="small" text @click="goTo(warning.route)">处理</el-button>
                  </div>
                </div>
              </div>
              <div class="job-status-tip">优先处理高风险项，可有效减少简历流失与职位沉底。</div>
            </el-card>

            <el-card shadow="never" class="interview-card">
              <template #header>
                <div class="flex justify-between items-center">
                  <span class="font-bold text-gray-700">面试日程</span>
                  <el-button size="small" @click="$router.push('/merchant/interviews')">进入日程</el-button>
                </div>
              </template>

              <div class="interview-overview">
                <div class="overview-badges">
                  <span class="overview-badge pending">待确认 {{ pendingInterviewCount }}</span>
                  <span class="overview-badge today">今日 {{ todayInterviewCount }}</span>
                </div>

                <el-skeleton v-if="interviewLoading" animated :rows="3" />
                <div v-else-if="upcomingInterviews.length" class="overview-list">
                  <div v-for="item in upcomingInterviews" :key="item.id" class="overview-item">
                    <div class="overview-time">{{ formatTimeOnly(item.scheduleTime) || '待定' }}</div>
                    <div class="overview-main">
                      <div class="overview-name">{{ item.applicantName || '候选人' }}</div>
                      <div class="overview-meta">
                        {{ item.jobName || '-' }} · 第{{ item.roundNo }}轮 · {{ getInterviewModeLabel(item.method) }}
                      </div>
                    </div>
                    <el-tag size="small" :type="getInterviewStatusTag(item.status)">
                      {{ getInterviewStatusText(item.status) }}
                    </el-tag>
                  </div>
                </div>
                <el-empty v-else description="暂无近期面试" />
              </div>
            </el-card>

            <el-card shadow="never" class="candidate-priority-card" v-loading="interviewLoading">
              <template #header>
                <div class="flex justify-between items-center">
                  <span class="font-bold text-gray-700">优先候选人</span>
                  <el-button size="small" @click="$router.push('/merchant/resumes')">查看全部</el-button>
                </div>
              </template>
              <div v-if="priorityCandidates.length" class="candidate-list">
                <div v-for="item in priorityCandidates" :key="item.id" class="candidate-item">
                  <div class="candidate-main">
                    <div class="candidate-name">{{ item.applicantName || '候选人' }}</div>
                    <div class="candidate-meta">{{ item.jobName || '-' }} · 第{{ item.roundNo }}轮</div>
                  </div>
                  <el-button size="small" text @click="$router.push('/merchant/interviews')">去跟进</el-button>
                </div>
              </div>
              <el-empty v-else description="暂无待优先跟进候选人" />
            </el-card>
          </div>
        </el-col>
      </el-row>
    </div>

  </div>
</template>

<script setup>
/*
文件速览：
1. 文件职责：承载商家工作台的数据总览、任务优先级、风险预警、面试概览与治理提醒聚焦卡。
2. 对外入口：initData、fetchDashboardStats、fetchInterviewOverview、goToGovernanceNotice。
3. 关键结构：dashboardStats、todoItems、warningItems、merchantRectifyFocus、governanceLaneGroups。
4. 阅读建议：先看治理提醒与企业资料整改聚焦的 computed，再看三段治理队列，最后看统计拉取与面试概览逻辑。
*/
import { ref, reactive, onMounted, computed, watch } from 'vue'
import { Document, Calendar, Clock, Suitcase, School, ArrowRight } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { checkMerchantStatus, getMerchantDashboardStats } from '@/api/merchant'
import { getMerchantDeliveryList } from '@/api/delivery'
import { getInterviewList } from '@/api/interview'
import { useGovernanceStore } from '@/stores/governance'

const router = useRouter()
const governanceStore = useGovernanceStore()
const loading = ref(true)
const funnelRange = ref('7天')
const interviewLoading = ref(false)
const statsLoading = ref(false)
const upcomingInterviews = ref([])
const allInterviews = ref([])
const pendingInterviewCount = ref(0)
const todayInterviewCount = ref(0)

const merchantStatus = reactive({
  isNew: false,      // 默认为 false，防止闪烁，等接口回来再变
  auditStatus: -1
})

const dashboardStats = reactive({
  jobViewCount: 0,
  jobTotalCount: 0,
  jobOnlineCount: 0,
  jobApprovedCount: 0,
  deliveryCount: 0,
  funnelViewedCount: 0,
  funnelInterviewCount: 0
})
const governanceCounts = computed(() => governanceStore.counts || {
  unread: 0,
  pendingAction: 0,
  pendingReview: 0
})
const governancePriorityNotices = computed(() => governanceStore.highPriorityNotices || [])
const governanceBuckets = computed(() => governanceStore.workbenchBuckets || {
  read: [],
  action: [],
  review: []
})

const rangeDays = computed(() => (funnelRange.value === '30天' ? 30 : 7))
const pendingResumeCount = computed(() =>
  Math.max((dashboardStats.deliveryCount || 0) - (dashboardStats.funnelViewedCount || 0), 0)
)

const interviewReadyCount = computed(() =>
  Math.max((dashboardStats.funnelViewedCount || 0) - (dashboardStats.funnelInterviewCount || 0), 0)
)

const pendingJobAuditCount = computed(() =>
  Math.max((dashboardStats.jobTotalCount || 0) - (dashboardStats.jobApprovedCount || 0), 0)
)

const lowExposureJobCount = computed(() => {
  const onlineCount = dashboardStats.jobOnlineCount || 0
  if (!onlineCount) return 0
  const avgView = (dashboardStats.jobViewCount || 0) / onlineCount
  return avgView < 20 ? onlineCount : 0
})

const overdueInterviewFeedbackCount = computed(() => {
  const now = Date.now()
  return allInterviews.value.filter((item) => {
    const time = parseScheduleDate(item.scheduleTime)?.getTime()
    return !!time && time < now && item.status === 1
  }).length
})

const governancePendingTotal = computed(() =>
  Number(governanceCounts.value.unread || 0)
  + Number(governanceCounts.value.pendingAction || 0)
  + Number(governanceCounts.value.pendingReview || 0)
)
const urgentTaskCount = computed(() =>
  pendingResumeCount.value
  + interviewReadyCount.value
  + pendingInterviewCount.value
  + pendingJobAuditCount.value
  + governancePendingTotal.value
)
const merchantRectifyFocus = computed(() => governancePriorityNotices.value.find((item) => item.noticeType === 'MERCHANT_RECTIFY') || null)
const governanceReadCount = computed(() => Number(governanceCounts.value.unread || 0))
const governanceActionCount = computed(() =>
  Math.max(Number(governanceCounts.value.pendingAction || 0), governanceBuckets.value.action.length)
)
const governanceReviewCount = computed(() => Number(governanceCounts.value.pendingReview || 0))
const governanceLaneGroups = computed(() => {
  const focusId = Number(merchantRectifyFocus.value?.id || 0)
  const filterFocus = (items) => items.filter((item) => Number(item.id) !== focusId)
  const readItems = filterFocus(governanceBuckets.value.read)
  const actionItems = filterFocus(governanceBuckets.value.action)
  const reviewItems = filterFocus(governanceBuckets.value.review)
  const hasActionFocus = Boolean(merchantRectifyFocus.value) && governanceActionCount.value > 0 && actionItems.length === 0
  return [
    {
      key: 'read',
      label: '先看说明',
      hint: '先理解平台说明和处理背景。',
      count: governanceReadCount.value,
      items: readItems,
      emptyText: '当前没有新的待查看事项',
      footer: '先看清楚要求，再决定改职位还是企业资料。',
      actionText: '查看通知'
    },
    {
      key: 'action',
      label: '先处理整改',
      hint: '需要你立刻修改资料、补说明或继续跟进。',
      count: governanceActionCount.value,
      items: actionItems,
      emptyText: hasActionFocus ? '当前最优先的整改动作已在左侧主聚焦卡展示' : '当前没有需要立即执行的整改动作',
      footer: hasActionFocus ? '先完成左侧企业资料整改，再继续处理其他治理动作。' : '这里最容易影响招聘节奏，建议优先清空。',
      actionText: '立即处理'
    },
    {
      key: 'review',
      label: '等待复核',
      hint: '你已提交材料，当前关注平台反馈即可。',
      count: governanceReviewCount.value,
      items: reviewItems,
      emptyText: '当前没有等待平台复核的事项',
      footer: '复核中的事项无需重复提交，留意平台反馈。',
      actionText: '查看进度'
    }
  ]
})
const governanceHasLaneItems = computed(() =>
  governanceLaneGroups.value.some((item) => item.items.length > 0 || item.count > 0)
)
const merchantRectifyTone = computed(() => {
  const status = merchantRectifyFocus.value?.status
  if (status === 'FINISHED') return 'success'
  if (status === 'PENDING_REVIEW') return 'review'
  return 'warning'
})
const merchantRectifyStageText = computed(() => {
  const status = merchantRectifyFocus.value?.status
  if (status === 'FINISHED') return '本轮整改已完成'
  if (status === 'PENDING_REVIEW') return '平台复核中'
  if (status === 'PENDING_READ') return '请先查看整改要求'
  if (status === 'REJECTED') return '复核退回，需继续修正'
  if (status === 'EXPIRED') return '已逾期，请立即处理'
  return '等待你修正企业资料'
})
const merchantRectifyDetailText = computed(() => merchantRectifyFocus.value?.requiredAction || merchantRectifyFocus.value?.summary || '请进入企业信息页根据平台说明修正资料。')
const merchantRectifyNextStep = computed(() => {
  const status = merchantRectifyFocus.value?.status
  if (status === 'FINISHED') return '当前无需额外动作，保持企业资料与招聘信息一致即可。'
  if (status === 'PENDING_REVIEW') return '资料已经提交，本轮等待平台管理员复核。'
  if (status === 'PENDING_READ') return '先查看通知要求，再逐项修正企业信息与资质材料。'
  if (status === 'REJECTED') return '根据最新驳回意见继续补齐资料，保存后再次进入待复核。'
  if (status === 'EXPIRED') return '优先修正企业资料并尽快提交，避免继续影响招聘可信度。'
  return '进入企业信息管理页修改资料，保存后系统会自动重新提交审核。'
})
const merchantRectifyDeadlineText = computed(() => formatGovernanceDeadline(merchantRectifyFocus.value))
const merchantRectifyFooterHint = computed(() => {
  const status = merchantRectifyFocus.value?.status
  if (status === 'PENDING_REVIEW') return '当前平台正在复核，无需重复提交。'
  if (status === 'FINISHED') return '该事项已完成，可回到日常招聘节奏。'
  return '企业资料整改会直接影响职位可信度与企业展示，建议优先处理。'
})

const governanceHeroTitle = computed(() => {
  if (!governancePendingTotal.value) {
    return '平台通知当前已清空，招聘流程可继续按常规节奏推进。'
  }
  if (merchantRectifyFocus.value) {
    return '企业资料整改正在影响当前招聘可信度，建议优先处理这一事项。'
  }
  return `当前有 ${governancePendingTotal.value} 项平台事项需要优先跟进`
})

const governanceHeroDesc = computed(() => {
  if (!governancePendingTotal.value) {
    return '后续如果出现职位整改、商家资料整改或举报处理结果，系统会第一时间同步到这里。'
  }
  if (merchantRectifyFocus.value) {
    return '先修正企业资料并重新提交审核，完成后再继续处理其他治理事项，能更快恢复平台展示和招聘节奏。'
  }
  const unread = governanceReadCount.value
  const pendingAction = governanceActionCount.value
  const pendingReview = governanceReviewCount.value
  return `先确认 ${unread} 项待查看提醒，再处理 ${pendingAction} 项整改说明；另有 ${pendingReview} 项正在等待平台复核。`
})
const governanceQuickFocus = computed(() => {
  if (!governancePendingTotal.value) {
    return {
      title: '当前节奏平稳',
      desc: '治理通知已经清空，后续只需在有新增提醒时进入通知中心继续跟进。'
    }
  }
  if (merchantRectifyFocus.value) {
    return {
      title: '优先处理企业资料整改',
      desc: '企业资料整改会直接影响职位展示和商家可信度，建议先完成修改并提交复核。'
    }
  }
  if (governanceActionCount.value > 0) {
    return {
      title: `先清理 ${governanceActionCount.value} 项待处理整改`,
      desc: '这类事项最容易影响招聘节奏，建议先改资料、补说明，再回到常规招聘流程。'
    }
  }
  if (governanceReadCount.value > 0) {
    return {
      title: `先阅读 ${governanceReadCount.value} 项平台说明`,
      desc: '先看明白通知背景和要求，再决定是改职位、改企业资料，还是继续等待后续处理。'
    }
  }
  return {
    title: `重点跟进 ${governanceReviewCount.value} 项复核进度`,
    desc: '当前以等待平台反馈为主，无需重复提交，重点关注平台回执和最新处理结果。'
  }
})

/**
 * @description 根据任务量估算处理时长，便于商家安排优先级。
 */
const estimateHandleMinutes = (count, perItemMinutes, baseMinutes = 2) => {
  const c = Number(count || 0)
  if (c <= 0) return 0
  return Math.min(Math.round(c * perItemMinutes + baseMinutes), 90)
}

/**
 * @description 统一将分钟转换为可读文案。
 */
const formatEtaText = (minutes) => {
  if (!minutes) return '无需处理'
  if (minutes < 60) return `${minutes} 分钟`
  const hour = Math.floor(minutes / 60)
  const minute = minutes % 60
  return minute ? `${hour} 小时 ${minute} 分钟` : `${hour} 小时`
}

/**
 * @description 将数值任务量映射为优先级标签。
 */
const resolvePriorityMeta = (score) => {
  if (score >= 80) return { priorityLabel: 'P0 立即处理', priorityTag: 'danger' }
  if (score >= 40) return { priorityLabel: 'P1 今日处理', priorityTag: 'warning' }
  return { priorityLabel: 'P2 可排期', priorityTag: 'info' }
}

const coreMetrics = computed(() => ([
  { label: '待查看简历', value: pendingResumeCount.value, subLabel: '优先处理', icon: Document, colorClass: 'bg-blue-500' },
  { label: '待约面候选人', value: interviewReadyCount.value, subLabel: `近${rangeDays.value}天`, icon: Calendar, colorClass: 'bg-indigo-500' },
  { label: '待确认面试', value: pendingInterviewCount.value, subLabel: '立即跟进', icon: Clock, colorClass: 'bg-orange-500' },
  { label: '在招职位', value: dashboardStats.jobOnlineCount, subLabel: '保持活跃', icon: Suitcase, colorClass: 'bg-green-500' },
]))

const todoItems = computed(() => {
  const items = [
    {
      key: 'resume-view',
      title: '新投递待查看',
      desc: '尽快查看最新投递，避免高意向候选人流失。',
      count: pendingResumeCount.value,
      route: '/merchant/resumes',
      actionText: '去查看',
      weight: 5,
      etaMinutes: estimateHandleMinutes(pendingResumeCount.value, 2.5)
    },
    {
      key: 'resume-interview',
      title: '候选人待约面',
      desc: '已查看但未发起面试的候选人，建议优先安排邀约。',
      count: interviewReadyCount.value,
      route: '/merchant/resumes',
      actionText: '去邀约',
      weight: 4,
      etaMinutes: estimateHandleMinutes(interviewReadyCount.value, 3)
    },
    {
      key: 'interview-confirm',
      title: '面试待确认',
      desc: '今日和近期面试中仍有待确认安排，需要尽快完成确认。',
      count: pendingInterviewCount.value,
      route: '/merchant/interviews',
      actionText: '去确认',
      weight: 6,
      etaMinutes: estimateHandleMinutes(pendingInterviewCount.value, 4)
    },
    {
      key: 'job-audit',
      title: '职位审核跟进',
      desc: '存在审核中或待处理职位，建议尽快完善信息并跟进状态。',
      count: pendingJobAuditCount.value,
      route: '/merchant/jobs',
      actionText: '去处理',
      weight: 3,
      etaMinutes: estimateHandleMinutes(pendingJobAuditCount.value, 5)
    },
    {
      key: 'governance',
      title: '平台整改与提醒',
      desc: '优先处理平台通知中的整改、警告和待复核事项，避免影响职位发布与企业状态。',
      count: governancePendingTotal.value,
      route: '/merchant/governance',
      actionText: '去处理',
      weight: 7,
      etaMinutes: estimateHandleMinutes(governancePendingTotal.value, 4, 3)
    }
  ]
  return items
    .map((item) => {
      const score = item.count * item.weight
      const priorityMeta = resolvePriorityMeta(score)
      return {
        ...item,
        score,
        etaText: formatEtaText(item.etaMinutes),
        priorityLabel: priorityMeta.priorityLabel,
        priorityTag: priorityMeta.priorityTag
      }
    })
    .sort((a, b) => b.score - a.score)
})

const warningItems = computed(() => ([
  {
    key: 'governance',
    title: '平台治理提醒',
    desc: '存在待查看、待处理或待复核事项时，建议优先进入通知中心处理。',
    count: governancePendingTotal.value,
    route: '/merchant/governance'
  },
  {
    key: 'job-audit',
    title: '职位审核积压',
    desc: '未通过审核或等待处理的职位会影响简历流入。',
    count: pendingJobAuditCount.value,
    route: '/merchant/jobs'
  },
  {
    key: 'low-exposure',
    title: '岗位曝光偏低',
    desc: '在招职位平均浏览偏低，建议优化职位标题与标签。',
    count: lowExposureJobCount.value,
    route: '/merchant/jobs'
  },
  {
    key: 'interview-overdue',
    title: '面试反馈积压',
    desc: '已确认且过期的面试请及时更新结果，避免候选人体验下降。',
    count: overdueInterviewFeedbackCount.value,
    route: '/merchant/interviews'
  }
]))

const priorityCandidates = computed(() =>
  allInterviews.value
    .filter((item) => item.status === 0 || item.status === 1)
    .sort((a, b) => {
      const aTime = parseScheduleDate(a.scheduleTime)?.getTime() || Number.MAX_SAFE_INTEGER
      const bTime = parseScheduleDate(b.scheduleTime)?.getTime() || Number.MAX_SAFE_INTEGER
      return aTime - bTime
    })
    .slice(0, 3)
)

const formatNumber = (value) => {
  const num = Number(value || 0)
  return Number.isNaN(num) ? '0' : num.toLocaleString('zh-CN')
}

const noticeTypeText = (value) => {
  const map = {
    JOB_RECTIFY: '职位整改',
    MERCHANT_RECTIFY: '商家整改',
    REPORT_RESULT: '举报结果',
    USER_WARNING: '用户警告',
    BAN_NOTICE: '封禁通知'
  }
  return map[value] || '平台通知'
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

/**
 * @description 为治理事项状态提供 Element Plus 标签色，避免聚焦卡重渲染时调用缺失方法。
 */
const statusTag = (value) => {
  const map = {
    PENDING_READ: 'warning',
    PENDING_ACTION: 'danger',
    PENDING_REVIEW: 'primary',
    FINISHED: 'success',
    REJECTED: 'danger',
    EXPIRED: 'warning',
    CLOSED: 'info'
  }
  return map[value] || 'info'
}

const formatGovernanceLaneMeta = (item, laneKey) => {
  if (!item) return '请进入通知中心查看详情'
  if (laneKey === 'review') {
    return item.latestActionTime
      ? `最近提交：${String(item.latestActionTime).slice(0, 16).replace('T', ' ')}`
      : '平台复核中'
  }
  if (item.isOverdue) return '已超过处理时限'
  if (item.dueTime) return `截止 ${String(item.dueTime).slice(5, 16).replace('T', ' ')}`
  return item.summary || '请进入通知中心查看详情'
}

const formatGovernanceDeadline = (item) => {
  if (item?.isOverdue) return '已超过处理时限'
  if (!item?.dueTime) return '暂无截止时间'
  return `截止 ${String(item.dueTime).slice(0, 16).replace('T', ' ')}`
}

const goTo = (route) => {
  if (route) {
    router.push(route)
  }
}

const goToGovernanceNotice = (noticeId) => {
  router.push({
    path: '/merchant/governance',
    query: noticeId ? { noticeId: String(noticeId) } : undefined
  })
}

const goToCompanyRectify = () => {
  router.push(
    merchantRectifyFocus.value?.id
      ? { path: '/merchant/company', query: { noticeId: String(merchantRectifyFocus.value.id) } }
      : '/merchant/company'
  )
}

// 统一解析时间，兼容不同格式
const parseScheduleDate = (value) => {
  if (!value) return null
  const raw = String(value).trim()
  if (!raw) return null
  const normalized = raw.replace('T', ' ')
  const safe = normalized.replace(/-/g, '/')
  const date = new Date(safe)
  if (Number.isNaN(date.getTime())) return null
  return date
}

const formatTimeOnly = (value) => {
  const date = parseScheduleDate(value)
  if (!date) return ''
  const pad = (num) => String(num).padStart(2, '0')
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`
}

// 统一判断面试方式，明确区分线上/线下
const getInterviewModeLabel = (method) => {
  if (!method) return '未标注'
  const text = String(method)
  if (text.includes('线下')) return '线下面试'
  if (text.includes('线上')) return '线上面试'
  return method
}

const getInterviewStatusText = (status) => {
  const map = { 0: '待确认', 1: '已确认', 2: '已拒绝', 3: '已取消', 4: '已完成' }
  return map[status] || '未知'
}

const getInterviewStatusTag = (status) => {
  const map = { 0: 'warning', 1: 'success', 2: 'danger', 3: 'info', 4: 'primary' }
  return map[status] || 'info'
}

// 拉取面试概览（多轮面试）
const fetchInterviewOverview = async () => {
  interviewLoading.value = true
  try {
    const res = await getMerchantDeliveryList({ current: 1, size: 50, status: 2 })
    const records = res.data?.records || []
    const tasks = records.map(async (row) => {
      try {
        const response = await getInterviewList(row.id)
        const list = response.data || []
        const applicant = row.applicant || {}
        const applicantName = applicant.realName || applicant.nickname || applicant.name || '候选人'
        return list.map((item) => ({
          ...item,
          deliveryId: row.id,
          applicantName,
          jobName: row.jobName || row.jobTitle || row.job?.title || ''
        }))
      } catch (error) {
        return []
      }
    })

    const result = await Promise.all(tasks)
    const all = result.flat()
    allInterviews.value = all

    const now = Date.now()
    const scheduled = all.filter((item) => parseScheduleDate(item.scheduleTime))
    pendingInterviewCount.value = all.filter((item) => item.status === 0).length

    const todayKey = new Date().toDateString()
    todayInterviewCount.value = scheduled.filter((item) => {
      const date = parseScheduleDate(item.scheduleTime)
      return date && date.toDateString() === todayKey
    }).length

    upcomingInterviews.value = scheduled
      .filter((item) => parseScheduleDate(item.scheduleTime)?.getTime() >= now)
      .sort((a, b) => parseScheduleDate(a.scheduleTime).getTime() - parseScheduleDate(b.scheduleTime).getTime())
      .slice(0, 3)
  } catch (error) {
    console.error('获取面试概览失败:', error)
    allInterviews.value = []
    upcomingInterviews.value = []
    pendingInterviewCount.value = 0
    todayInterviewCount.value = 0
  } finally {
    interviewLoading.value = false
  }
}

// 拉取工作台统计数据（真实数据）
const fetchDashboardStats = async () => {
  statsLoading.value = true
  try {
    const res = await getMerchantDashboardStats({ rangeDays: rangeDays.value })
    const data = res?.data || {}
    dashboardStats.jobViewCount = data.jobViewCount || 0
    dashboardStats.jobTotalCount = data.jobTotalCount || 0
    dashboardStats.jobOnlineCount = data.jobOnlineCount || 0
    dashboardStats.jobApprovedCount = data.jobApprovedCount || 0
    dashboardStats.deliveryCount = data.deliveryCount || 0
    dashboardStats.funnelViewedCount = data.funnelViewedCount || 0
    dashboardStats.funnelInterviewCount = data.funnelInterviewCount || 0
  } catch (error) {
    dashboardStats.jobViewCount = 0
    dashboardStats.jobTotalCount = 0
    dashboardStats.jobOnlineCount = 0
    dashboardStats.jobApprovedCount = 0
    dashboardStats.deliveryCount = 0
    dashboardStats.funnelViewedCount = 0
    dashboardStats.funnelInterviewCount = 0
  } finally {
    statsLoading.value = false
  }
}

const initData = async () => {
  try {
    // 调用真实接口
    const res = await checkMerchantStatus()

    if (res.code === 200) {
       // 后端返回 true 才是新用户，返回 false 就是老用户
       merchantStatus.isNew = res.data.isNew
       merchantStatus.auditStatus = res.data.auditStatus
    }
  } catch (error) {
    console.error('获取商户状态失败', error)
    // 💡 容错处理：如果接口失败，默认不显示新手引导，避免阻塞老用户
    // merchantStatus.isNew = false
  } finally {
    loading.value = false
  }

  if (!merchantStatus.isNew) {
    try {
      // 工作台与布局统一消费治理 store，避免同页出现多份摘要源导致的不同步。
      await governanceStore.fetchSummary('MERCHANT', {
        force: true
      })
    } catch (error) {
      console.error('获取商家工作台初始化数据失败', error)
    }
    await Promise.all([
      fetchDashboardStats(),
      fetchInterviewOverview()
    ])
  }
}

onMounted(() => {
  initData()
})

watch(funnelRange, () => {
  if (!merchantStatus.isNew) {
    fetchDashboardStats()
  }
})

watch(
  () => governanceStore.pendingTotal,
  () => {
    if (!merchantStatus.isNew) {
      governanceStore.fetchSummary('MERCHANT', {
        force: true
      }).catch(() => {})
    }
  }
)
</script>

<style scoped>
.dashboard-container {
  max-width: 1200px;
  margin: 0 auto;
}

.governance-spotlight {
  display: grid;
  gap: 18px;
  padding: 22px;
  border-radius: 24px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  background:
    radial-gradient(circle at top left, rgba(0, 113, 227, 0.08), transparent 32%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(248, 250, 252, 0.96));
  box-shadow: 0 18px 36px rgba(15, 23, 42, 0.05);
}

.governance-spotlight--active {
  border-color: rgba(0, 113, 227, 0.22);
  box-shadow: 0 20px 42px rgba(15, 23, 42, 0.08);
}

.governance-spotlight__hero {
  display: grid;
  grid-template-columns: minmax(0, 1.18fr) minmax(280px, 0.82fr);
  gap: 16px;
  align-items: stretch;
}

.governance-spotlight__hero-main,
.governance-spotlight__hero-side {
  min-width: 0;
}

.governance-spotlight__hero-main {
  display: grid;
  gap: 12px;
  align-content: start;
}

.governance-overview-card {
  height: 100%;
  display: grid;
  gap: 14px;
  padding: 18px 20px;
  border-radius: 20px;
  border: 1px solid rgba(0, 113, 227, 0.16);
  background:
    radial-gradient(circle at top right, rgba(0, 113, 227, 0.12), transparent 34%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(239, 246, 255, 0.96));
  box-shadow: 0 14px 30px rgba(15, 23, 42, 0.05);
}

.governance-overview-card__eyebrow {
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  font-weight: 700;
  color: #64748b;
}

.governance-overview-card__title {
  font-size: 20px;
  font-weight: 700;
  line-height: 1.4;
  color: #0f172a;
}

.governance-overview-card__desc {
  font-size: 13px;
  line-height: 1.8;
  color: #475569;
}

.governance-overview-card__metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.governance-overview-metric {
  display: grid;
  gap: 6px;
  padding: 12px 14px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.76);
  border: 1px solid rgba(148, 163, 184, 0.16);
}

.governance-overview-metric__label {
  font-size: 12px;
  color: #64748b;
}

.governance-overview-metric__value {
  font-size: 20px;
  line-height: 1;
  font-weight: 700;
  color: #0f172a;
}

.governance-overview-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.governance-spotlight__eyebrow {
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #64748b;
  font-weight: 700;
}

.governance-spotlight__title {
  margin: 0;
  font-size: 24px;
  line-height: 1.35;
  color: #0f172a;
}

.governance-spotlight__desc {
  margin: 0;
  color: #64748b;
  line-height: 1.75;
}

.governance-spotlight__stats {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.governance-spotlight__guide {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.governance-stat-pill {
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(0, 113, 227, 0.14);
  color: #0f172a;
  font-size: 13px;
  font-weight: 600;
}

.governance-guide-step {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 12px;
  border-radius: 999px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  background: rgba(255, 255, 255, 0.74);
  font-size: 12px;
  font-weight: 700;
}

.governance-guide-step--read {
  color: #2563eb;
}

.governance-guide-step--action {
  color: #b45309;
}

.governance-guide-step--review {
  color: #0f766e;
}

.governance-spotlight__panel {
  display: grid;
  grid-template-columns: minmax(320px, 0.88fr) minmax(0, 1.12fr);
  gap: 12px;
  align-items: start;
}

.governance-spotlight__panel--lanes-only {
  grid-template-columns: 1fr;
}

.governance-lane-board {
  display: grid;
  grid-template-columns: repeat(3, minmax(220px, 1fr));
  gap: 12px;
}

.governance-focus-card {
  display: grid;
  gap: 14px;
  padding: 18px 20px;
  border-radius: 20px;
  border: 1px solid rgba(0, 113, 227, 0.16);
  background:
    radial-gradient(circle at top left, rgba(0, 113, 227, 0.08), transparent 28%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(239, 246, 255, 0.94));
}

.governance-focus-card--warning {
  border-color: rgba(245, 158, 11, 0.26);
  background:
    radial-gradient(circle at top left, rgba(245, 158, 11, 0.12), transparent 28%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(255, 251, 235, 0.95));
}

.governance-focus-card--review {
  border-color: rgba(0, 113, 227, 0.22);
}

.governance-focus-card--success {
  border-color: rgba(34, 197, 94, 0.22);
  background:
    radial-gradient(circle at top left, rgba(34, 197, 94, 0.1), transparent 28%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(240, 253, 244, 0.95));
}

.governance-focus-card__head,
.governance-focus-card__footer {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.governance-focus-card__meta {
  display: grid;
  gap: 6px;
}

.governance-focus-card__eyebrow {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #64748b;
}

.governance-focus-card__stage {
  font-size: 18px;
  font-weight: 700;
  color: #0f172a;
}

.governance-focus-card__title {
  font-size: 16px;
  font-weight: 700;
  color: #111827;
}

.governance-focus-card__desc {
  color: #475569;
  line-height: 1.75;
}

.governance-focus-card__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.governance-focus-card__fact {
  display: grid;
  gap: 6px;
  padding: 14px 16px;
  border-radius: 16px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  background: rgba(255, 255, 255, 0.76);
}

.governance-focus-card__fact-label {
  font-size: 12px;
  color: #64748b;
}

.governance-focus-card__fact-value {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
  line-height: 1.7;
}

.governance-focus-card__hint {
  font-size: 12px;
  color: #64748b;
  line-height: 1.7;
}

.governance-focus-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.governance-lane-card {
  display: grid;
  gap: 10px;
  padding: 16px 18px;
  border-radius: 18px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  background: rgba(255, 255, 255, 0.92);
}

.governance-lane-card--read {
  background:
    radial-gradient(circle at top left, rgba(59, 130, 246, 0.08), transparent 26%),
    rgba(255, 255, 255, 0.94);
}

.governance-lane-card--action {
  background:
    radial-gradient(circle at top left, rgba(245, 158, 11, 0.1), transparent 26%),
    rgba(255, 255, 255, 0.94);
}

.governance-lane-card--review {
  background:
    radial-gradient(circle at top left, rgba(16, 185, 129, 0.08), transparent 26%),
    rgba(255, 255, 255, 0.94);
}

.governance-lane-card__head,
.governance-lane-card__footer {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.governance-lane-card__summary {
  min-width: 0;
  flex: 1 1 auto;
}

.governance-lane-card__label {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
  word-break: break-word;
}

.governance-lane-card__hint,
.governance-lane-card__footer {
  font-size: 12px;
  color: #64748b;
  line-height: 1.6;
}

.governance-lane-card__hint,
.governance-lane-card__footer-copy,
.governance-lane-entry__title {
  word-break: break-word;
}

.governance-lane-card__count {
  min-width: 30px;
  height: 30px;
  display: inline-grid;
  place-items: center;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(148, 163, 184, 0.16);
  color: #0f172a;
  font-size: 13px;
  font-weight: 700;
  flex-shrink: 0;
}

.governance-lane-card__list {
  display: grid;
  gap: 8px;
}

.governance-lane-entry {
  width: 100%;
  display: grid;
  gap: 4px;
  padding: 12px 14px;
  text-align: left;
  border: 1px solid rgba(148, 163, 184, 0.14);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.84);
  cursor: pointer;
  transition: transform 0.22s ease, border-color 0.22s ease, box-shadow 0.22s ease;
}

.governance-lane-entry:hover {
  transform: translateY(-1px);
  border-color: rgba(0, 113, 227, 0.18);
  box-shadow: 0 10px 20px rgba(15, 23, 42, 0.06);
}

.governance-lane-entry__type {
  font-size: 11px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: #64748b;
  font-weight: 700;
}

.governance-lane-entry__title {
  font-size: 14px;
  font-weight: 700;
  color: #111827;
}

.governance-lane-entry__meta {
  font-size: 12px;
  color: #64748b;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.governance-lane-card__empty {
  min-height: 96px;
  display: grid;
  place-content: center;
  text-align: center;
  padding: 14px;
  border-radius: 14px;
  border: 1px dashed rgba(148, 163, 184, 0.18);
  color: #94a3b8;
  font-size: 12px;
  line-height: 1.7;
}

.governance-lane-card__footer {
  flex-wrap: wrap;
  align-items: flex-end;
}

.governance-lane-card__footer-copy {
  min-width: 0;
  flex: 1 1 120px;
}

.governance-lane-card__footer-action {
  flex: 0 0 auto;
  margin-left: auto;
}

.governance-spotlight__empty {
  min-height: 160px;
  border-radius: 18px;
  border: 1px dashed rgba(148, 163, 184, 0.28);
  background: rgba(255, 255, 255, 0.7);
  display: grid;
  place-content: center;
  gap: 8px;
  text-align: center;
  padding: 18px;
}

.governance-spotlight__empty-title {
  font-size: 17px;
  font-weight: 700;
  color: #0f172a;
}

.governance-spotlight__empty-desc {
  color: #64748b;
  line-height: 1.7;
}

/* 引导页专属样式 */
.onboarding-wrapper {
  min-height: 80vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background: transparent;
}

.welcome-box {
  width: 100%;
  max-width: 800px;
  padding: 40px;
}

.icon-box {
  width: 64px;
  height: 64px;
  border-radius: 18px;
  display: flex;
  justify-content: center;
  align-items: center;
}

.task-card {
  border: 1px solid var(--ui-border);
  border-radius: 16px;
  transition: all 0.25s;
}
.task-card:hover {
  border-color: var(--ui-border-strong);
  box-shadow: var(--ui-shadow-md);
  transform: translateY(-1px);
}

.stat-card {
  border-radius: 16px;
  box-shadow: none;
}

.quick-action-card {
  border: 1px solid var(--ui-border);
  border-radius: 16px;
}

.quick-action-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.quick-action-btn {
  width: 100%;
  height: 38px;
}

.task-center-card {
  border: 1px solid var(--ui-border);
  border-radius: 16px;
}

.task-summary {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid var(--ui-border);
  background: var(--ui-surface-muted);
  margin-bottom: 12px;
}

.summary-main {
  color: var(--ui-muted-strong);
  font-size: 14px;
}

.summary-count {
  color: #d92d20;
  font-size: 20px;
  font-weight: 700;
  margin: 0 2px;
}

.todo-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.todo-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  border: 1px solid var(--ui-border);
  border-radius: 12px;
  padding: 10px 12px;
  background: var(--ui-surface);
}

.todo-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--ui-text);
}

.todo-title-line {
  display: flex;
  align-items: center;
  gap: 8px;
}

.todo-desc {
  font-size: 12px;
  color: var(--ui-muted);
  margin-top: 2px;
}

.todo-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.todo-eta {
  font-size: 12px;
  color: var(--ui-muted);
}

.warning-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.warning-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid var(--ui-border);
  background: var(--ui-surface-muted);
}

.warning-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--ui-text);
}

.warning-desc {
  font-size: 12px;
  color: var(--ui-muted);
  margin-top: 2px;
}

.warning-right {
  display: flex;
  align-items: center;
  gap: 6px;
}

.candidate-priority-card {
  border: 1px solid var(--ui-border);
  border-radius: 16px;
}

.candidate-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.candidate-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
  border: 1px solid var(--ui-border);
  border-radius: 12px;
  padding: 10px 12px;
}

.candidate-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--ui-text);
}

.candidate-meta {
  margin-top: 2px;
  font-size: 12px;
  color: var(--ui-muted);
}

/* 面试概览卡片 */
.interview-overview {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.overview-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.overview-badge {
  padding: 4px 10px;
  border-radius: 999px;
  background: #f2f2f7;
  color: var(--ui-muted-strong);
  font-size: 12px;
  font-weight: 600;
}

.overview-badge.pending {
  background: rgba(245, 158, 11, 0.18);
  color: #b45309;
}

.overview-badge.today {
  background: var(--ui-accent-light);
  color: var(--ui-accent);
}

.overview-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.overview-item {
  display: grid;
  grid-template-columns: 54px minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid var(--ui-border);
  background: var(--ui-surface);
}

.overview-time {
  font-size: 13px;
  font-weight: 600;
  color: var(--ui-accent);
}

.overview-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--ui-text);
}

.overview-meta {
  font-size: 12px;
  color: var(--ui-muted);
  margin-top: 2px;
}

/* 辅助类 */
.mb-6 { margin-bottom: 1.5rem; }
.mb-8 { margin-bottom: 2rem; }
.mb-12 { margin-bottom: 3rem; }
.mt-2 { margin-top: 0.5rem; }
.mt-1 { margin-top: 0.25rem; }
.mt-4 { margin-top: 1rem; }
.mt-8 { margin-top: 2rem; }
.pt-4 { padding-top: 1rem; }
.py-6 { padding-top: 1.5rem; padding-bottom: 1.5rem; }
.h-full { height: 100%; }
.text-white { color: #fff; }
.text-white\/80 { color: rgba(255, 255, 255, 0.8); }
.text-white\/50 { color: rgba(255, 255, 255, 0.5); }
.text-sm { font-size: 0.875rem; }
.text-xs { font-size: 0.75rem; }
.text-3xl { font-size: 1.875rem; }
.text-4xl { font-size: 2.25rem; }
.text-lg { font-size: 1.125rem; }
.font-bold { font-weight: 700; }
.flex { display: flex; }
.justify-between { justify-content: space-between; }
.items-center { align-items: center; }
.items-start { align-items: flex-start; }
.border-t { border-top-width: 1px; }
.text-gray-700 { color: var(--ui-muted-strong); }
.text-gray-600 { color: var(--ui-muted-strong); }
.text-gray-500 { color: var(--ui-muted); }
.text-gray-800 { color: var(--ui-text); }
.mb-2 { margin-bottom: 0.5rem; }
.text-center { text-align: center; }
.gap-6 { gap: 1.5rem; }
.flex-1 { flex: 1 1 0%; }
.bg-blue-50 { background-color: var(--ui-accent-light); }
.text-blue-500 { color: var(--ui-accent); }
.border-none { border-style: none; }

/* 颜色类（更接近 Apple 的柔和渐变） */
.bg-blue-500 { background: linear-gradient(135deg, #0a84ff, #5ac8fa); }
.bg-indigo-500 { background: linear-gradient(135deg, #5e5ce6, #7d7aff); }
.bg-orange-500 { background: linear-gradient(135deg, #ff9f0a, #ffcc00); }
.bg-green-500 { background: linear-gradient(135deg, #30d158, #66d4a7); }

.stat-card {
  border: none;
  cursor: pointer;
  transition: transform 0.2s;
  border-radius: 14px;
  overflow: hidden;
  box-shadow: var(--ui-shadow-md);
  min-height: 112px;
}
.stat-card:hover {
  transform: translateY(-5px);
}

.stat-card :deep(.el-card__body) {
  padding: 22px;
}

/* 右侧堆叠卡片 */
.right-stack {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.job-status-card :deep(.el-card__body) {
  padding-top: 10px;
}

.job-status-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.job-status-item {
  background: var(--ui-surface-muted);
  border-radius: 12px;
  padding: 10px 12px;
  border: 1px solid var(--ui-border);
  text-align: center;
}

.job-status-label {
  font-size: 12px;
  color: var(--ui-muted);
}

.job-status-value {
  margin-top: 6px;
  font-size: 18px;
  font-weight: 600;
  color: var(--ui-text);
}

.job-status-tip {
  margin-top: 10px;
  font-size: 12px;
  color: #8e8e93;
}

.interview-card {
  height: 100%;
}

/* 漏斗图样式 */
.funnel-container {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.funnel-step {
  border-radius: 14px;
  padding: 12px 14px;
  border: 1px solid var(--ui-border);
  background: linear-gradient(180deg, var(--ui-surface) 0%, var(--ui-surface-muted) 100%);
  box-shadow: var(--ui-shadow-sm);
}

.funnel-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.funnel-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.funnel-values {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.funnel-step .label {
  font-weight: 600;
  color: var(--ui-text);
}

.funnel-step .count {
  font-size: 18px;
  font-weight: 700;
  color: var(--ui-text);
}

.funnel-step .rate {
  font-size: 12px;
  color: var(--ui-muted);
  background: #f2f2f7;
  padding: 2px 8px;
  border-radius: 999px;
}

.funnel-step .badge {
  font-size: 11px;
  color: var(--ui-muted-strong);
  background: var(--ui-accent-light);
  padding: 2px 8px;
  border-radius: 999px;
}

.funnel-step .badge.baseline {
  background: rgba(16, 185, 129, 0.12);
  color: #047857;
}

.funnel-bar {
  position: relative;
  height: 10px;
  background: #e9edf3;
  border-radius: 999px;
  overflow: hidden;
}

.funnel-step .bar {
  height: 100%;
  border-radius: 999px;
  transition: width 0.3s ease;
}

.step-1 .bar { background: linear-gradient(90deg, #38bdf8, #0ea5e9); }
.step-2 .bar { background: linear-gradient(90deg, #5ac8fa, #0071e3); }
.step-3 .bar { background: linear-gradient(90deg, #818cf8, #6366f1); }
.step-4 .bar { background: linear-gradient(90deg, #34d399, #10b981); }

@media (max-width: 992px) {
  .governance-spotlight {
    padding: 18px;
  }

  .governance-spotlight__hero {
    grid-template-columns: 1fr;
  }

  .governance-overview-card__metrics {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .governance-spotlight__panel,
  .governance-lane-board {
    grid-template-columns: 1fr;
  }

  .governance-focus-card__grid {
    grid-template-columns: 1fr;
  }

  .quick-action-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .governance-overview-card__metrics {
    grid-template-columns: 1fr;
  }
}
</style>
