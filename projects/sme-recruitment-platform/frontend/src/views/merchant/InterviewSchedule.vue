<!--
文件速览：
1. 文件职责：商家面试日程页，负责周/月/列表视图切换、筛选候选人安排与查看当天面试。
2. 页面入口：商家路由 `/merchant/interviews`。
3. 关键结构：overview-grid、toolbar-row、filter-row、calendar-wrap、interview-grid。
4. 阅读建议：先看顶部工具栏与筛选区，再看周/月视图，最后看列表视图和断点样式。
-->
<template>
  <div class="interview-page">
    <el-card shadow="never" class="interview-card" v-loading="loading">
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

      <div class="filter-row adaptive-filter-row">
        <el-input
          v-model="filters.keyword"
          placeholder="搜索候选人 / 职位"
          size="small"
          clearable
          class="filter-input adaptive-filter-control adaptive-filter-control--lg"
        />
        <el-select v-model="filters.status" placeholder="面试状态" size="small" class="filter-select adaptive-filter-control adaptive-filter-control--sm">
          <el-option label="全部状态" value="all" />
          <el-option label="待确认" value="pending" />
          <el-option label="已确认" value="confirmed" />
          <el-option label="已完成" value="done" />
          <el-option label="已取消" value="cancel" />
        </el-select>
        <el-date-picker
          v-model="filters.date"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="选择日期"
          size="small"
          class="filter-date adaptive-filter-control adaptive-filter-control--sm"
        />
        <div class="adaptive-filter-actions">
          <el-button size="small" @click="resetFilters">重置</el-button>
        </div>
      </div>

      <el-skeleton v-if="loading" animated :rows="6" />

      <template v-else>
        <div v-if="filteredInterviews.length">
          <div v-if="viewMode === 'week'" class="calendar-wrap">
            <div class="calendar-main">
              <div class="calendar-header">
                <span>{{ weekLabel }}</span>
                <span class="calendar-hint">点击卡片快速进入候选人列表</span>
              </div>
              <div class="calendar-grid">
                <div class="calendar-row calendar-head">
                  <div v-for="day in weekDays" :key="day.toISOString()" class="calendar-head-cell">
                    {{ formatWeekday(day) }}
                  </div>
                </div>
                <div class="calendar-row calendar-body">
                  <div
                    v-for="day in weekDays"
                    :key="day.toISOString() + '-cell'"
                    class="calendar-cell"
                    :class="{ 'is-today': isToday(day) }"
                  >
                    <div class="calendar-date">{{ formatDateLabel(day) }}</div>
                    <div class="calendar-events">
                      <div
                        v-for="item in getPreviewItemsForDay(day, 2)"
                        :key="item.id"
                        class="calendar-event"
                        @click="openResumeList"
                      >
                        <div class="event-time">{{ formatTimeOnly(item.scheduleTime) || '待定' }}</div>
                        <div class="event-title">{{ item.applicantName || '候选人' }}</div>
                        <div class="event-company">{{ item.jobName || '-' }}</div>
                        <div class="event-tags">
                          <el-tag size="small" :type="getInterviewModeTag(item.method)">{{ getInterviewModeLabel(item.method) }}</el-tag>
                          <el-tag size="small" :type="getInterviewStatusTag(item.status)">{{ getInterviewStatusText(item.status) }}</el-tag>
                          <el-tag size="small" :type="getUrgencyInfo(item.scheduleTime).type">{{ getUrgencyInfo(item.scheduleTime).text }}</el-tag>
                        </div>
                      </div>
                      <div v-if="getMoreCountForDay(day, 2)" class="calendar-more">还有 {{ getMoreCountForDay(day, 2) }} 场</div>
                      <div v-if="getItemsForDay(day).length === 0" class="calendar-empty">暂无安排</div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div class="calendar-side">
              <div class="side-card side-card--tabs">
                <el-tabs v-model="sideTab" class="side-tabs">
                  <el-tab-pane label="今日" name="today">
                    <div class="side-title">今日面试</div>
                    <div v-if="todayItems.length" class="side-list">
                      <div v-for="item in todayItems" :key="item.id" class="side-item">
                        <div class="side-row">
                          <span class="side-job">{{ item.applicantName || '候选人' }}</span>
                          <span class="side-time">{{ formatTimeOnly(item.scheduleTime) || '待定' }}</span>
                        </div>
                        <div class="side-meta">{{ item.jobName || '-' }} · 第{{ item.roundNo }}轮</div>
                        <el-button size="small" plain @click="openResumeList">查看候选人</el-button>
                      </div>
                    </div>
                    <el-empty v-else description="今日暂无面试" />
                  </el-tab-pane>
                  <el-tab-pane label="待确认" name="pending">
                    <div class="side-title">待确认 / 待安排</div>
                    <div v-if="pendingItems.length" class="side-list">
                      <div v-for="item in pendingItems" :key="item.id" class="side-item">
                        <div class="side-row">
                          <span class="side-job">{{ item.applicantName || '候选人' }}</span>
                          <el-tag size="small" type="warning">待确认</el-tag>
                        </div>
                        <div class="side-meta">第{{ item.roundNo }}轮 · {{ getInterviewModeLabel(item.method) }}</div>
                        <el-button size="small" plain @click="openResumeList">查看安排</el-button>
                      </div>
                    </div>
                    <el-empty v-else description="暂无待确认" />
                  </el-tab-pane>
                  <el-tab-pane label="选中日期" name="selected">
                    <div class="side-title">选中日期：{{ selectedDateLabel || '-' }}</div>
                    <div v-if="selectedDayItems.length" class="side-list">
                      <div v-for="item in selectedDayItems" :key="item.id" class="side-item">
                        <div class="side-row">
                          <span class="side-job">{{ item.applicantName || '候选人' }}</span>
                          <span class="side-time">{{ formatTimeOnly(item.scheduleTime) || '待定' }}</span>
                        </div>
                        <div class="side-meta">{{ item.jobName || '-' }} · 第{{ item.roundNo }}轮</div>
                        <div class="side-meta">
                          {{ getInterviewModeLabel(item.method) }} · {{ getUrgencyInfo(item.scheduleTime).text }}
                        </div>
                        <el-button size="small" plain @click="openResumeList">查看安排</el-button>
                      </div>
                    </div>
                    <el-empty v-else description="暂无面试" />
                  </el-tab-pane>
                </el-tabs>
              </div>
            </div>
          </div>

          <div v-else-if="viewMode === 'month'" class="month-wrap">
            <div class="calendar-main">
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
                    v-for="day in monthDays"
                    :key="day.toISOString() + '-month'"
                    class="month-cell"
                    :class="{
                      'is-outside': !isSameMonth(day, selectedDate),
                      'is-today': isToday(day),
                      'is-selected': isSelectedDate(day)
                    }"
                    @click="selectDate(day)"
                  >
                    <div class="month-date">{{ formatDateLabel(day) }}</div>
                    <div class="month-events">
                      <div
                        v-for="item in getPreviewItemsForDay(day, 1)"
                        :key="item.id"
                        class="month-event"
                        @click.stop="openResumeList"
                      >
                        <span class="month-event-time">{{ formatTimeOnly(item.scheduleTime) || '待定' }}</span>
                        <span class="month-event-title">{{ item.applicantName || '候选人' }}</span>
                      </div>
                      <div v-if="getMoreCountForDay(day, 1)" class="month-more">还有 {{ getMoreCountForDay(day, 1) }} 场</div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div class="calendar-side">
              <div class="side-card side-card--tabs">
                <el-tabs v-model="sideTab" class="side-tabs">
                  <el-tab-pane label="今日" name="today">
                    <div class="side-title">今日面试</div>
                    <div v-if="todayItems.length" class="side-list">
                      <div v-for="item in todayItems" :key="item.id" class="side-item">
                        <div class="side-row">
                          <span class="side-job">{{ item.applicantName || '候选人' }}</span>
                          <span class="side-time">{{ formatTimeOnly(item.scheduleTime) || '待定' }}</span>
                        </div>
                        <div class="side-meta">{{ item.jobName || '-' }} · 第{{ item.roundNo }}轮</div>
                      </div>
                    </div>
                    <el-empty v-else description="今日暂无面试" />
                  </el-tab-pane>
                  <el-tab-pane label="待确认" name="pending">
                    <div class="side-title">待确认 / 待安排</div>
                    <div v-if="pendingItems.length" class="side-list">
                      <div v-for="item in pendingItems" :key="item.id" class="side-item">
                        <div class="side-row">
                          <span class="side-job">{{ item.applicantName || '候选人' }}</span>
                          <el-tag size="small" type="warning">待确认</el-tag>
                        </div>
                        <div class="side-meta">第{{ item.roundNo }}轮 · {{ getInterviewModeLabel(item.method) }}</div>
                      </div>
                    </div>
                    <el-empty v-else description="暂无待确认" />
                  </el-tab-pane>
                  <el-tab-pane label="选中日期" name="selected">
                    <div class="side-title">选中日期：{{ selectedDateLabel || '-' }}</div>
                    <div v-if="selectedDayItems.length" class="side-list">
                      <div v-for="item in selectedDayItems" :key="item.id" class="side-item">
                        <div class="side-row">
                          <span class="side-job">{{ item.applicantName || '候选人' }}</span>
                          <span class="side-time">{{ formatTimeOnly(item.scheduleTime) || '待定' }}</span>
                        </div>
                        <div class="side-meta">{{ item.jobName || '-' }} · 第{{ item.roundNo }}轮</div>
                        <div class="side-meta">
                          {{ getInterviewModeLabel(item.method) }} · {{ getUrgencyInfo(item.scheduleTime).text }}
                        </div>
                      </div>
                    </div>
                    <el-empty v-else description="暂无面试" />
                  </el-tab-pane>
                </el-tabs>
              </div>
            </div>
          </div>

          <div v-else class="list-view">
            <div class="list-header">
              <div>
                <div class="list-title">面试列表</div>
                <div class="list-sub">共 {{ filteredInterviews.length }} 条</div>
              </div>
            </div>
            <div class="interview-grid">
              <div v-for="item in filteredInterviews" :key="item.id" class="interview-card-item">
                <div class="card-top">
                  <div class="time-block">
                    <div class="time-text">{{ formatTimeOnly(item.scheduleTime) || '待定' }}</div>
                    <div class="date-text">{{ formatDate(item.scheduleTime) || '-' }}</div>
                  </div>
                  <el-tag size="small" :type="getInterviewStatusTag(item.status)">
                    {{ getInterviewStatusText(item.status) }}
                  </el-tag>
                </div>
                <div class="card-main">
                  <div class="candidate-row">
                    <el-avatar :size="34" :src="item.applicantAvatar">{{ item.applicantName?.charAt(0) }}</el-avatar>
                    <div>
                      <div class="candidate-name">{{ item.applicantName || '候选人' }}</div>
                      <div class="candidate-job">{{ item.jobName || '职位未填写' }}</div>
                    </div>
                  </div>
                  <div class="card-meta">
                    <span>第{{ item.roundNo }}轮 · {{ getInterviewModeLabel(item.method) }}</span>
                    <span class="meta-split">|</span>
                    <span>{{ item.location || '待补充' }}</span>
                  </div>
                  <div class="card-remark">备注：{{ item.remark || '—' }}</div>
                </div>
                <div class="card-actions">
                  <el-button size="small" @click="openResumeList">查看候选人</el-button>
                  <el-button
                    v-if="canConfirm(item.status)"
                    size="small"
                    type="primary"
                    plain
                    @click="handleUpdateStatus(item, 1)"
                  >标记确认</el-button>
                  <el-button
                    v-if="canComplete(item.status)"
                    size="small"
                    type="success"
                    plain
                    @click="handleUpdateStatus(item, 4)"
                  >标记完成</el-button>
                  <el-button
                    v-if="canCancel(item.status)"
                    size="small"
                    type="danger"
                    plain
                    @click="handleUpdateStatus(item, 3)"
                  >取消面试</el-button>
                </div>
              </div>
            </div>
          </div>
        </div>
        <el-empty v-else description="暂无面试安排" />
      </template>
    </el-card>
  </div>
