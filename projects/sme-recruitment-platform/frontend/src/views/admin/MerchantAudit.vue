<!--
文件速览：
1. 文件职责：管理员商家审核页，负责筛选商家、批量审核、查看详情与运营状态调整。
2. 页面入口：管理员路由 `/admin/merchants`。
3. 关键结构：query、merchants、selectedMerchants、detailVisible、auditLogs。
4. 阅读建议：先看筛选区和批量区，再看表格，最后看详情抽屉与操作区。
-->
<template>
  <div class="admin-page">
    <div class="page-header">
      <div>
        <h2 class="page-title">商家审核</h2>
        <p class="page-desc">新商家需审核通过后才能发布职位</p>
      </div>
    </div>

    <el-card class="filter-card">
      <div class="filter-row adaptive-filter-row">
        <el-input v-model="query.keyword" placeholder="企业/联系人关键词" clearable class="adaptive-filter-control adaptive-filter-control--lg" />
        <el-select v-model="query.status" placeholder="审核状态" clearable class="adaptive-filter-control adaptive-filter-control--sm">
          <el-option label="待审核" :value="0" />
          <el-option label="已通过" :value="1" />
          <el-option label="已驳回" :value="2" />
        </el-select>
        <div class="adaptive-filter-actions">
          <el-button type="primary" @click="applyFilter">筛选</el-button>
          <el-button @click="resetFilter">重置</el-button>
        </div>
      </div>
      <div class="batch-row adaptive-meta-row">
        <div class="batch-actions">
          <el-button type="success" :disabled="!selectedMerchants.length" @click="batchApprove">批量通过</el-button>
          <el-button type="danger" :disabled="!selectedMerchants.length" @click="openBatchReject">批量驳回</el-button>
        </div>
        <div class="batch-hint">已选 {{ selectedMerchants.length }} 条待审核记录</div>
      </div>
    </el-card>

    <el-card>
      <el-table
        ref="merchantTableRef"
        :data="merchants"
        stripe
        v-loading="loading"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="52" :selectable="rowSelectable" />
        <el-table-column prop="companyName" label="企业名称" min-width="200" />
        <el-table-column prop="contact" label="联系人" width="120" />
        <el-table-column label="被举报" width="100">
          <template #default="{ row }">
            {{ row.reportCount ?? 0 }}
          </template>
        </el-table-column>
        <el-table-column label="运营状态" width="120">
          <template #default="{ row }">
            <el-tag :type="publishTag(row.publishStatus)">{{ publishText(row.publishStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="submittedAt" label="提交时间" width="120" />
        <el-table-column label="审核状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.auditStatus)">{{ statusText(row.auditStatus) }}</el-tag>
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

    <el-drawer v-model="detailVisible" size="420px" title="商家详情">
      <div v-if="currentMerchant" class="detail-block">
        <div class="detail-item"><span>企业名称：</span>{{ currentMerchant.companyName }}</div>
        <div class="detail-item"><span>联系人：</span>{{ currentMerchant.contact }}</div>
        <div class="detail-item"><span>联系方式：</span>{{ currentMerchant.phone }}</div>
        <div class="detail-item"><span>企业地址：</span>{{ currentMerchant.address }}</div>
        <div class="detail-item"><span>统一信用代码：</span>{{ currentMerchant.creditCode || '未提交' }}</div>
        <div class="detail-item"><span>法人姓名：</span>{{ currentMerchant.legalPerson || '未提交' }}</div>
        <div class="detail-item">
          <span>营业执照：</span>
          <el-link
            v-if="currentMerchant.licenseUrl"
            type="primary"
            @click.prevent="openFile(currentMerchant.licenseUrl)"
          >
            查看
          </el-link>
          <span v-else>未提交</span>
        </div>
        <div class="detail-item">
          <span>资质材料：</span>
          <div class="detail-files">
            <template v-if="currentMerchant.qualificationList && currentMerchant.qualificationList.length">
              <el-tag
                v-for="(item, index) in currentMerchant.qualificationList"
                :key="item + index"
                type="info"
              >
                材料{{ index + 1 }}
                <el-link class="ml-2" type="primary" @click.prevent="openFile(item)">查看</el-link>
              </el-tag>
            </template>
            <span v-else>未提交</span>
          </div>
        </div>
        <div class="detail-item"><span>提交时间：</span>{{ currentMerchant.submittedAt }}</div>
        <div class="detail-item"><span>审核状态：</span>{{ statusText(currentMerchant.auditStatus) }}</div>
        <div class="detail-item"><span>运营状态：</span>{{ publishText(currentMerchant.publishStatus) }}</div>
        <div class="detail-item"><span>被举报次数：</span>{{ currentMerchant.reportCount ?? 0 }}</div>
        <div v-if="currentMerchant.reason" class="detail-item"><span>驳回原因：</span>{{ currentMerchant.reason }}</div>
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
      <div class="drawer-actions" v-if="currentMerchant">
        <el-button
          type="success"
          :disabled="currentMerchant.auditStatus !== 0"
          @click="approve(currentMerchant)"
        >
          通过
        </el-button>
        <el-button
          type="danger"
          :disabled="currentMerchant.auditStatus !== 0"
          @click="openReject(currentMerchant)"
        >
          驳回
        </el-button>
        <el-button
          v-if="currentMerchant.auditStatus === 1 && currentMerchant.publishStatus !== 0"
          type="warning"
          @click="updatePublishStatus(currentMerchant, 0, '限制发布')"
        >
          限制发布
        </el-button>
        <el-button
          v-if="currentMerchant.publishStatus === 0"
          type="primary"
          plain
          @click="updatePublishStatus(currentMerchant, 1, '解除限制')"
        >
          解除限制
        </el-button>
        <el-button
          v-if="currentMerchant.auditStatus === 1 && currentMerchant.publishStatus !== 2"
          type="danger"
          plain
          @click="updatePublishStatus(currentMerchant, 2, '封禁企业')"
        >
          封禁企业
        </el-button>
      </div>
    </el-drawer>

    <el-dialog v-model="rejectVisible" title="驳回原因" width="420px">
      <el-input v-model="rejectReason" type="textarea" rows="4" placeholder="请输入驳回原因" />
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" @click="confirmReject">确认驳回</el-button>
      </template>
    </el-dialog>

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
import { ref, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { auditMerchant, auditMerchantBatch, getAdminMerchants, getMerchantAuditLogs, updateMerchantStatus } from '@/api/admin'
import { formatFileUrl } from '@/utils/file'

const query = ref({
  keyword: '',
  status: null
})

const merchants = ref([])
const loading = ref(false)
const merchantTableRef = ref(null)
const selectedMerchants = ref([])
const pagination = ref({
  current: 1,
  size: 20,
  total: 0
})

const currentMerchant = ref(null)
const detailVisible = ref(false)
const rejectVisible = ref(false)
const rejectReason = ref('')
const batchRejectVisible = ref(false)
const batchRejectReason = ref('')
const auditLogs = ref([])
const logsLoading = ref(false)

const statusText = (status) => {
  const map = { 0: '待审核', 1: '已通过', 2: '已驳回' }
  return map[status] || '未知'
}

const statusTag = (status) => {
  const map = { 0: 'info', 1: 'success', 2: 'danger' }
  return map[status] || 'info'
}

const publishText = (status) => {
  const map = { 0: '限制发布', 1: '正常', 2: '封禁' }
  return map[status] || '未知'
}

const publishTag = (status) => {
  const map = { 0: 'warning', 1: 'success', 2: 'danger' }
  return map[status] || 'info'
}

const normalizeList = (data) => {
  if (Array.isArray(data)) return data
  return data?.records || []
}

const rowSelectable = (row) => {
  return Number(row?.auditStatus) === 0
}

const handleSelectionChange = (rows) => {
  selectedMerchants.value = rows || []
}

const normalizeQualificationUrls = (value) => {
  if (!value) return []
  if (Array.isArray(value)) return value
  if (typeof value === 'string') {
    try {
      return JSON.parse(value) || []
    } catch (e) {
      return []
    }
  }
  return []
}

const fetchMerchants = async () => {
  loading.value = true
  try {
    const res = await getAdminMerchants({
      keyword: query.value.keyword?.trim(),
      status: query.value.status,
      current: pagination.value.current,
      size: pagination.value.size
    })
    if (res.code === 200) {
      merchants.value = normalizeList(res.data)
      pagination.value.total = res.data?.total || 0
    } else {
      merchants.value = []
      pagination.value.total = 0
    }
  } catch (error) {
    console.error('获取商家审核列表失败', error)
    merchants.value = []
    pagination.value.total = 0
  } finally {
    loading.value = false
    await nextTick()
    if (merchantTableRef.value) {
      merchantTableRef.value.clearSelection()
    }
    selectedMerchants.value = []
  }
}

const applyFilter = () => {
  pagination.value.current = 1
  fetchMerchants()
}

const resetFilter = () => {
  query.value.keyword = ''
  query.value.status = null
  pagination.value.current = 1
  fetchMerchants()
}

const openDetail = (row) => {
  currentMerchant.value = {
    ...row,
    qualificationList: normalizeQualificationUrls(row.qualificationUrls)
  }
  detailVisible.value = true
  fetchAuditLogs(row.id)
}

const approve = async (row) => {
  try {
    await auditMerchant(row.id, { status: 1 })
    ElMessage.success('已通过审核')
    fetchMerchants()
    if (currentMerchant.value && currentMerchant.value.id === row.id) {
      currentMerchant.value = {
        ...currentMerchant.value,
        auditStatus: 1,
        reason: null
      }
    }
  } catch (error) {
    console.error('审核通过失败', error)
  }
}

const openReject = (row) => {
  currentMerchant.value = row
  rejectReason.value = ''
  rejectVisible.value = true
}

const confirmReject = async () => {
  if (!currentMerchant.value) return
  if (!rejectReason.value.trim()) {
    ElMessage.warning('请填写驳回原因')
    return
  }
  try {
    await auditMerchant(currentMerchant.value.id, { status: 2, reason: rejectReason.value })
    rejectVisible.value = false
    ElMessage.success('已驳回商家')
    fetchMerchants()
    currentMerchant.value = {
      ...currentMerchant.value,
      auditStatus: 2,
      reason: rejectReason.value
    }
  } catch (error) {
    console.error('驳回商家失败', error)
  }
}

const batchApprove = async () => {
  if (!selectedMerchants.value.length) {
    ElMessage.warning('请先选择待审核商家')
    return
  }
  try {
    await ElMessageBox.confirm(`确认批量通过选中的 ${selectedMerchants.value.length} 家商家吗？`, '确认批量通过', {
      type: 'success',
      confirmButtonText: '确认通过',
      cancelButtonText: '取消'
    })
    const ids = selectedMerchants.value.map(item => item.id)
    await auditMerchantBatch({ ids, status: 1 })
    ElMessage.success('批量通过成功')
    fetchMerchants()
  } catch (error) {
    // 用户取消不提示
  }
}

const openBatchReject = () => {
  if (!selectedMerchants.value.length) {
    ElMessage.warning('请先选择待审核商家')
    return
  }
  batchRejectReason.value = ''
  batchRejectVisible.value = true
}

const confirmBatchReject = async () => {
  if (!selectedMerchants.value.length) return
  if (!batchRejectReason.value.trim()) {
    ElMessage.warning('请填写驳回原因')
    return
  }
  try {
    await ElMessageBox.confirm(`确认批量驳回选中的 ${selectedMerchants.value.length} 家商家吗？`, '确认批量驳回', {
      type: 'warning',
      confirmButtonText: '确认驳回',
      cancelButtonText: '取消'
    })
    const ids = selectedMerchants.value.map(item => item.id)
    await auditMerchantBatch({ ids, status: 2, reason: batchRejectReason.value })
    batchRejectVisible.value = false
    ElMessage.success('批量驳回成功')
    fetchMerchants()
  } catch (error) {
    // 用户取消不提示
  }
}

const updatePublishStatus = async (row, status, actionText) => {
  try {
    await ElMessageBox.confirm(`确认${actionText}该商家吗？`, '操作确认', {
      type: 'warning',
      confirmButtonText: '确认',
      cancelButtonText: '取消'
    })
    await updateMerchantStatus(row.id, { status, reason: actionText })
    ElMessage.success('操作成功')
    fetchMerchants()
    if (currentMerchant.value && currentMerchant.value.id === row.id) {
      currentMerchant.value = {
        ...currentMerchant.value,
        publishStatus: status
      }
    }
  } catch (error) {
    // 用户取消不提示
  }
}

const openFile = (url) => {
  const target = formatFileUrl(url)
  if (!target) return
  window.open(target, '_blank')
}

const fetchAuditLogs = async (merchantId) => {
  logsLoading.value = true
  try {
    const res = await getMerchantAuditLogs(merchantId)
    auditLogs.value = Array.isArray(res.data) ? res.data : (res.data?.records || [])
  } catch (error) {
    auditLogs.value = []
  } finally {
    logsLoading.value = false
  }
}

const formatLogAction = (item) => {
  const actionMap = {
    AUDIT: '审核处理',
    STATUS: '运营状态调整',
    HANDLE: '处理举报'
  }
  const action = actionMap[item?.action] || item?.action || '操作'
  return `${action}`
}

onMounted(() => {
  fetchMerchants()
})

const handlePageChange = (page) => {
  pagination.value.current = page
  fetchMerchants()
}

const handleSizeChange = (size) => {
  pagination.value.size = size
  pagination.value.current = 1
  fetchMerchants()
}
</script>

<style scoped>
.admin-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.page-title {
  margin: 0;
  font-size: 20px;
}

.page-desc {
  margin: 6px 0 0;
  color: #6b7280;
  font-size: 13px;
}

.filter-card {
  border-radius: 12px;
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

.detail-block {
  display: flex;
  flex-direction: column;
  gap: 10px;
  font-size: 14px;
}

.detail-item span {
  color: #64748b;
}

.detail-files {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.ml-2 { margin-left: 0.5rem; }

.drawer-actions {
  margin-top: 16px;
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  padding-top: 12px;
  border-top: 1px dashed #e2e8f0;
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
