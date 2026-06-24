<!--
文件速览：
1. 文件职责：求职者面试日程页，负责周/月/列表视图切换、日历侧栏与面试邀约确认。
2. 页面入口：求职者路由 `/applicant/interviews`。
3. 关键结构：overview-grid、toolbar-row、summary-card、weekDayCards、monthDayCards、sideTabSections、currentInviteDisplay、displayInterviewRounds。
4. 阅读建议：先看顶部工具栏和统计条，再看周/月视图，最后看面试详情弹窗。
-->
<template>
  <div class="interview-page">
    <el-card shadow="never" class="interview-card">

      <div class="overview-grid">
        <div class="overview-card">
          <div class="toolbar-row">
            <div class="toolbar-left">
              <el-radio-group v-model="viewMode">
                <el-radio-button value="week">周视图</el-radio-button>
                <el-radio-button value="month">月视图</el-radio-button>
                <el-radio-button value="list">列表视图</el-radio-button>
              </el-radio-group>
              <el-date-picker
                v-model="selectedDate"
                type="date"
                format="YYYY-MM-DD"
                :clearable="false"
                :editable="false"
                class="toolbar-date"
              />
            </div>
            <div class="toolbar-right">
              <el-button size="small" @click="goToday">今天</el-button>
              <el-button size="small" @click="goPrev">{{ prevLabel }}</el-button>
              <el-button size="small" @click="goNext">{{ nextLabel }}</el-button>
            </div>
          </div>

          <div class="legend-row">
            <span class="legend-title">标签说明</span>
            <el-tag size="small" type="success">线上面试</el-tag>
            <el-tag size="small" type="warning">线下面试</el-tag>
            <el-tag size="small" type="info">待确认</el-tag>
            <el-tag size="small" type="danger">24小时内</el-tag>
          </div>
        </div>

        <div class="summary-card">
          <div class="summary-strip">
            <div class="summary-item is-today" role="button" tabindex="0" @click="handleSummaryClick('today')">
              <span class="summary-label">今日</span>
              <span class="summary-value">{{ interviewStats.today }}</span>
            </div>
            <div class="summary-item is-pending" role="button" tabindex="0" @click="handleSummaryClick('pending')">
              <span class="summary-label">待确认</span>
              <span class="summary-value">{{ interviewStats.pending }}</span>
            </div>
            <div class="summary-item is-week" role="button" tabindex="0" @click="handleSummaryClick('week')">
              <span class="summary-label">本周</span>
              <span class="summary-value">{{ interviewStats.week }}</span>
            </div>
            <div class="summary-item is-total" role="button" tabindex="0" @click="handleSummaryClick('total')">
              <span class="summary-label">总计</span>
              <span class="summary-value">{{ interviewStats.total }}</span>
            </div>
          </div>
        </div>
      </div>

      <el-skeleton v-if="loading" animated :rows="6" />

      <template v-else>
        <div v-if="interviewItems.length">
          <div v-if="isCalendarView" :class="calendarPanelClass">
            <div class="calendar-main">
              <template v-if="!isMonthView">
                <div class="calendar-header">
                  <span>{{ weekLabel }}</span>
                  <span class="calendar-hint">点击面试卡片查看轮次详情</span>
                </div>
                <div class="calendar-grid">
                  <div class="calendar-row calendar-head">
                    <div v-for="card in weekDayCards" :key="card.dateKey" class="calendar-head-cell">
                      {{ formatWeekday(card.day) }}
                    </div>
                  </div>
                  <div class="calendar-row calendar-body">
                    <div
                      v-for="card in weekDayCards"
                      :key="card.dateKey + '-cell'"
                      class="calendar-cell"
                      :class="{ 'is-today': isToday(card.day) }"
                    >
                      <div class="calendar-date">{{ formatDateLabel(card.day) }}</div>
                      <div class="calendar-events">
                        <div
                          v-for="item in card.previewItems"
                          :key="item.id"
                          class="calendar-event"
                          @click="openInterviewByItem(item)"
                        >
                          <div class="event-time">{{ item.timeText }}</div>
                          <div class="event-title">{{ item.jobTitleText }}</div>
                          <div class="event-company">{{ item.companyNameText }}</div>
                          <div class="event-tags">
                            <el-tag v-for="tag in item.calendarTagItems" :key="tag.key" size="small" :type="tag.type">
                              {{ tag.text }}
                            </el-tag>
                          </div>
                        </div>
                        <div v-if="card.moreCount" class="calendar-more">还有 {{ card.moreCount }} 场</div>
                        <div v-if="card.items.length === 0" class="calendar-empty">暂无安排</div>
                      </div>
                    </div>
                  </div>
                </div>
              </template>
              <template v-else>
                <div class="calendar-header">
                  <span>{{ monthLabel }}</span>
                  <span class="calendar-hint">点击日期查看当天安排</span>
                </div>
                <div class="month-grid">
                  <div class="calendar-row calendar-head">
                    <div v-for="label in weekHeader" :key="label" class="calendar-head-cell">
                      {{ label }}
                    </div>
                  </div>
                  <div class="calendar-row month-body">
                    <div
                      v-for="card in monthDayCards"
                      :key="card.dateKey + '-month'"
                      class="month-cell"
                      :class="{
                        'is-outside': !isSameMonth(card.day, selectedDate),
                        'is-today': isToday(card.day),
                        'is-selected': isSelectedDate(card.day)
                      }"
                      @click="selectDate(card.day)"
                    >
                      <div class="month-date">{{ formatDateLabel(card.day) }}</div>
                      <div class="month-events">
                        <div
                          v-for="item in card.previewItems"
                          :key="item.id"
                          class="month-event"
                          @click.stop="openInterviewByItem(item)"
                        >
                          <span class="month-event-time">{{ item.timeText }}</span>
                          <span class="month-event-title">{{ item.compactJobTitleText }}</span>
                        </div>
                        <div v-if="card.moreCount" class="month-more">还有 {{ card.moreCount }} 场</div>
                      </div>
                    </div>
                  </div>
                </div>
              </template>
            </div>

            <div class="calendar-side">
              <div class="side-card side-card--tabs">
                <el-tabs v-model="sideTab" class="side-tabs">
                  <el-tab-pane
                    v-for="section in sideTabSections"
                    :key="section.name"
                    :label="section.label"
                    :name="section.name"
                  >
                    <div class="side-title">{{ section.title }}</div>
                    <div v-if="section.items.length" class="side-list">
                      <div v-for="item in section.items" :key="item.id" class="side-item">
                        <div class="side-row">
                          <span class="side-job">{{ item.jobTitleText }}</span>
                          <el-tag v-if="item.badgeText" size="small" :type="item.badgeType">
                            {{ item.badgeText }}
                          </el-tag>
                          <span v-else-if="item.trailingText" class="side-time">{{ item.trailingText }}</span>
                        </div>
                        <div class="side-meta">{{ item.primaryMeta }}</div>
                        <div v-if="item.secondaryMeta" class="side-meta">{{ item.secondaryMeta }}</div>
                        <el-button v-if="item.showAction" size="small" plain @click="openInterviewByItem(item)">
                          查看安排
                        </el-button>
                      </div>
                    </div>
                    <el-empty v-else :description="section.emptyDescription" />
                  </el-tab-pane>
                </el-tabs>
              </div>
            </div>
          </div>

          <div v-else class="list-view">
            <el-table :data="interviewListView" stripe class="interview-table">
              <el-table-column label="日期" width="120">
                <template #default="{ row }">
                  {{ row.dateText }}
                </template>
              </el-table-column>
              <el-table-column label="时间" width="90">
                <template #default="{ row }">
                  {{ row.listTimeText }}
                </template>
              </el-table-column>
              <el-table-column label="职位 / 公司" min-width="220">
                <template #default="{ row }">
                  <div class="list-title">{{ row.jobTitleText }}</div>
                  <div class="list-sub">{{ row.companyNameText }}</div>
                </template>
              </el-table-column>
              <el-table-column label="轮次" width="90">
                <template #default="{ row }">{{ row.roundLabel }}</template>
              </el-table-column>
              <el-table-column label="方式" width="120">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.modeMeta.tag">{{ row.modeMeta.label }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="120">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.statusMeta.tag">{{ row.statusMeta.text }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="提醒" width="120">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.urgencyMeta.type">{{ row.urgencyMeta.text }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="140">
                <template #default="{ row }">
                  <el-button size="small" type="primary" plain @click="openInterviewByItem(row)">查看安排</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>

        <el-empty v-else description="暂无面试安排" />
        <div class="empty-hint">如需查看面试日程，请等待商家发起面试邀约或切换有面试记录的账号。</div>
      </template>
    </el-card>

    <el-dialog v-model="dialogVisible" title="面试安排" width="520px">
      <div class="invite-dialog">
        <div class="invite-row">
          <span class="invite-label">职位：</span>
          <span class="invite-value">{{ currentInviteDisplay.inviteJobTitleText }}</span>
        </div>
        <div class="invite-row">
          <span class="invite-label">公司：</span>
          <span class="invite-value">{{ currentInviteDisplay.inviteCompanyNameText }}</span>
        </div>
          <div class="invite-block">
            <div class="invite-block-title">面试轮次</div>
          <el-skeleton v-if="interviewLoading" animated :rows="2" />
          <div v-else-if="displayInterviewRounds.length" class="invite-list">
            <div v-for="item in displayInterviewRounds" :key="item.id" class="invite-item">
              <div class="invite-item-header">
                <span class="invite-item-round">{{ item.roundLabel }}</span>
                <el-tag size="small" :type="item.displayStatus.type">
                  {{ item.displayStatus.text }}
                </el-tag>
              </div>
              <div class="invite-item-meta">时间：{{ item.dateTimeText }}</div>
              <div class="invite-item-meta">
                方式：
                <el-tag size="small" :type="item.modeMeta.tag">
                  {{ item.modeMeta.label }}
                </el-tag>
              </div>
              <div class="invite-item-meta">
                {{ item.locationLabel }}
                {{ item.locationText }}
              </div>
              <div class="invite-item-meta">备注：{{ item.remarkText }}</div>
              <div class="invite-item-actions">
                <el-button
                  v-if="item.status === 0"
                  size="small"
                  type="success"
                  plain
                  :disabled="item.isBlocked"
                  @click="handleInterviewStatus(item, 1)"
                >确认面试</el-button>
                <el-button
                  v-if="item.status === 0"
                  size="small"
                  type="danger"
                  plain
                  :disabled="item.isBlocked"
                  @click="handleInterviewStatus(item, 2)"
                >拒绝面试</el-button>
              </div>
              <div v-if="item.isBlocked" class="invite-item-tip">需先确认上一轮</div>
            </div>
          </div>
          <div v-else class="invite-empty">暂无面试记录</div>
        </div>
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/*
文件速览：
1. 文件职责：承载求职者面试日程的周/月/列表视图、侧栏摘要与面试邀约确认弹窗。
2. 对外入口：fetchData、openInterview、openInterviewByDelivery、handleInterviewStatus。
3. 关键结构：displayInterviewItems、scheduledItemsByDateKey、weekDayCards、monthDayCards、sideTabSections、currentInviteDisplay、displayInterviewRounds。
4. 阅读建议：先看日期分组与展示字段加工，再看周/月视图和右侧 tab 配置，最后看邀约弹窗里的轮次状态处理。
*/
import { ref, computed, onMounted, watch } from 'vue'
import { getMyApplications } from '@/api/applicant'
import { getInterviewList, updateInterviewStatus } from '@/api/interview'
import { ElMessage } from 'element-plus'
import { formatDateTime, formatDateYMD, formatTimeHM } from '@/utils/format'
import { fetchAllPagedRecords } from '@/utils/pagedRecords'

const INTERVIEW_STATUS_TEXT_MAP = {
  0: '待确认',
  1: '已确认',
  2: '已拒绝',
  3: '已取消',
  4: '已完成'
}
const INTERVIEW_STATUS_TAG_MAP = {
  0: 'warning',
  1: 'success',
  2: 'danger',
  3: 'info',
  4: 'primary'
}
const INTERVIEW_MODE_META_MAP = {
  ONLINE: {
    label: '线上面试',
    tag: 'success',
    sceneLabel: '线上',
    locationLabel: '会议链接：'
  },
  OFFLINE: {
    label: '线下面试',
    tag: 'warning',
    sceneLabel: '线下',
    locationLabel: '面试地点：'
  },
  UNKNOWN: {
    label: '未标注',
    tag: 'info',
    sceneLabel: '线下',
    locationLabel: '面试地点：'
  }
}
const BLOCKED_INTERVIEW_DISPLAY_STATUS = {
  text: '等待上一轮确认',
  type: 'info'
}
const APPLICATION_PAGE_SIZE = 50
const APPLICATION_MAX_PAGES = 50

const loading = ref(false)
const viewMode = ref('week')
const applications = ref([])
const interviewItems = ref([])

const dialogVisible = ref(false)
const currentInvite = ref({})
const interviewList = ref([])
const interviewLoading = ref(false)
const sideTab = ref('today')

const selectedDate = ref(new Date())
const weekStart = ref(getStartOfWeek(selectedDate.value))
const isMonthView = computed(() => viewMode.value === 'month')
const isCalendarView = computed(() => viewMode.value !== 'list')
const calendarNavMeta = computed(() => (isMonthView.value
  ? { unit: 'month', prevLabel: '上个月', nextLabel: '下个月' }
  : { unit: 'week', prevLabel: '上一周', nextLabel: '下一周' }))
const calendarPanelClass = computed(() => (isMonthView.value ? 'month-wrap' : 'calendar-wrap'))

const getInterviewStatusText = (status) => {
  return INTERVIEW_STATUS_TEXT_MAP[status] || '未知'
}
const getInterviewStatusTag = (status) => {
  return INTERVIEW_STATUS_TAG_MAP[status] || 'info'
}

// 统一判断面试方式，明确区分线上/线下
const getInterviewMode = (method) => {
  if (!method) return 'UNKNOWN'
  const text = String(method)
  if (text.includes('线下')) return 'OFFLINE'
  if (text.includes('线上')) return 'ONLINE'
  return 'UNKNOWN'
}
const getInterviewModeMeta = (method) => {
  return INTERVIEW_MODE_META_MAP[getInterviewMode(method)] || INTERVIEW_MODE_META_MAP.UNKNOWN
}

// 判断当前轮次是否被上一轮阻塞（上一轮待确认）
const isRoundBlocked = (list, index) => {
  if (!Array.isArray(list) || index <= 0) return false
  return list.slice(0, index).some((item) => Number(item?.status) === 0)
}

// 面试状态展示：若上一轮未确认，统一显示“等待上一轮确认”
const getInterviewDisplayStatus = (item, index, list) => {
  if (isRoundBlocked(list, index)) {
    return BLOCKED_INTERVIEW_DISPLAY_STATUS
  }
  return { text: getInterviewStatusText(item?.status), type: getInterviewStatusTag(item?.status) }
}

const sortInterviewRounds = (list) => {
  const records = Array.isArray(list) ? list : []
  return [...records].sort((a, b) => Number(a.roundNo) - Number(b.roundNo))
}

// 解析时间字符串，兼容 ISO / 普通格式
const parseScheduleDate = (value) => {
  if (value instanceof Date) {
    return Number.isNaN(value.getTime()) ? null : value
  }
  if (!value) return null
  const raw = String(value).trim()
  if (!raw) return null
  const normalized = raw.replace('T', ' ')
  const safe = normalized.replace(/-/g, '/')
  const date = new Date(safe)
  if (Number.isNaN(date.getTime())) return null
  return date
}

// 计算面试提醒级别（用于多面试场景的优先级提示）
const getUrgencyInfo = (value) => {
  const date = parseScheduleDate(value)
  if (!date) {
    return { text: '待定', type: 'info' }
  }
  const diff = date.getTime() - Date.now()
  const hours = diff / (1000 * 60 * 60)
  if (hours < 0) {
    return { text: '已过期', type: 'info' }
  }
  if (hours <= 24) {
    return { text: '24小时内', type: 'danger' }
  }
  if (hours <= 72) {
    return { text: '3天内', type: 'warning' }
  }
  return { text: '已安排', type: 'success' }
}

const formatDate = (value) => {
  return formatDateYMD(value, '')
}

const formatTimeOnly = (value) => {
  return formatTimeHM(value)
}
const buildInterviewDisplayItem = (item) => {
  const scheduleDate = parseScheduleDate(item?.scheduleTime)
  const modeMeta = getInterviewModeMeta(item?.method)
  const roundNo = Number(item?.roundNo)
  const statusMeta = {
    text: getInterviewStatusText(item?.status),
    tag: getInterviewStatusTag(item?.status)
  }
  const urgencyMeta = getUrgencyInfo(item?.scheduleTime)
  return {
    ...item,
    scheduleDate,
    scheduleDateKey: scheduleDate ? getDateKey(scheduleDate) : '',
    dateText: formatDate(item?.scheduleTime) || '待确认',
    dateTimeText: formatDateTime(item?.scheduleTime, '未记录'),
    timeText: formatTimeOnly(item?.scheduleTime) || '待定',
    listTimeText: formatTimeOnly(item?.scheduleTime) || '-',
    jobTitleText: item?.jobTitle || '职位已删除',
    compactJobTitleText: item?.jobTitle || '职位',
    companyNameText: item?.companyName || '-',
    inviteJobTitleText: item?.jobTitle || '-',
    inviteCompanyNameText: item?.companyName || '-',
    roundLabel: Number.isFinite(roundNo) && roundNo > 0 ? `第${roundNo}轮` : '轮次待定',
    locationText: item?.location || '待补充',
    remarkText: item?.remark || '-',
    modeMeta,
    statusMeta,
    urgencyMeta,
    calendarTagItems: [
      { key: 'mode', type: modeMeta.tag, text: modeMeta.label },
      { key: 'status', type: statusMeta.tag, text: statusMeta.text },
      { key: 'urgency', type: urgencyMeta.type, text: urgencyMeta.text }
    ],
    sceneLabel: modeMeta.sceneLabel,
    locationLabel: modeMeta.locationLabel
  }
}

const formatWeekday = (date) => {
  const labels = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  return labels[date.getDay()]
}

const formatDateLabel = (date) => {
  const pad = (num) => String(num).padStart(2, '0')
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

const getDateKey = (date) => {
  const pad = (num) => String(num).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}
const buildCalendarDayCard = (day, previewLimit, itemsByDateKey) => {
  const dateKey = getDateKey(day)
  const items = itemsByDateKey.get(dateKey) || []
  return {
    day,
    dateKey,
    items,
    previewItems: items.slice(0, previewLimit),
    moreCount: Math.max(items.length - previewLimit, 0)
  }
}
const buildSequentialDays = (start, count) => {
  return Array.from({ length: count }, (_, index) => {
    const day = new Date(start)
    day.setDate(start.getDate() + index)
    return day
  })
}
const buildDateRangeLabel = (start, dayOffset) => {
  const end = new Date(start)
  end.setDate(start.getDate() + dayOffset)
  return `${formatDate(start)} 至 ${formatDate(end)}`
}

function getStartOfWeek(date) {
  const temp = new Date(date)
  temp.setHours(0, 0, 0, 0)
  const day = temp.getDay()
  const diff = day === 0 ? -6 : 1 - day
  temp.setDate(temp.getDate() + diff)
  return temp
}

const calendarBaseDate = computed(() => selectedDate.value || new Date())
const todayDateKey = computed(() => getDateKey(new Date()))
const selectedDateKey = computed(() => (selectedDate.value ? getDateKey(selectedDate.value) : ''))
const monthStartDate = computed(() => getStartOfWeek(calendarBaseDate.value))
const weekHeader = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
const weekLabel = computed(() => buildDateRangeLabel(weekStart.value, 6))
const monthLabel = computed(() => buildDateRangeLabel(monthStartDate.value, 34))

const isToday = (date) => {
  return getDateKey(date) === todayDateKey.value
}

const isSelectedDate = (date) => {
  return Boolean(selectedDateKey.value) && getDateKey(date) === selectedDateKey.value
}

const isSameMonth = (date, target) => {
  const base = target || selectedDate.value || new Date()
  return date.getFullYear() === base.getFullYear() && date.getMonth() === base.getMonth()
}

const displayInterviewItems = computed(() => interviewItems.value.map((item) => buildInterviewDisplayItem(item)))
const scheduledItems = computed(() => displayInterviewItems.value.filter((item) => item.scheduleDate))
const pendingItems = computed(() => displayInterviewItems.value.filter((item) => !item.scheduleDate))
const scheduledItemsByDateKey = computed(() => {
  const grouped = new Map()
  scheduledItems.value.forEach((item) => {
    const key = item.scheduleDateKey
    if (!key) return
    const current = grouped.get(key) || []
    current.push(item)
    grouped.set(key, current)
  })
  return grouped
})
const getItemsForDateKey = (dateKey) => {
  if (!dateKey) return []
  return scheduledItemsByDateKey.value.get(dateKey) || []
}
const buildSideTabItem = (item, sectionName) => {
  switch (sectionName) {
    case 'today':
      return {
        ...item,
        trailingText: item.timeText,
        primaryMeta: `${item.companyNameText} · ${item.roundLabel}`,
        secondaryMeta: '',
        showAction: false
      }
    case 'pending':
      return {
        ...item,
        badgeText: '待确认',
        badgeType: 'warning',
        primaryMeta: `${item.roundLabel} · ${item.modeMeta.label}`,
        secondaryMeta: '',
        showAction: true
      }
    case 'selected':
      return {
        ...item,
        trailingText: item.timeText,
        primaryMeta: `${item.companyNameText} · ${item.roundLabel}`,
        secondaryMeta: `${item.sceneLabel} · ${item.urgencyMeta.text}`,
        showAction: true
      }
    default:
      return item
  }
}
const buildSideTabSection = ({ name, label, title, emptyDescription, items }) => {
  return {
    name,
    label,
    title,
    emptyDescription,
    items: items.map((item) => buildSideTabItem(item, name))
  }
}
const applicationsByDeliveryId = computed(() => {
  return new Map(
    applications.value.map((item) => [Number(item.id), item])
  )
})
const weekDayCards = computed(() => {
  return buildSequentialDays(weekStart.value, 7).map((day) =>
    buildCalendarDayCard(day, 2, scheduledItemsByDateKey.value)
  )
})
const monthDayCards = computed(() => {
  return buildSequentialDays(monthStartDate.value, 35).map((day) =>
    buildCalendarDayCard(day, 1, scheduledItemsByDateKey.value)
  )
})
const weekDateKeys = computed(() => new Set(weekDayCards.value.map((card) => card.dateKey)))

const todayItems = computed(() => getItemsForDateKey(todayDateKey.value))

const interviewStats = computed(() => {
  const weekCount = scheduledItems.value.filter((item) => weekDateKeys.value.has(item.scheduleDateKey)).length
  return {
    total: displayInterviewItems.value.length,
    week: weekCount,
    today: todayItems.value.length,
    pending: pendingItems.value.length
  }
})

const interviewListView = computed(() => {
  const list = displayInterviewItems.value.slice()
  return list.sort((a, b) => {
    const timeA = a.scheduleDate?.getTime() || Number.MAX_SAFE_INTEGER
    const timeB = b.scheduleDate?.getTime() || Number.MAX_SAFE_INTEGER
    return timeA - timeB
  })
})

const selectedDayItems = computed(() => getItemsForDateKey(selectedDateKey.value))
const currentInviteDisplay = computed(() => buildInterviewDisplayItem(currentInvite.value))
const displayInterviewRounds = computed(() => {
  return interviewList.value.map((item, index) => ({
    ...buildInterviewDisplayItem(item),
    displayStatus: getInterviewDisplayStatus(item, index, interviewList.value),
    isBlocked: isRoundBlocked(interviewList.value, index)
  }))
})

const selectedDateTabTitle = computed(() => `选中日期：${selectedDate.value ? formatDate(selectedDate.value) : '-'}`)
const sideTabSections = computed(() => {
  return [
    buildSideTabSection({
      name: 'today',
      label: '今日',
      title: '今日面试',
      emptyDescription: '今日暂无面试',
      items: todayItems.value
    }),
    buildSideTabSection({
      name: 'pending',
      label: '待确认',
      title: '待确认 / 待安排',
      emptyDescription: '暂无待确认',
      items: pendingItems.value
    }),
    buildSideTabSection({
      name: 'selected',
      label: '选中日期',
      title: selectedDateTabTitle.value,
      emptyDescription: '暂无面试',
      items: selectedDayItems.value
    })
  ]
})

const openInterviewByDelivery = (deliveryId) => {
  const target = applicationsByDeliveryId.value.get(Number(deliveryId))
  if (!target) {
    ElMessage.warning('未找到投递记录')
    return
  }
  openInterview(target)
}
const openInterviewByItem = (item) => {
  openInterviewByDelivery(item?.deliveryId)
}

const selectDate = (day) => {
  setSelectedCalendarDate(day, 'selected')
}

const setSelectedCalendarDate = (date, nextSideTab = sideTab.value) => {
  if (!date) return
  selectedDate.value = new Date(date)
  sideTab.value = nextSideTab
}
const openWeekView = (nextSideTab = sideTab.value, date = selectedDate.value || new Date()) => {
  viewMode.value = 'week'
  setSelectedCalendarDate(date, nextSideTab)
}
const shiftCalendarDate = (amount) => {
  const base = new Date(calendarNavMeta.value.unit === 'month' ? (selectedDate.value || new Date()) : weekStart.value)
  if (calendarNavMeta.value.unit === 'month') {
    base.setMonth(base.getMonth() + amount)
  } else {
    base.setDate(base.getDate() + amount * 7)
  }
  setSelectedCalendarDate(base)
}

const handleSummaryClick = (type) => {
  switch (type) {
    case 'today':
      openWeekView('today', new Date())
      break
    case 'pending':
      openWeekView('pending')
      break
    case 'week':
      openWeekView('selected')
      break
    case 'total':
      viewMode.value = 'list'
      break
    default:
      break
  }
}

const goToday = () => {
  setSelectedCalendarDate(new Date())
}

const goPrev = () => {
  shiftCalendarDate(-1)
}

const goNext = () => {
  shiftCalendarDate(1)
}

const prevLabel = computed(() => calendarNavMeta.value.prevLabel)
const nextLabel = computed(() => calendarNavMeta.value.nextLabel)

const openInterview = (row) => {
  currentInvite.value = row || {}
  dialogVisible.value = true
  loadInterviewList(row?.id)
}

const loadInterviewList = async (deliveryId) => {
  if (!deliveryId) {
    interviewList.value = []
    return
  }
  interviewLoading.value = true
  try {
    const res = await getInterviewList(deliveryId)
    interviewList.value = sortInterviewRounds(res.data)
  } catch (error) {
    console.error('获取面试记录失败:', error)
    interviewList.value = []
  } finally {
    interviewLoading.value = false
  }
}

const handleInterviewStatus = async (item, status) => {
  if (!item?.id) return
  if (item.isBlocked) {
    ElMessage.warning('请先确认上一轮面试')
    return
  }
  try {
    await updateInterviewStatus({ id: item.id, status })
    ElMessage.success('面试状态已更新')
    await loadInterviewList(currentInvite.value?.id)
    await fetchData()
  } catch (error) {
    console.error('更新面试状态失败:', error)
    ElMessage.error('更新面试状态失败')
  }
}

const fetchData = async () => {
  loading.value = true
  try {
    const { records, truncated } = await fetchAllPagedRecords({
      fetchPage: getMyApplications,
      baseParams: { status: 2 },
      pageSize: APPLICATION_PAGE_SIZE,
      maxPages: APPLICATION_MAX_PAGES,
      errorMessage: '获取面试安排失败'
    })
    applications.value = records
    if (truncated) {
      ElMessage.warning(`面试记录较多，当前仅载入前 ${APPLICATION_PAGE_SIZE * APPLICATION_MAX_PAGES} 条投递`)
    }

    const tasks = records.map(async (row) => {
      try {
        const response = await getInterviewList(row.id)
        const list = response.data || []
        if (!list.length) {
          return [{
            id: `pending-${row.id}`,
            deliveryId: row.id,
            jobTitle: row.jobTitle,
            companyName: row.companyName,
            roundNo: 1,
            status: 0,
            scheduleTime: row.interviewTime || null,
            method: row.interviewMethod || null,
            location: row.interviewLocation || null,
            remark: row.interviewRemark || null
          }]
        }
        return list.map((item) => ({
          ...item,
          deliveryId: row.id,
          jobTitle: row.jobTitle,
          companyName: row.companyName
        }))
      } catch (error) {
        return []
      }
    })

    const result = await Promise.all(tasks)
    interviewItems.value = result.flat()
  } catch (error) {
    console.error('获取面试安排失败:', error)
    applications.value = []
    interviewItems.value = []
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchData()
})

watch(selectedDate, (value) => {
  if (!value) return
  weekStart.value = getStartOfWeek(value)
})
</script>

<style scoped>
.interview-page {
  width: min(calc(100% - (var(--ui-shell-gutter) * 2)), var(--ui-main-shell-max-width));
  max-width: none;
  margin: 8px auto;
  padding: 4px 0 20px;
  position: relative;
  overflow: visible;
}

.interview-page::before,
.interview-page::after {
  content: '';
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
  filter: blur(6px);
  z-index: 0;
}

.interview-page::before {
  top: 0;
  left: 1%;
  width: min(26vw, 280px);
  height: min(26vw, 280px);
  background: radial-gradient(circle, rgba(10, 132, 255, 0.11), rgba(10, 132, 255, 0) 72%);
}

.interview-page::after {
  top: 160px;
  right: 3%;
  width: min(24vw, 240px);
  height: min(24vw, 240px);
  background: radial-gradient(circle, rgba(147, 197, 253, 0.11), rgba(147, 197, 253, 0) 72%);
}

.interview-page :deep(.el-card) {
  border-radius: 18px;
  border: none;
  background: transparent;
  box-shadow: none;
}

.interview-page :deep(.el-card__body) {
  position: relative;
  z-index: 1;
  padding: 16px;
}

.interview-card {
  position: relative;
  z-index: 1;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.74);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.9), rgba(250, 252, 255, 0.82)),
    linear-gradient(135deg, rgba(10, 132, 255, 0.08), rgba(10, 132, 255, 0.03) 48%, rgba(191, 219, 254, 0.12));
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.08);
}