</template>

<script setup>
/*
文件速览：
1. 文件职责：商家面试日程页，负责周/月/列表视图切换、筛选面试安排与查看指定日期候选人。
2. 对外入口：fetchInterviewSchedule、handleUpdateStatus、openResumeList。
3. 关键结构：viewMode、filters、scheduledItemsByDateKey、weekDays、monthDays。
4. 阅读建议：先看筛选与日期分组 helper，再看周/月/列表视图模板，最后看状态更新逻辑。
*/
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMerchantDeliveryList } from '@/api/delivery'
import { getInterviewList, updateInterviewStatus } from '@/api/interview'
import { formatDateYMD, formatTimeHM } from '@/utils/format'
import { fetchAllPagedRecords } from '@/utils/pagedRecords'

const router = useRouter()
const loading = ref(false)
const interviewItems = ref([])
const viewMode = ref('week')
const sideTab = ref('today')
const selectedDate = ref(new Date())
const weekStart = ref(getStartOfWeek(selectedDate.value))

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
const DELIVERY_PAGE_SIZE = 100
const DELIVERY_MAX_PAGES = 50
const FILTER_STATUS_VALUE_MAP = {
  pending: 0,
  confirmed: 1,
  done: 4,
  cancel: 3
}

const filters = reactive({
  keyword: '',
  status: 'all',
  date: ''
})

