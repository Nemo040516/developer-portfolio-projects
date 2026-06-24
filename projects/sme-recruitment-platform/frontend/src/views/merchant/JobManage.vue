<!--
文件速览：
1. 文件职责：商家职位管理页，负责职位列表、上下架、归档、复审、治理筛选排序、跨分页批量治理动作与治理通知跳转编辑。
2. 页面入口：商家路由 `/merchant/jobs`。
3. 关键结构：activeTab、queryParams、governanceViewState、selectedGovernanceJobIds、selectedGovernanceSnapshotMap、governanceQuickGroups、displayRows、drawerVisible、pendingFocusJobId。
4. 阅读建议：先看筛选 tab、治理概览与跨分页批量选择区，再看快捷动作和列表卡片，最后看治理通知跳转定位逻辑和抽屉编辑联动。
-->
<template>
  <div class="job-manage-container">
    <div class="page-header">
      <div>
        <h2 class="page-title">职位管理</h2>
        <p class="page-desc">职位上架后会展示在大厅，投递将进入候选人列表</p>
      </div>
      <div class="page-actions">
        <el-button type="primary" icon="Plus" @click="handleAdd">新建职位</el-button>
      </div>
    </div>

    <el-alert
      v-if="governanceJumpHint"
      :title="governanceJumpHint"
      type="info"
      show-icon
      :closable="false"
      class="governance-hint"
    />

    <div class="status-tabs">
      <el-tabs v-model="activeTab" class="status-tabs-inner">
        <el-tab-pane label="全部" name="all" />
        <el-tab-pane label="招聘中" name="recruiting" />
        <el-tab-pane label="审核中" name="pending" />
        <el-tab-pane label="已驳回" name="rejected" />
        <el-tab-pane label="已下架" name="offline" />
        <el-tab-pane label="已归档" name="archived" />
      </el-tabs>
    </div>

    <div v-if="hasGovernanceSummary" class="governance-summary">
      <div class="governance-summary__copy">
        <div class="governance-summary__title">当前页治理概览</div>
        <div class="governance-summary__desc">把待整改和待复核的职位优先处理，能更快恢复正常投放节奏。</div>
      </div>
      <div class="governance-summary__stats">
        <div class="governance-summary__item">
          <span class="governance-summary__label">待查看</span>
          <span class="governance-summary__value">{{ governanceSummary.pendingRead }}</span>
        </div>
        <div class="governance-summary__item">
          <span class="governance-summary__label">待整改</span>
          <span class="governance-summary__value">{{ governanceSummary.pendingAction }}</span>
        </div>
        <div class="governance-summary__item">
          <span class="governance-summary__label">待复核</span>
          <span class="governance-summary__value">{{ governanceSummary.pendingReview }}</span>
        </div>
        <div class="governance-summary__item">
          <span class="governance-summary__label">已完成</span>
          <span class="governance-summary__value">{{ governanceSummary.finished }}</span>
        </div>
      </div>
    </div>

    <div v-if="tableData.length > 0" class="governance-toolbar">
      <div class="governance-toolbar__copy">
        <div class="governance-toolbar__title">治理视图工具</div>
        <div class="governance-toolbar__desc">先按治理状态缩小范围，再按优先级排序，能更快处理待整改职位。</div>
      </div>
      <div class="governance-toolbar__main">
        <div class="governance-filter-group">
          <button
            v-for="item in governanceFilterOptions"
            :key="item.value"
            type="button"
            class="governance-filter-chip"
            :class="{ 'governance-filter-chip--active': governanceViewState.filter === item.value }"
            @click="governanceViewState.filter = item.value"
          >
            <span>{{ item.label }}</span>
            <strong>{{ governanceFilterCount(item.value) }}</strong>
          </button>
        </div>
        <div class="governance-toolbar__aside">
          <div class="governance-toolbar__meta">
            当前页展示 {{ displayRows.length }} / {{ tableData.length }} 个职位
            <span v-if="hasGovernanceSelections"> · 已选 {{ selectedGovernanceRows.length }} 个治理职位</span>
          </div>
          <el-select v-model="governanceViewState.sort" size="small" class="governance-sort-select">
            <el-option
              v-for="item in governanceSortOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </div>
      </div>

      <div v-if="hasSelectableGovernanceRows" class="governance-selection">
        <div class="governance-selection__copy">
          <div class="governance-selection__title">批量处理选择区</div>
          <div class="governance-selection__desc">
            已选治理职位会跨分页暂存，切页后仍可继续补选；只有你主动移除或清空时，已选范围才会变化。
          </div>
        </div>
        <div class="governance-selection__stats">
          <div class="governance-selection__stat">
            <span class="governance-selection__label">已选总数</span>
            <strong class="governance-selection__value">{{ selectedGovernanceRows.length }}</strong>
          </div>
          <div class="governance-selection__stat">
            <span class="governance-selection__label">待查看</span>
            <strong class="governance-selection__value">{{ selectedGovernanceSummary.pendingRead }}</strong>
          </div>
          <div class="governance-selection__stat">
            <span class="governance-selection__label">待整改</span>
            <strong class="governance-selection__value">{{ selectedGovernanceSummary.pendingAction }}</strong>
          </div>
          <div class="governance-selection__stat">
            <span class="governance-selection__label">待复核</span>
            <strong class="governance-selection__value">{{ selectedGovernanceSummary.pendingReview }}</strong>
          </div>
        </div>
        <div class="governance-selection__memory">
          <span class="governance-selection__memory-chip">当前页已选 {{ currentPageSelectedCount }}</span>
          <span class="governance-selection__memory-chip">跨页暂存 {{ crossPageSelectedCount }}</span>
          <span class="governance-selection__memory-text">跨分页暂存会在你继续翻页或切换筛选时保留已选治理职位。</span>
        </div>
        <div class="governance-selection__actions">
          <el-button
            plain
            size="small"
            :disabled="allDisplayGovernanceSelected"
            @click="selectAllGovernanceRows"
          >
            补入当前页治理项
          </el-button>
          <el-button
            plain
            size="small"
            :disabled="actionableGovernanceRows.length === 0"
            @click="selectGovernanceRowsByStage('actionable')"
          >
            补入当前页待整改
          </el-button>
          <el-button
            plain
            size="small"
            :disabled="pendingReviewGovernanceRows.length === 0"
            @click="selectGovernanceRowsByStage('review')"
          >
            补入当前页待复核
          </el-button>
          <el-button
            plain
            size="small"
            :disabled="currentPageSelectedCount === 0"
            @click="removeCurrentPageGovernanceSelections"
          >
            移除当前页已选
          </el-button>
          <el-button
            text
            size="small"
            :disabled="!hasGovernanceSelections"
            @click="clearGovernanceSelection"
          >
            清空全部已选
          </el-button>
        </div>
      </div>

      <div v-if="hasGovernanceQuickActions" class="governance-quick-grid">
        <article
          v-for="group in governanceQuickGroups"
          :key="group.key"
          class="governance-quick-card"
          :class="`governance-quick-card--${group.key}`"
        >
          <div class="governance-quick-card__head">
            <div>
              <div class="governance-quick-card__label">{{ group.label }}</div>
              <div class="governance-quick-card__hint">{{ group.hint }}</div>
            </div>
            <strong class="governance-quick-card__count">{{ group.count }}</strong>
          </div>
          <div class="governance-quick-card__body">
            <div class="governance-quick-card__title">{{ group.previewTitle }}</div>
            <div class="governance-quick-card__meta">{{ group.previewMeta }}</div>
          </div>
          <div class="governance-quick-card__actions">
            <el-button
              v-if="group.action === 'batchRead'"
              type="primary"
              plain
              size="small"
              :loading="batchActionLoading === group.action"
              :disabled="group.count === 0"
              @click="handleBatchRead()"
            >
              批量确认已读
            </el-button>
            <el-button
              v-else-if="group.action === 'editTop'"
              type="primary"
              size="small"
              :disabled="group.count === 0"
              @click="handleStartTopGovernanceJob()"
            >
              继续处理首项
            </el-button>
            <el-button
              v-else-if="group.action === 'openReview'"
              size="small"
              plain
              :disabled="group.count === 0"
              @click="handleOpenTopReviewNotice()"
            >
              查看首条通知
            </el-button>
          </div>
        </article>
      </div>
    </div>

    <!-- 列表区域 -->
    <div class="list-wrapper" v-loading="loading">
      <div v-if="displayRows.length > 0" class="job-list">
        <div
          v-for="row in displayRows"
          :key="row.id"
          class="job-card"
          :class="{ 'job-card--selected': isGovernanceSelected(row) }"
        >
          <div class="job-main">
            <div class="job-card__head">
              <div class="job-title-text">{{ row.title }}</div>
              <div v-if="getGovernanceNotice(row)" class="job-card__selector">
                <el-checkbox
                  :model-value="isGovernanceSelected(row)"
                  @change="(value) => toggleGovernanceSelection(row, value)"
                >
                  纳入批量处理
                </el-checkbox>
              </div>
            </div>

            <div class="job-sub-info">
              <span>分类：{{ row.categoryName || '未设置' }}</span>
              <span class="dot">·</span>
              <span>招聘人数：{{ row.headcount || 1 }}人</span>
              <span class="dot">·</span>
              <span>更新时间：{{ row.updateTime || '—' }}</span>
              <span class="dot">·</span>
              <span>发布时间：{{ row.createTime || '—' }}</span>
            </div>

            <div class="job-meta-line">
              <span class="meta-label">职位状态：</span>
              <el-tag size="small" effect="plain" :type="getJobStatusTag(row.status)">
                {{ getJobStatusText(row.status) }}
              </el-tag>
              <span class="meta-label">审核状态：</span>
              <el-tooltip
                v-if="row.auditStatus === 2 && row.auditReason"
                effect="dark"
                placement="top"
                :content="`驳回原因：${row.auditReason}`"
              >
                <el-tag size="small" effect="plain" :type="getAuditTag(row.auditStatus)">
                  {{ getAuditText(row.auditStatus) }}
                </el-tag>
              </el-tooltip>
              <el-tag v-else size="small" effect="plain" :type="getAuditTag(row.auditStatus)">
                {{ getAuditText(row.auditStatus) }}
              </el-tag>
            </div>

            <div
              v-if="getGovernanceNotice(row)"
              class="governance-inline"
              :class="{ 'governance-inline--overdue': isGovernanceOverdue(getGovernanceNotice(row)) }"
            >
              <div class="governance-inline__head">
                <span class="governance-inline__label">治理进度</span>
                <div class="governance-inline__tags">
                  <el-tag size="small" :type="getGovernanceStatusTag(getGovernanceNotice(row)?.status)">
                    {{ getGovernanceStatusText(getGovernanceNotice(row)?.status) }}
                  </el-tag>
                  <el-tag
                    size="small"
                    effect="plain"
                    :type="getGovernancePriorityTag(getGovernanceNotice(row), row)"
                  >
                    {{ getGovernancePriorityText(getGovernanceNotice(row), row) }}
                  </el-tag>
                  <el-tag
                    v-if="isGovernanceOverdue(getGovernanceNotice(row))"
                    size="small"
                    type="danger"
                    effect="light"
                  >
                    已逾期
                  </el-tag>
                </div>
              </div>
              <div class="governance-inline__title">
                {{ getGovernanceStageTitle(getGovernanceNotice(row)) }}
              </div>
              <div class="governance-inline__desc">
                {{ getGovernanceStageDesc(getGovernanceNotice(row), row) }}
              </div>
              <div class="governance-inline__meta">
                <span>通知编号：{{ getGovernanceNotice(row)?.noticeNo || '—' }}</span>
                <span>截止：{{ getGovernanceNotice(row)?.dueTime || '—' }}</span>
                <span>最近动作：{{ getGovernanceNotice(row)?.latestActionTime || '—' }}</span>
              </div>
              <div class="governance-inline__actions">
                <el-button
                  v-if="canEditFromGovernance(getGovernanceNotice(row))"
                  type="primary"
                  size="small"
                  @click="handleEdit(row)"
                >
                  立即修改
                </el-button>
                <el-button
                  size="small"
                  plain
                  @click="goToGovernanceNotice(getGovernanceNotice(row)?.id)"
                >
                  查看通知
                </el-button>
              </div>
            </div>
          </div>

          <div class="job-actions">
            <el-button type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-dropdown
              trigger="click"
              @command="(command) => handleMoreCommand(row, command)"
            >
              <el-button size="small">
                更多
                <el-icon class="el-icon--right">
                  <ArrowDown />
                </el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-if="row.status === 1" command="offline" class="danger-item">
                    下架
                  </el-dropdown-item>
                  <el-dropdown-item v-if="row.status === 1" command="archive" class="danger-item">
                    归档
                  </el-dropdown-item>
                  <el-dropdown-item
                    v-if="row.status === 0"
                    command="publish"
                    :disabled="row.auditStatus !== 1"
                  >
                    上架
                  </el-dropdown-item>
                  <el-dropdown-item v-if="row.status === 0" command="archive" class="danger-item">
                    归档
                  </el-dropdown-item>
                  <el-dropdown-item
                    v-if="row.status === 2"
                    command="restore"
                    :disabled="row.auditStatus !== 1"
                  >
                    恢复
                  </el-dropdown-item>
                  <el-dropdown-item v-if="row.auditStatus === 2" command="resubmit">
                    提交复审
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </div>
    </div>

    <el-empty v-if="!loading && displayRows.length === 0" :description="emptyDescription" class="empty-block">
      <el-button type="primary" @click="handleAdd">新建职位</el-button>
    </el-empty>

    <!-- 分页 -->
    <div class="pagination-container">
      <el-pagination
        v-model:current-page="queryParams.current"
        v-model:page-size="queryParams.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>

    <!-- 抽屉组件 -->
    <JobDrawer
      v-if="drawerVisible"
      v-model="drawerVisible"
      :current-row="currentRow"
      :category-options="categoryList"
      @success="fetchData"
    />
  </div>