.interview-card::before {
  content: '';
  position: absolute;
  inset: -18% auto auto -6%;
  width: min(30vw, 320px);
  height: min(30vw, 320px);
  border-radius: 50%;
  background: radial-gradient(circle, rgba(10, 132, 255, 0.08), rgba(10, 132, 255, 0) 70%);
  pointer-events: none;
}


.overview-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(260px, 360px);
  gap: 16px;
  margin-bottom: 16px;
  align-items: start;
}

.overview-card,
.summary-card {
  padding: 12px 14px;
  border-radius: 14px;
  border: 1px solid rgba(15, 23, 42, 0.07);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.92), rgba(244, 248, 255, 0.82));
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.74);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.summary-card {
  min-width: 260px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(239, 246, 255, 0.9));
}

.toolbar-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  min-width: 0;
}

.toolbar-left {
  flex: 1 1 420px;
}

.toolbar-right {
  justify-content: flex-end;
}

.toolbar-date {
  min-width: 160px;
  flex: 0 1 180px;
}

.toolbar-left :deep(.el-radio-group) {
  padding: 4px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.74);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.82);
}

.toolbar-left :deep(.el-radio-button__inner) {
  border: none !important;
  border-radius: 999px !important;
  background: transparent;
  box-shadow: none !important;
  color: #475569;
  padding: 8px 14px;
}

