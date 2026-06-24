<!--
文件速览：
1. 文件职责：求职者端平台提醒中心，负责查看警告、举报结果、限制说明、跨页阶段批量处理，并在受限账号场景下切换为“只读 + 封禁申诉”模式。
2. 页面入口：求职者路由 `/applicant/notices`。
3. 关键结构：activeStatus、activeStage、currentOverview、governanceStageGroups、stageDetailNavigation、query、notices、currentNotice、actionDialogVisible、stageActionLoading。
4. 阅读建议：先看顶部治理总览面板和跨页阶段快捷动作，再看阶段卡与提醒列表联动，最后看详情抽屉与连续查看、申诉弹窗。
-->
<template>
  <div class="notice-center">
    <header class="notice-header">
      <div class="notice-header__copy">
        <h1 class="notice-header__title">平台提醒</h1>
        <p class="notice-header__desc">统一查看账号警告、举报结果和限制说明，先处理待查看与待处理事项。</p>
      </div>
      <div class="notice-header__meta">
        <span class="notice-header__meta-label">当前列表</span>
        <strong class="notice-header__meta-value">{{ pagination.total }}</strong>
        <span class="notice-header__meta-label">条事项</span>
      </div>
    </header>

    <el-alert
      v-if="isRestrictedMode"
      :title="restrictedNoticeTitle"
      :description="restrictedNoticeText"
      type="warning"
      show-icon
      :closable="false"
      class="notice-restricted-alert"
    />

    <section class="governance-overview-panel">
      <div class="governance-overview-panel__head">
        <div class="governance-overview-panel__copy">
          <span class="governance-overview-panel__eyebrow">
            {{ activeStage ? '当前按阶段查看' : '当前按状态查看' }}
          </span>
          <h3 class="governance-overview-panel__title">{{ currentOverview.title }}</h3>
          <p class="governance-overview-panel__desc">{{ currentOverview.hint }}</p>
        </div>
        <div class="governance-overview-panel__stats">
          <div class="governance-overview-stat">
            <span class="governance-overview-stat__label">当前视图</span>
            <strong class="governance-overview-stat__value">{{ currentOverview.total }}</strong>
          </div>
          <div class="governance-overview-stat">
            <span class="governance-overview-stat__label">待处理</span>
            <strong class="governance-overview-stat__value">{{ statusCount('PENDING_ACTION') }}</strong>
          </div>
          <div class="governance-overview-stat">
            <span class="governance-overview-stat__label">已完成</span>
            <strong class="governance-overview-stat__value">{{ statusCount('FINISHED') }}</strong>
          </div>
        </div>
      </div>

      <div class="governance-overview-panel__filters">
        <span class="governance-overview-panel__filters-caption">状态筛选</span>
        <div class="governance-overview-panel__chips">
          <button
            v-for="item in statusTabs"
            :key="item.value"
            type="button"
            class="status-filter-chip"
            :class="{ 'status-filter-chip--active': !activeStage && activeStatus === item.value }"
            @click="changeStatus(item.value)"
          >
            <span class="status-filter-chip__label">{{ item.label }}</span>
            <span class="status-filter-chip__count">{{ statusCount(item.value) }}</span>
          </button>
        </div>
      </div>

      <div class="governance-stage-board">
        <article
          v-for="group in governanceStageGroups"
          :key="group.key"
          class="governance-stage-card"
          :class="[
            `governance-stage-card--${group.key}`,
            { 'governance-stage-card--active': activeStage === group.key }
          ]"
          @click="applyStageFilter(group.key)"
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
              @click.stop="openDetail(item)"
            >
              <span class="governance-stage-entry__title">{{ item.title }}</span>
              <span class="governance-stage-entry__meta">{{ formatStagePreviewMeta(item, group.key) }}</span>
            </button>
          </div>
          <div v-else class="governance-stage-card__empty">{{ group.emptyText }}</div>
        </article>
      </div>

      <div v-if="activeStage" class="stage-batch-bar">
        <div class="stage-batch-bar__copy">
          <div class="stage-batch-bar__title">{{ currentOverview.title }}快捷处理</div>
          <div class="stage-batch-bar__desc">
            当前阶段已载入 {{ notices.length }} / {{ pagination.total }} 条提醒
            <span v-if="stageBatchAcknowledgeCount > 0">，其中 {{ stageBatchAcknowledgeCount }} 条可直接批量确认已读。</span>
            <span v-else>，可直接从首条开始连续查看详情。</span>
          </div>
        </div>
        <div class="stage-batch-bar__actions">
          <el-button
            v-if="stageBatchAcknowledgeCount > 0"
            type="primary"
            plain
            :loading="stageActionLoading === 'batchRead'"
            @click="handleStageBatchAcknowledge"
          >
            批量确认已读（{{ stageBatchAcknowledgeCount }}）
          </el-button>
          <el-button
            v-if="canStageBatchReadAcrossPages"
            type="primary"
            :loading="stageActionLoading === 'batchReadAll'"
            @click="handleStageBatchAcknowledgeAcrossPages"
          >
            跨页确认全部已读（{{ pagination.total }}）
          </el-button>
          <el-button
            :disabled="stageNoticeQueue.length === 0"
            @click="openStageNoticeAt(0)"
          >
            打开首条详情
          </el-button>
          <el-button
            :loading="stageActionLoading === 'export'"
            :disabled="pagination.total === 0"
            @click="handleStageExportSummary"
          >
            导出阶段摘要
          </el-button>
        </div>
      </div>
    </section>

    <el-card class="notice-filter">
      <div class="notice-filter__row">
        <el-select v-model="query.noticeType" placeholder="提醒类型" clearable class="notice-filter__control">
          <el-option
            v-for="item in noticeTypeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <div class="notice-filter__tip">高优先级或已逾期事项会自动高亮，建议优先处理。</div>
        <div class="notice-filter__actions">
          <el-button type="primary" @click="applyFilter">筛选</el-button>
          <el-button @click="resetFilter">重置</el-button>
        </div>
      </div>
    </el-card>

    <section v-loading="loading" class="notice-list">
      <el-empty v-if="!loading && notices.length === 0" description="当前没有需要处理的平台提醒" />

      <article
        v-for="item in notices"
        :key="item.id"
        class="notice-item"
        :class="{
          'notice-item--warning': isHighAttention(item),
          'notice-item--unread': item.status === 'PENDING_READ'
        }"
      >
        <div class="notice-item__head">
          <div class="notice-item__title-block">
            <h2 class="notice-item__title">{{ item.title }}</h2>
            <div class="notice-item__tags">
              <el-tag size="small" :type="noticeTypeTag(item.noticeType)" effect="plain">
                {{ noticeTypeText(item.noticeType) }}
              </el-tag>
              <el-tag size="small" :type="severityTag(item.severity)" effect="light">
                {{ severityText(item.severity) }}
              </el-tag>
              <el-tag size="small" :type="statusTag(item.status)">
                {{ statusText(item.status) }}
              </el-tag>
              <el-tag v-if="isOverdue(item)" size="small" type="danger">已逾期</el-tag>
            </div>
          </div>
          <el-button size="small" @click="openDetail(item)">查看详情</el-button>
        </div>

        <p class="notice-item__summary">{{ item.summary || '暂无摘要' }}</p>

        <div class="notice-item__meta">
          <span>编号：{{ item.noticeNo }}</span>
          <span>来源：{{ sourceModuleText(item.sourceModule) }}</span>
          <span>截止：{{ formatText(item.dueTime) }}</span>
          <span>最近动作：{{ formatText(item.latestActionTime) }}</span>
        </div>

        <div class="notice-item__actions">
          <el-button
            v-if="canAcknowledgeNotice(item)"
            type="primary"
            plain
            @click="handleAcknowledge(item)"
          >
            确认已读
          </el-button>
          <el-button
            v-if="canAppealNotice(item)"
            type="warning"
            plain
            @click="openActionDialog(item, 'APPEAL')"
          >
            提交申诉
          </el-button>
          <el-button
            v-else-if="canReplyNotice(item)"
            type="info"
            plain
            @click="openActionDialog(item, 'REPLY')"
          >
            补充说明
          </el-button>
        </div>
      </article>
    </section>

    <div class="pagination-container">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next, jumper"
        :total="pagination.total"
        :page-size="pagination.size"
        :current-page="pagination.current"
        :page-sizes="[10, 20, 50]"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>

    <el-drawer
      v-model="detailVisible"
      size="520px"
      title="提醒详情"
      destroy-on-close
    >
      <div v-if="detailLoading" class="drawer-loading">
        <el-skeleton :rows="10" animated />
      </div>
      <div v-else-if="currentNotice" class="drawer-body">
        <div class="drawer-hero">
          <div class="drawer-hero__title">{{ currentNotice.title }}</div>
          <div class="drawer-hero__summary">{{ currentNotice.summary || '暂无摘要' }}</div>
          <div class="drawer-hero__tags">
            <el-tag :type="noticeTypeTag(currentNotice.noticeType)" effect="plain">
              {{ noticeTypeText(currentNotice.noticeType) }}
            </el-tag>
            <el-tag :type="severityTag(currentNotice.severity)" effect="light">
              {{ severityText(currentNotice.severity) }}
            </el-tag>
            <el-tag :type="statusTag(currentNotice.status)">
              {{ statusText(currentNotice.status) }}
            </el-tag>
          </div>
        </div>

        <div class="drawer-grid">
          <div class="drawer-card">
            <div class="drawer-card__label">来源模块</div>
            <div class="drawer-card__value">{{ sourceModuleText(currentNotice.sourceModule) }}</div>
            <div class="drawer-card__sub">通知编号：{{ currentNotice.noticeNo }}</div>
          </div>
          <div class="drawer-card">
            <div class="drawer-card__label">处理时限</div>
            <div class="drawer-card__value">{{ formatText(currentNotice.dueTime) }}</div>
            <div class="drawer-card__sub">{{ isOverdue(currentNotice) ? '已超过时限' : '请在截止前完成处理' }}</div>
          </div>
          <div class="drawer-card">
            <div class="drawer-card__label">首次已读</div>
            <div class="drawer-card__value">{{ formatText(currentNotice.readTime) }}</div>
            <div class="drawer-card__sub">{{ currentNotice.needAck ? '该事项要求确认已读' : '该事项无需确认已读' }}</div>
          </div>
          <div class="drawer-card">
            <div class="drawer-card__label">最近动作</div>
            <div class="drawer-card__value">{{ formatText(currentNotice.latestActionTime) }}</div>
            <div class="drawer-card__sub">创建于 {{ formatText(currentNotice.createTime) }}</div>
          </div>
        </div>

        <div class="detail-section">
          <div class="detail-section__title">平台说明</div>
          <div class="detail-panel">
            <div class="detail-panel__item">
              <span class="detail-panel__label">详细说明</span>
              <div class="detail-panel__value">{{ currentNotice.detail || '暂无详细说明' }}</div>
            </div>
            <div class="detail-panel__item">
              <span class="detail-panel__label">平台要求</span>
              <div class="detail-panel__value">{{ currentNotice.requiredAction || '暂无要求' }}</div>
            </div>
          </div>
        </div>

        <div class="detail-section">
          <div class="detail-section__title">我可以做什么</div>
          <div v-if="stageDetailNavigation.total > 1" class="drawer-sequence-actions">
            <el-button
              plain
              :disabled="!stageDetailNavigation.hasPrev"
              @click="openStageNoticeAt(stageDetailNavigation.index - 1)"
            >
              上一条
            </el-button>
            <span class="drawer-sequence-actions__meta">
              {{ stageDetailNavigation.index + 1 }} / {{ stageDetailNavigation.total }}
            </span>
            <el-button
              plain
              :disabled="!stageDetailNavigation.hasNext"
              @click="openStageNoticeAt(stageDetailNavigation.index + 1)"
            >
              下一条
            </el-button>
          </div>
          <div class="drawer-actions">
            <el-button
              v-if="canAcknowledgeNotice(currentNotice)"
              type="primary"
              plain
              @click="handleAcknowledge(currentNotice)"
            >
              确认已读
            </el-button>
            <el-button
              v-if="canAppealNotice(currentNotice)"
              type="warning"
              plain
              @click="openActionDialog(currentNotice, 'APPEAL')"
            >
              提交申诉
            </el-button>
            <el-button
              v-else-if="canReplyNotice(currentNotice)"
              type="info"
              plain
              @click="openActionDialog(currentNotice, 'REPLY')"
            >
              补充说明
            </el-button>
          </div>
        </div>

        <div class="detail-section">
          <div class="detail-section__title">处理时间线</div>
          <el-empty v-if="!currentNotice.actions?.length" description="暂无时间线记录" />
          <el-timeline v-else class="timeline-list">
            <el-timeline-item
              v-for="item in currentNotice.actions"
              :key="item.id"
              :timestamp="formatText(item.createTime)"
            >
              <div class="timeline-card">
                <div class="timeline-card__head">
                  <span class="timeline-card__title">{{ actionTypeText(item.actionType) }}</span>
                  <span class="timeline-card__role">{{ actorRoleText(item.actorRole) }}</span>
                </div>
                <div class="timeline-card__meta">{{ item.actorName || '未知用户' }}</div>
                <div class="timeline-card__content">{{ item.content || '无补充说明' }}</div>
              </div>
            </el-timeline-item>
          </el-timeline>
        </div>
      </div>
      <el-empty v-else description="请选择需要查看的提醒" />
    </el-drawer>

    <el-dialog
      v-model="actionDialogVisible"
      :title="actionDialogTitle"
      width="460px"
      destroy-on-close
    >
      <div class="action-dialog-copy">
        {{ actionDialogHint }}
      </div>
      <el-input
        v-model="actionForm.content"
        type="textarea"
        :rows="5"
        :placeholder="actionDialogPlaceholder"
      />
      <template #footer>
        <el-button @click="actionDialogVisible = false">取消</el-button>
        <el-button :type="actionDialogButtonType" @click="submitAction">
          {{ actionDialogButtonText }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/*
文件速览：
1. 文件职责：承载求职者端平台提醒列表、详情查看、已读确认、跨页阶段批量处理与申诉/补充说明动作，并在受限账号时切到“只读 + 封禁申诉”模式。
2. 对外入口：fetchNotices、openDetail、handleAcknowledge、handleStageBatchAcknowledge、handleStageBatchAcknowledgeAcrossPages、handleStageExportSummary、openStageNoticeAt、openActionDialog、submitAction。
3. 关键结构：statusTabs、activeStage、currentOverview、governanceStageGroups、stageNoticeQueue、stageDetailNavigation、query、pagination、currentNotice、actionForm、isRestrictedMode。
4. 阅读建议：先看 isRestrictedMode 与治理总览面板，再看阶段切换、跨页阶段快捷动作与导出摘要，最后看详情抽屉、连续查看与动作提交弹窗。
*/
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getMyGovernanceNoticeDetail,
  getMyGovernanceNotices,
  markGovernanceNoticeRead,
  submitGovernanceNoticeAction
} from '@/api/governance'
import { useGovernanceStore } from '@/stores/governance'
import { useUserStore } from '@/stores/user'
import { downloadGovernanceStageText, fetchAllGovernanceStageNotices } from '@/utils/governanceStageTools'