</template>

<script setup>
/*
文件速览：
1. 文件职责：管理职位列表筛选、治理筛选排序、跨分页批量治理快捷动作、状态操作、抽屉编辑与治理通知跳转定位。
2. 对外入口：fetchData、handleEdit、handleBatchRead、handleStartTopGovernanceJob、toggleGovernanceSelection、syncGovernanceFocusFromRoute。
3. 关键结构：activeTab、queryParams、governanceViewState、selectedGovernanceJobIds、selectedGovernanceSnapshotMap、governanceQuickGroups、displayRows、pendingFocusJobId、governanceJumpHint。
4. 阅读建议：先看 fetchData、治理筛选排序与跨分页批量选择 computed，再看治理状态映射、批量动作与跳转定位逻辑。
*/
import { computed, ref, reactive, onMounted, watch, defineAsyncComponent, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMerchantJobs, updateJobStatus, getCategoryList, resubmitJobAudit } from '@/api/job'
import { getMyGovernanceNotices, markGovernanceNoticeRead } from '@/api/governance'
import { useGovernanceStore } from '@/stores/governance'
import { ArrowDown } from '@element-plus/icons-vue'

// 职位抽屉按需加载，仅在用户打开抽屉时请求相关代码。
const JobDrawer = defineAsyncComponent(() => import('@/components/merchant/JobDrawer.vue'))
const route = useRoute()
const router = useRouter()
const governanceStore = useGovernanceStore()

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const drawerVisible = ref(false)
const currentRow = ref(null)
const categoryList = ref([])
const pendingFocusJobId = ref(null)
const governanceJumpHint = ref('')
const jobGovernanceMap = ref({})
const selectedGovernanceJobIds = ref([])
const selectedGovernanceSnapshotMap = ref({})
const batchActionLoading = ref('')
const governanceViewState = reactive({
  filter: 'all',
  sort: 'priority'
})

