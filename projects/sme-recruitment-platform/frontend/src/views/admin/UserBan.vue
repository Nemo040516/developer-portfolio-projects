<!--
文件速览：
1. 文件职责：管理员账号风控页，负责查询账号、限制/封禁/解除以及密码重置。
2. 页面入口：管理员路由中的账号管理页面。
3. 关键结构：query、pagination、banForm、resetForm、fetchUsers、action-row。
4. 阅读建议：先看表格操作区，再看两个弹窗，最后看底部方法与按钮排版样式。
-->
<template>
  <div class="admin-page">
    <el-card class="filter-card">
        <div class="filter-header">
          <div class="header-left">
            <span class="header-title">账号封禁与黑名单</span>
            <span class="header-hint">查询账号 → 限制/封禁/重置密码 → 支持解除</span>
          </div>
        </div>
      <div class="filter-row adaptive-filter-row">
        <el-input v-model="query.keyword" placeholder="账号/昵称/手机号/邮箱" clearable class="adaptive-filter-control adaptive-filter-control--lg" />
        <el-select v-model="query.role" placeholder="角色" clearable class="adaptive-filter-control adaptive-filter-control--sm">
          <el-option label="管理员" value="ADMIN" />
          <el-option label="商家" value="MERCHANT" />
          <el-option label="求职者" value="APPLICANT" />
        </el-select>
        <el-select v-model="query.status" placeholder="账号状态" clearable class="adaptive-filter-control adaptive-filter-control--sm">
          <el-option label="正常" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
        <el-select v-model="query.banStatus" placeholder="封禁状态" clearable class="adaptive-filter-control adaptive-filter-control--sm">
          <el-option label="正常" :value="0" />
          <el-option label="限制" :value="1" />
          <el-option label="封禁/拉黑" :value="2" />
        </el-select>
        <div class="adaptive-filter-actions">
          <el-button type="primary" @click="applyFilter">筛选</el-button>
          <el-button @click="resetFilter">重置</el-button>
        </div>
      </div>
    </el-card>

    <el-card>
        <el-table :data="users" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="账号信息" min-width="220">
          <template #default="{ row }">
            <div class="user-title-cell">
              <div class="user-title-text">{{ row.username }}</div>
              <div class="user-sub-text">{{ row.nickname || '—' }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="角色" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="roleTag(row.role)">{{ roleLabel(row.role) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="账号状态" width="120">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="封禁状态" width="160">
          <template #default="{ row }">
            <el-tag size="small" :type="banTag(row.banStatus)">{{ banLabel(row.banStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="封禁原因" min-width="180">
          <template #default="{ row }">
            {{ row.banReason || '—' }}
          </template>
        </el-table-column>
        <el-table-column label="封禁截止" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.banUntil) || '—' }}
          </template>
        </el-table-column>
        <el-table-column label="联系方式" min-width="200">
          <template #default="{ row }">
            <div class="user-sub-text">{{ row.phone || '—' }}</div>
            <div class="user-sub-text">{{ row.email || '—' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="最近密码重置" min-width="240">
          <template #default="{ row }">
            <div v-if="row.latestPasswordResetTime" class="reset-log-cell">
              <div class="user-title-text">{{ formatDateTime(row.latestPasswordResetTime) }}</div>
              <div class="user-sub-text">
                操作人：{{ row.latestPasswordResetOperatorName || (row.latestPasswordResetOperatorId ? `管理员#${row.latestPasswordResetOperatorId}` : '—') }}
              </div>
              <div class="user-sub-text reset-detail">{{ row.latestPasswordResetDetail || '—' }}</div>
            </div>
            <div v-else class="user-sub-text">未发生重置</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <div class="action-row">
              <el-button size="small" type="warning" plain @click="openBanDialog(row, 'limit')">限制</el-button>
              <el-button size="small" type="danger" plain @click="openBanDialog(row, 'ban')">封禁</el-button>
              <el-button size="small" type="primary" plain @click="openResetDialog(row)">重置密码</el-button>
              <el-button size="small" type="success" plain :disabled="!row.banStatus" @click="clearBan(row)">解除</el-button>
            </div>
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

    <el-dialog v-model="banVisible" :title="banDialogTitle" width="460px">
      <div class="ban-form">
        <div class="ban-row">
          <span class="ban-label">账号</span>
          <span class="ban-value">{{ currentUser?.username || '-' }}</span>
        </div>
        <div class="ban-row">
          <span class="ban-label">角色</span>
          <el-tag size="small" :type="roleTag(currentUser?.role)">{{ roleLabel(currentUser?.role) }}</el-tag>
        </div>
        <el-form :model="banForm" label-width="90px">
          <el-form-item label="封禁原因">
            <el-input v-model="banForm.banReason" type="textarea" rows="3" placeholder="请输入封禁原因" />
          </el-form-item>
          <el-form-item v-if="banForm.banStatus === 1" label="截止时间">
            <el-date-picker
              v-model="banForm.banUntil"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              placeholder="选择限制截止时间"
              :editable="false"
              :clearable="true"
              style="width: 100%"
            />
          </el-form-item>
        </el-form>
        <div class="ban-tip" v-if="banForm.banStatus === 1">
          限制状态建议设置截止时间，到期后可自动解除限制。
        </div>
      </div>
      <template #footer>
        <el-button @click="banVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmBan">确认</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resetVisible" title="管理员重置密码" width="460px" @closed="resetPasswordFormState">
      <div class="ban-form">
        <div class="ban-row">
          <span class="ban-label">账号</span>
          <span class="ban-value">{{ currentUser?.username || '-' }}</span>
        </div>
        <div class="ban-row">
          <span class="ban-label">角色</span>
          <el-tag size="small" :type="roleTag(currentUser?.role)">{{ roleLabel(currentUser?.role) }}</el-tag>
        </div>
        <el-form :model="resetForm" label-width="90px">
          <el-form-item label="临时密码">
            <el-input v-model="resetForm.newPassword" type="password" show-password placeholder="请输入6位及以上临时密码" />
          </el-form-item>
          <el-form-item label="确认密码">
            <el-input v-model="resetForm.confirmPassword" type="password" show-password placeholder="请再次输入临时密码" />
          </el-form-item>
          <el-form-item label="重置原因">
            <el-input v-model="resetForm.reason" type="textarea" rows="3" placeholder="例如：用户忘记密码，已线下核验身份" />
          </el-form-item>
        </el-form>
        <div class="ban-tip">
          重置后请通过线下安全渠道告知用户临时密码，并提醒其首次登录后立即修改。
        </div>
      </div>
      <template #footer>
        <el-button @click="resetVisible = false">取消</el-button>
        <el-button type="primary" :loading="resetSubmitting" @click="confirmResetPassword">确认重置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/*
文件速览：
1. 文件职责：管理账号查询、封禁操作和管理员重置密码交互。
2. 对外入口：页面加载时 fetchUsers；操作入口为 openBanDialog、openResetDialog。
3. 关键结构：banForm 负责封禁，resetForm 负责密码重置，pagination 负责分页。
4. 阅读建议：先看 fetchUsers，再看两个确认函数，最后看辅助格式化方法。
*/
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAdminUsers, resetUserPassword, updateUserBan } from '@/api/admin'

const query = ref({
  keyword: '',
  role: '',
  status: undefined,
  banStatus: undefined
})

const users = ref([])
const loading = ref(false)
const pagination = ref({
  current: 1,
  size: 20,
  total: 0
})

const banVisible = ref(false)
const banMode = ref('limit')
const currentUser = ref(null)
const banForm = ref({
  banStatus: 1,
  banReason: '',
  banUntil: ''
})
const resetVisible = ref(false)
const resetSubmitting = ref(false)
const resetForm = ref({
  newPassword: '',
  confirmPassword: '',
  reason: ''
})

const banDialogTitle = computed(() => (banMode.value === 'ban' ? '账号封禁/拉黑' : '账号限制'))

const roleLabel = (role) => {
  if (role === 'ADMIN') return '管理员'
  if (role === 'MERCHANT') return '商家'
  if (role === 'APPLICANT') return '求职者'
  return role || '—'
}

const roleTag = (role) => {
  if (role === 'ADMIN') return 'danger'
  if (role === 'MERCHANT') return 'warning'
  if (role === 'APPLICANT') return 'success'
  return 'info'
}

const statusLabel = (status) => (Number(status) === 0 ? '禁用' : '正常')
const statusTag = (status) => (Number(status) === 0 ? 'danger' : 'success')

const banLabel = (status) => {
  if (Number(status) === 1) return '限制'
  if (Number(status) === 2) return '封禁/拉黑'
  return '正常'
}
const banTag = (status) => {
  if (Number(status) === 1) return 'warning'
  if (Number(status) === 2) return 'danger'
  return 'success'
}

const formatDateTime = (value) => {
  if (!value) return ''
  const text = String(value).replace('T', ' ')
  return text.length > 19 ? text.slice(0, 19) : text
}

const normalizeList = (data) => {
  if (Array.isArray(data)) return data
  return data?.records || []
}

const fetchUsers = async () => {
  loading.value = true
  try {
    const res = await getAdminUsers({
      keyword: query.value.keyword?.trim(),
      role: query.value.role || undefined,
      status: query.value.status,
      banStatus: query.value.banStatus,
      current: pagination.value.current,
      size: pagination.value.size
    })
    if (res.code === 200) {
      users.value = normalizeList(res.data)
      pagination.value.total = res.data?.total || 0
    } else {
      users.value = []
      pagination.value.total = 0
    }
  } catch (error) {
    console.error('获取账号列表失败', error)
    users.value = []
    pagination.value.total = 0
  } finally {
    loading.value = false
  }
}

const applyFilter = () => {
  pagination.value.current = 1
  fetchUsers()
}

const resetFilter = () => {
  query.value = {
    keyword: '',
    role: '',
    status: undefined,
    banStatus: undefined
  }
  pagination.value.current = 1
  fetchUsers()
}

const openBanDialog = (row, mode) => {
  currentUser.value = row
  banMode.value = mode
  banForm.value = {
    banStatus: mode === 'ban' ? 2 : 1,
    banReason: '',
    banUntil: ''
  }
  banVisible.value = true
}

const resetPasswordFormState = () => {
  resetForm.value = {
    newPassword: '',
    confirmPassword: '',
    reason: ''
  }
}

const openResetDialog = (row) => {
  currentUser.value = row
  resetPasswordFormState()
  resetVisible.value = true
}

const confirmBan = async () => {
  if (!currentUser.value) return
  if (banForm.value.banStatus === 1 && !banForm.value.banUntil) {
    ElMessage.warning('限制状态建议填写截止时间')
    return
  }
  try {
    await updateUserBan(currentUser.value.id, {
      banStatus: banForm.value.banStatus,
      banReason: banForm.value.banReason,
      banUntil: banForm.value.banUntil || null
    })
    ElMessage.success('账号状态已更新')
    banVisible.value = false
    fetchUsers()
  } catch (error) {
    console.error('更新账号状态失败', error)
  }
}

const clearBan = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确认解除账号 ${row.username} 的限制/封禁吗？`,
      '解除封禁',
      {
        confirmButtonText: '确认解除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    await updateUserBan(row.id, { banStatus: 0 })
    ElMessage.success('已解除限制')
    fetchUsers()
  } catch (error) {
    // 用户取消无需处理
  }
}

const confirmResetPassword = async () => {
  if (!currentUser.value) return
  if (!resetForm.value.newPassword) {
    ElMessage.warning('请输入临时密码')
    return
  }
  if (resetForm.value.newPassword.length < 6) {
    ElMessage.warning('临时密码长度不能少于6位')
    return
  }
  if (resetForm.value.newPassword !== resetForm.value.confirmPassword) {
    ElMessage.warning('两次输入的临时密码不一致')
    return
  }
  if (!resetForm.value.reason?.trim()) {
    ElMessage.warning('请填写重置原因')
    return
  }
  resetSubmitting.value = true
  try {
    await resetUserPassword(currentUser.value.id, {
      newPassword: resetForm.value.newPassword,
      confirmPassword: resetForm.value.confirmPassword,
      reason: resetForm.value.reason.trim()
    })
    ElMessage.success(`已为账号 ${currentUser.value.username} 重置临时密码`)
    resetVisible.value = false
    resetPasswordFormState()
  } catch (error) {
    console.error('重置密码失败', error)
  } finally {
    resetSubmitting.value = false
  }
}

const handlePageChange = (page) => {
  pagination.value.current = page
  fetchUsers()
}

const handleSizeChange = (size) => {
  pagination.value.size = size
  pagination.value.current = 1
  fetchUsers()
}

onMounted(() => {
  fetchUsers()
})
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

.pagination-row {
  padding-top: 12px;
}

.user-title-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.user-title-text {
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
}

.user-sub-text {
  font-size: 12px;
  color: #94a3b8;
}

.reset-log-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.reset-detail {
  line-height: 1.6;
  color: #64748b;
}

.action-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

/* 操作区固定为两列，避免按钮换行后出现“上三下一”或右侧留白过大的情况。 */
.action-row :deep(.el-button) {
  width: 100%;
  margin-left: 0;
  justify-content: center;
}

.ban-form {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ban-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.ban-label {
  color: #64748b;
  min-width: 64px;
}

.ban-value {
  color: #1f2937;
  font-weight: 600;
}

.ban-tip {
  font-size: 12px;
  color: #94a3b8;
}

@media (max-width: 720px) {
  .action-row :deep(.el-button) {
    min-width: 0;
  }
}
</style>