const route = useRoute()
const router = useRouter()
const governanceStore = useGovernanceStore()
const userStore = useUserStore()
const GOVERNANCE_ROLE = 'APPLICANT'
const restrictedNoticeTitle = '当前账号处于受限状态，可只读查看平台提醒并提交封禁申诉'
const TERMINAL_NOTICE_STATUSES = ['FINISHED', 'CLOSED', 'EXPIRED']
const NOTICE_TYPE_TEXT_MAP = {
  REPORT_RESULT: '举报结果',
  USER_WARNING: '用户警告',
  BAN_NOTICE: '封禁通知'
}
const NOTICE_TYPE_TAG_MAP = {
  REPORT_RESULT: 'info',
  USER_WARNING: 'warning',
  BAN_NOTICE: 'danger'
}
const SEVERITY_TEXT_MAP = {
  INFO: '提示',
  WARNING: '警告',
  HIGH: '高风险'
}
const SEVERITY_TAG_MAP = {
  INFO: 'info',
  WARNING: 'warning',
  HIGH: 'danger'
}
const STATUS_TEXT_MAP = {
  PENDING_READ: '待查看',
  PENDING_ACTION: '待处理',
  PENDING_REVIEW: '待复核',
  FINISHED: '已完成',
  REJECTED: '已驳回',
  EXPIRED: '已失效',
  CLOSED: '已关闭'
}
const STATUS_TAG_MAP = {
  PENDING_READ: 'info',
  PENDING_ACTION: 'warning',
  PENDING_REVIEW: 'primary',
  FINISHED: 'success',
  REJECTED: 'danger',
  EXPIRED: 'danger',
  CLOSED: 'info'
}
const SOURCE_MODULE_TEXT_MAP = {
  REPORT: '举报处理',
  RISK_CONTROL: '风险控制',
  JOB_AUDIT: '职位审核',
  MERCHANT_AUDIT: '商家审核'
}
const ACTOR_ROLE_TEXT_MAP = {
  ADMIN: '管理员',
  MERCHANT: '商家',
  APPLICANT: '求职者'
}
const ACTION_TYPE_TEXT_MAP = {
  READ: '确认已读',
  REPLY: '补充说明',
  APPEAL: '提交申诉',
  APPROVE: '处理通过',
  REJECT: '处理驳回',
  CLOSE: '事项关闭'
}
const DEFAULT_ACTION_DIALOG_META = {
  title: '补充说明',
  hint: '你可以补充说明本次事项的背景、解释或其他需要平台知晓的信息。',
  placeholder: '请输入补充说明，例如：该情况的实际背景是……',
  buttonType: 'primary',
  buttonText: '确认提交说明'
}
const ACTION_DIALOG_META_MAP = {
  APPEAL: {
    title: '提交申诉',
    hint: '请说明你对本次平台处理的异议，并尽量写清事实经过。',
    placeholder: '请输入申诉说明，例如：我认为该次处理存在误判，原因是……',
    buttonType: 'warning',
    buttonText: '确认提交申诉'
  },
  REPLY: DEFAULT_ACTION_DIALOG_META
}
const BAN_APPEAL_ACTION_DIALOG_META = {
  title: '提交封禁申诉',
  hint: '请说明你对本次封禁处理的异议，并尽量写清事实经过、时间线或可供平台复核的说明。',
  placeholder: '请输入申诉说明，例如：我认为本次封禁存在误判，实际情况是……',
  buttonType: 'warning',
  buttonText: '确认提交申诉'
}
const isRestrictedMode = computed(() =>
  userStore.restrictedMode && userStore.role === GOVERNANCE_ROLE
)
const restrictedNoticeText = computed(() =>
  userStore.restrictedReason || '除封禁通知申诉外，当前账号暂时不能确认已读、提交补充说明或进行其他业务操作。'
)