const queryParams = reactive({
  current: 1,
  size: 10,
  status: null,
  auditStatus: null
})

const activeTab = ref('all')
const governanceFilterOptions = [
  { label: '全部职位', value: 'all' },
  { label: '待整改优先', value: 'actionable' },
  { label: '待复核', value: 'review' },
  { label: '已完成', value: 'finished' },
  { label: '无治理', value: 'none' }
]
const governanceSortOptions = [
  { label: '按治理优先级排序', value: 'priority' },
  { label: '按截止时间最近排序', value: 'deadline' },
  { label: '按最近动作排序', value: 'latestAction' },
  { label: '按职位更新时间排序', value: 'updateTime' }
]

const governanceSummary = computed(() => {
  const summary = {
    pendingRead: 0,
    pendingAction: 0,
    pendingReview: 0,
    finished: 0
  }

  tableData.value.forEach((row) => {
    const notice = getGovernanceNotice(row)
    if (!notice) return
    if (notice.status === 'PENDING_READ') summary.pendingRead += 1
    if (notice.status === 'PENDING_ACTION' || notice.status === 'REJECTED') summary.pendingAction += 1
    if (notice.status === 'PENDING_REVIEW') summary.pendingReview += 1
    if (notice.status === 'FINISHED') summary.finished += 1
  })

  return summary
})

const hasGovernanceSummary = computed(() => {
  return governanceSummary.value.pendingRead
    + governanceSummary.value.pendingAction
    + governanceSummary.value.pendingReview
    + governanceSummary.value.finished > 0
})

const governanceFilterCount = (filter) => {
  return tableData.value.filter((row) => matchesGovernanceFilter(row, filter)).length
}

const compareGovernanceRowsByPriority = (a, b) => {
  const scoreA = getGovernancePriorityScore(a.notice, a.row)
  const scoreB = getGovernancePriorityScore(b.notice, b.row)
  if (scoreA !== scoreB) return scoreB - scoreA

  const dueA = a.notice?.dueTime ? new Date(a.notice.dueTime).getTime() : Number.MAX_SAFE_INTEGER
  const dueB = b.notice?.dueTime ? new Date(b.notice.dueTime).getTime() : Number.MAX_SAFE_INTEGER
  if (dueA !== dueB) return dueA - dueB

  return new Date(b.notice?.latestActionTime || b.notice?.createTime || 0).getTime()
    - new Date(a.notice?.latestActionTime || a.notice?.createTime || 0).getTime()
}

const governanceRowsByPriority = computed(() => {
  return tableData.value
    .map((row) => ({
      row,
      notice: getGovernanceNotice(row)
    }))
    .filter((item) => item.notice)
    .sort(compareGovernanceRowsByPriority)
})

const pendingReadGovernanceRows = computed(() =>
  governanceRowsByPriority.value.filter((item) => item.notice?.status === 'PENDING_READ')
)

const actionableGovernanceRows = computed(() =>
  governanceRowsByPriority.value.filter((item) =>
    ['PENDING_ACTION', 'REJECTED', 'EXPIRED'].includes(item.notice?.status)
  )
)

const pendingReviewGovernanceRows = computed(() =>
  governanceRowsByPriority.value.filter((item) => item.notice?.status === 'PENDING_REVIEW')
)

const displayGovernanceRows = computed(() =>
  displayRows.value
    .map((row) => ({
      row,
      notice: getGovernanceNotice(row)
    }))
    .filter((item) => item.notice)
)

const displayGovernanceRowMap = computed(() => {
  return displayGovernanceRows.value.reduce((result, item) => {
    result[Number(item.row.id)] = item
    return result
  }, {})
})

const selectedGovernanceRows = computed(() => {
  return selectedGovernanceJobIds.value
    .map((id) => {
      const normalizedId = Number(id)
      return displayGovernanceRowMap.value[normalizedId] || selectedGovernanceSnapshotMap.value[normalizedId] || null
    })
    .filter((item) => item?.row && item?.notice)
    .sort(compareGovernanceRowsByPriority)
})

const hasGovernanceSelections = computed(() => selectedGovernanceRows.value.length > 0)

const hasSelectableGovernanceRows = computed(() => displayGovernanceRows.value.length > 0)

const currentPageSelectedCount = computed(() => {
  return displayGovernanceRows.value.filter((item) => isGovernanceSelected(item.row)).length
})

const crossPageSelectedCount = computed(() => {
  return Math.max(selectedGovernanceRows.value.length - currentPageSelectedCount.value, 0)
})

const allDisplayGovernanceSelected = computed(() => {
  return hasSelectableGovernanceRows.value && currentPageSelectedCount.value === displayGovernanceRows.value.length
})

const selectedGovernanceSummary = computed(() => {
  const summary = {
    pendingRead: 0,
    pendingAction: 0,
    pendingReview: 0,
    finished: 0
  }

  selectedGovernanceRows.value.forEach((item) => {
    const status = item.notice?.status
    if (status === 'PENDING_READ') summary.pendingRead += 1
    if (['PENDING_ACTION', 'REJECTED', 'EXPIRED'].includes(status)) summary.pendingAction += 1
    if (status === 'PENDING_REVIEW') summary.pendingReview += 1
    if (status === 'FINISHED') summary.finished += 1
  })

  return summary
})

