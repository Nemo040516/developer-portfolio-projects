<!--
文件速览：
1. 文件职责：求职者投递记录页，负责状态筛选、搜索排序、投递卡片展示与面试进度弹窗。
2. 页面入口：求职者路由 `/applicant/applications`。
3. 关键结构：statusTabs、queryParams、filter-pills-row、apple-grid、displayInterviewRounds、app-footer。
4. 阅读建议：先看顶部筛选条，再看卡片网格，最后看面试进度弹窗与底部断点样式。
-->
<template>
  <div class="applications-page">
    <!-- 1. 精简筛选与排序栏 (Apple Premium Bar) -->
    <header class="app-header">
      <div class="header-content">
        <div class="filter-pills-row">
          <div class="pill-group">
            <div 
              v-for="tab in statusTabs" 
              :key="tab.value"
              :class="['filter-pill', { active: queryParams.status === tab.value }]"
              @click="setStatusFilter(tab.value)"
            >
              {{ tab.label }}
              <span class="count-dot" v-if="statusCount[tab.value] > 0">{{ statusCount[tab.value] }}</span>
            </div>
          </div>
          
          <div class="header-right-tools">
            <div class="sort-pill" @click="toggleTimeOrder">
              <el-icon><Sort /></el-icon>
              <span>{{ queryParams.timeOrder === 'desc' ? '最新投递' : '最早投递' }}</span>
            </div>
            <div class="search-capsule">
              <el-input
                v-model="keywordInput"
                placeholder="搜索公司/职位"
                prefix-icon="Search"
                clearable
                @keyup.enter="handleSearch"
              />
            </div>
          </div>
        </div>
      </div>
    </header>

    <!-- 2. Apple 风格卡片网格 -->
    <main class="applications-feed" v-loading="loading">
      <div v-if="applications.length" class="apple-grid">
        <div 
          v-for="app in applications" 
          :key="app.id" 
          :class="['apple-app-card', `status-card-${app.status ?? 0}`]"
        >
          <div class="card-top">
            <div class="company-meta">
              <el-avatar :size="36" :src="formatFileUrl(app.companyLogo)" class="apple-logo" />
              <div class="company-text">
                <span class="card-company-name">{{ app.companyName }}</span>
                <span class="card-time">投递于 {{ formatSimpleTime(app.createTime) }}</span>
              </div>
            </div>
            <el-tag :type="getStatusTag(app.status)" size="small" round effect="plain" class="status-tag-pill">
              {{ getStatusText(app.status) }}
            </el-tag>
          </div>

          <div class="card-body" @click="viewJob(app)">
            <h3 class="card-job-title">{{ app.jobTitle || '职位已删除' }}</h3>
            <div class="card-salary">{{ formatSalaryK(app.minSalary, app.maxSalary) }}</div>
          </div>

          <div v-if="app.status === 2 || app.status === 3" class="apple-feedback-row" @click="app.status === 2 ? openInvite(app) : null">
            <template v-if="app.status === 2">
              <div class="feedback-pill feedback-pill-blue">
                <el-icon><Calendar /></el-icon> 收到面试安排
              </div>
            </template>
            <template v-else>
              <div class="feedback-pill feedback-pill-gray">
                <el-icon><InfoFilled /></el-icon> 暂时不匹配反馈
              </div>
            </template>
          </div>

          <div class="card-footer-apple">
            <div class="footer-hint">{{ isJobVisible(app) ? '查看职位详情' : '职位已下线' }}</div>
            <div class="footer-actions">
              <el-button link icon="ChatDotRound" @click.stop="handleChat(app)"></el-button>
              <el-button link icon="View" @click.stop="viewJob(app)"></el-button>
            </div>
          </div>
        </div>
      </div>
      <el-empty v-else description="暂无记录" />
    </main>

    <!-- 3. 固定底部分页 (Footer) -->
    <footer class="app-footer" v-if="total > 0">
      <el-pagination
        v-model:current-page="queryParams.current"
        v-model:page-size="queryParams.size"
        :total="total"
        layout="total, prev, pager, next"
        background
        @current-change="fetchApplications"
      />
    </footer>

    <el-dialog v-model="inviteDialogVisible" title="面试进度" width="520px" center destroy-on-close>
      <div class="invite-dialog">
        <div class="invite-summary-panel">
          <div class="invite-summary-main">
            <div class="invite-summary-job">{{ currentInviteDisplay.jobTitleText }}</div>
            <div class="invite-summary-company">{{ currentInviteDisplay.companyNameText }}</div>
            <div class="invite-summary-meta">
              最新安排：{{ currentInviteDisplay.latestScheduleText }}
            </div>
          </div>
          <el-tag size="small" :type="currentInviteDisplay.latestStatus.type" effect="plain" round>
            {{ currentInviteDisplay.latestStatus.text }}
          </el-tag>
        </div>

        <div class="invite-block">
          <div class="invite-block-title">面试轮次</div>
          <el-skeleton v-if="inviteLoading" animated :rows="3" />
          <div v-else-if="displayInterviewRounds.length" class="invite-list">
            <div v-for="item in displayInterviewRounds" :key="item.id" class="invite-item">
              <div class="invite-item-header">
                <span class="invite-item-round">{{ item.roundLabel }}</span>
                <el-tag size="small" :type="item.displayStatus.type" effect="plain" round>
                  {{ item.displayStatus.text }}
                </el-tag>
              </div>
              <div class="invite-item-meta">时间：{{ item.dateTimeText }}</div>
              <div class="invite-item-meta">
                方式：
                <el-tag size="small" :type="item.modeMeta.tag" effect="plain">
                  {{ item.modeMeta.label }}
                </el-tag>
              </div>
              <div class="invite-item-meta">{{ item.locationLabel }}{{ item.locationText }}</div>
              <div class="invite-item-meta">备注：{{ item.remarkText }}</div>
              <div v-if="item.canOperate && item.status === 0" class="invite-item-actions">
                <el-button
                  size="small"
                  type="success"
                  plain
                  :disabled="item.isBlocked"
                  @click="handleInterviewStatus(item, 1)"
                >
                  确认面试
                </el-button>
                <el-button
                  size="small"
                  type="danger"
                  plain
                  :disabled="item.isBlocked"
                  @click="handleInterviewStatus(item, 2)"
                >
                  拒绝面试
                </el-button>
              </div>
              <div v-if="item.isBlocked" class="invite-item-tip">需先确认上一轮面试</div>
            </div>
          </div>
          <div v-else class="invite-empty">暂无面试记录</div>
        </div>
      </div>
      <template #footer>
        <el-button @click="inviteDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { getMyApplications } from '@/api/applicant'