.toolbar-left :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background: #ffffff;
  color: #0a84ff;
  box-shadow: 0 8px 18px rgba(10, 132, 255, 0.12);
}

.toolbar-date :deep(.el-input__wrapper) {
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.76);
  border: 1px solid transparent;
  box-shadow: none !important;
  transition: border-color 0.2s ease, background-color 0.2s ease;
}

.toolbar-date :deep(.el-input__wrapper.is-focus) {
  background: #ffffff;
  border-color: #0a84ff;
}

.toolbar-right :deep(.el-button) {
  border-radius: 999px;
  border-color: rgba(15, 23, 42, 0.08);
  background: rgba(255, 255, 255, 0.8);
  color: #475569;
}

.toolbar-right :deep(.el-button:hover) {
  background: #ffffff;
  color: #0a84ff;
  border-color: rgba(10, 132, 255, 0.22);
}

.legend-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.legend-title {
  font-size: 12px;
  color: #64748b;
  margin-right: 4px;
}

.summary-strip {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.summary-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border-radius: 999px;
  border: 1px solid #e2e8f0;
  background: #ffffff;
  font-size: 12px;
  color: #475569;
  cursor: pointer;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}

.summary-item:hover {
  border-color: rgba(10, 132, 255, 0.18);
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.08);
  transform: translateY(-1px);
}