const pendingReadBatchRows = computed(() =>
  hasGovernanceSelections.value
    ? selectedGovernanceRows.value.filter((item) => item.notice?.status === 'PENDING_READ')
    : pendingReadGovernanceRows.value
)

const actionableBatchRows = computed(() =>
  hasGovernanceSelections.value
    ? selectedGovernanceRows.value.filter((item) => ['PENDING_ACTION', 'REJECTED', 'EXPIRED'].includes(item.notice?.status))
    : actionableGovernanceRows.value
)

const pendingReviewBatchRows = computed(() =>
  hasGovernanceSelections.value
    ? selectedGovernanceRows.value.filter((item) => item.notice?.status === 'PENDING_REVIEW')
    : pendingReviewGovernanceRows.value
)

const governanceQuickGroups = computed(() => [
  {
    key: 'read',
    label: hasGovernanceSelections.value ? '已选待查看' : '待查看队列',
    hint: hasGovernanceSelections.value ? '将只对已选职位中的待查看通知执行已读。' : '先统一确认已读，避免待查看事项持续堆积。',
    count: pendingReadBatchRows.value.length,
    previewTitle: pendingReadBatchRows.value[0]?.row?.title || '当前没有待查看职位',
    previewMeta: pendingReadBatchRows.value[0]?.notice?.dueTime
      ? `最早截止：${pendingReadBatchRows.value[0].notice.dueTime}`
      : (hasGovernanceSelections.value ? '当前已选职位里没有待确认已读事项' : '当前页无需确认新的治理通知'),
    action: 'batchRead'
  },
  {
    key: 'action',
    label: hasGovernanceSelections.value ? '已选待整改' : '待整改队列',
    hint: hasGovernanceSelections.value ? '将从已选职位里定位最优先的整改项。' : '按优先级直接进入当前最该处理的职位。',
    count: actionableBatchRows.value.length,
    previewTitle: actionableBatchRows.value[0]?.row?.title || '当前没有待整改职位',
    previewMeta: actionableBatchRows.value[0]
      ? `${getGovernancePriorityText(actionableBatchRows.value[0].notice, actionableBatchRows.value[0].row)} · ${getGovernanceStageTitle(actionableBatchRows.value[0].notice)}`
      : (hasGovernanceSelections.value ? '当前已选职位里没有待整改事项' : '当前页无需继续修改职位'),
    action: 'editTop'
  },
  {
    key: 'review',
    label: hasGovernanceSelections.value ? '已选待复核' : '待复核队列',
    hint: hasGovernanceSelections.value ? '将从已选职位里打开首条待复核通知。' : '优先查看平台最近反馈，避免遗漏新的审核结果。',
    count: pendingReviewBatchRows.value.length,
    previewTitle: pendingReviewBatchRows.value[0]?.row?.title || '当前没有待复核职位',
    previewMeta: pendingReviewBatchRows.value[0]?.notice?.latestActionTime
      ? `最近提交：${pendingReviewBatchRows.value[0].notice.latestActionTime}`
      : (hasGovernanceSelections.value ? '当前已选职位里没有待复核事项' : '当前页没有等待平台复核的职位'),
    action: 'openReview'
  }
])

const hasGovernanceQuickActions = computed(() =>
  governanceQuickGroups.value.some((item) => item.count > 0)
)

const emptyDescription = computed(() => {
  if (tableData.value.length === 0) return '暂无职位'
  if (governanceViewState.filter === 'actionable') return '当前页没有待整改优先的职位'
  if (governanceViewState.filter === 'review') return '当前页没有待复核职位'
  if (governanceViewState.filter === 'finished') return '当前页没有已完成治理的职位'
  if (governanceViewState.filter === 'none') return '当前页所有职位都已关联治理事项'
  return '当前筛选下暂无职位'
})

/**
 * @description 在当前页内给治理事项打优先级分，供筛选和排序共用。
 */
const getGovernancePriorityScore = (notice, row) => {
  if (!notice) return 0
  let score = 0
  if (isGovernanceOverdue(notice)) score += 400
  if (notice.status === 'EXPIRED') score += 320
  if (notice.status === 'REJECTED') score += 280
  if (notice.status === 'PENDING_ACTION') score += 240
  if (notice.status === 'PENDING_READ') score += 220
  if (notice.status === 'PENDING_REVIEW') score += 180
  if (notice.severity === 'HIGH') score += 120
  if (notice.severity === 'WARNING') score += 80
  if (row?.auditStatus === 2) score += 40
  return score
}

const getGovernancePriorityText = (notice, row) => {
  if (!notice) return '常规'
  const score = getGovernancePriorityScore(notice, row)
  if (score >= 360) return 'P0 立即处理'
  if (score >= 220) return 'P1 今日处理'
  if (score >= 120) return 'P2 跟进中'
  return 'P3 常规'
}

const getGovernancePriorityTag = (notice, row) => {
  if (!notice) return 'info'
  const score = getGovernancePriorityScore(notice, row)
  if (score >= 360) return 'danger'
  if (score >= 220) return 'warning'
  if (score >= 120) return 'primary'
  return 'info'
}

const matchesGovernanceFilter = (row, filter = governanceViewState.filter) => {
  const notice = getGovernanceNotice(row)
  if (filter === 'none') return !notice
  if (filter === 'review') return notice?.status === 'PENDING_REVIEW'
  if (filter === 'finished') return notice?.status === 'FINISHED'
  if (filter === 'actionable') return ['PENDING_READ', 'PENDING_ACTION', 'REJECTED', 'EXPIRED'].includes(notice?.status)
  return true
}

const displayRows = computed(() => {
  const rows = tableData.value.filter((row) => matchesGovernanceFilter(row))
  return [...rows].sort((a, b) => {
    const noticeA = getGovernanceNotice(a)
    const noticeB = getGovernanceNotice(b)
    if (governanceViewState.sort === 'deadline') {
      const dueA = noticeA?.dueTime ? new Date(noticeA.dueTime).getTime() : Number.MAX_SAFE_INTEGER
      const dueB = noticeB?.dueTime ? new Date(noticeB.dueTime).getTime() : Number.MAX_SAFE_INTEGER
      if (dueA !== dueB) return dueA - dueB
    } else if (governanceViewState.sort === 'latestAction') {
      const actionA = new Date(noticeA?.latestActionTime || noticeA?.createTime || 0).getTime()
      const actionB = new Date(noticeB?.latestActionTime || noticeB?.createTime || 0).getTime()
      if (actionA !== actionB) return actionB - actionA
    } else if (governanceViewState.sort === 'updateTime') {
      const updateA = new Date(a.updateTime || a.createTime || 0).getTime()
      const updateB = new Date(b.updateTime || b.createTime || 0).getTime()
      if (updateA !== updateB) return updateB - updateA
    } else {
      const scoreA = getGovernancePriorityScore(noticeA, a)
      const scoreB = getGovernancePriorityScore(noticeB, b)
      if (scoreA !== scoreB) return scoreB - scoreA
      const dueA = noticeA?.dueTime ? new Date(noticeA.dueTime).getTime() : Number.MAX_SAFE_INTEGER
      const dueB = noticeB?.dueTime ? new Date(noticeB.dueTime).getTime() : Number.MAX_SAFE_INTEGER
      if (dueA !== dueB) return dueA - dueB
    }
    return new Date(b.updateTime || b.createTime || 0).getTime() - new Date(a.updateTime || a.createTime || 0).getTime()
  })
})