import { getInterviewList, updateInterviewStatus } from '@/api/interview'
import { getPublicJobDetail } from '@/api/job'
import { formatFileUrl } from '@/utils/file'
import { formatDateTime, formatMonthDayTime, formatSalaryK } from '@/utils/format'
import { ElMessage } from 'element-plus'
import { Calendar, InfoFilled, Sort } from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const applications = ref([])
const total = ref(0)
const keywordInput = ref('')

const queryParams = reactive({
  current: 1,
  size: 12,
  status: null,
  keyword: null,
  timeOrder: 'desc'
})

const statusTabs = [
  { label: '全部', value: null },
  { label: '已投递', value: 0 },
  { label: '已初筛', value: 1 },
  { label: '面试邀约', value: 2 },
  { label: '不合适', value: 3 }
]

const inviteDialogVisible = ref(false)
const inviteLoading = ref(false)
const currentInvite = ref({})
const interviewRounds = ref([])

const INTERVIEW_STATUS_META_MAP = {
  0: { text: '待确认', type: 'warning' },
  1: { text: '已确认', type: 'success' },
  2: { text: '已拒绝', type: 'danger' },
  3: { text: '已取消', type: 'info' },
  4: { text: '已完成', type: 'primary' }
}
const BLOCKED_INTERVIEW_DISPLAY_STATUS = {
  text: '等待上一轮确认',
  type: 'info'
}
const INTERVIEW_MODE_META_MAP = {
  ONLINE: {
    label: '线上面试',
    tag: 'success',
    locationLabel: '会议链接：'
  },
  OFFLINE: {
    label: '线下面试',
    tag: 'warning',
    locationLabel: '面试地点：'
  },
  UNKNOWN: {
    label: '未标注',
    tag: 'info',
    locationLabel: '面试地点：'
  }
}