const statusTabs = [
  { label: '全部', value: 'all', hint: '查看全部平台提醒' },
  { label: '待查看', value: 'PENDING_READ', hint: '优先阅读平台说明' },
  { label: '待处理', value: 'PENDING_ACTION', hint: '需要确认、说明或申诉' },
  { label: '待复核', value: 'PENDING_REVIEW', hint: '等待管理员复核' },
  { label: '已完成', value: 'FINISHED', hint: '已完成闭环' }
]

const noticeTypeOptions = [
  { label: '举报结果', value: 'REPORT_RESULT' },
  { label: '用户警告', value: 'USER_WARNING' },
  { label: '封禁通知', value: 'BAN_NOTICE' }
]

const activeStatus = ref('all')
const activeStage = ref('')
const query = ref({
  noticeType: ''
})

const notices = ref([])
const loading = ref(false)
const detailLoading = ref(false)
const detailVisible = ref(false)
const currentNotice = ref(null)

const pagination = ref({
  current: 1,
  size: 10,
  total: 0
})

const stageActionLoading = ref('')
const actionDialogVisible = ref(false)
const actionForm = ref({
  noticeId: null,
  noticeType: '',
  actionType: '',
  content: ''
})

const statusOverviewCounts = computed(() => ({
  all: Number(governanceStore.counts.total || pagination.value.total || 0),
  PENDING_READ: Number(governanceStore.counts.unread || 0),
  PENDING_ACTION: Number(governanceStore.counts.pendingAction || 0),
  PENDING_REVIEW: Number(governanceStore.counts.pendingReview || 0),
  FINISHED: Number(governanceStore.counts.finished || 0)
}))
const governanceStageGroups = computed(() => [
  {
    key: 'read',
    label: '先读说明',
    hint: '先看平台解释，再决定是否需要继续动作。',
    count: Number(governanceStore.counts.unread || 0),
    items: governanceStore.workbenchBuckets.read.slice(0, 2),
    emptyText: '当前没有新的待查看提醒'
  },
  {
    key: 'action',
    label: '需要你说明',
    hint: '这类事项需要补充说明、申诉或继续跟进。',
    count: Math.max(
      Number(governanceStore.counts.pendingAction || 0),
      governanceStore.workbenchBuckets.action.length
    ),
    items: governanceStore.workbenchBuckets.action.slice(0, 2),
    emptyText: '当前没有需要你补充的事项'
  },
  {
    key: 'review',
    label: '平台处理中',
    hint: '你已反馈，当前只需留意平台后续结果。',
    count: Number(governanceStore.counts.pendingReview || 0),
    items: governanceStore.workbenchBuckets.review.slice(0, 2),
    emptyText: '当前没有等待平台复核的事项'
  }
])
const currentStageGroup = computed(() =>
  governanceStageGroups.value.find((item) => item.key === activeStage.value) || null
)
const currentOverview = computed(() => {
  if (currentStageGroup.value) {
    return {
      title: currentStageGroup.value.label,
      hint: `${currentStageGroup.value.hint} 当前共有 ${currentStageGroup.value.count} 条事项。`,
      total: currentStageGroup.value.count
    }
  }

  const currentStatus = statusTabs.find((item) => item.value === activeStatus.value) || statusTabs[0]
  const total = Number(statusOverviewCounts.value[currentStatus.value] || 0)
  return {
    title: currentStatus.value === 'all' ? '全部平台提醒' : currentStatus.label,
    hint: currentStatus.hint,
    total
  }
})
const activeStageTitle = computed(() => currentStageGroup.value?.label || currentOverview.value.title)