// 获取职位列表
const fetchData = async () => {
  loading.value = true
  try {
    const res = await getMerchantJobs(queryParams)
    if (res.code === 200 && res.data) {
      tableData.value = res.data.records || []
      total.value = res.data.total || 0
      await fetchGovernanceSnapshot()
      await nextTick()
      tryOpenFocusJob()
    } else {
      tableData.value = []
      total.value = 0
      jobGovernanceMap.value = {}
    }
  } catch (error) {
    console.error('获取职位列表失败', error)
    tableData.value = []
    total.value = 0
    jobGovernanceMap.value = {}
  } finally {
    loading.value = false
  }
}

// 获取分类列表
const fetchCategories = async () => {
  try {
    const res = await getCategoryList()
    if (res.code === 200) {
      // 后端直接返回树形结构，无需前端转换
      categoryList.value = res.data
    }
  } catch (error) {
    console.error('获取分类失败', error)
  }
}

// 新建职位
const handleAdd = () => {
  currentRow.value = null
  drawerVisible.value = true
}

// 编辑职位
const handleEdit = (row) => {
  // 统一兼容驼峰/下划线字段，避免已有值被 undefined 覆盖。
  currentRow.value = {
    ...row,
    min_salary: row.min_salary ?? row.minSalary,
    max_salary: row.max_salary ?? row.maxSalary,
    work_location: row.work_location ?? row.workLocation,
    category_id: row.category_id ?? row.categoryId
  }
  drawerVisible.value = true
}

const fetchGovernanceSnapshot = async () => {
  const jobIds = tableData.value.map((item) => Number(item.id)).filter((id) => Number.isFinite(id) && id > 0)
  if (jobIds.length === 0) {
    jobGovernanceMap.value = {}
    return
  }

  try {
    const res = await getMyGovernanceNotices({
      current: 1,
      size: 200
    })
    if (res.code !== 200) {
      jobGovernanceMap.value = {}
      return
    }

    const notices = Array.isArray(res.data?.records) ? res.data.records : []
    const latestMap = {}
    notices.forEach((item) => {
      const relatedJobId = Number(item.relatedJobId)
      if (!jobIds.includes(relatedJobId)) return
      const previous = latestMap[relatedJobId]
      if (!previous) {
        latestMap[relatedJobId] = item
        return
      }
      const currentTime = new Date(item.latestActionTime || item.createTime || 0).getTime()
      const previousTime = new Date(previous.latestActionTime || previous.createTime || 0).getTime()
      if (currentTime >= previousTime) {
        latestMap[relatedJobId] = item
      }
    })
    jobGovernanceMap.value = latestMap
  } catch (error) {
    console.error('获取治理状态失败', error)
    jobGovernanceMap.value = {}
  }
}

const refreshGovernanceSummary = async () => {
  try {
    await governanceStore.fetchSummary('MERCHANT', {
      force: true,
      cacheMs: 0
    })
  } catch (error) {
    // 这里不额外打断职位页操作，导航与工作台会在后续轮询中再次同步。
  }
}

const getGovernanceNotice = (row) => {
  if (!row?.id) return null
  return jobGovernanceMap.value[Number(row.id)] || null
}

const buildGovernanceSelectionSnapshot = (row, notice = getGovernanceNotice(row)) => {
  if (!row?.id || !notice?.id) return null
  return {
    row: { ...row },
    notice: { ...notice }
  }
}

const syncSelectedGovernanceSnapshotsWithCurrentPage = () => {
  const nextSnapshotMap = { ...selectedGovernanceSnapshotMap.value }
  selectedGovernanceJobIds.value.forEach((id) => {
    const currentItem = displayGovernanceRowMap.value[Number(id)]
    if (!currentItem) return
    const snapshot = buildGovernanceSelectionSnapshot(currentItem.row, currentItem.notice)
    if (snapshot) {
      nextSnapshotMap[Number(id)] = snapshot
    } else {
      delete nextSnapshotMap[Number(id)]
    }
  })
  selectedGovernanceSnapshotMap.value = nextSnapshotMap
}

const isGovernanceSelected = (row) => {
  return selectedGovernanceJobIds.value.includes(Number(row?.id))
}

const toggleGovernanceSelection = (row, checked) => {
  const jobId = Number(row?.id)
  const notice = getGovernanceNotice(row)
  if (!Number.isFinite(jobId) || !notice) return
  const nextIds = new Set(selectedGovernanceJobIds.value.map((id) => Number(id)))
  const nextSnapshotMap = { ...selectedGovernanceSnapshotMap.value }
  if (checked) {
    nextIds.add(jobId)
    nextSnapshotMap[jobId] = buildGovernanceSelectionSnapshot(row, notice)
  } else {
    nextIds.delete(jobId)
    delete nextSnapshotMap[jobId]
  }
  selectedGovernanceJobIds.value = [...nextIds]
  selectedGovernanceSnapshotMap.value = nextSnapshotMap
}

const clearGovernanceSelection = () => {
  selectedGovernanceJobIds.value = []
  selectedGovernanceSnapshotMap.value = {}
}

const addGovernanceSelections = (items) => {
  const nextIds = new Set(selectedGovernanceJobIds.value.map((id) => Number(id)))
  const nextSnapshotMap = { ...selectedGovernanceSnapshotMap.value }
  items.forEach((item) => {
    const jobId = Number(item?.row?.id)
    if (!Number.isFinite(jobId) || !item?.notice?.id) return
    nextIds.add(jobId)
    nextSnapshotMap[jobId] = buildGovernanceSelectionSnapshot(item.row, item.notice)
  })
  selectedGovernanceJobIds.value = [...nextIds]
  selectedGovernanceSnapshotMap.value = nextSnapshotMap
}

const selectAllGovernanceRows = () => {
  addGovernanceSelections(displayGovernanceRows.value)
}

const selectGovernanceRowsByStage = (stage) => {
  const sourceRows = stage === 'review'
    ? pendingReviewGovernanceRows.value
    : actionableGovernanceRows.value

  if (!sourceRows.length) {
    ElMessage.info(stage === 'review' ? '当前页没有待复核职位' : '当前页没有待整改职位')
    return
  }
  addGovernanceSelections(sourceRows)
}