const statusCount = computed(() => {
  const map = { null: 0, 0: 0, 1: 0, 2: 0, 3: 0 }
  applications.value.forEach(item => {
    const s = item.status === null ? 0 : Number(item.status)
    if (map[s] !== undefined) map[s]++
  })
  map[null] = total.value
  return map
})

const getStatusText = (s) => ({ 0: '已投递', 1: '已初筛', 2: '面试中', 3: '不合适' }[s ?? 0] || '未知')
const getStatusTag = (s) => ({ 0: 'info', 1: 'primary', 2: 'success', 3: 'warning' }[s ?? 0] || 'info')
const isJobVisible = (app) => Number(app.jobStatus) === 1 && Number(app.jobAuditStatus) === 1
const getInterviewStatusMeta = (status) => INTERVIEW_STATUS_META_MAP[Number(status)] || { text: '未知', type: 'info' }
const getInterviewMode = (method) => {
  const text = String(method || '')
  if (text.includes('线下')) return 'OFFLINE'
  if (text.includes('线上')) return 'ONLINE'
  return 'UNKNOWN'
}
const getInterviewModeMeta = (method) => INTERVIEW_MODE_META_MAP[getInterviewMode(method)] || INTERVIEW_MODE_META_MAP.UNKNOWN
const isRoundBlocked = (list, index) => Array.isArray(list) && index > 0 && list.slice(0, index).some(item => Number(item?.status) === 0)
const getInterviewDisplayStatus = (item, index, list) => (isRoundBlocked(list, index)
  ? BLOCKED_INTERVIEW_DISPLAY_STATUS
  : getInterviewStatusMeta(item?.status))
const sortInterviewRounds = (list) => [...(Array.isArray(list) ? list : [])].sort((a, b) => Number(a?.roundNo) - Number(b?.roundNo))
const buildFallbackInterviewRound = (app) => {
  if (!app) return null
  const hasInterviewInfo = app.interviewTime
    || app.interviewLocation
    || app.interviewMethod
    || app.interviewRemark
    || app.interviewStatus !== null
    && app.interviewStatus !== undefined
  if (!hasInterviewInfo) return null
  return {
    id: `fallback-${app.id}`,
    deliveryId: app.id,
    roundNo: 1,
    status: app.interviewStatus ?? 0,
    scheduleTime: app.interviewTime || null,
    method: app.interviewMethod || null,
    location: app.interviewLocation || null,
    remark: app.interviewRemark || null,
    canOperate: false
  }
}
const buildInterviewRoundDisplay = (item, index, list) => {
  const modeMeta = getInterviewModeMeta(item?.method)
  const canOperate = item?.canOperate ?? Number.isFinite(Number(item?.id))
  return {
    ...item,
    canOperate,
    roundLabel: `第 ${item?.roundNo || index + 1} 轮`,
    dateTimeText: formatDateTime(item?.scheduleTime, '待商家补充时间'),
    modeMeta,
    locationLabel: modeMeta.locationLabel,
    locationText: item?.location || '待商家补充',
    remarkText: item?.remark || '暂无备注',
    displayStatus: getInterviewDisplayStatus(item, index, list),
    isBlocked: canOperate && isRoundBlocked(list, index)
  }
}