// 统一解析时间，兼容不同格式
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

const formatTimeOnly = (value) => {
  return formatTimeHM(value)
}

const formatDate = (value) => {
  return formatDateYMD(value, '')
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

const getScheduleDateKey = (value) => {
  const date = parseScheduleDate(value)
  return date ? getDateKey(date) : ''
}

function getStartOfWeek(date) {
  const temp = new Date(date)
  temp.setHours(0, 0, 0, 0)
  const day = temp.getDay()
  const diff = day === 0 ? -6 : 1 - day
  temp.setDate(temp.getDate() + diff)
  return temp
}

const getInterviewModeLabel = (method) => {
  if (!method) return '未标注'
  const text = String(method)
  if (text.includes('线下')) return '线下面试'
  if (text.includes('线上')) return '线上面试'
  return method
}

const getInterviewModeTag = (method) => {
  if (!method) return 'info'
  const text = String(method)
  if (text.includes('线下')) return 'warning'
  if (text.includes('线上')) return 'success'
  return 'info'
}

const getInterviewStatusText = (status) => {
  return INTERVIEW_STATUS_TEXT_MAP[status] || '未知'
}

const getInterviewStatusTag = (status) => {
  return INTERVIEW_STATUS_TAG_MAP[status] || 'info'
}

// 面试紧急度提示
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

const canConfirm = (status) => status === 0
const canComplete = (status) => status === 1
const canCancel = (status) => status === 0 || status === 1

const filteredInterviews = computed(() => {
  let list = interviewItems.value.slice()
  if (filters.status !== 'all') {
    const target = FILTER_STATUS_VALUE_MAP[filters.status]
    list = list.filter((item) => item.status === target)
  }

  if (filters.date) {
    list = list.filter((item) => formatDate(item.scheduleTime) === filters.date)
  }

  if (filters.keyword) {
    const keyword = filters.keyword.trim()
    if (keyword) {
      list = list.filter((item) => {
        const applicantName = item.applicantName || ''
        const jobName = item.jobName || ''
        return applicantName.includes(keyword) || jobName.includes(keyword)
      })
    }
  }

  return list
})

const scheduledItems = computed(() => filteredInterviews.value.filter((item) => parseScheduleDate(item.scheduleTime)))
const pendingItems = computed(() => filteredInterviews.value.filter((item) => item.status === 0))
const scheduledItemsByDateKey = computed(() => {
  const grouped = new Map()
  scheduledItems.value.forEach((item) => {
    const key = getScheduleDateKey(item.scheduleTime)
    if (!key) return
    const current = grouped.get(key) || []
    current.push(item)
    grouped.set(key, current)
  })
  return grouped
})

const todayItems = computed(() => {
  const todayKey = getDateKey(new Date())
  return scheduledItems.value.filter((item) => getScheduleDateKey(item.scheduleTime) === todayKey)
})

const interviewStats = computed(() => {
  const todayKey = getDateKey(new Date())
  const weekKeys = new Set(weekDays.value.map((day) => getDateKey(day)))
  const weekCount = scheduledItems.value.filter((item) => weekKeys.has(getScheduleDateKey(item.scheduleTime))).length
  const todayCount = scheduledItems.value.filter((item) => getScheduleDateKey(item.scheduleTime) === todayKey).length
  return {
    total: filteredInterviews.value.length,
    week: weekCount,
    today: todayCount,
    pending: pendingItems.value.length
  }
})

const weekDays = computed(() => {
  const start = weekStart.value
  return Array.from({ length: 7 }, (_, index) => {
    const day = new Date(start)
    day.setDate(start.getDate() + index)
    return day
  })
})

const weekHeader = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']

const weekLabel = computed(() => {
  const start = weekStart.value
  const end = new Date(start)
  end.setDate(start.getDate() + 6)
  return `${formatDate(start)} 至 ${formatDate(end)}`
})

const monthLabel = computed(() => {
  const start = getStartOfWeek(selectedDate.value || new Date())
  const end = new Date(start)
  end.setDate(start.getDate() + 34)
  return `${formatDate(start)} 至 ${formatDate(end)}`
})

const isToday = (date) => {
  const today = getDateKey(new Date())
  return getDateKey(date) === today
}

const isSelectedDate = (date) => {
  if (!selectedDate.value) return false
  return getDateKey(date) === getDateKey(selectedDate.value)
}

const isSameMonth = (date, target) => {
  const base = target || selectedDate.value || new Date()
  return date.getFullYear() === base.getFullYear() && date.getMonth() === base.getMonth()
}

const getItemsForDay = (day) => {
  const dayKey = getDateKey(day)
  return scheduledItemsByDateKey.value.get(dayKey) || []
}

const getPreviewItemsForDay = (day, limit) => {
  return getItemsForDay(day).slice(0, limit)
}

const getMoreCountForDay = (day, limit) => {
  const count = getItemsForDay(day).length
  return count > limit ? count - limit : 0
}

const selectedDayItems = computed(() => {
  if (!selectedDate.value) return []
  const key = getDateKey(selectedDate.value)
  return scheduledItems.value.filter((item) => getScheduleDateKey(item.scheduleTime) === key)
})

const selectedDateLabel = computed(() => {
  if (!selectedDate.value) return ''
  return formatDate(selectedDate.value)
})

const monthDays = computed(() => {
  // 滚动月视图：从选中日期所在周开始，展示 5 周
  const start = getStartOfWeek(selectedDate.value || new Date())
  return Array.from({ length: 35 }, (_, index) => {
    const day = new Date(start)
    day.setDate(start.getDate() + index)
    return day
  })
})

const selectDate = (day) => {
  selectedDate.value = new Date(day)
  sideTab.value = 'selected'
}

const handleSummaryClick = (type) => {
  if (type === 'today') {
    viewMode.value = 'week'
    sideTab.value = 'today'
    goToday()
    return
  }
  if (type === 'pending') {
    viewMode.value = 'week'
    sideTab.value = 'pending'
    return
  }
  if (type === 'week') {
    viewMode.value = 'week'
    sideTab.value = 'selected'
    weekStart.value = getStartOfWeek(selectedDate.value || new Date())
    return
  }
  if (type === 'total') {
    viewMode.value = 'list'
  }
}

const goToday = () => {
  const today = new Date()
  selectedDate.value = today
  weekStart.value = getStartOfWeek(today)
}

const prevWeek = () => {
  const target = new Date(weekStart.value)
  target.setDate(target.getDate() - 7)
  weekStart.value = getStartOfWeek(target)
  selectedDate.value = new Date(target)
}

const nextWeek = () => {
  const target = new Date(weekStart.value)
  target.setDate(target.getDate() + 7)
  weekStart.value = getStartOfWeek(target)
  selectedDate.value = new Date(target)
}

const prevMonth = () => {
  const base = selectedDate.value || new Date()
  const target = new Date(base)
  target.setMonth(base.getMonth() - 1)
  selectedDate.value = target
  weekStart.value = getStartOfWeek(target)
}

const nextMonth = () => {
  const base = selectedDate.value || new Date()
  const target = new Date(base)
  target.setMonth(base.getMonth() + 1)
  selectedDate.value = target
  weekStart.value = getStartOfWeek(target)
}

const goPrev = () => {
  if (viewMode.value === 'month') {
    prevMonth()
    return
  }
  prevWeek()
}

const goNext = () => {
  if (viewMode.value === 'month') {
    nextMonth()
    return
  }
  nextWeek()
}

const prevLabel = computed(() => (viewMode.value === 'month' ? '上个月' : '上一周'))
const nextLabel = computed(() => (viewMode.value === 'month' ? '下个月' : '下一周'))

const openResumeList = () => {
  router.push('/merchant/resumes')
}

const resetFilters = () => {
  filters.keyword = ''
  filters.status = 'all'
  filters.date = ''
}

const handleUpdateStatus = async (item, status) => {
  const actionMap = { 1: '确认面试', 3: '取消面试', 4: '标记完成' }
  const actionLabel = actionMap[status] || '更新状态'
  try {
    await ElMessageBox.confirm(`确认要${actionLabel}吗？`, '操作确认', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (error) {
    return
  }

  try {
    await updateInterviewStatus({ id: item.id, status })
    ElMessage.success(`${actionLabel}成功`)
    fetchInterviewSchedule()
  } catch (error) {
    console.error('更新面试状态失败:', error)
    ElMessage.error('更新面试状态失败')
  }
}

// 拉取商家侧面试记录并整合候选人信息
const fetchInterviewSchedule = async () => {
  loading.value = true
  try {
    const { records, truncated } = await fetchAllPagedRecords({
      fetchPage: getMerchantDeliveryList,
      baseParams: { status: 2 },
      pageSize: DELIVERY_PAGE_SIZE,
      maxPages: DELIVERY_MAX_PAGES,
      errorMessage: '获取面试日程失败'
    })
    if (truncated) {
      ElMessage.warning(`面试记录较多，当前仅载入前 ${DELIVERY_PAGE_SIZE * DELIVERY_MAX_PAGES} 条投递`)
    }

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
          applicantAvatar: applicant.avatar,
          applicantPhone: applicant.phone || applicant.mobile || '',
          jobName: row.jobName || row.jobTitle || row.job?.title || ''
        }))
      } catch (error) {
        return []
      }
    })

    const result = await Promise.all(tasks)
    const merged = result.flat()
    interviewItems.value = merged
      .slice()
      .sort((a, b) => {
        const timeA = parseScheduleDate(a.scheduleTime)
        const timeB = parseScheduleDate(b.scheduleTime)
        if (!timeA && !timeB) return Number(a.roundNo) - Number(b.roundNo)
        if (!timeA) return 1
        if (!timeB) return -1
        return timeA.getTime() - timeB.getTime()
      })
  } catch (error) {
    console.error('获取面试日程失败:', error)
    ElMessage.error('获取面试日程失败')
    interviewItems.value = []
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchInterviewSchedule()
})