const removeCurrentPageGovernanceSelections = () => {
  if (currentPageSelectedCount.value === 0) {
    ElMessage.info('当前页没有已选治理职位')
    return
  }
  const currentPageIds = new Set(displayGovernanceRows.value.map((item) => Number(item.row.id)))
  selectedGovernanceJobIds.value = selectedGovernanceJobIds.value.filter((id) => !currentPageIds.has(Number(id)))
  const nextSnapshotMap = { ...selectedGovernanceSnapshotMap.value }
  currentPageIds.forEach((id) => {
    delete nextSnapshotMap[id]
  })
  selectedGovernanceSnapshotMap.value = nextSnapshotMap
}

const isGovernanceOverdue = (notice) => {
  if (!notice?.dueTime) return false
  if (['FINISHED', 'CLOSED', 'EXPIRED'].includes(notice.status)) return false
  return new Date(notice.dueTime).getTime() < Date.now()
}

const getGovernanceStatusText = (status) => {
  const map = {
    PENDING_READ: '待查看',
    PENDING_ACTION: '待整改',
    PENDING_REVIEW: '待复核',
    FINISHED: '已完成',
    REJECTED: '需继续修改',
    EXPIRED: '已失效',
    CLOSED: '已关闭'
  }
  return map[status] || '治理中'
}

const getGovernanceStatusTag = (status) => {
  const map = {
    PENDING_READ: 'info',
    PENDING_ACTION: 'warning',
    PENDING_REVIEW: 'primary',
    FINISHED: 'success',
    REJECTED: 'danger',
    EXPIRED: 'danger',
    CLOSED: 'info'
  }
  return map[status] || 'info'
}

const getGovernanceStageTitle = (notice) => {
  if (!notice) return ''
  const map = {
    PENDING_READ: '平台已下发整改要求，等待你确认并开始处理',
    PENDING_ACTION: '当前职位处于整改阶段，请先修改职位内容',
    PENDING_REVIEW: '已提交复核说明，等待管理员再次审核',
    FINISHED: '整改事项已完成，可继续正常运营该职位',
    REJECTED: '本次整改结果未通过，请根据说明继续修改',
    EXPIRED: '整改时限已过，请尽快联系管理员处理',
    CLOSED: '该治理事项已关闭'
  }
  return map[notice.status] || '该职位已关联平台治理事项'
}

const getGovernanceStageDesc = (notice, row) => {
  if (!notice) return row?.auditReason || '暂无治理说明'
  if (notice.requiredAction) return notice.requiredAction
  if (notice.summary) return notice.summary
  return row?.auditReason || '暂无治理说明'
}

const formatGovernanceNowText = () => {
  const now = new Date()
  const pad = (value) => String(value).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
}

const canEditFromGovernance = (notice) => {
  return ['PENDING_READ', 'PENDING_ACTION', 'REJECTED'].includes(notice?.status)
}

const goToGovernanceNotice = (noticeId) => {
  if (!noticeId) {
    router.push('/merchant/governance')
    return
  }
  router.push({
    path: '/merchant/governance',
    query: {
      noticeId: String(noticeId)
    }
  })
}

const handleStartTopGovernanceJob = () => {
  const target = actionableBatchRows.value[0]?.row
  if (!target) {
    ElMessage.info(hasGovernanceSelections.value ? '当前已选职位里没有可直接处理的整改项' : '当前页没有可直接处理的整改职位')
    return
  }
  handleEdit(target)
  ElMessage.info(`已为你打开${hasGovernanceSelections.value ? '已选范围内' : ''}优先级最高的待整改职位：${target.title}`)
}

const handleOpenTopReviewNotice = () => {
  const targetNotice = pendingReviewBatchRows.value[0]?.notice
  if (!targetNotice) {
    ElMessage.info(hasGovernanceSelections.value ? '当前已选职位里没有待复核通知' : '当前页没有待复核通知')
    return
  }
  goToGovernanceNotice(targetNotice.id)
}

const handleBatchRead = async () => {
  const pendingRows = pendingReadBatchRows.value
  const noticeIds = pendingRows
    .map((item) => Number(item.notice?.id))
    .filter((id) => Number.isFinite(id) && id > 0)

  if (noticeIds.length === 0) {
    ElMessage.info(hasGovernanceSelections.value ? '当前已选职位里没有待确认已读的治理通知' : '当前页没有待确认已读的治理通知')
    return
  }

  batchActionLoading.value = 'batchRead'
  try {
    const results = await Promise.allSettled(noticeIds.map((id) => markGovernanceNoticeRead(id)))
    const successNoticeIds = results
      .map((item, index) => ({
        result: item,
        noticeId: noticeIds[index]
      }))
      .filter((item) => item.result.status === 'fulfilled' && item.result.value?.code === 200)
      .map((item) => item.noticeId)
    const successCount = successNoticeIds.length
    const failedCount = noticeIds.length - successCount

    if (successNoticeIds.length > 0) {
      const pendingRowMap = pendingRows.reduce((result, item) => {
        result[Number(item.notice?.id)] = item
        return result
      }, {})
      const nextSnapshotMap = { ...selectedGovernanceSnapshotMap.value }
      const nowText = formatGovernanceNowText()
      successNoticeIds.forEach((noticeId) => {
        const sourceItem = pendingRowMap[noticeId]
        const jobId = Number(sourceItem?.row?.id)
        if (!Number.isFinite(jobId) || !sourceItem?.notice) return
        nextSnapshotMap[jobId] = {
          row: { ...sourceItem.row },
          notice: {
            ...sourceItem.notice,
            status: Number(sourceItem.notice.needReply) === 1 ? 'PENDING_ACTION' : 'FINISHED',
            readTime: nowText,
            latestActionTime: nowText,
            updateTime: nowText,
            closedTime: Number(sourceItem.notice.needReply) === 1 ? null : nowText
          }
        }
      })
      selectedGovernanceSnapshotMap.value = nextSnapshotMap
    }

    await fetchData()
    await refreshGovernanceSummary()

    if (failedCount > 0) {
      ElMessage.warning(`已确认 ${successCount} 条通知，另有 ${failedCount} 条未处理成功`)
      return
    }
    ElMessage.success(`已批量确认 ${successCount} 条治理通知`)
  } catch (error) {
    ElMessage.error(error?.message || '批量确认已读失败')
  } finally {
    batchActionLoading.value = ''
  }
}

const clearGovernanceQuery = async () => {
  const nextQuery = { ...route.query }
  delete nextQuery.openEdit
  delete nextQuery.focusJob
  await router.replace({
    path: route.path,
    query: nextQuery
  })
}

const tryOpenFocusJob = async () => {
  if (!pendingFocusJobId.value) return

  const targetRow = tableData.value.find((item) => Number(item.id) === Number(pendingFocusJobId.value))
  if (!targetRow) {
    return
  }

  handleEdit(targetRow)
  ElMessage.info('已从平台通知定位到待整改职位，请完成修改后再提交复审。')
  pendingFocusJobId.value = null
  governanceJumpHint.value = ''
  await clearGovernanceQuery()
}