const currentInviteDisplay = computed(() => {
  const latestRound = interviewRounds.value[interviewRounds.value.length - 1] || buildFallbackInterviewRound(currentInvite.value)
  const latestStatus = latestRound
    ? getInterviewStatusMeta(latestRound.status)
    : { text: getStatusText(currentInvite.value?.status), type: getStatusTag(currentInvite.value?.status) }

  return {
    jobTitleText: currentInvite.value?.jobTitle || '职位信息待补充',
    companyNameText: currentInvite.value?.companyName || '企业信息待补充',
    latestScheduleText: latestRound?.scheduleTime
      ? formatDateTime(latestRound.scheduleTime, '待商家确认')
      : '待商家补充安排',
    latestStatus
  }
})
const displayInterviewRounds = computed(() => interviewRounds.value.map((item, index) => buildInterviewRoundDisplay(item, index, interviewRounds.value)))

const fetchApplications = async () => {
  loading.value = true
  try {
    const res = await getMyApplications(queryParams)
    applications.value = res.data.records || []
    total.value = res.data.total || 0
  } finally { loading.value = false }
}

const handleSearch = () => { queryParams.keyword = keywordInput.value.trim() || null; queryParams.current = 1; fetchApplications() }
const setStatusFilter = (s) => { queryParams.status = s; queryParams.current = 1; fetchApplications() }
const toggleTimeOrder = () => { queryParams.timeOrder = queryParams.timeOrder === 'desc' ? 'asc' : 'desc'; queryParams.current = 1; fetchApplications() }

const formatSimpleTime = (v) => {
  return formatMonthDayTime(v, v || '')
}

const viewJob = (app) => router.push(`/job/detail/${app.jobId}`)
const handleChat = async (app) => {
  try {
    const res = await getPublicJobDetail(app.jobId)
    const job = res.data || res
    router.push({ path: '/chat', query: { targetId: job.publisherId, targetName: job.publisherName, companyName: app.companyName, jobTitle: app.jobTitle, jobId: app.jobId } })
  } catch (e) { ElMessage.error('无法发起沟通') }
}

const loadInterviewRounds = async (app) => {
  if (!app?.id) {
    interviewRounds.value = []
    return
  }
  inviteLoading.value = true
  try {
    const res = await getInterviewList(app.id)
    const list = sortInterviewRounds(res.data || [])
    const fallback = buildFallbackInterviewRound(app)
    interviewRounds.value = list.length ? list : (fallback ? [fallback] : [])
  } catch (error) {
    console.error('获取面试进度失败:', error)
    const fallback = buildFallbackInterviewRound(app)
    interviewRounds.value = fallback ? [fallback] : []
  } finally {
    inviteLoading.value = false
  }
}

const openInvite = async (app) => {
  currentInvite.value = app || {}
  inviteDialogVisible.value = true
  interviewRounds.value = []
  await loadInterviewRounds(app)
}

const handleInterviewStatus = async (item, status) => {
  if (!item?.canOperate || !item?.id) return
  if (item.isBlocked) {
    ElMessage.warning('请先确认上一轮面试')
    return
  }
  try {
    await updateInterviewStatus({ id: item.id, status })
    ElMessage.success('面试状态已更新')
    await Promise.all([
      fetchApplications(),
      loadInterviewRounds(currentInvite.value)
    ])
    const refreshedInvite = applications.value.find(app => Number(app.id) === Number(currentInvite.value?.id))
    if (refreshedInvite) {
      currentInvite.value = refreshedInvite
    }
  } catch (error) {
    console.error('更新面试状态失败:', error)
  }
}

onMounted(() => { if (route.query.status) queryParams.status = Number(route.query.status); fetchApplications() })
</script>

<style scoped>
.applications-page {
  position: relative;
  width: min(calc(100% - (var(--ui-shell-gutter) * 2)), var(--ui-main-shell-max-width));
  margin: 0 auto;
  padding: 28px 0 40px;
  /* 核心：撑开全屏高度，确保 footer 在底 */
  display: flex;
  flex-direction: column;
  min-height: calc(100vh - 84px);
  box-sizing: border-box;
  overflow: visible;
}