.summary-item.is-today {
  border-color: #bfdbfe;
  color: #1d4ed8;
  background: rgba(239, 246, 255, 0.9);
}

.summary-item.is-pending {
  border-color: #fde68a;
  color: #b45309;
  background: rgba(255, 248, 235, 0.92);
}

.summary-item.is-week {
  border-color: #e2e8f0;
}

.summary-item.is-total {
  color: #94a3b8;
}

.summary-value {
  font-weight: 600;
  color: #0f172a;
}

.calendar-wrap,
.month-wrap {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 18px;
}

.calendar-main {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.calendar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  color: #475569;
}

.calendar-hint {
  font-size: 12px;
  color: #94a3b8;
}

.calendar-grid {
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 14px;
  overflow: hidden;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(248, 250, 255, 0.94));
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.78);
}

.month-grid {
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 14px;
  overflow: hidden;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(248, 250, 255, 0.94));
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.78);
}

.calendar-row {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
}

.calendar-head {
  background: rgba(241, 246, 255, 0.92);
  border-bottom: 1px solid #e2e8f0;
}

.calendar-head-cell {
  padding: 10px 8px;
  font-size: 12px;
  font-weight: 600;
  color: #475569;
  text-align: center;
}

.calendar-body {
  min-height: 440px;
}