const syncGovernanceFocusFromRoute = () => {
  const openEdit = route.query.openEdit === '1'
  const focusJob = Number(route.query.focusJob)
  if (!openEdit || !Number.isFinite(focusJob) || focusJob <= 0) {
    return false
  }

  pendingFocusJobId.value = focusJob
  governanceJumpHint.value = '当前是从平台通知跳转而来，请先修改职位，再在详情中提交复核说明。'
  governanceViewState.filter = 'actionable'
  governanceViewState.sort = 'priority'

  if (activeTab.value !== 'rejected') {
    activeTab.value = 'rejected'
  } else {
    queryParams.current = 1
    applyTabFilter()
  }
  return true
}

const getJobStatusText = (status) => {
  const map = { 1: '招聘中', 0: '已下架', 2: '已归档' }
  return map[status] || '未知'
}

const getJobStatusTag = (status) => {
  const map = { 1: 'success', 0: 'info', 2: 'warning' }
  return map[status] || 'info'
}

const getAuditText = (status) => {
  const map = { 0: '审核中', 1: '已通过', 2: '已驳回' }
  return map[status] || '未知'
}

const getAuditTag = (status) => {
  const map = { 0: 'warning', 1: 'success', 2: 'danger' }
  return map[status] || 'info'
}

const applyTabFilter = () => {
  if (activeTab.value === 'recruiting') {
    queryParams.status = 1
    queryParams.auditStatus = 1
  } else if (activeTab.value === 'pending') {
    queryParams.status = null
    queryParams.auditStatus = 0
  } else if (activeTab.value === 'rejected') {
    queryParams.status = null
    queryParams.auditStatus = 2
  } else if (activeTab.value === 'offline') {
    queryParams.status = 0
    queryParams.auditStatus = null
  } else if (activeTab.value === 'archived') {
    queryParams.status = 2
    queryParams.auditStatus = null
  } else {
    queryParams.status = null
    queryParams.auditStatus = null
  }
  queryParams.current = 1
  fetchData()
}

// 状态变更
const setStatus = async (row, targetStatus) => {
  if (targetStatus === 1 && row.auditStatus !== 1) {
    ElMessage.warning('职位未通过审核，暂不能上架')
    return
  }
  const actionMap = { 0: '下架', 1: '上架', 2: '归档' }
  const actionText = actionMap[targetStatus] || '更新'

  try {
    await updateJobStatus(row.id, targetStatus)
    ElMessage.success(`职位已${actionText}`)
    fetchData()
  } catch (error) {
    console.error('状态修改失败', error)
    ElMessage.error('操作失败，请重试')
  }
}

const resubmitAudit = async (row) => {
  try {
    await resubmitJobAudit(row.id)
    ElMessage.success('已提交复审')
    await fetchData()
    await refreshGovernanceSummary()
  } catch (error) {
    console.error('提交复审失败', error)
    ElMessage.error('提交失败，请稍后重试')
  }
}

const handleMoreCommand = async (row, command) => {
  if (command === 'offline') {
    try {
      await ElMessageBox.confirm('确定下架该职位吗？下架后求职者将无法查看。', '确认下架', {
        type: 'warning',
        confirmButtonText: '确认下架',
        cancelButtonText: '取消'
      })
      await setStatus(row, 0)
    } catch (error) {
      return
    }
    return
  }
  if (command === 'archive') {
    try {
      await ElMessageBox.confirm('确定归档该职位吗？归档后将从主列表移出。', '确认归档', {
        type: 'warning',
        confirmButtonText: '确认归档',
        cancelButtonText: '取消'
      })
      await setStatus(row, 2)
    } catch (error) {
      return
    }
    return
  }
  if (command === 'publish') {
    setStatus(row, 1)
    return
  }
  if (command === 'restore') {
    setStatus(row, 1)
    return
  }
  if (command === 'resubmit') {
    resubmitAudit(row)
  }
}

// 分页处理
const handleSizeChange = (val) => {
  queryParams.size = val
  fetchData()
}

const handleCurrentChange = (val) => {
  queryParams.current = val
  fetchData()
}

onMounted(() => {
  const jumpedFromGovernance = syncGovernanceFocusFromRoute()
  if (!jumpedFromGovernance) {
    fetchData()
  }
  fetchCategories()
})

watch(activeTab, applyTabFilter)

watch(
  displayGovernanceRows,
  () => {
    syncSelectedGovernanceSnapshotsWithCurrentPage()
  },
  { immediate: true }
)

watch(
  () => [route.query.openEdit, route.query.focusJob],
  () => {
    syncGovernanceFocusFromRoute()
  }
)
</script>

<style scoped>
.job-manage-container {
  padding: 20px 18px;
  background-color: transparent;
  border-radius: 0;
  border: none;
  min-height: calc(100vh - 40px);
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
}

.governance-hint {
  margin-bottom: 12px;
}

.list-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.status-tabs {
  margin: 6px 0 12px;
}

.governance-summary {
  margin-bottom: 14px;
  display: flex;
  justify-content: space-between;
  gap: 18px;
  padding: 16px 18px;
  border-radius: 18px;
  border: 1px solid rgba(59, 130, 246, 0.12);
  background:
    radial-gradient(circle at left top, rgba(59, 130, 246, 0.12), transparent 30%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(248, 250, 252, 0.94));
}

.governance-summary__copy {
  display: grid;
  gap: 6px;
  max-width: 420px;
}

.governance-summary__title {
  font-size: 15px;
  font-weight: 700;
  color: #111827;
}

.governance-summary__desc {
  font-size: 13px;
  color: #64748b;
  line-height: 1.6;
}

.governance-summary__stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  min-width: 420px;
}

.governance-summary__item {
  display: grid;
  gap: 4px;
  padding: 12px 14px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(148, 163, 184, 0.14);
}

.governance-summary__label {
  font-size: 12px;
  color: #94a3b8;
}

.governance-summary__value {
  font-size: 22px;
  font-weight: 700;
  color: #0f172a;
}

.governance-toolbar {
  margin-bottom: 14px;
  display: grid;
  gap: 14px;
  padding: 16px 18px;
  border-radius: 18px;
  border: 1px solid rgba(148, 163, 184, 0.14);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(248, 250, 252, 0.94));
}

.governance-toolbar__copy {
  display: grid;
  gap: 6px;
}

.governance-toolbar__title {
  font-size: 15px;
  font-weight: 700;
  color: #111827;
}

.governance-toolbar__desc,
.governance-toolbar__meta {
  font-size: 13px;
  color: #64748b;
  line-height: 1.6;
}

.governance-toolbar__main {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: center;
}

.governance-selection {
  display: grid;
  gap: 14px;
  padding: 15px 16px;
  border-radius: 18px;
  border: 1px solid rgba(0, 113, 227, 0.12);
  background:
    linear-gradient(145deg, rgba(0, 113, 227, 0.08), rgba(255, 255, 255, 0.96));
}

.governance-selection__copy {
  display: grid;
  gap: 6px;
}

.governance-selection__title {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
}

.governance-selection__desc {
  font-size: 13px;
  color: #64748b;
  line-height: 1.7;
}