const stageNoticeQueue = computed(() => (activeStage.value ? notices.value : []))

const stageBatchAcknowledgeIds = computed(() =>
  stageNoticeQueue.value
    .filter((item) => canAcknowledgeNotice(item))
    .map((item) => Number(item.id))
    .filter((id) => Number.isFinite(id) && id > 0)
)

const stageBatchAcknowledgeCount = computed(() => stageBatchAcknowledgeIds.value.length)
const canStageBatchReadAcrossPages = computed(() => {
  return activeStage.value === 'read'
    && !isRestrictedMode.value
    && Number(pagination.value.total || 0) > stageBatchAcknowledgeCount.value
})

const stageDetailNavigation = computed(() => {
  const ids = stageNoticeQueue.value.map((item) => Number(item.id)).filter((id) => Number.isFinite(id) && id > 0)
  const currentId = Number(currentNotice.value?.id)
  const index = ids.findIndex((id) => id === currentId)
  return {
    total: ids.length,
    index,
    hasPrev: index > 0,
    hasNext: index >= 0 && index < ids.length - 1
  }
})

const noticeTypeText = (value) => {
  return NOTICE_TYPE_TEXT_MAP[value] || '平台提醒'
}

const noticeTypeTag = (value) => {
  return NOTICE_TYPE_TAG_MAP[value] || 'info'
}

const severityText = (value) => {
  return SEVERITY_TEXT_MAP[value] || '未知级别'
}

const severityTag = (value) => {
  return SEVERITY_TAG_MAP[value] || 'info'
}

const statusText = (value) => {
  return STATUS_TEXT_MAP[value] || '未知状态'
}

const statusTag = (value) => {
  return STATUS_TAG_MAP[value] || 'info'
}

const sourceModuleText = (value) => {
  return SOURCE_MODULE_TEXT_MAP[value] || '平台治理'
}

const actorRoleText = (value) => {
  return ACTOR_ROLE_TEXT_MAP[value] || '系统'
}

const actionTypeText = (value) => {
  return ACTION_TYPE_TEXT_MAP[value] || '处理动作'
}

const getActionDialogMeta = (actionType, noticeType) => {
  if (actionType === 'APPEAL' && noticeType === 'BAN_NOTICE') {
    return BAN_APPEAL_ACTION_DIALOG_META
  }
  return ACTION_DIALOG_META_MAP[actionType] || DEFAULT_ACTION_DIALOG_META
}

const currentActionDialogMeta = computed(() => (
  getActionDialogMeta(actionForm.value.actionType, actionForm.value.noticeType)
))

const actionDialogTitle = computed(() => currentActionDialogMeta.value.title)
const actionDialogHint = computed(() => currentActionDialogMeta.value.hint)
const actionDialogPlaceholder = computed(() => currentActionDialogMeta.value.placeholder)
const actionDialogButtonType = computed(() => currentActionDialogMeta.value.buttonType)
const actionDialogButtonText = computed(() => currentActionDialogMeta.value.buttonText)

const normalizeList = (data) => {
  if (Array.isArray(data)) return data
  return data?.records || []
}