.month-body {
  min-height: 420px;
}

.calendar-cell {
  padding: 8px;
  border-right: 1px solid #e2e8f0;
  border-bottom: 1px solid #e2e8f0;
  min-height: 150px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.month-cell {
  padding: 8px;
  border-right: 1px solid #e2e8f0;
  border-bottom: 1px solid #e2e8f0;
  min-height: 96px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  cursor: pointer;
}

.month-cell.is-outside {
  background: #f8fafc;
  color: #94a3b8;
}

.month-cell.is-today {
  background: #f0f7ff;
}

.month-cell.is-selected {
  outline: 2px solid rgba(37, 99, 235, 0.2);
  background: #eff6ff;
}

.calendar-cell:nth-child(7n) {
  border-right: none;
}

.month-cell:nth-child(7n) {
  border-right: none;
}

.calendar-cell.is-today {
  background: #f0f7ff;
}

.calendar-date {
  font-size: 12px;
  font-weight: 600;
  color: #1e293b;
}

.month-date {
  font-size: 12px;
  font-weight: 600;
  color: #1e293b;
}

.month-events {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.month-event {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #475569;
  background: rgba(247, 250, 255, 0.94);
  border-radius: 8px;
  padding: 4px 6px;
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.month-event-time {
  color: #1d4ed8;
  font-weight: 600;
}

.month-event-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.month-more {
  font-size: 12px;
  color: #64748b;
}

.calendar-events {
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex: 1;
}

.calendar-event {
  padding: 8px;
  border-radius: 10px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(247, 250, 255, 0.94);
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.calendar-event:hover {
  border-color: #93c5fd;
  background: #eff6ff;
}

.event-time {
  font-size: 12px;
  color: #1d4ed8;
  font-weight: 600;
}

.event-title {
  font-size: 12px;
  color: #1f2937;
  font-weight: 600;
}

.event-company {
  font-size: 12px;
  color: #64748b;
}

.event-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.calendar-more {
  font-size: 12px;
  color: #64748b;
}

.calendar-empty {
  font-size: 12px;
  color: #94a3b8;
}

.calendar-side {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.side-card {
  border-radius: 12px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  padding: 12px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(248, 250, 255, 0.92));
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.74);
}

.side-card--tabs {
  padding: 8px 10px 12px;
  max-height: 640px;
  overflow: hidden;
}

.side-tabs :deep(.el-tabs__header) {
  margin: 0 0 8px;
}

.side-tabs :deep(.el-tabs__item:hover),
.side-tabs :deep(.el-tabs__item.is-active) {
  color: #0a84ff;
}

.side-tabs :deep(.el-tabs__active-bar) {
  background: #0a84ff;
}

.side-tabs :deep(.el-tabs__content) {
  max-height: 560px;
  overflow: auto;
  padding-right: 4px;
}

.side-title {
  font-size: 13px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 8px;
}

.side-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.side-item {
  border: 1px dashed rgba(10, 132, 255, 0.14);
  border-radius: 10px;
  padding: 8px;
  background: rgba(248, 250, 255, 0.72);
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.side-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.side-job {
  font-size: 12px;
  font-weight: 600;
  color: #1f2937;
}

.side-time {
  font-size: 12px;
  color: #1d4ed8;
}

.side-meta {
  font-size: 12px;
  color: #64748b;
}

.list-view {
  margin-top: 8px;
  border-radius: 14px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  padding: 8px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(248, 250, 255, 0.92));
}

.list-title {
  font-size: 13px;
  font-weight: 600;
  color: #1f2937;
}

.list-sub {
  font-size: 12px;
  color: #64748b;
}

.invite-dialog {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.invite-row {
  display: flex;
  gap: 8px;
}

.invite-label {
  color: #64748b;
  font-size: 13px;
  min-width: 64px;
}

.invite-block {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed #e2e8f0;
}

.invite-block-title {
  font-size: 13px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 8px;
}

.invite-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.invite-item {
  padding: 10px 12px;
  background: rgba(247, 250, 255, 0.94);
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 12px;
}

.invite-item-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.invite-item-round {
  font-weight: 600;
  color: #1f2937;
  font-size: 12px;
}

.invite-item-meta {
  font-size: 12px;
  color: #475569;
  margin-top: 2px;
}

.invite-item-actions {
  margin-top: 8px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.invite-item-tip {
  margin-top: 6px;
  font-size: 12px;
  color: #64748b;
}

.invite-empty {
  font-size: 12px;
  color: #94a3b8;
}

.empty-hint {
  margin-top: 8px;
  text-align: center;
  font-size: 12px;
  color: #94a3b8;
}

@media (max-width: 980px) {
  .calendar-wrap,
  .month-wrap {
    grid-template-columns: 1fr;
  }
  .overview-grid {
    grid-template-columns: 1fr;
  }
  .overview-card,
  .summary-card {
    padding: 12px;
  }
  .toolbar-row {
    flex-direction: column;
    align-items: flex-start;
  }

  .toolbar-right {
    width: 100%;
    justify-content: flex-start;
  }

  .side-card--tabs {
    max-height: 520px;
  }
}

@media (max-width: 720px) {
  .toolbar-left,
  .toolbar-right {
    width: 100%;
  }

  .toolbar-left > * {
    flex: 1 1 140px;
  }

  .toolbar-right > * {
    flex: 1 1 88px;
  }

  .toolbar-date {
    min-width: 0;
    width: 100%;
  }
}
</style>