.governance-selection__stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.governance-selection__stat {
  display: grid;
  gap: 4px;
  padding: 12px 14px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.86);
  border: 1px solid rgba(148, 163, 184, 0.12);
}

.governance-selection__label {
  font-size: 12px;
  color: #94a3b8;
}

.governance-selection__value {
  font-size: 20px;
  font-weight: 700;
  color: #0f172a;
}

.governance-selection__memory {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.governance-selection__memory-chip {
  display: inline-flex;
  align-items: center;
  padding: 7px 12px;
  border-radius: 999px;
  border: 1px solid rgba(0, 113, 227, 0.12);
  background: rgba(255, 255, 255, 0.86);
  color: #2563eb;
  font-size: 12px;
  font-weight: 700;
}

.governance-selection__memory-text {
  font-size: 12px;
  line-height: 1.7;
  color: #64748b;
}

.governance-selection__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.governance-quick-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.governance-quick-card {
  display: grid;
  gap: 12px;
  padding: 16px;
  border-radius: 18px;
  border: 1px solid rgba(148, 163, 184, 0.14);
  background: rgba(255, 255, 255, 0.84);
}

.governance-quick-card--read {
  background:
    radial-gradient(circle at top left, rgba(59, 130, 246, 0.08), transparent 26%),
    rgba(255, 255, 255, 0.92);
}

.governance-quick-card--action {
  background:
    radial-gradient(circle at top left, rgba(245, 158, 11, 0.08), transparent 26%),
    rgba(255, 255, 255, 0.92);
}

.governance-quick-card--review {
  background:
    radial-gradient(circle at top left, rgba(16, 185, 129, 0.08), transparent 26%),
    rgba(255, 255, 255, 0.92);
}

.governance-quick-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.governance-quick-card__label {
  font-size: 14px;
  font-weight: 700;
  color: #111827;
}

.governance-quick-card__hint {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.6;
  color: #64748b;
}

.governance-quick-card__count {
  font-size: 26px;
  line-height: 1;
  font-weight: 700;
  color: #0f172a;
}

.governance-quick-card__body {
  display: grid;
  gap: 6px;
  min-height: 62px;
}

.governance-quick-card__title {
  font-size: 14px;
  line-height: 1.6;
  font-weight: 700;
  color: #0f172a;
}

.governance-quick-card__meta {
  font-size: 12px;
  line-height: 1.7;
  color: #64748b;
}

.governance-quick-card__actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.governance-filter-group {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.governance-filter-chip {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border-radius: 999px;
  border: 1px solid rgba(148, 163, 184, 0.18);
  background: rgba(255, 255, 255, 0.86);
  color: #475569;
  cursor: pointer;
  transition: transform .2s ease, border-color .2s ease, box-shadow .2s ease, color .2s ease;
}

.governance-filter-chip strong {
  color: #0f172a;
}

.governance-filter-chip:hover {
  transform: translateY(-1px);
  border-color: rgba(0, 113, 227, 0.28);
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.06);
}

.governance-filter-chip--active {
  border-color: rgba(0, 113, 227, 0.3);
  background: linear-gradient(135deg, rgba(0, 113, 227, 0.12), rgba(255, 255, 255, 0.95));
  color: #0f172a;
}

.governance-toolbar__aside {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  align-items: center;
  gap: 12px;
}

.governance-sort-select {
  width: 220px;
}

.status-tabs-inner :deep(.el-tabs__nav-wrap::after) {
  background-color: transparent;
}

.status-tabs-inner :deep(.el-tabs__item) {
  font-size: 14px;
  color: #475569;
}

.status-tabs-inner :deep(.el-tabs__item.is-active) {
  color: #1d4ed8;
  font-weight: 600;
}

.job-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.job-card {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 18px;
  border: 1px solid #edf1f7;
  border-radius: 14px;
  background: #fff;
  transition: box-shadow 0.2s ease, border-color 0.2s ease;
}

.job-card--selected {
  border-color: rgba(0, 113, 227, 0.28);
  box-shadow: 0 12px 24px rgba(0, 113, 227, 0.08);
}

.job-card:hover {
  border-color: #e2e8f0;
  box-shadow: 0 10px 20px rgba(15, 23, 42, 0.08);
}

.job-card--selected:hover {
  border-color: rgba(0, 113, 227, 0.28);
  box-shadow: 0 14px 26px rgba(0, 113, 227, 0.1);
}

.job-main {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.job-manage-container::before {
  content: '';
  display: block;
}

.job-manage-container :deep(.el-tabs__header) {
  margin-bottom: 0;
}

.job-manage-container :deep(.el-tabs__nav-wrap) {
  min-height: 44px;
}

.job-tag-group {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.job-title-text {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
}

.job-card__head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.job-card__selector {
  flex-shrink: 0;
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(248, 250, 252, 0.92);
  border: 1px solid rgba(148, 163, 184, 0.14);
}

.job-card__selector :deep(.el-checkbox__label) {
  font-size: 12px;
  color: #475569;
}

.job-sub-info {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #94a3b8;
}

.job-sub-info .dot {
  color: #cbd5f5;
}

.job-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.job-meta-line {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  font-size: 12px;
  color: #94a3b8;
}

.job-meta-line :deep(.el-tag) {
  height: 20px;
  line-height: 18px;
  border-radius: 10px;
}

.governance-inline {
  display: grid;
  gap: 10px;
  padding: 14px 16px;
  border-radius: 16px;
  border: 1px solid rgba(59, 130, 246, 0.16);
  background:
    linear-gradient(180deg, rgba(239, 246, 255, 0.95), rgba(255, 255, 255, 0.98));
}

.governance-inline--overdue {
  border-color: rgba(239, 68, 68, 0.18);
  background:
    linear-gradient(180deg, rgba(254, 242, 242, 0.95), rgba(255, 255, 255, 0.98));
}

.governance-inline__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.governance-inline__label {
  font-size: 12px;
  color: #94a3b8;
}

.governance-inline__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.governance-inline__title {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
}

.governance-inline__desc {
  color: #475569;
  line-height: 1.7;
}

.governance-inline__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  font-size: 12px;
  color: #94a3b8;
}

.governance-inline__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.meta-label {
  color: #94a3b8;
}

.job-manage-container :deep(.el-dropdown-menu__item.danger-item) {
  color: #dc2626;
}

.job-manage-container :deep(.el-dropdown-menu__item.danger-item:hover) {
  background-color: #fee2e2;
}

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
  flex-shrink: 0;
}

@media (max-width: 960px) {
  .governance-summary {
    flex-direction: column;
  }

  .governance-toolbar__main {
    flex-direction: column;
    align-items: stretch;
  }

  .governance-quick-grid {
    grid-template-columns: 1fr;
  }

  .governance-selection__stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .governance-toolbar__aside {
    justify-content: space-between;
  }

  .governance-sort-select {
    width: 100%;
  }

  .governance-summary__stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    min-width: 0;
  }

  .governance-inline__head {
    flex-direction: column;
    align-items: flex-start;
  }

  .governance-inline__meta {
    flex-direction: column;
    gap: 6px;
  }

  .job-card__head {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
