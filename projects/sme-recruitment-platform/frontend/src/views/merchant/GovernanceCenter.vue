<!--
文件速览：
1. 文件职责：商家端平台通知中心，负责查看治理通知、确认已读、跨页阶段批量处理、整改说明提交、封禁申诉，并跳转到职位或企业资料修改。
2. 页面入口：商家路由 `/merchant/governance`。
3. 关键结构：activeStatus、activeStage、currentOverview、governanceStageGroups、stageDetailNavigation、query、notices、currentNotice、submitDialogVisible、actionDialogVisible、stageActionLoading。
4. 阅读建议：先看顶部治理总览面板和跨页阶段快捷动作，再看阶段卡与列表联动，最后看详情抽屉、连续查看与整改动作。
-->
<template>
  <div class="governance-center">
    <div class="page-header">
      <div>
        <h2 class="page-title">平台通知</h2>
        <p class="page-desc">集中处理职位整改、平台提醒和复核进度，优先完成待查看与已驳回事项。</p>
      </div>
      <div class="page-header-meta">
        <span class="meta-caption">当前列表</span>
        <span class="meta-count">{{ pagination.total }}</span>
        <span class="meta-unit">条事项</span>
      </div>
    </div>

    <el-alert
      v-if="isRestrictedMode"
      :title="RESTRICTED_NOTICE_TITLE"
      :description="restrictedNoticeText"
      type="warning"
      show-icon
      :closable="false"
      class="governance-restricted-alert"
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
            @click="changeStatusTab(item.value)"
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
              <span class="governance-stage-entry__type">{{ noticeTypeText(item.noticeType) }}</span>
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
            当前阶段已载入 {{ notices.length }} / {{ pagination.total }} 条事项
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

    <el-card class="filter-card">
      <div class="filter-row">
        <el-select v-model="query.noticeType" placeholder="通知类型" clearable class="filter-control">
          <el-option
            v-for="item in noticeTypeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
        <div class="filter-tip">逾期事项会自动高亮，便于优先处理。</div>
        <div class="filter-actions">
          <el-button type="primary" @click="applyFilter">筛选</el-button>
          <el-button @click="resetFilter">重置</el-button>
        </div>
      </div>
    </el-card>

    <div v-loading="loading" class="notice-list">
      <el-empty v-if="!loading && notices.length === 0" description="当前没有待处理的平台通知" />

      <article
        v-for="item in notices"
        :key="item.id"
        class="notice-card"
        :class="{ 'notice-card--overdue': isOverdue(item), 'notice-card--unread': item.status === 'PENDING_READ' }"
      >
        <div class="notice-card__main">
          <div class="notice-card__head">
            <div class="notice-card__title-wrap">
              <h3 class="notice-card__title">{{ item.title }}</h3>
              <div class="notice-card__meta">
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

          <p class="notice-card__summary">{{ item.summary || '暂无摘要' }}</p>

          <div class="notice-card__info">
            <span>编号：{{ item.noticeNo }}</span>
            <span v-if="item.relatedJobTitle">职位：{{ item.relatedJobTitle }}</span>
            <span>最近动作：{{ formatText(item.latestActionTime) }}</span>
            <span>截止时间：{{ formatText(item.dueTime) }}</span>
          </div>

          <div class="notice-card__actions">
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
              @click="openActionDialog(item)"
            >
              提交申诉
            </el-button>
            <el-button
              v-if="canGoEdit(item)"
              type="primary"
              @click="goEditJob(item)"
            >
              去修改职位
            </el-button>
            <el-button
              v-if="canGoEditCompany(item)"
              type="primary"
              @click="goEditCompany(item)"
            >
              去修改企业资料
            </el-button>
            <el-button
              v-if="canSubmitFix(item)"
              type="success"
              plain
              @click="openSubmitDialog(item)"
            >
              提交复核说明
            </el-button>
          </div>
        </div>
      </article>
    </div>

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
      title="通知详情"
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
          <div class="drawer-meta-card">
            <div class="drawer-meta-card__label">关联职位</div>
            <div class="drawer-meta-card__value">{{ currentNotice.relatedJobTitle || '未绑定职位' }}</div>
            <div class="drawer-meta-card__sub">职位 ID：{{ currentNotice.relatedJobId || '—' }}</div>
          </div>
          <div class="drawer-meta-card">
            <div class="drawer-meta-card__label">处理时限</div>
            <div class="drawer-meta-card__value">{{ formatText(currentNotice.dueTime) }}</div>
            <div class="drawer-meta-card__sub">{{ isOverdue(currentNotice) ? '当前已逾期' : '请在截止前完成处理' }}</div>
          </div>
          <div class="drawer-meta-card">
            <div class="drawer-meta-card__label">首次已读</div>
            <div class="drawer-meta-card__value">{{ formatText(currentNotice.readTime) }}</div>
            <div class="drawer-meta-card__sub">{{ currentNotice.needAck ? '该事项要求确认已读' : '无需确认已读' }}</div>
          </div>
          <div class="drawer-meta-card">
            <div class="drawer-meta-card__label">最近动作</div>
            <div class="drawer-meta-card__value">{{ formatText(currentNotice.latestActionTime) }}</div>
            <div class="drawer-meta-card__sub">创建于 {{ formatText(currentNotice.createTime) }}</div>
          </div>
        </div>

        <div class="detail-section">
          <div class="detail-section__title">平台说明</div>
          <div class="detail-card">
            <div class="detail-item">
              <span class="detail-item__label">详细说明</span>
              <div class="detail-item__value">{{ currentNotice.detail || '暂无详细说明' }}</div>
            </div>
            <div class="detail-item">
              <span class="detail-item__label">平台要求</span>
              <div class="detail-item__value">{{ currentNotice.requiredAction || '暂无要求' }}</div>
            </div>
          </div>
        </div>

        <div class="detail-section">
          <div class="detail-section__title">处理动作</div>
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
              @click="openActionDialog(currentNotice)"
            >
              提交申诉
            </el-button>
            <el-button
              v-if="canGoEdit(currentNotice)"
              type="primary"
              @click="goEditJob(currentNotice)"
            >
              去修改职位
            </el-button>
            <el-button
              v-if="canGoEditCompany(currentNotice)"
              type="primary"
              @click="goEditCompany(currentNotice)"
            >
              去修改企业资料
            </el-button>
            <el-button
              v-if="canSubmitFix(currentNotice)"
              type="success"
              plain
              @click="openSubmitDialog(currentNotice)"
            >
              提交复核说明
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

      <el-empty v-else description="请选择需要查看的通知" />
    </el-drawer>

    <el-dialog
      v-model="submitDialogVisible"
      title="提交复核说明"
      width="460px"
      destroy-on-close
    >
      <div class="submit-dialog-copy">
        请先完成职位修改，再提交本次整改说明。提交后，该事项会进入“待复核”。
      </div>
      <el-input
        v-model="submitForm.content"
        type="textarea"
        :rows="5"
        placeholder="请输入本次修改内容，例如：已补充岗位职责、任职要求，并修正薪资说明"
      />
      <template #footer>
        <el-button @click="submitDialogVisible = false">取消</el-button>
        <el-button type="success" @click="submitFixAction">确认提交</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="actionDialogVisible"
      title="提交封禁申诉"
      width="460px"
      destroy-on-close
    >
      <div class="submit-dialog-copy">
        {{ ACTION_DIALOG_HINT }}
      </div>
      <el-input
        v-model="actionForm.content"
        type="textarea"
        :rows="5"
        :placeholder="ACTION_DIALOG_PLACEHOLDER"
      />
      <template #footer>
        <el-button @click="actionDialogVisible = false">取消</el-button>
        <el-button type="warning" @click="submitAction">确认提交申诉</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/*
文件速览：
1. 文件职责：承载商家端治理通知的查看、已读确认、跨页阶段批量处理、整改提交、封禁申诉，以及职位/企业资料修改跳转，并在受限账号时切到半只读模式。
2. 对外入口：fetchNotices、openDetail、handleAcknowledge、handleStageBatchAcknowledge、handleStageBatchAcknowledgeAcrossPages、handleStageExportSummary、openStageNoticeAt、goEditJob、goEditCompany、submitFixAction、submitAction。
3. 关键结构：statusTabs、activeStage、currentOverview、governanceStageGroups、stageNoticeQueue、stageDetailNavigation、query、pagination、currentNotice、submitForm、actionForm、isRestrictedMode。
4. 阅读建议：先看 isRestrictedMode 与治理总览面板，再看阶段切换、跨页阶段快捷动作与导出摘要，最后看详情抽屉、连续查看、整改动作与封禁申诉。
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
const GOVERNANCE_ROLE = 'MERCHANT'
const RESTRICTED_NOTICE_TITLE = '当前账号处于受限状态，可只读查看平台通知并提交封禁申诉'
const ACTION_DIALOG_HINT = '请说明你对本次封禁处理的异议，并尽量补充事实背景、时间线或可供平台复核的说明。'
const ACTION_DIALOG_PLACEHOLDER = '请输入申诉说明，例如：我认为本次封禁存在误判，实际情况是……'
const isRestrictedMode = computed(() =>
  userStore.restrictedMode && userStore.role === GOVERNANCE_ROLE
)
const restrictedNoticeText = computed(() =>
  userStore.restrictedReason || '除封禁通知申诉外，当前账号暂时不能确认已读、跳转整改页或提交复核说明。'
)

const statusTabs = [
  { label: '全部', value: 'all', hint: '查看全部事项' },
  { label: '待查看', value: 'PENDING_READ', hint: '优先确认已读' },
  { label: '待处理', value: 'PENDING_ACTION', hint: '尽快完成整改' },
  { label: '待复核', value: 'PENDING_REVIEW', hint: '等待管理员处理' },
  { label: '已完成', value: 'FINISHED', hint: '已完成闭环' }
]

const noticeTypeOptions = [
  { label: '职位整改', value: 'JOB_RECTIFY' },
  { label: '商家整改', value: 'MERCHANT_RECTIFY' },
  { label: '举报结果', value: 'REPORT_RESULT' },
  { label: '用户警告', value: 'USER_WARNING' },
  { label: '封禁通知', value: 'BAN_NOTICE' }
]
const NOTICE_TYPE_TEXT_MAP = {
  JOB_RECTIFY: '职位整改',
  MERCHANT_RECTIFY: '商家整改',
  REPORT_RESULT: '举报结果',
  USER_WARNING: '用户警告',
  BAN_NOTICE: '封禁通知'
}
const NOTICE_TYPE_TAG_MAP = {
  JOB_RECTIFY: 'warning',
  MERCHANT_RECTIFY: 'warning',
  REPORT_RESULT: 'info',
  USER_WARNING: 'danger',
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
const ACTOR_ROLE_TEXT_MAP = {
  ADMIN: '管理员',
  MERCHANT: '商家',
  APPLICANT: '求职者'
}
const ACTION_TYPE_TEXT_MAP = {
  READ: '确认已读',
  SUBMIT_FIX: '提交复核说明',
  REPLY: '补充说明',
  APPEAL: '发起申诉',
  APPROVE: '通过复核',
  REJECT: '驳回复核',
  CLOSE: '关闭事项'
}

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

const submitDialogVisible = ref(false)
const submitForm = ref({
  noticeId: null,
  content: ''
})
const stageActionLoading = ref('')
const actionDialogVisible = ref(false)
const actionForm = ref({
  noticeId: null,
  noticeType: '',
  actionType: 'APPEAL',
  content: ''
})

const statusOverviewCounts = computed(() => ({
  all: Number(governanceStore.counts.total || pagination.value.total || 0),
  PENDING_READ: Number(governanceStore.counts.unread || 0),
  PENDING_ACTION: Number(governanceStore.counts.pendingAction || 0),
  PENDING_REVIEW: Number(governanceStore.counts.pendingReview || 0),
  FINISHED: Number(governanceStore.counts.finished || 0)
}))
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
    title: currentStatus.value === 'all' ? '全部平台通知' : currentStatus.label,
    hint: currentStatus.hint,
    total
  }
})
const governanceStageGroups = computed(() => [
  {
    key: 'read',
    label: '先看说明',
    hint: '先理解平台要求和背景，再决定具体整改动作。',
    count: Number(governanceStore.counts.unread || 0),
    items: governanceStore.workbenchBuckets.read.slice(0, 2),
    emptyText: '当前没有新的待查看事项'
  },
  {
    key: 'action',
    label: '先处理整改',
    hint: '这里是最影响招聘节奏的事项，建议优先清空。',
    count: Math.max(
      Number(governanceStore.counts.pendingAction || 0),
      governanceStore.workbenchBuckets.action.length
    ),
    items: governanceStore.workbenchBuckets.action.slice(0, 2),
    emptyText: '当前没有需要立即执行的整改动作'
  },
  {
    key: 'review',
    label: '等待复核',
    hint: '你已提交材料，当前只需留意平台反馈。',
    count: Number(governanceStore.counts.pendingReview || 0),
    items: governanceStore.workbenchBuckets.review.slice(0, 2),
    emptyText: '当前没有等待平台复核的事项'
  }
])
const currentStageGroup = computed(() =>
  governanceStageGroups.value.find((item) => item.key === activeStage.value) || null
)

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
  return NOTICE_TYPE_TEXT_MAP[value] || '未知类型'
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

const actorRoleText = (value) => {
  return ACTOR_ROLE_TEXT_MAP[value] || '系统'
}

const actionTypeText = (value) => {
  return ACTION_TYPE_TEXT_MAP[value] || '未知动作'
}

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

const isOverdue = (item) => {
  if (!item?.dueTime) return false
  return ['FINISHED', 'CLOSED', 'EXPIRED'].includes(item.status) === false
    && new Date(item.dueTime).getTime() < Date.now()
}

const formatText = (value) => value || '—'

const statusCount = (status) => {
  return Number(statusOverviewCounts.value[status] || 0)
}

const canAcknowledgeNotice = (item) => !isRestrictedMode.value && !!item?.canAcknowledge
const canAppealNotice = (item) => Boolean(item?.canAppeal) && item?.noticeType === 'BAN_NOTICE'

const canGoEdit = (item) => {
  return !isRestrictedMode.value
    && Boolean(item?.relatedJobId)
    && ['PENDING_ACTION', 'PENDING_READ', 'REJECTED'].includes(item.status)
}

const canGoEditCompany = (item) => {
  return !isRestrictedMode.value
    && item?.noticeType === 'MERCHANT_RECTIFY'
    && ['PENDING_ACTION', 'PENDING_READ', 'REJECTED', 'PENDING_REVIEW'].includes(item.status)
}

const canSubmitFix = (item) => {
  return !isRestrictedMode.value && Boolean(item?.canReply) && Boolean(item?.relatedJobId)
}

const canUseRestrictedBanAppeal = (item, actionType = 'APPEAL') => {
  return actionType === 'APPEAL' && item?.noticeType === 'BAN_NOTICE'
}

const formatStagePreviewMeta = (item, groupKey) => {
  if (!item) return '请进入通知详情查看完整说明'
  if (groupKey === 'review') {
    return item.latestActionTime
      ? `最近提交：${String(item.latestActionTime).slice(0, 16).replace('T', ' ')}`
      : '平台复核中'
  }
  if (item.isOverdue) return '已超过处理时限'
  if (item.dueTime) return `截止 ${String(item.dueTime).slice(5, 16).replace('T', ' ')}`
  return item.summary || '请进入通知详情查看完整说明'
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
  if (!query.value.noticeType) return '全部通知类型'
  return `通知类型：${noticeTypeText(query.value.noticeType)}`
})
const activeStageTitle = computed(() => currentStageGroup.value?.label || currentOverview.value.title)

const buildMerchantStageSummaryText = (records, total, truncated) => {
  const lines = [
    `平台通知阶段摘要`,
    `导出时间：${formatExportTimestamp()}`,
    `阶段视图：${activeStageTitle.value}`,
    `筛选条件：${stageFilterText.value}`,
    `事项总数：${total}`,
    truncated ? '说明：由于分页保护，本次摘要仅导出前 3000 条事项。' : ''
  ].filter(Boolean)

  if (!records.length) {
    lines.push('', '当前筛选下没有可导出的事项。')
    return lines.join('\n')
  }

  records.forEach((item, index) => {
    lines.push(
      '',
      `${index + 1}. [${noticeTypeText(item.noticeType)}] ${item.title || '未命名事项'}`,
      `   状态：${statusText(item.status)} | 严重级别：${severityText(item.severity)}`,
      `   编号：${item.noticeNo || '—'} | 截止：${formatText(item.dueTime)} | 最近动作：${formatText(item.latestActionTime)}`,
      `   关联职位：${item.relatedJobTitle || '—'}`,
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
    ElMessage.error(res.msg || '获取平台通知失败')
  } catch (error) {
    notices.value = []
    pagination.value.total = 0
    ElMessage.error(error?.message || '获取平台通知失败')
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
    ElMessage.error(res.msg || '获取通知详情失败')
  } catch (error) {
    currentNotice.value = null
    ElMessage.error(error?.message || '获取通知详情失败')
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

const changeStatusTab = (status) => {
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
    ElMessage.info('当前阶段没有可查看的通知详情')
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
    // 摘要同步失败时保持当前页面可用，不额外打断用户操作。
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
    ElMessage.warning(restrictedNoticeText.value)
    return
  }
  if (!stageBatchAcknowledgeIds.value.length) {
    ElMessage.info('当前阶段没有可批量确认已读的通知')
    return
  }
  stageActionLoading.value = 'batchRead'
  try {
    const results = await Promise.allSettled(stageBatchAcknowledgeIds.value.map((id) => markGovernanceNoticeRead(id)))
    const successCount = results.filter((item) => item.status === 'fulfilled' && item.value?.code === 200).length
    const failedCount = stageBatchAcknowledgeIds.value.length - successCount
    await refreshAfterAction()
    if (failedCount > 0) {
      ElMessage.warning(`已确认 ${successCount} 条通知，另有 ${failedCount} 条处理失败`)
      return
    }
    ElMessage.success(`已批量确认 ${successCount} 条通知`)
  } catch (error) {
    ElMessage.error(error?.message || '批量确认已读失败')
  } finally {
    stageActionLoading.value = ''
  }
}

const handleStageBatchAcknowledgeAcrossPages = async () => {
  if (isRestrictedMode.value) {
    ElMessage.warning(restrictedNoticeText.value)
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
      ElMessage.info('当前阶段没有可跨页确认已读的通知')
      return
    }

    const results = await Promise.allSettled(noticeIds.map((id) => markGovernanceNoticeRead(id)))
    const successCount = results.filter((item) => item.status === 'fulfilled' && item.value?.code === 200).length
    const failedCount = noticeIds.length - successCount
    await refreshAfterAction()

    if (failedCount > 0) {
      ElMessage.warning(`已跨页确认 ${successCount} 条通知，另有 ${failedCount} 条处理失败${truncated ? '；本次处理范围受分页保护限制。' : ''}`)
      return
    }
    ElMessage.success(`已跨页确认 ${successCount} 条通知${truncated ? '（已按分页保护截断）' : ''}`)
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
    const content = buildMerchantStageSummaryText(records, total, truncated)
    downloadGovernanceStageText(
      `merchant-governance-${activeStage.value}-${formatExportFileTimestamp()}.txt`,
      content
    )
    ElMessage.success(`已导出“${activeStageTitle.value}”阶段摘要，共 ${records.length} 条事项`)
  } catch (error) {
    ElMessage.error(error?.message || '导出阶段摘要失败')
  } finally {
    stageActionLoading.value = ''
  }
}

const handleAcknowledge = async (item) => {
  if (isRestrictedMode.value) {
    ElMessage.warning(restrictedNoticeText.value)
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

const goEditJob = (item) => {
  if (isRestrictedMode.value) {
    ElMessage.warning(restrictedNoticeText.value)
    return
  }
  if (!item?.relatedJobId) {
    ElMessage.warning('当前事项未绑定职位')
    return
  }
  router.push({
    path: '/merchant/jobs',
    query: {
      openEdit: '1',
      focusJob: String(item.relatedJobId)
    }
  })
}

const goEditCompany = (item) => {
  if (isRestrictedMode.value) {
    ElMessage.warning(restrictedNoticeText.value)
    return
  }
  if (!item?.id) {
    ElMessage.warning('当前事项信息异常，请稍后重试')
    return
  }
  router.push({
    path: '/merchant/company',
    query: {
      noticeId: String(item.id)
    }
  })
}

const openSubmitDialog = (item) => {
  if (isRestrictedMode.value) {
    ElMessage.warning(restrictedNoticeText.value)
    return
  }
  submitForm.value = {
    noticeId: item.id,
    content: ''
  }
  submitDialogVisible.value = true
}

const submitFixAction = async () => {
  if (isRestrictedMode.value) {
    ElMessage.warning(restrictedNoticeText.value)
    return
  }
  const content = submitForm.value.content.trim()
  if (!submitForm.value.noticeId) {
    ElMessage.warning('事项状态异常，请重新打开')
    return
  }
  if (!content) {
    ElMessage.warning('请填写本次整改说明')
    return
  }

  try {
    const res = await submitGovernanceNoticeAction(submitForm.value.noticeId, {
      actionType: 'SUBMIT_FIX',
      content
    })
    if (res.code === 200) {
      ElMessage.success('已提交复核说明')
      submitDialogVisible.value = false
      await refreshAfterAction(submitForm.value.noticeId)
      return
    }
    ElMessage.error(res.msg || '提交复核说明失败')
  } catch (error) {
    ElMessage.error(error?.message || '提交复核说明失败')
  }
}

const openActionDialog = (item) => {
  if (isRestrictedMode.value && !canUseRestrictedBanAppeal(item)) {
    ElMessage.warning(restrictedNoticeText.value)
    return
  }
  actionForm.value = {
    noticeId: item.id,
    noticeType: item.noticeType || '',
    actionType: 'APPEAL',
    content: ''
  }
  actionDialogVisible.value = true
}

const submitAction = async () => {
  if (isRestrictedMode.value && !canUseRestrictedBanAppeal(actionForm.value, actionForm.value.actionType)) {
    ElMessage.warning(restrictedNoticeText.value)
    return
  }
  const content = String(actionForm.value.content || '').trim()
  if (!actionForm.value.noticeId) {
    ElMessage.warning('事项状态异常，请重新打开')
    return
  }
  if (!content) {
    ElMessage.warning('请填写申诉说明')
    return
  }

  try {
    const res = await submitGovernanceNoticeAction(actionForm.value.noticeId, {
      actionType: actionForm.value.actionType,
      content
    })
    if (res.code === 200) {
      ElMessage.success('申诉已提交')
      actionDialogVisible.value = false
      await refreshAfterAction(actionForm.value.noticeId)
      return
    }
    ElMessage.error(res.msg || '提交申诉失败')
  } catch (error) {
    ElMessage.error(error?.message || '提交申诉失败')
  }
}

onMounted(async () => {
  await Promise.all([fetchNotices(), refreshGovernanceSummary()])
  await syncOpenNoticeFromRoute()
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
.governance-center {
  display: grid;
  gap: 18px;
}

.governance-restricted-alert {
  border-radius: 18px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 16px;
}

.page-title {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  color: #111827;
}

.page-desc {
  margin: 8px 0 0;
  color: #64748b;
  line-height: 1.7;
}

.page-header-meta {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
  padding: 14px 18px;
  border-radius: 18px;
  border: 1px solid rgba(0, 113, 227, 0.14);
  background: linear-gradient(140deg, rgba(0, 113, 227, 0.12), rgba(255, 255, 255, 0.98));
}

.meta-caption,
.meta-unit {
  font-size: 13px;
  color: #64748b;
}

.meta-count {
  font-size: 24px;
  font-weight: 700;
  color: #0f172a;
}

.governance-overview-panel {
  display: grid;
  gap: 14px;
  padding: 18px;
  border-radius: 28px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  background:
    radial-gradient(circle at top right, rgba(0, 113, 227, 0.08), transparent 28%),
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
  color: #0f172a;
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
  color: #0f172a;
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
  border-color: rgba(0, 113, 227, 0.22);
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
  color: #0f172a;
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
  border: 1px solid rgba(0, 113, 227, 0.14);
  background: linear-gradient(145deg, rgba(0, 113, 227, 0.08), rgba(255, 255, 255, 0.96));
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
  border-color: rgba(0, 113, 227, 0.22);
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
  color: #111827;
}

.governance-stage-card__hint {
  margin-top: 2px;
  font-size: 12px;
  line-height: 1.6;
  color: #64748b;
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
  color: #0f172a;
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
  border-color: rgba(0, 113, 227, 0.18);
  box-shadow: 0 10px 20px rgba(15, 23, 42, 0.06);
}

.governance-stage-entry__type {
  font-size: 11px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: #64748b;
  font-weight: 700;
}

.governance-stage-entry__title {
  font-size: 14px;
  font-weight: 700;
  color: #111827;
  line-height: 1.5;
}

.governance-stage-entry__meta {
  font-size: 12px;
  color: #64748b;
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
  color: #94a3b8;
  font-size: 12px;
  line-height: 1.7;
}

.filter-card {
  border: 1px solid rgba(148, 163, 184, 0.14);
}

.filter-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

.filter-control {
  width: 220px;
}

.filter-actions {
  display: flex;
  gap: 10px;
  margin-left: auto;
}

.filter-tip {
  font-size: 13px;
  color: #94a3b8;
}

.notice-list {
  display: grid;
  gap: 14px;
}

.notice-card {
  padding: 22px;
  border-radius: 24px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(248, 250, 252, 0.94));
  box-shadow: 0 18px 36px rgba(15, 23, 42, 0.04);
}

.notice-card--unread {
  border-color: rgba(59, 130, 246, 0.22);
  background:
    radial-gradient(circle at top left, rgba(59, 130, 246, 0.12), transparent 34%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(248, 250, 252, 0.94));
}

.notice-card--overdue {
  border-color: rgba(239, 68, 68, 0.2);
  background:
    radial-gradient(circle at top left, rgba(239, 68, 68, 0.08), transparent 34%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(254, 242, 242, 0.9));
}

.notice-card__main {
  display: grid;
  gap: 12px;
}

.notice-card__head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.notice-card__title-wrap {
  display: grid;
  gap: 10px;
}

.notice-card__title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: #111827;
}

.notice-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.notice-card__summary {
  margin: 0;
  color: #475569;
  line-height: 1.7;
}

.notice-card__info {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  font-size: 13px;
  color: #94a3b8;
}

.notice-card__actions {
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
  border: 1px solid rgba(0, 113, 227, 0.14);
  background: linear-gradient(150deg, rgba(0, 113, 227, 0.12), rgba(255, 255, 255, 0.98));
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

.drawer-meta-card {
  display: grid;
  gap: 6px;
  padding: 16px;
  border-radius: 18px;
  background: rgba(248, 250, 252, 0.92);
  border: 1px solid rgba(148, 163, 184, 0.14);
}

.drawer-meta-card__label {
  font-size: 12px;
  color: #94a3b8;
}

.drawer-meta-card__value {
  font-weight: 700;
  color: #111827;
  line-height: 1.5;
}

.drawer-meta-card__sub {
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

.detail-card {
  display: grid;
  gap: 14px;
  padding: 18px;
  border-radius: 18px;
  border: 1px solid rgba(148, 163, 184, 0.14);
  background: rgba(255, 255, 255, 0.96);
}

.detail-item {
  display: grid;
  gap: 8px;
}

.detail-item__label {
  font-size: 13px;
  color: #94a3b8;
}

.detail-item__value {
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

.submit-dialog-copy {
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
  .page-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .page-header-meta {
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

  .filter-actions {
    margin-left: 0;
  }

  .notice-card__head {
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