.applications-page::before,
.applications-page::after {
  content: '';
  position: absolute;
  pointer-events: none;
  border-radius: 50%;
  filter: blur(4px);
  z-index: 0;
}

.applications-page::before {
  top: 18px;
  left: 2%;
  width: min(24vw, 260px);
  height: min(24vw, 260px);
  background: radial-gradient(circle, rgba(10, 132, 255, 0.1), rgba(10, 132, 255, 0) 70%);
}

.applications-page::after {
  top: 120px;
  right: 4%;
  width: min(22vw, 220px);
  height: min(22vw, 220px);
  background: radial-gradient(circle, rgba(122, 162, 255, 0.09), rgba(122, 162, 255, 0) 72%);
}

.app-header { position: relative; z-index: 1; margin-bottom: 24px; flex-shrink: 0; }
.header-content {
  border-radius: 22px;
  border: 1px solid rgba(255, 255, 255, 0.74);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.9) 0%, rgba(251, 251, 253, 0.82) 100%),
    linear-gradient(135deg, rgba(10, 132, 255, 0.08), rgba(10, 132, 255, 0.03) 46%, rgba(191, 219, 254, 0.12));
  box-shadow: 0 18px 40px rgba(15, 23, 42, 0.08);
  padding: 16px;
  backdrop-filter: blur(14px);
}
.filter-pills-row { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; flex-wrap: wrap; }
.pill-group { display: flex; gap: 8px; flex-wrap: wrap; flex: 1 1 420px; min-width: 0; }

.filter-pill {
  padding: 7px 16px;
  background: rgba(255, 255, 255, 0.66);
  border-radius: 999px;
  font-size: 13px;
  color: #1d1d1f;
  cursor: pointer;
  transition: all var(--ui-motion-fast) var(--ui-ease-standard);
  display: flex;
  align-items: center;
  gap: 6px;
  border: 1px solid transparent;
  font-weight: 500;
}
.filter-pill.active { background: #ffffff; border-color: rgba(10, 132, 255, 0.2); color: #007aff; box-shadow: 0 8px 18px rgba(10, 132, 255, 0.12); }
.count-dot { font-size: 10px; background: rgba(0, 122, 255, 0.1); padding: 1px 6px; border-radius: 999px; }

.header-right-tools { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; margin-left: auto; justify-content: flex-end; }
.sort-pill {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #6e6e73;
  cursor: pointer;
  padding: 7px 12px;
  border-radius: 999px;
  transition: all var(--ui-motion-fast) var(--ui-ease-standard);
  background: rgba(255, 255, 255, 0.66);
  border: 1px solid transparent;
  white-space: nowrap;
}
.sort-pill:hover { background: #ffffff; color: #1d1d1f; border-color: rgba(0, 122, 255, 0.28); box-shadow: 0 8px 18px rgba(10, 132, 255, 0.08); }
.search-capsule {
  width: clamp(200px, 20vw, 300px);
  min-width: 200px;
}
.search-capsule :deep(.el-input__wrapper) {
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.7);
  box-shadow: none !important;
  width: 100%;
  border: 1px solid transparent;
  transition: border-color var(--ui-motion-fast) var(--ui-ease-standard), background-color var(--ui-motion-fast) var(--ui-ease-standard);
}
.search-capsule :deep(.el-input__wrapper.is-focus) {
  background: #fff;
  box-shadow: none !important;
  border-color: #007aff;
}

.applications-feed {
  flex: 1; /* 撑开中间，挤走 footer */
  margin-bottom: 40px;
  position: relative;
  z-index: 1;
}

.apple-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: clamp(16px, 1.4vw, 20px);
}