const buildListQuery = (overrides = {}) => {
  return {
    current: pagination.value.current,
    size: pagination.value.size,
    status: activeStage.value ? undefined : (activeStatus.value === 'all' ? undefined : activeStatus.value),
    stage: activeStage.value || undefined,
    noticeType: query.value.noticeType || undefined,
    ...overrides
  }
}

const formatText = (value) => value || '—'

const isOverdue = (item) => {
  if (!item?.dueTime) return false
  if (TERMINAL_NOTICE_STATUSES.includes(item.status)) return false
  return new Date(item.dueTime).getTime() < Date.now()
}

const isHighAttention = (item) => {
  return item?.severity === 'HIGH' || isOverdue(item)
}

const statusCount = (status) => {
  return Number(statusOverviewCounts.value[status] || 0)
}

const canAcknowledgeNotice = (item) => !isRestrictedMode.value && !!item?.canAcknowledge
const canAppealNotice = (item) => {
  if (!item?.canAppeal) return false
  if (!isRestrictedMode.value) return true
  return item?.noticeType === 'BAN_NOTICE'
}
const canReplyNotice = (item) => !isRestrictedMode.value && !!item?.canReply
const canUseRestrictedBanAppeal = (item, actionType = 'APPEAL') => {
  return actionType === 'APPEAL' && item?.noticeType === 'BAN_NOTICE'
}
const warnRestrictedMode = () => {
  ElMessage.warning(restrictedNoticeText.value)
}
const canContinueRestrictedAction = (item, actionType = 'APPEAL') => {
  return !isRestrictedMode.value || canUseRestrictedBanAppeal(item, actionType)
}
const ensureRestrictedActionAllowed = (item, actionType = 'APPEAL') => {
  if (canContinueRestrictedAction(item, actionType)) {
    return true
  }
  warnRestrictedMode()
  return false
}
const formatStageMetaTime = (value, prefix, fallbackText) => {
  if (!value) return fallbackText
  return `${prefix} ${String(value).slice(5, 16).replace('T', ' ')}`
}

const formatStagePreviewMeta = (item, groupKey) => {
  if (!item) return '请进入提醒详情查看完整说明'
  if (groupKey === 'review') {
    return formatStageMetaTime(item.latestActionTime, '最近更新', '等待平台继续处理')
  }
  if (item.isOverdue) return '已超过处理时限'
  return formatStageMetaTime(item.dueTime, '截止', item.summary || '请进入提醒详情查看完整说明')
}

const formatExportTimestamp = () => {
  const now = new Date()
  const pad = (value) => String(value).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
}

const formatExportFileTimestamp = () => {
  return formatExportTimestamp().replace(/[ :]/g, '-')
}

const stageFilterText = computed(() => {
  if (!query.value.noticeType) return '全部提醒类型'
  return `提醒类型：${noticeTypeText(query.value.noticeType)}`
})

const buildApplicantStageSummaryText = (records, total, truncated) => {
  const lines = [
    '平台提醒阶段摘要',
    `导出时间：${formatExportTimestamp()}`,
    `阶段视图：${activeStageTitle.value}`,
    `筛选条件：${stageFilterText.value}`,
    `事项总数：${total}`,
    truncated ? '说明：由于分页保护，本次摘要仅导出前 3000 条事项。' : ''
  ].filter(Boolean)

  if (!records.length) {
    lines.push('', '当前筛选下没有可导出的提醒。')
    return lines.join('\n')
  }

  records.forEach((item, index) => {
    lines.push(
      '',
      `${index + 1}. [${noticeTypeText(item.noticeType)}] ${item.title || '未命名提醒'}`,
      `   状态：${statusText(item.status)} | 严重级别：${severityText(item.severity)}`,
      `   编号：${item.noticeNo || '—'} | 来源：${sourceModuleText(item.sourceModule)} | 最近动作：${formatText(item.latestActionTime)}`,
      `   截止时间：${formatText(item.dueTime)}`,
      `   摘要：${item.summary || '—'}`
    )
  })

  return lines.join('\n')
}

const fetchAllCurrentStageNotices = async () => {
  if (!activeStage.value) {
    return {
      total: 0,
      records: [],
      truncated: false
    }
  }
  return fetchAllGovernanceStageNotices({
    fetchPage: getMyGovernanceNotices,
    baseParams: buildListQuery(),
    normalizeList
  })
}

const fetchNotices = async () => {
  loading.value = true
  try {
    const res = await getMyGovernanceNotices(buildListQuery())
    if (res.code === 200) {
      notices.value = normalizeList(res.data)
      pagination.value.total = res.data?.total || 0
      return
    }
    notices.value = []
    pagination.value.total = 0
    ElMessage.error(res.msg || '获取平台提醒失败')
  } catch (error) {
    notices.value = []
    pagination.value.total = 0
    ElMessage.error(error?.message || '获取平台提醒失败')
  } finally {
    loading.value = false
  }
}

const fetchDetail = async (noticeId) => {
  detailLoading.value = true
  try {
    const res = await getMyGovernanceNoticeDetail(noticeId)
    if (res.code === 200) {
      currentNotice.value = res.data
      return
    }
    currentNotice.value = null
    ElMessage.error(res.msg || '获取提醒详情失败')
  } catch (error) {
    currentNotice.value = null
    ElMessage.error(error?.message || '获取提醒详情失败')
  } finally {
    detailLoading.value = false
  }
}

const applyFilter = () => {
  pagination.value.current = 1
  fetchNotices()
}

const resetFilter = () => {
  activeStatus.value = 'all'
  activeStage.value = ''
  query.value = {
    noticeType: ''
  }
  pagination.value.current = 1
  fetchNotices()
}

const changeStatus = (status) => {
  activeStatus.value = status
  activeStage.value = ''
  pagination.value.current = 1
  fetchNotices()
}

const applyStageFilter = (stageKey) => {
  activeStage.value = activeStage.value === stageKey ? '' : stageKey
  activeStatus.value = 'all'
  pagination.value.current = 1
  fetchNotices()
}

const handlePageChange = (page) => {
  pagination.value.current = page
  fetchNotices()
}

const handleSizeChange = (size) => {
  pagination.value.size = size
  pagination.value.current = 1
  fetchNotices()
}

const openDetail = async (item) => {
  detailVisible.value = true
  currentNotice.value = item
  await fetchDetail(item.id)
}

const openStageNoticeAt = async (index) => {
  const target = stageNoticeQueue.value[index]
  if (!target?.id) {
    ElMessage.info('当前阶段没有可查看的提醒详情')
    return
  }
  await openDetail(target)
}