watch(selectedDate, (value) => {
  if (!value) return
  weekStart.value = getStartOfWeek(value)
})
</script>

<style scoped>
.interview-page {
  width: 100%;
  max-width: none;
  margin: 8px auto;
  padding: 0 8px;
}

.interview-page :deep(.el-card) {
  border-radius: 18px;
}

.interview-page :deep(.el-card__body) {
  padding: 16px;
}

.overview-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(220px, 300px);
  gap: 12px;
  margin-bottom: 16px;
  align-items: start;
}

.overview-card,
.summary-card {
  padding: 12px 14px;
  border-radius: 14px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.summary-card {
  min-width: 220px;
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
  border-color: #cbd5f5;
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.08);
  transform: translateY(-1px);
}

.summary-item.is-today {
  border-color: #bfdbfe;
  color: #1d4ed8;
}

.summary-item.is-pending {
  border-color: #fde68a;
  color: #b45309;
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

.filter-row {
  gap: 10px;
  margin-bottom: 16px;
}

.filter-input {
  width: auto;
}

.filter-select {
  width: auto;
}

.filter-date {
  width: auto;
}

.calendar-wrap,
.month-wrap {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 16px;
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

.calendar-grid,
.month-grid {
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  overflow: hidden;
  background: #ffffff;
}

.calendar-row {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
}

.calendar-head {
  background: #f1f5f9;
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

.calendar-cell:nth-child(7n),
.month-cell:nth-child(7n) {
  border-right: none;
}

.calendar-cell.is-today {
  background: #f0f7ff;
}

.calendar-date,
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
  background: #f8fafc;
  border-radius: 8px;
  padding: 4px 6px;
  border: 1px solid #e2e8f0;
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
  border: 1px solid #e2e8f0;
  background: #f8fafc;
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
  border: 1px solid #e2e8f0;
  padding: 12px;
  background: #ffffff;
}

.side-card--tabs {
  padding: 8px 10px 12px;
  max-height: 640px;
  overflow: hidden;
}

.side-tabs :deep(.el-tabs__header) {
  margin: 0 0 8px;
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
  border: 1px dashed #e2e8f0;
  border-radius: 10px;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.side-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
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
  border: 1px solid #e2e8f0;
  padding: 12px;
  background: #ffffff;
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
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

.interview-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.interview-card-item {
  border-radius: 14px;
  border: 1px solid #e2e8f0;
  background: #ffffff;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  box-shadow: 0 6px 16px rgba(15, 23, 42, 0.04);
}

.card-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.time-block {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.time-text {
  font-size: 16px;
  font-weight: 600;
  color: #1d4ed8;
}

.date-text {
  font-size: 12px;
  color: #64748b;
}

.candidate-row {
  display: flex;
  gap: 10px;
  align-items: center;
}

.candidate-name {
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
}

.candidate-job {
  font-size: 12px;
  color: #64748b;
  margin-top: 2px;
}

.card-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  font-size: 12px;
  color: #475569;
}

.meta-split {
  color: #cbd5f5;
}

.card-remark {
  font-size: 12px;
  color: #64748b;
}

.card-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

@media (max-width: 1100px) {
  .calendar-wrap,
  .month-wrap {
    grid-template-columns: 1fr;
  }
  .overview-grid {
    grid-template-columns: 1fr;
  }
  .interview-grid {
    grid-template-columns: 1fr;
  }

  .toolbar-right {
    width: 100%;
    justify-content: flex-start;
  }
}

@media (max-width: 720px) {
  .interview-page {
    padding: 0;
  }
  .toolbar-row {
    flex-direction: column;
    align-items: flex-start;
  }

  .toolbar-left,
  .toolbar-right,
  .filter-row {
    width: 100%;
  }

  .toolbar-left > * {
    flex: 1 1 140px;
  }

  .toolbar-right > * {
    flex: 1 1 88px;
  }

  .toolbar-date,
  .filter-input,
  .filter-select,
  .filter-date {
    min-width: 0;
    width: 100%;
  }

  .filter-row :deep(.el-button) {
    flex: 1 1 120px;
  }

  .calendar-body {
    min-height: auto;
  }
}
</style>