.apple-app-card {
  position: relative;
  overflow: hidden;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(249, 250, 252, 0.94));
  border-radius: 22px;
  border: 1px solid rgba(0, 0, 0, 0.05);
  padding: 18px 18px 14px;
  display: flex;
  flex-direction: column;
  transition: all var(--ui-motion-base) var(--ui-ease-standard);
  min-height: 196px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.03);
}

.apple-app-card::before {
  content: '';
  position: absolute;
  inset: 0 0 auto;
  height: 3px;
  background: linear-gradient(90deg, var(--status-accent, #0a84ff), rgba(255, 255, 255, 0));
}

.apple-app-card::after {
  content: '';
  position: absolute;
  top: -48px;
  right: -40px;
  width: 132px;
  height: 132px;
  border-radius: 50%;
  background: radial-gradient(circle, var(--status-glow, rgba(10, 132, 255, 0.14)), rgba(255, 255, 255, 0) 68%);
  pointer-events: none;
}

.status-card-2 {
  --status-accent: #30a46c;
  --status-glow: rgba(48, 164, 108, 0.14);
  background: linear-gradient(180deg, #ffffff 0%, #f5fcfa 100%);
  border-color: rgba(48, 164, 108, 0.18);
}
.status-card-3 {
  --status-accent: #ff9f0a;
  --status-glow: rgba(255, 159, 10, 0.12);
  background: linear-gradient(180deg, #ffffff 0%, #fff9f4 100%);
  border-color: rgba(255, 159, 10, 0.16);
}
.status-card-0 {
  --status-accent: #0a84ff;
  --status-glow: rgba(10, 132, 255, 0.12);
  background: linear-gradient(180deg, #ffffff 0%, #f6fbff 100%);
  border-color: rgba(10, 132, 255, 0.12);
}

.status-card-1 {
  --status-accent: #2563eb;
  --status-glow: rgba(37, 99, 235, 0.12);
  background: linear-gradient(180deg, #ffffff 0%, #f5f9ff 100%);
  border-color: rgba(37, 99, 235, 0.15);
}

.apple-app-card:hover {
  transform: translateY(-3px);
  border-color: rgba(0, 122, 255, 0.18);
  box-shadow: 0 16px 34px rgba(15, 23, 42, 0.1);
}

.card-top { display: flex; align-items: flex-start; justify-content: space-between; gap: 10px; margin-bottom: 14px; }
.company-meta { display: flex; align-items: center; gap: 10px; min-width: 0; flex: 1; }
.company-text { display: flex; flex-direction: column; min-width: 0; }
.apple-logo { border-radius: 10px; border: 1px solid rgba(255, 255, 255, 0.88); background: linear-gradient(180deg, #ffffff, #f8f9fc); width: 36px; height: 36px; box-shadow: 0 6px 14px rgba(15, 23, 42, 0.06); }
.card-company-name {
  font-size: 13px;
  font-weight: 600;
  color: #6e6e73;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.status-tag-pill { flex-shrink: 0; font-weight: 600; }

.card-body {
  cursor: pointer;
  border-radius: 14px;
  padding: 6px 2px 10px;
  transition: background-color var(--ui-motion-fast) var(--ui-ease-standard), padding-left var(--ui-motion-fast) var(--ui-ease-standard), padding-right var(--ui-motion-fast) var(--ui-ease-standard);
}
.card-body:hover {
  background: rgba(255, 255, 255, 0.72);
  padding-left: 10px;
  padding-right: 10px;
}

.card-job-title {
  font-size: 17px;
  font-weight: 700;
  color: #1d1d1f;
  margin: 0 0 6px;
  line-height: 1.35;
  min-height: 46px;
}
.card-salary { font-size: 15px; font-weight: 700; color: #0a84ff; margin-bottom: 4px; }
.card-time { font-size: 11px; color: #a1a1a6; margin-top: 2px; }

.apple-feedback-row { margin-top: 8px; margin-bottom: 8px; }
.status-card-2 .apple-feedback-row { cursor: pointer; }
.feedback-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 600;
  border: 1px solid transparent;
}
.feedback-pill-blue {
  background: rgba(0, 122, 255, 0.08);
  color: #007aff;
  border-color: rgba(0, 122, 255, 0.14);
}
.feedback-pill-gray {
  background: rgba(142, 142, 147, 0.1);
  color: #636366;
  border-color: rgba(142, 142, 147, 0.16);
}

.card-footer-apple {
  display: flex; justify-content: space-between; align-items: center; margin-top: auto; padding-top: 12px; border-top: 1px solid #f5f5f7;
}
.footer-hint { font-size: 12px; color: #8e8e93; }

.footer-actions { display: flex; gap: 4px; }
.footer-actions :deep(.el-button) {
  width: 30px;
  height: 30px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.8);
  color: #6e6e73;
  margin: 0;
  border: 1px solid rgba(15, 23, 42, 0.06);
}
.footer-actions :deep(.el-button:hover) {
  background: rgba(10, 132, 255, 0.12);
  color: #007aff;
}

.app-footer {
  margin-top: auto; /* 核心 */
  padding: 32px 0;
  display: flex;
  justify-content: center;
  border-top: 1px solid rgba(255, 255, 255, 0.64);
  position: relative;
  z-index: 1;
}
.app-footer :deep(.el-pagination.is-background .btn-prev),
.app-footer :deep(.el-pagination.is-background .btn-next),
.app-footer :deep(.el-pagination.is-background .el-pager li) {
  border-radius: 10px;
  background: #f5f5f7;
}
.app-footer :deep(.el-pagination.is-background .el-pager li.is-active) {
  background: #007aff;
  color: #fff;
}

.invite-dialog {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.invite-summary-panel {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 16px;
  border-radius: 16px;
  background: linear-gradient(180deg, rgba(248, 250, 255, 0.96), rgba(241, 246, 255, 0.92));
  border: 1px solid rgba(10, 132, 255, 0.08);
}

.invite-summary-main {
  min-width: 0;
}

.invite-summary-job {
  font-size: 16px;
  font-weight: 700;
  color: #1d1d1f;
  line-height: 1.4;
}

.invite-summary-company {
  margin-top: 4px;
  font-size: 13px;
  color: #475569;
}

.invite-summary-meta {
  margin-top: 8px;
  font-size: 12px;
  color: #64748b;
}

.invite-block {
  padding-top: 4px;
}

.invite-block-title {
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: 600;
  color: #1f2937;
}

.invite-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.invite-item {
  padding: 12px 14px;
  border-radius: 14px;
  background: rgba(247, 250, 255, 0.94);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.invite-item-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.invite-item-round {
  font-size: 13px;
  font-weight: 600;
  color: #1f2937;
}

.invite-item-meta {
  margin-top: 6px;
  font-size: 12px;
  color: #475569;
}

.invite-item-actions {
  margin-top: 10px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.invite-item-tip {
  margin-top: 8px;
  font-size: 12px;
  color: #64748b;
}

.invite-empty {
  font-size: 12px;
  color: #94a3b8;
}

@media (max-width: 960px) {
  .filter-pills-row {
    align-items: stretch;
  }

  .pill-group {
    width: 100%;
  }

  .header-right-tools {
    width: 100%;
    justify-content: space-between;
  }
}

@media (max-width: 1000px) { .apple-grid { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 700px) {
  .applications-page { padding: 24px 0 32px; }
  .header-content { padding: 12px; }
  .header-right-tools { align-items: stretch; }
  .sort-pill { justify-content: center; }
  .search-capsule { width: 100%; min-width: 0; }
  .apple-grid { grid-template-columns: 1fr; gap: 14px; }
  .card-top { flex-wrap: wrap; }
  .card-job-title { min-height: 0; }
  .app-footer { padding: 24px 0; }
  .invite-summary-panel { flex-direction: column; }
}

@media (max-width: 520px) {
  .filter-pill { padding: 7px 12px; }
  .footer-hint { max-width: 140px; }
}
</style>

