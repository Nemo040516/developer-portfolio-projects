<!--
文件速览：
1. 文件职责：管理员职位审核页，负责筛选、批量审核、查看详情与撤回审核。
2. 页面入口：管理员路由 `/admin/jobs`。
3. 关键结构：query、activeTab、jobs、selectedJobs、detailVisible。
4. 阅读建议：先看筛选与批量区，再看表格，最后看抽屉与底部样式。
-->
<template>
  <div class="admin-page">
    <el-card class="filter-card">
      <div class="filter-header">
        <div class="header-left">
          <span class="header-title">职位审核</span>
          <span class="header-hint">选择状态 → 查看详情 → 通过/驳回</span>
        </div>
      </div>
      <div class="filter-row adaptive-filter-row">
        <el-input v-model="query.keyword" placeholder="职位/企业关键词" clearable class="adaptive-filter-control adaptive-filter-control--lg" />
        <div class="adaptive-filter-actions">
          <el-button type="primary" @click="applyFilter">筛选</el-button>
          <el-button @click="resetFilter">重置</el-button>
        </div>
      </div>
      <div class="batch-row adaptive-meta-row">
        <div class="batch-actions">
          <el-button type="success" :disabled="!selectedJobs.length" @click="batchApprove">批量通过</el-button>
          <el-button type="danger" :disabled="!selectedJobs.length" @click="openBatchReject">批量驳回</el-button>
        </div>
        <div class="batch-hint">已选 {{ selectedJobs.length }} 条待审核记录</div>
      </div>
      <div class="status-tabs">
        <el-tabs v-model="activeTab" class="status-tabs-inner">
          <el-tab-pane name="all">
            <template #label>
              <span>全部</span>
              <span class="tab-count">{{ jobCounts.total }}</span>
            </template>
          </el-tab-pane>
          <el-tab-pane name="pending">
            <template #label>
              <span>待审核</span>
              <span class="tab-count">{{ jobCounts.pending }}</span>
            </template>
          </el-tab-pane>
          <el-tab-pane name="approved">
            <template #label>
              <span>已通过</span>
              <span class="tab-count">{{ jobCounts.approved }}</span>
            </template>
          </el-tab-pane>
          <el-tab-pane name="rejected">
            <template #label>
              <span>已驳回</span>
              <span class="tab-count">{{ jobCounts.rejected }}</span>
            </template>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-card>

    <el-card>
      <el-table
        ref="jobTableRef"
        :data="jobs"
        stripe
        v-loading="loading"
        :default-sort="defaultSort"
        :row-class-name="setRowClass"
        @selection-change="handleSelectionChange"
        @sort-change="handleSortChange"
      >
        <el-table-column type="selection" width="52" :selectable="rowSelectable" />
        <el-table-column label="职位/企业" min-width="280">
          <template #default="{ row }">
            <div class="job-title-cell">
              <div class="job-title-text">{{ row.title }}</div>
              <div class="job-sub-text">{{ row.companyName || '—' }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="headcount" label="招聘人数" width="100">
          <template #default="{ row }">
            {{ row.headcount || 1 }} 人
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="提交时间" width="180" sortable="custom" />
        <el-table-column prop="auditStatus" label="审核状态" width="160" sortable="custom">
          <template #default="{ row }">
            <div class="status-indicator">
              <span class="status-bar" :class="statusClass(row.auditStatus)"></span>
              <el-tag :type="statusTag(row.auditStatus)">{{ statusText(row.auditStatus) }}</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDetail(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-row adaptive-pagination">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next, jumper"
          :total="pagination.total"
          :page-size="pagination.size"
          :current-page="pagination.current"
          :page-sizes="[10, 20, 50, 100]"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" size="420px" title="职位详情">
        <div v-if="currentJob" class="drawer-body">
          <div class="detail-block">
            <div class="detail-item"><span>职位名称：</span>{{ currentJob.title }}</div>
            <div class="detail-item"><span>企业名称：</span>{{ currentJob.companyName }}</div>
            <div class="detail-item"><span>工作地点：</span>{{ currentJob.location || '—' }}</div>
            <div class="detail-item"><span>薪资范围：</span>{{ currentJob.salary || '—' }}</div>
            <div class="detail-item"><span>招聘人数：</span>{{ currentJob.headcount || 1 }} 人</div>
            <div class="detail-item"><span>标签：</span>{{ (currentJob.tags || []).join(' / ') }}</div>
            <div class="detail-item"><span>提交时间：</span>{{ currentJob.createdAt }}</div>
            <div class="detail-item"><span>变更摘要：</span>{{ currentJob.lastEditSummary || '—' }}</div>
            <div class="detail-item"><span>最近修改：</span>{{ currentJob.lastEditTime || '—' }}</div>
            <div class="detail-item"><span>审核状态：</span>{{ statusText(currentJob.auditStatus) }}</div>
            <div v-if="currentJob.reason" class="detail-item"><span>驳回原因：</span>{{ currentJob.reason }}</div>
          </div>
          <div class="audit-log-section">
            <div class="section-title">操作记录</div>
            <el-skeleton v-if="logsLoading" :rows="3" animated />
            <el-timeline v-else-if="auditLogs.length > 0">
              <el-timeline-item
                v-for="item in auditLogs"
                :key="item.id"
                :timestamp="item.createTime || '—'"
              >
                <div class="log-title">{{ formatLogAction(item) }}</div>
                <div class="log-detail">{{ item.detail || '—' }}</div>
                <div class="log-meta">
                  操作者：{{ item.operatorRole || '—' }} {{ item.operatorId || '' }}
                </div>
              </el-timeline-item>
            </el-timeline>
            <el-empty v-else description="暂无操作记录" />
          </div>
          <div class="drawer-actions">
            <el-button
              type="success"
              :disabled="currentJob.auditStatus !== 0"
              @click="approve(currentJob)"
            >
              通过
            </el-button>
            <el-button
              type="danger"
              :disabled="currentJob.auditStatus !== 0"
              @click="openReject(currentJob)"
            >
              驳回
            </el-button>
            <el-button
              v-if="currentJob.auditStatus !== 0"
              type="warning"
              @click="openRevoke(currentJob)"
            >
              撤回审核
            </el-button>
          </div>
        </div>
    </el-drawer>

    <!-- 驳回弹窗 -->
    <el-dialog v-model="rejectVisible" title="驳回原因" width="420px">
      <el-input v-model="rejectReason" type="textarea" rows="4" placeholder="请输入驳回原因" />
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" @click="confirmReject">确认驳回</el-button>
      </template>
    </el-dialog>

    <!-- 撤回弹窗 -->
    <el-dialog v-model="revokeVisible" title="撤回原因" width="420px">
      <el-input v-model="revokeReason" type="textarea" rows="4" placeholder="请输入撤回原因" />
      <template #footer>
        <el-button @click="revokeVisible = false">取消</el-button>
        <el-button type="warning" @click="confirmRevoke">确认撤回</el-button>
      </template>
    </el-dialog>

    <!-- 批量驳回弹窗 -->
    <el-dialog v-model="batchRejectVisible" title="批量驳回原因" width="420px">
      <el-input v-model="batchRejectReason" type="textarea" rows="4" placeholder="请输入驳回原因" />
      <template #footer>
        <el-button @click="batchRejectVisible = false">取消</el-button>
        <el-button type="danger" @click="confirmBatchReject">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, watch, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { auditJob, auditJobBatch, getAdminJobs, revokeJobAudit, getJobAuditLogs, getJobAuditCounts } from '@/api/admin'

const query = ref({
  keyword: '',
  status: null
})

const activeTab = ref('all')

const jobs = ref([])
const loading = ref(false)
const jobTableRef = ref(null)
const selectedJobs = ref([])
const pagination = ref({
  current: 1,
  size: 20,
  total: 0
})
const defaultSort = {
  prop: 'createdAt',
  order: 'descending'
}
const sortState = ref({
  prop: defaultSort.prop,
  order: defaultSort.order
})
const timeOrder = ref(defaultSort.order === 'ascending' ? 'asc' : 'desc')

const currentJob = ref(null)
const detailVisible = ref(false)
const rejectVisible = ref(false)
const rejectReason = ref('')
const revokeVisible = ref(false)
const revokeReason = ref('')
const batchRejectVisible = ref(false)
const batchRejectReason = ref('')
const auditLogs = ref([])
const logsLoading = ref(false)
const jobCounts = ref({
  total: 0,
  pending: 0,
  approved: 0,
  rejected: 0
})

const statusText = (status) => {
  const map = { 0: '待审核', 1: '已通过', 2: '已驳回' }
  return map[status] || '未知'
}

const statusTag = (status) => {
  const map = { 0: 'info', 1: 'success', 2: 'danger' }
  return map[status] || 'info'
}

const statusClass = (status) => {
  const map = {
    0: 'status-pending',
    1: 'status-approved',
    2: 'status-rejected'
  }
  return map[status] || 'status-unknown'
}

const isUserCancel = (error) => {
  const message = String(error || '')
  return message.includes('cancel') || message.includes('close')
}

const normalizeList = (data) => {
  if (Array.isArray(data)) return data
  return data?.records || []
}

const rowSelectable = (row) => {
  return Number(row?.auditStatus) === 0
}

const handleSelectionChange = (rows) => {
  selectedJobs.value = rows || []
}

const parseTags = (tags) => {
  if (Array.isArray(tags)) return tags
  if (typeof tags === 'string') {
    return tags.split(',').map((item) => item.trim()).filter(Boolean)
  }
  return []
}

const fetchJobs = async () => {
  loading.value = true
  try {
    const res = await getAdminJobs({
      keyword: query.value.keyword?.trim(),
      status: query.value.status,
      current: pagination.value.current,
      size: pagination.value.size,
      sortField: sortState.value.prop || undefined,
      sortOrder: sortState.value.prop === 'auditStatus'
        ? 'priority'
        : (sortState.value.order === 'ascending' ? 'asc' : (sortState.value.order === 'descending' ? 'desc' : undefined)),
      timeOrder: timeOrder.value
    })
    if (res.code === 200) {
      jobs.value = normalizeList(res.data).map((item) => ({
        ...item,
        lastEditSummary: item.lastEditSummary || '—'
      }))
      applyLocalSort()
      pagination.value.total = res.data?.total || 0
    } else {
      jobs.value = []
      pagination.value.total = 0
    }
  } catch (error) {
    console.error('获取职位审核列表失败', error)
    jobs.value = []
    pagination.value.total = 0
  } finally {
    loading.value = false
    await nextTick()
    if (jobTableRef.value) {
      jobTableRef.value.clearSelection()
    }
    selectedJobs.value = []
  }
}

const fetchCounts = async () => {
  try {
    const res = await getJobAuditCounts()
    if (res.code === 200 && res.data) {
      jobCounts.value = {
        total: res.data.total || 0,
        pending: res.data.pending || 0,
        approved: res.data.approved || 0,
        rejected: res.data.rejected || 0
      }
    }
  } catch (error) {
    console.error('获取审核统计失败', error)
  }
}

const applyFilter = () => {
  pagination.value.current = 1
  fetchJobs()
}

const resetFilter = () => {
  query.value.keyword = ''
  query.value.status = null
  activeTab.value = 'all'
  sortState.value = { ...defaultSort }
  timeOrder.value = defaultSort.order === 'ascending' ? 'asc' : 'desc'
  pagination.value.current = 1
  fetchJobs()
}

const openDetail = (row) => {
  currentJob.value = {
    ...row,
    tags: parseTags(row.tags)
  }
  detailVisible.value = true
  fetchAuditLogs(row.id)
}

const approve = async (row) => {
  try {
    await ElMessageBox.confirm('确认通过该职位审核吗？', '确认通过', {
      type: 'success',
      confirmButtonText: '确认通过',
      cancelButtonText: '取消'
    })
    await auditJob(row.id, { status: 1 })
    ElMessage.success('已通过审核')
    fetchJobs()
    fetchCounts()
    if (currentJob.value && currentJob.value.id === row.id) {
      currentJob.value = {
        ...currentJob.value,
        auditStatus: 1,
        reason: null
      }
    }
  } catch (error) {
    if (!isUserCancel(error)) {
      console.error('审核通过失败', error)
    }
  }
}

const openReject = (row) => {
  currentJob.value = row
  rejectReason.value = ''
  rejectVisible.value = true
}

const confirmReject = async () => {
  if (!currentJob.value) return
  if (!rejectReason.value.trim()) {
    ElMessage.warning('请填写驳回原因')
    return
  }
  try {
    await ElMessageBox.confirm('确认驳回该职位审核吗？', '确认驳回', {
      type: 'warning',
      confirmButtonText: '确认驳回',
      cancelButtonText: '取消'
    })
    await auditJob(currentJob.value.id, { status: 2, reason: rejectReason.value })
    rejectVisible.value = false
    ElMessage.success('已驳回职位')
    fetchJobs()
    fetchCounts()
    currentJob.value = {
      ...currentJob.value,
      auditStatus: 2,
      reason: rejectReason.value
    }
  } catch (error) {
    if (!isUserCancel(error)) {
      console.error('驳回职位失败', error)
    }
  }
}

const batchApprove = async () => {
  if (!selectedJobs.value.length) {
    ElMessage.warning('请先选择待审核职位')
    return
  }
  try {
    await ElMessageBox.confirm(`确认批量通过选中的 ${selectedJobs.value.length} 条职位吗？`, '确认批量通过', {
      type: 'success',
      confirmButtonText: '确认通过',
      cancelButtonText: '取消'
    })
    const ids = selectedJobs.value.map(item => item.id)
    await auditJobBatch({ ids, status: 1 })
    ElMessage.success('批量通过成功')
    fetchJobs()
    fetchCounts()
  } catch (error) {
    if (!isUserCancel(error)) {
      console.error('批量通过失败', error)
    }
  }
}

const openBatchReject = () => {
  if (!selectedJobs.value.length) {
    ElMessage.warning('请先选择待审核职位')
    return
  }
  batchRejectReason.value = ''
  batchRejectVisible.value = true
}

const confirmBatchReject = async () => {
  if (!selectedJobs.value.length) return
  if (!batchRejectReason.value.trim()) {
    ElMessage.warning('请填写驳回原因')
    return
  }
  try {
    await ElMessageBox.confirm(`确认批量驳回选中的 ${selectedJobs.value.length} 条职位吗？`, '确认批量驳回', {
      type: 'warning',
      confirmButtonText: '确认驳回',
      cancelButtonText: '取消'
    })
    const ids = selectedJobs.value.map(item => item.id)
    await auditJobBatch({ ids, status: 2, reason: batchRejectReason.value })
    batchRejectVisible.value = false
    ElMessage.success('批量驳回成功')
    fetchJobs()
    fetchCounts()
  } catch (error) {
    if (!isUserCancel(error)) {
      console.error('批量驳回失败', error)
    }
  }
}

const handleSortChange = ({ prop, order }) => {
  sortState.value = { prop, order }
  if (prop === 'createdAt') {
    timeOrder.value = order === 'ascending' ? 'asc' : 'desc'
  }
  pagination.value.current = 1
  fetchJobs()
}

const openRevoke = (row) => {
  currentJob.value = row
  revokeReason.value = ''
  revokeVisible.value = true
}

const confirmRevoke = async () => {
  if (!currentJob.value) return
  if (!revokeReason.value.trim()) {
    ElMessage.warning('请填写撤回原因')
    return
  }
  try {
    await revokeJobAudit(currentJob.value.id, { reason: revokeReason.value })
    revokeVisible.value = false
    ElMessage.success('已撤回审核')
    fetchJobs()
    fetchCounts()
    currentJob.value = {
      ...currentJob.value,
      auditStatus: 0,
      reason: null
    }
  } catch (error) {
    if (!isUserCancel(error)) {
      console.error('撤回审核失败', error)
    }
  }
}

const fetchAuditLogs = async (jobId) => {
  logsLoading.value = true
  try {
    const res = await getJobAuditLogs(jobId)
    auditLogs.value = Array.isArray(res.data) ? res.data : (res.data?.records || [])
  } catch (error) {
    console.error('获取操作记录失败', error)
    auditLogs.value = []
  } finally {
    logsLoading.value = false
  }
}

const formatLogAction = (item) => {
  const actionMap = {
    CREATE: '新建职位',
    UPDATE: '编辑职位',
    AUDIT: '审核处理',
    RESUBMIT: '提交复审',
    STATUS: '状态更新',
    REVOKE: '撤回审核',
    HANDLE: '处理举报'
  }
  const action = actionMap[item?.action] || item?.action || '操作'
  return `${action}`
}

const setRowClass = ({ row }) => {
  const status = Number(row?.auditStatus)
  if (Number.isNaN(status)) return ''
  if (status === 0) return 'row-pending'
  if (status === 1) return 'row-approved'
  if (status === 2) return 'row-rejected'
  return ''
}

onMounted(() => {
  fetchJobs()
  fetchCounts()
})

watch(activeTab, (val) => {
  pagination.value.current = 1
  sortState.value = { ...defaultSort }
  timeOrder.value = defaultSort.order === 'ascending' ? 'asc' : 'desc'
  if (val === 'pending') {
    query.value.status = 0
  } else if (val === 'approved') {
    query.value.status = 1
  } else if (val === 'rejected') {
    query.value.status = 2
  } else {
    query.value.status = null
  }
  fetchJobs()
})

const handlePageChange = (page) => {
  pagination.value.current = page
  fetchJobs()
}

const handleSizeChange = (size) => {
  pagination.value.size = size
  pagination.value.current = 1
  fetchJobs()
}

const parseTimeValue = (value) => {
  if (!value) return 0
  const text = String(value).replace('T', ' ').replace(/-/g, '/')
  const date = new Date(text)
  const time = date.getTime()
  return Number.isNaN(time) ? 0 : time
}

const applyLocalSort = () => {
  const { prop, order } = sortState.value || {}
  if (!prop || !order) return
  const direction = order === 'ascending' ? 1 : -1
  const timeDirection = timeOrder.value === 'asc' ? 1 : -1
  const statusPriority = (value) => {
    const status = Number(value)
    if (status === 1) return 1
    if (status === 0) return 2
    if (status === 2) return 3
    return 4
  }
  jobs.value = [...jobs.value].sort((a, b) => {
    let aVal = a?.[prop]
    let bVal = b?.[prop]
    if (prop === 'createdAt') {
      aVal = parseTimeValue(aVal)
      bVal = parseTimeValue(bVal)
    } else if (prop === 'auditStatus') {
      const aPriority = statusPriority(aVal)
      const bPriority = statusPriority(bVal)
      if (aPriority !== bPriority) {
        return aPriority > bPriority ? 1 : -1
      }
      const aTime = parseTimeValue(a?.createdAt)
      const bTime = parseTimeValue(b?.createdAt)
      if (aTime === bTime) return 0
      return aTime > bTime ? timeDirection : -timeDirection
    }
    if (aVal === bVal) return 0
    return aVal > bVal ? direction : -direction
  })
}
</script>

<style scoped>
.admin-page {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.filter-card {
  border-radius: 12px;
  margin-bottom: 0;
}

.filter-card :deep(.el-card__body) {
  padding: 14px 18px;
}

.filter-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.header-title {
  font-size: 16px;
  font-weight: 600;
  color: #111827;
}

.header-hint {
  font-size: 12px;
  color: #94a3b8;
}
.filter-row {
  gap: 12px;
}

.batch-row {
  gap: 12px;
  margin-top: 10px;
}

.batch-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.batch-hint {
  font-size: 12px;
  color: #94a3b8;
}

.admin-page :deep(.el-drawer__body) {
  padding: 16px 20px;
}

.status-tabs {
  margin-top: 8px;
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

.tab-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 22px;
  height: 20px;
  padding: 0 6px;
  margin-left: 6px;
  border-radius: 999px;
  background: #eef2f7;
  color: #475569;
  font-size: 12px;
}

.status-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-bar {
  width: 6px;
  height: 22px;
  border-radius: 6px;
  background: #cbd5f5;
}

.status-pending {
  background: #f59e0b;
}

.status-approved {
  background: #10b981;
}

.status-rejected {
  background: #ef4444;
}

.status-unknown {
  background: #94a3b8;
}

.audit-log-section {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px dashed #e2e8f0;
}

.section-title {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
  margin-bottom: 8px;
}

.log-title {
  font-size: 13px;
  font-weight: 600;
  color: #1f2937;
}

.log-detail {
  font-size: 12px;
  color: #64748b;
  margin-top: 4px;
}

.log-meta {
  font-size: 12px;
  color: #94a3b8;
  margin-top: 4px;
}

.admin-page :deep(.el-table__body tr.row-pending > td) {
  background-color: #fff7ed !important;
}

.admin-page :deep(.el-table__body tr.row-approved > td) {
  background-color: #ecfdf3 !important;
}

.admin-page :deep(.el-table__body tr.row-rejected > td) {
  background-color: #fef2f2 !important;
}

.detail-block {
  display: flex;
  flex-direction: column;
  gap: 10px;
  font-size: 14px;
}

.detail-item span {
  color: #64748b;
}

.job-title-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.job-title-text {
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
}

.job-sub-text {
  font-size: 12px;
  color: #94a3b8;
}

.drawer-body {
  min-height: 100%;
  display: flex;
  flex-direction: column;
}

.drawer-actions {
  margin-top: auto;
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  padding: 12px 0;
  border-top: 1px solid #eef2f7;
  background: #fff;
  position: sticky;
  bottom: 0;
}

.pagination-row {
  padding-top: 12px;
}

@media (max-width: 720px) {
  .drawer-actions {
    flex-wrap: wrap;
    justify-content: flex-start;
  }

  .drawer-actions :deep(.el-button) {
    flex: 1 1 120px;
  }
}
</style>