const clearNoticeQuery = async () => {
  if (!route.query.noticeId) return
  const nextQuery = { ...route.query }
  delete nextQuery.noticeId
  await router.replace({
    path: route.path,
    query: nextQuery
  })
}

const syncOpenNoticeFromRoute = async () => {
  const noticeId = Number(route.query.noticeId)
  if (!Number.isFinite(noticeId) || noticeId <= 0) {
    return
  }
  detailVisible.value = true
  currentNotice.value = notices.value.find((item) => Number(item.id) === noticeId) || null
  await fetchDetail(noticeId)
  await clearNoticeQuery()
}

const refreshGovernanceSummary = async () => {
  try {
    await governanceStore.fetchSummary(GOVERNANCE_ROLE, {
      force: true,
      cacheMs: 0
    })
  } catch (error) {
    // 摘要同步失败时保持当前页可继续操作，等待下一轮刷新兜底。
  }
}

const resolveEmptyStatusFallback = () => {
  if (activeStage.value) {
    if ((currentStageGroup.value?.count || 0) > 0) return false

    const nextStage = governanceStageGroups.value.find((item) => item.count > 0)?.key || ''
    if (nextStage === activeStage.value) return false
    activeStage.value = nextStage
    pagination.value.current = 1
    return true
  }

  if (activeStatus.value === 'all') return false

  const currentCount = Number(statusOverviewCounts.value[activeStatus.value] || 0)
  if (currentCount > 0) return false

  const nextStatus = ['PENDING_READ', 'PENDING_ACTION', 'PENDING_REVIEW', 'FINISHED']
    .find((status) => Number(statusOverviewCounts.value[status] || 0) > 0) || 'all'

  if (nextStatus === activeStatus.value) return false
  activeStatus.value = nextStatus
  pagination.value.current = 1
  return true
}

const refreshAfterAction = async (noticeId) => {
  await refreshGovernanceSummary()
  resolveEmptyStatusFallback()
  await fetchNotices()
  if (detailVisible.value && noticeId) {
    await fetchDetail(noticeId)
  }
}

const handleStageBatchAcknowledge = async () => {
  if (isRestrictedMode.value) {
    warnRestrictedMode()
    return
  }
  if (!stageBatchAcknowledgeIds.value.length) {
    ElMessage.info('当前阶段没有可批量确认已读的提醒')
    return
  }
  stageActionLoading.value = 'batchRead'
  try {
    const results = await Promise.allSettled(stageBatchAcknowledgeIds.value.map((id) => markGovernanceNoticeRead(id)))
    const successCount = results.filter((item) => item.status === 'fulfilled' && item.value?.code === 200).length
    const failedCount = stageBatchAcknowledgeIds.value.length - successCount
    await refreshAfterAction()
    if (failedCount > 0) {
      ElMessage.warning(`已确认 ${successCount} 条提醒，另有 ${failedCount} 条处理失败`)
      return
    }
    ElMessage.success(`已批量确认 ${successCount} 条提醒`)
  } catch (error) {
    ElMessage.error(error?.message || '批量确认已读失败')
  } finally {
    stageActionLoading.value = ''
  }
}

const handleStageBatchAcknowledgeAcrossPages = async () => {
  if (isRestrictedMode.value) {
    warnRestrictedMode()
    return
  }
  if (!activeStage.value) {
    ElMessage.info('请先选择一个阶段后再执行跨页处理')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认对“${activeStageTitle.value}”阶段下当前筛选结果的全部提醒执行跨页已读吗？这会覆盖所有分页中的待查看事项。`,
      '跨页批量确认',
      {
        type: 'warning',
        confirmButtonText: '确认处理',
        cancelButtonText: '取消',
        closeOnClickModal: false
      }
    )
  } catch (error) {
    return
  }

  stageActionLoading.value = 'batchReadAll'
  try {
    const { records, truncated } = await fetchAllCurrentStageNotices()
    const noticeIds = records
      .filter((item) => canAcknowledgeNotice(item))
      .map((item) => Number(item.id))
      .filter((id) => Number.isFinite(id) && id > 0)

    if (!noticeIds.length) {
      ElMessage.info('当前阶段没有可跨页确认已读的提醒')
      return
    }

    const results = await Promise.allSettled(noticeIds.map((id) => markGovernanceNoticeRead(id)))
    const successCount = results.filter((item) => item.status === 'fulfilled' && item.value?.code === 200).length
    const failedCount = noticeIds.length - successCount
    await refreshAfterAction()

    if (failedCount > 0) {
      ElMessage.warning(`已跨页确认 ${successCount} 条提醒，另有 ${failedCount} 条处理失败${truncated ? '；本次处理范围受分页保护限制。' : ''}`)
      return
    }
    ElMessage.success(`已跨页确认 ${successCount} 条提醒${truncated ? '（已按分页保护截断）' : ''}`)
  } catch (error) {
    ElMessage.error(error?.message || '跨页批量确认已读失败')
  } finally {
    stageActionLoading.value = ''
  }
}

const handleStageExportSummary = async () => {
  if (!activeStage.value) {
    ElMessage.info('请先选择一个阶段后再导出摘要')
    return
  }
  stageActionLoading.value = 'export'
  try {
    const { total, records, truncated } = await fetchAllCurrentStageNotices()
    const content = buildApplicantStageSummaryText(records, total, truncated)
    downloadGovernanceStageText(
      `applicant-governance-${activeStage.value}-${formatExportFileTimestamp()}.txt`,
      content
    )
    ElMessage.success(`已导出“${activeStageTitle.value}”阶段摘要，共 ${records.length} 条提醒`)
  } catch (error) {
    ElMessage.error(error?.message || '导出阶段摘要失败')
  } finally {
    stageActionLoading.value = ''
  }
}

const handleAcknowledge = async (item) => {
  if (isRestrictedMode.value) {
    warnRestrictedMode()
    return
  }
  try {
    const res = await markGovernanceNoticeRead(item.id)
    if (res.code === 200) {
      ElMessage.success('已确认已读')
      await refreshAfterAction(item.id)
      return
    }
    ElMessage.error(res.msg || '确认已读失败')
  } catch (error) {
    ElMessage.error(error?.message || '确认已读失败')
  }
}

const openActionDialog = (item, actionType) => {
  if (!ensureRestrictedActionAllowed(item, actionType)) {
    return
  }
  actionForm.value = {
    noticeId: item.id,
    noticeType: item.noticeType || '',
    actionType,
    content: ''
  }
  actionDialogVisible.value = true
}

const submitAction = async () => {
  if (!ensureRestrictedActionAllowed(actionForm.value, actionForm.value.actionType)) {
    return
  }
  const content = String(actionForm.value.content || '').trim()
  if (!actionForm.value.noticeId || !actionForm.value.actionType) {
    ElMessage.warning('事项状态异常，请重新打开')
    return
  }
  if (!content) {
    ElMessage.warning('请填写说明内容')
    return
  }

  try {
    const res = await submitGovernanceNoticeAction(actionForm.value.noticeId, {
      actionType: actionForm.value.actionType,
      content
    })
    if (res.code === 200) {
      ElMessage.success(actionForm.value.actionType === 'APPEAL' ? '申诉已提交' : '补充说明已提交')
      actionDialogVisible.value = false
      await refreshAfterAction(actionForm.value.noticeId)
      return
    }
    ElMessage.error(res.msg || '提交失败')
  } catch (error) {
    ElMessage.error(error?.message || '提交失败')
  }
}

onMounted(() => {
  Promise.all([fetchNotices(), refreshGovernanceSummary()]).then(() => {
    syncOpenNoticeFromRoute()
  })
})

watch(
  () => route.query.noticeId,
  () => {
    if (route.query.noticeId) {
      syncOpenNoticeFromRoute()
    }
  }
)
</script>

<style scoped>
.notice-center {
  width: min(calc(100% - 48px), var(--ui-main-shell-max-width));
  margin: 0 auto;
  display: grid;
  gap: 18px;
}

.notice-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 16px;
  padding: 12px 4px 0;
}

.notice-restricted-alert {
  border-radius: 18px;
}

.notice-header__title {
  margin: 0;
  font-size: 30px;
  line-height: 1.2;
  color: #111827;
}

.notice-header__desc {
  margin: 10px 0 0;
  color: #64748b;
  line-height: 1.7;
}

.notice-header__meta {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
  padding: 14px 18px;
  border-radius: 18px;
  border: 1px solid rgba(59, 130, 246, 0.14);
  background: linear-gradient(140deg, rgba(59, 130, 246, 0.12), rgba(255, 255, 255, 0.98));
}

.notice-header__meta-label {
  font-size: 13px;
  color: #64748b;
}

.notice-header__meta-value {
  font-size: 24px;
  color: #0f172a;
}

.governance-overview-panel {
  display: grid;
  gap: 14px;
  padding: 18px;
  border-radius: 28px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  background:
    radial-gradient(circle at top right, rgba(59, 130, 246, 0.08), transparent 28%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(248, 250, 252, 0.94));
  box-shadow: 0 18px 36px rgba(15, 23, 42, 0.04);
}

.governance-overview-panel__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
}

.governance-overview-panel__copy {
  max-width: 620px;
  display: grid;
  gap: 6px;
}

.governance-overview-panel__eyebrow {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #0a84ff;
}

.governance-overview-panel__title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: #111827;
}

.governance-overview-panel__desc {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: #64748b;
}

.governance-overview-panel__stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(88px, 1fr));
  gap: 10px;
  min-width: 320px;
}

.governance-overview-stat {
  display: grid;
  gap: 6px;
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px solid rgba(148, 163, 184, 0.14);
  background: rgba(255, 255, 255, 0.84);
}

.governance-overview-stat__label {
  font-size: 12px;
  color: #64748b;
}

.governance-overview-stat__value {
  font-size: 24px;
  line-height: 1;
  font-weight: 700;
  color: #111827;
}

.governance-overview-panel__filters {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.governance-overview-panel__filters-caption {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: #94a3b8;
}

.governance-overview-panel__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.status-filter-chip {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border-radius: 999px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  background: rgba(255, 255, 255, 0.8);
  cursor: pointer;
  transition: transform 0.22s ease, border-color 0.22s ease, box-shadow 0.22s ease, background-color 0.22s ease;
}

.status-filter-chip:hover,
.status-filter-chip--active {
  transform: translateY(-1px);
  border-color: rgba(59, 130, 246, 0.22);
  background: rgba(239, 246, 255, 0.96);
  box-shadow: 0 12px 24px rgba(15, 23, 42, 0.05);
}

.status-filter-chip__label {
  font-size: 13px;
  color: #334155;
}

.status-filter-chip__count {
  min-width: 24px;
  height: 24px;
  display: inline-grid;
  place-items: center;
  padding: 0 6px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.06);
  font-size: 12px;
  font-weight: 700;
  color: #111827;
}

.governance-stage-board {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.stage-batch-bar {
  margin-top: 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px solid rgba(59, 130, 246, 0.14);
  background: linear-gradient(145deg, rgba(59, 130, 246, 0.08), rgba(255, 255, 255, 0.96));
}

.stage-batch-bar__copy {
  display: grid;
  gap: 6px;
}

.stage-batch-bar__title {
  font-size: 14px;
  font-weight: 700;
  color: #111827;
}

.stage-batch-bar__desc {
  font-size: 13px;
  line-height: 1.7;
  color: #64748b;
}

.stage-batch-bar__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.governance-stage-card {
  display: grid;
  gap: 10px;
  padding: 16px 18px;
  border-radius: 18px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  background: rgba(255, 255, 255, 0.94);
  cursor: pointer;
  transition: transform 0.22s ease, border-color 0.22s ease, box-shadow 0.22s ease;
}

.governance-stage-card--read {
  background:
    radial-gradient(circle at top left, rgba(59, 130, 246, 0.08), transparent 24%),
    rgba(255, 255, 255, 0.96);
}

.governance-stage-card--action {
  background:
    radial-gradient(circle at top left, rgba(245, 158, 11, 0.1), transparent 24%),
    rgba(255, 255, 255, 0.96);
}

.governance-stage-card--review {
  background:
    radial-gradient(circle at top left, rgba(16, 185, 129, 0.08), transparent 24%),
    rgba(255, 255, 255, 0.96);
}

.governance-stage-card:hover,
.governance-stage-card--active {
  transform: translateY(-1px);
  border-color: rgba(59, 130, 246, 0.22);
  box-shadow: 0 14px 28px rgba(15, 23, 42, 0.06);
}

.governance-stage-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.governance-stage-card__label {
  font-size: 14px;
  font-weight: 700;
  color: #1d1d1f;
}

.governance-stage-card__hint {
  margin-top: 2px;
  font-size: 12px;
  line-height: 1.6;
  color: #8e8e93;
}

.governance-stage-card__count {
  min-width: 30px;
  height: 30px;
  display: inline-grid;
  place-items: center;
  border-radius: 999px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  background: rgba(255, 255, 255, 0.84);
  font-size: 13px;
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
  gap: 4px;
  padding: 12px 14px;
  text-align: left;
  border: 1px solid rgba(148, 163, 184, 0.14);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.84);
  cursor: pointer;
  transition: transform 0.22s ease, border-color 0.22s ease, box-shadow 0.22s ease;
}

.governance-stage-entry:hover {
  transform: translateY(-1px);
  border-color: rgba(10, 132, 255, 0.18);
  box-shadow: 0 10px 20px rgba(15, 23, 42, 0.06);
}

.governance-stage-entry__title {
  font-size: 14px;
  font-weight: 700;
  color: #1d1d1f;
  line-height: 1.5;
}

.governance-stage-entry__meta {
  font-size: 12px;
  color: #8e8e93;
  line-height: 1.6;
}

.governance-stage-card__empty {
  min-height: 88px;
  display: grid;
  place-content: center;
  text-align: center;
  padding: 12px;
  border-radius: 14px;
  border: 1px dashed rgba(148, 163, 184, 0.18);
  color: #9ca3af;
  font-size: 12px;
  line-height: 1.7;
}

.notice-filter {
  border: 1px solid rgba(148, 163, 184, 0.14);
}

.notice-filter__row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

.notice-filter__control {
  width: 220px;
}

.notice-filter__tip {
  font-size: 13px;
  color: #94a3b8;
}

.notice-filter__actions {
  margin-left: auto;
  display: flex;
  gap: 10px;
}

.notice-list {
  display: grid;
  gap: 14px;
}

.notice-item {
  padding: 22px;
  border-radius: 24px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(248, 250, 252, 0.94));
  box-shadow: 0 18px 36px rgba(15, 23, 42, 0.04);
}

.notice-item--warning {
  border-color: rgba(245, 158, 11, 0.18);
  background:
    radial-gradient(circle at top left, rgba(245, 158, 11, 0.08), transparent 30%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(255, 251, 235, 0.92));
}

.notice-item--unread {
  border-color: rgba(59, 130, 246, 0.2);
}

.notice-item__head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.notice-item__title-block {
  display: grid;
  gap: 10px;
}

.notice-item__title {
  margin: 0;
  font-size: 20px;
  color: #111827;
}

.notice-item__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.notice-item__summary {
  margin: 14px 0 0;
  color: #475569;
  line-height: 1.7;
}

.notice-item__meta {
  margin-top: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  font-size: 13px;
  color: #94a3b8;
}

.notice-item__actions {
  margin-top: 14px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.pagination-container {
  display: flex;
  justify-content: flex-end;
}

.drawer-loading {
  padding: 8px 8px 16px;
}

.drawer-body {
  display: grid;
  gap: 20px;
  padding-bottom: 20px;
}

.drawer-hero {
  display: grid;
  gap: 10px;
  padding: 18px;
  border-radius: 22px;
  border: 1px solid rgba(59, 130, 246, 0.14);
  background: linear-gradient(150deg, rgba(59, 130, 246, 0.12), rgba(255, 255, 255, 0.98));
}

.drawer-hero__title {
  font-size: 20px;
  font-weight: 700;
  color: #111827;
  line-height: 1.5;
}

.drawer-hero__summary {
  color: #475569;
  line-height: 1.7;
}

.drawer-hero__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.drawer-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.drawer-card {
  display: grid;
  gap: 6px;
  padding: 16px;
  border-radius: 18px;
  background: rgba(248, 250, 252, 0.92);
  border: 1px solid rgba(148, 163, 184, 0.14);
}

.drawer-card__label {
  font-size: 12px;
  color: #94a3b8;
}

.drawer-card__value {
  font-weight: 700;
  color: #111827;
}

.drawer-card__sub {
  font-size: 12px;
  color: #64748b;
}

.detail-section {
  display: grid;
  gap: 12px;
}

.detail-section__title {
  font-size: 15px;
  font-weight: 700;
  color: #111827;
}

.detail-panel {
  display: grid;
  gap: 14px;
  padding: 18px;
  border-radius: 18px;
  border: 1px solid rgba(148, 163, 184, 0.14);
  background: rgba(255, 255, 255, 0.96);
}

.detail-panel__item {
  display: grid;
  gap: 8px;
}

.detail-panel__label {
  font-size: 13px;
  color: #94a3b8;
}

.detail-panel__value {
  color: #1f2937;
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-word;
}

.drawer-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.drawer-sequence-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.drawer-sequence-actions__meta {
  font-size: 13px;
  color: #64748b;
}

.timeline-list {
  padding-left: 2px;
}

.timeline-card {
  display: grid;
  gap: 6px;
  padding-bottom: 6px;
}

.timeline-card__head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.timeline-card__title {
  font-weight: 700;
  color: #111827;
}

.timeline-card__role,
.timeline-card__meta {
  font-size: 12px;
  color: #94a3b8;
}

.timeline-card__content {
  color: #475569;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}

.action-dialog-copy {
  margin-bottom: 12px;
  color: #64748b;
  line-height: 1.6;
}

@media (max-width: 1280px) {
  .governance-overview-panel__head {
    flex-direction: column;
  }

  .governance-stage-board {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .stage-batch-bar {
    flex-direction: column;
    align-items: flex-start;
  }
}

@media (max-width: 960px) {
  .notice-center {
    width: min(calc(100% - 24px), var(--ui-main-shell-max-width));
  }

  .notice-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .notice-header__meta {
    width: 100%;
    justify-content: center;
  }

  .governance-overview-panel__stats {
    width: 100%;
    min-width: 0;
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .governance-stage-board {
    grid-template-columns: 1fr;
  }

  .governance-overview-panel__filters {
    align-items: flex-start;
    flex-direction: column;
  }

  .governance-overview-panel__chips {
    width: 100%;
  }

  .stage-batch-bar {
    flex-direction: column;
    align-items: flex-start;
  }

  .notice-filter__actions {
    margin-left: 0;
  }

  .notice-item__head {
    flex-direction: column;
    align-items: flex-start;
  }

  .drawer-grid {
    grid-template-columns: 1fr;
  }

  .drawer-sequence-actions {
    flex-wrap: wrap;
  }
}
</style>
