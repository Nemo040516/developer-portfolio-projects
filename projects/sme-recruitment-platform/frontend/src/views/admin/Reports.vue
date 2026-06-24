<!--
文件速览：
1. 文件职责：管理员举报处理页，负责筛选举报、批量处理、查看对象快照与执行处置动作。
2. 页面入口：管理员路由 `/admin/reports`。
3. 关键结构：query、reports、selectedReports、detailVisible、workspace-grid。
4. 阅读建议：先看顶部筛选区，再看表格，最后看举报处理工作台弹窗。
-->
<template>
  <div class="admin-page">
    <div class="page-header">
      <div>
        <h2 class="page-title">举报处理</h2>
        <p class="page-desc">先核对举报对象详情，再处理举报，降低误判风险</p>
      </div>
    </div>

    <el-card class="filter-card">
      <div class="filter-row adaptive-filter-row">
        <el-select v-model="query.type" placeholder="举报类型" clearable class="adaptive-filter-control adaptive-filter-control--sm">
          <el-option label="职位" value="JOB" />
          <el-option label="企业" value="MERCHANT" />
          <el-option label="个人账号" value="USER" />
        </el-select>
        <el-select v-model="query.status" placeholder="处理状态" clearable class="adaptive-filter-control adaptive-filter-control--sm">
          <el-option label="待处理" :value="0" />
          <el-option label="已处理" :value="1" />
          <el-option label="已驳回" :value="2" />
        </el-select>
        <div class="adaptive-filter-actions">
          <el-button type="primary" @click="applyFilter">筛选</el-button>
          <el-button @click="resetFilter">重置</el-button>
        </div>
      </div>
      <div class="batch-row adaptive-meta-row">
        <div class="batch-actions">
          <el-button type="primary" :disabled="!selectedReports.length" @click="openBatchHandle">批量处理</el-button>
          <el-button type="danger" :disabled="!selectedReports.length" @click="batchReject">批量驳回</el-button>
        </div>
        <div class="batch-hint">已选 {{ selectedReports.length }} 条待处理举报</div>
      </div>
    </el-card>

    <el-card>
      <el-table
        ref="reportTableRef"
        :data="reports"
        stripe
        v-loading="loading"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="52" :selectable="rowSelectable" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            {{ typeText(row.type) }}
          </template>
        </el-table-column>
        <el-table-column label="举报对象" min-width="220">
          <template #default="{ row }">
            <div class="target-cell">
              <div class="target-title">{{ row.targetName || `对象#${row.targetId}` }}</div>
              <div class="target-sub">ID: {{ row.targetId }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="对象快照" min-width="220">
          <template #default="{ row }">
            {{ objectSummary(row) }}
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="举报原因" min-width="220" show-overflow-tooltip />
        <el-table-column label="被举报次数" width="120">
          <template #default="{ row }">
            {{ row.reportCount ?? 0 }}
          </template>
        </el-table-column>
        <el-table-column prop="reporter" label="举报人" width="150" />
        <el-table-column prop="createdAt" label="时间" width="170" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openDetail(row)">查看</el-button>
            <el-button size="small" type="success" :disabled="row.status !== 0" @click="openHandle(row)">处理</el-button>
            <el-button size="small" type="danger" :disabled="row.status !== 0" @click="reject(row)">驳回</el-button>
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

    <el-dialog
      v-model="detailVisible"
      title="举报处理工作台"
      width="88%"
      top="4vh"
      destroy-on-close
      class="report-detail-dialog"
    >
      <div v-if="currentReport" class="detail-dialog-body">
        <div class="workspace-grid">
          <div class="workspace-main">
            <div class="drawer-content">
              <div class="detail-summary">
                <div class="summary-title">{{ currentReport.targetName || `对象#${currentReport.targetId}` }}</div>
                <div class="summary-tags">
                  <el-tag type="warning">{{ typeText(currentReport.type) }}</el-tag>
                  <el-tag :type="statusTag(currentReport.status)">{{ statusText(currentReport.status) }}</el-tag>
                  <el-tag type="info">被举报 {{ currentReport.reportCount ?? 0 }} 次</el-tag>
                </div>
              </div>

              <el-collapse v-model="activeDetailPanels" class="detail-collapse">
                <el-collapse-item name="target" title="被举报对象详情">
                  <div v-if="currentTargetSnapshotCards.length" class="snapshot-card">
                    <div class="snapshot-head">
                      <div class="snapshot-title">举报快照（提交当时）</div>
                      <div class="snapshot-time">
                        抓取时间：{{ formatEmpty(currentTargetSnapshot?.capturedAt) }}
                      </div>
                    </div>
                    <div class="snapshot-grid">
                      <div
                        v-for="item in currentTargetSnapshotCards"
                        :key="`${item.label}-${item.value}`"
                        class="snapshot-item"
                      >
                        <div class="snapshot-label">{{ item.label }}</div>
                        <div class="snapshot-value">{{ formatEmpty(item.value) }}</div>
                      </div>
                    </div>
                  </div>

                  <template v-if="currentReport.type === 'JOB'">
                    <div class="job-detail-panel">
                      <div class="job-detail-hero">
                        <div class="job-title-row">
                          <div class="job-title">{{ formatEmpty(currentReport.jobTitle) }}</div>
                          <div class="job-salary">{{ formatEmpty(currentReport.jobSalary) }}</div>
                        </div>

                        <div class="job-meta-row">
                          <div class="job-meta-chip">地点：{{ formatEmpty(currentReport.jobLocation) }}</div>
                          <div class="job-meta-chip">经验：{{ formatEmpty(currentReport.jobExperience) }}</div>
                          <div class="job-meta-chip">学历：{{ formatEmpty(currentReport.jobDegree) }}</div>
                          <div class="job-meta-chip">招聘人数：{{ currentReport.jobHeadcount || 1 }} 人</div>
                          <div class="job-meta-chip">岗位类别：{{ formatEmpty(currentReport.jobCategoryName) }}</div>
                        </div>

                        <div v-if="parseTagList(currentReport.jobTags).length" class="job-tag-row">
                          <el-tag
                            v-for="tag in parseTagList(currentReport.jobTags)"
                            :key="tag"
                            size="small"
                            effect="plain"
                          >
                            {{ tag }}
                          </el-tag>
                        </div>

                        <div class="job-status-row">
                          <el-tag type="info">对象ID: {{ currentReport.targetId ?? '—' }}</el-tag>
                          <el-tag :type="currentReport.jobStatus === 1 ? 'success' : 'danger'">
                            发布状态：{{ jobStatusText(currentReport.jobStatus) }}
                          </el-tag>
                          <el-tag :type="currentReport.jobAuditStatus === 1 ? 'success' : (currentReport.jobAuditStatus === 2 ? 'danger' : 'warning')">
                            审核状态：{{ auditStatusText(currentReport.jobAuditStatus) }}
                          </el-tag>
                        </div>
                      </div>

                      <div class="job-company-card">
                        <el-avatar
                          :size="48"
                          :src="formatFileUrl(currentReport.jobCompanyLogo)"
                        />
                        <div class="job-company-main">
                          <div class="job-company-name">{{ formatEmpty(currentReport.jobCompanyName) }}</div>
                          <div class="job-company-sub">
                            {{ formatEmpty(currentReport.jobCompanyIndustry) }} · {{ formatEmpty(currentReport.jobCompanyScale) }}
                          </div>
                          <div class="job-company-sub">{{ formatEmpty(currentReport.jobCompanyAddress) }}</div>
                          <div class="job-company-sub">
                            招聘负责人：{{ formatEmpty(currentReport.jobPublisherName) }}
                          </div>
                        </div>
                      </div>

                      <el-descriptions :column="1" border size="small">
                        <el-descriptions-item label="职位描述">
                          <div class="long-text">{{ formatEmpty(currentReport.jobDescription) }}</div>
                        </el-descriptions-item>
                        <el-descriptions-item label="任职要求">
                          <div class="long-text">{{ formatEmpty(currentReport.jobRequirement) }}</div>
                        </el-descriptions-item>
                      </el-descriptions>
                    </div>
                  </template>

                  <template v-else>
                    <el-descriptions :column="1" border size="small">
                      <el-descriptions-item label="对象ID">
                        {{ currentReport.targetId ?? '—' }}
                      </el-descriptions-item>

                      <template v-if="currentReport.type === 'MERCHANT'">
                        <el-descriptions-item label="企业名称">
                          {{ formatEmpty(currentReport.merchantCompanyName) }}
                        </el-descriptions-item>
                        <el-descriptions-item label="行业">
                          {{ formatEmpty(currentReport.merchantIndustry) }}
                        </el-descriptions-item>
                        <el-descriptions-item label="规模">
                          {{ formatEmpty(currentReport.merchantScale) }}
                        </el-descriptions-item>
                        <el-descriptions-item label="融资阶段">
                          {{ formatEmpty(currentReport.merchantFinancing) }}
                        </el-descriptions-item>
                        <el-descriptions-item label="联系人">
                          {{ formatEmpty(currentReport.merchantContactName) }}
                        </el-descriptions-item>
                        <el-descriptions-item label="联系电话">
                          {{ formatEmpty(currentReport.merchantContactPhone) }}
                        </el-descriptions-item>
                        <el-descriptions-item label="企业地址">
                          <div class="long-text">{{ formatEmpty(currentReport.merchantAddress) }}</div>
                        </el-descriptions-item>
                        <el-descriptions-item label="发布状态">
                          {{ merchantPublishText(currentReport.merchantPublishStatus) }}
                        </el-descriptions-item>
                        <el-descriptions-item label="审核状态">
                          {{ auditStatusText(currentReport.merchantAuditStatus) }}
                        </el-descriptions-item>
                        <el-descriptions-item label="企业简介">
                          <div class="long-text">{{ formatEmpty(currentReport.merchantDescription) }}</div>
                        </el-descriptions-item>
                      </template>

                      <template v-else-if="currentReport.type === 'USER'">
                        <el-descriptions-item label="账号昵称">
                          {{ formatEmpty(currentReport.userNickname) }}
                        </el-descriptions-item>
                        <el-descriptions-item label="账号名">
                          {{ formatEmpty(currentReport.userUsername) }}
                        </el-descriptions-item>
                        <el-descriptions-item label="账号角色">
                          {{ roleText(currentReport.userRole) }}
                        </el-descriptions-item>
                        <el-descriptions-item label="手机号">
                          {{ formatEmpty(currentReport.userPhone) }}
                        </el-descriptions-item>
                        <el-descriptions-item label="邮箱">
                          {{ formatEmpty(currentReport.userEmail) }}
                        </el-descriptions-item>
                        <el-descriptions-item label="账号状态">
                          {{ userStatusText(currentReport.userStatus) }}
                        </el-descriptions-item>
                        <el-descriptions-item label="封禁状态">
                          {{ userBanText(currentReport.userBanStatus) }}
                        </el-descriptions-item>
                      </template>
                    </el-descriptions>
                  </template>

                  <div
                    v-if="currentReport.type === 'JOB' && currentReport.targetId"
                    class="target-actions"
                  >
                    <el-button size="small" type="primary" plain @click="openJobDetail(currentReport.targetId)">
                      查看岗位详情页
                    </el-button>
                  </div>
                </el-collapse-item>

                <el-collapse-item name="report" title="举报内容与证据">
                  <el-descriptions :column="1" border size="small">
                    <el-descriptions-item label="举报人">
                      {{ formatEmpty(currentReport.reporter) }}
                    </el-descriptions-item>
                    <el-descriptions-item label="举报人角色">
                      {{ roleText(currentReport.reporterRole) }}
                    </el-descriptions-item>
                    <el-descriptions-item label="提交时间">
                      {{ formatEmpty(currentReport.createdAt) }}
                    </el-descriptions-item>
                    <el-descriptions-item label="举报原因">
                      <div class="long-text">{{ formatEmpty(currentReport.reason) }}</div>
                    </el-descriptions-item>
                  </el-descriptions>

                  <div class="evidence-section">
                    <div class="evidence-title">证据附件（{{ currentEvidenceList.length }}）</div>
                    <el-alert
                      v-if="currentReport.type === 'USER' && currentEvidenceList.length === 0"
                      type="warning"
                      :closable="false"
                      show-icon
                      title="该账号举报未上传证据附件，建议谨慎处理并要求补充截图。"
                      class="evidence-warning"
                    />
                    <template v-if="currentEvidenceList.length">
                      <div class="evidence-grid">
                        <div
                          v-for="(item, idx) in currentEvidenceList"
                          :key="item + idx"
                          class="evidence-item"
                        >
                          <el-image
                            v-if="isImageEvidence(item)"
                            :src="formatFileUrl(item)"
                            :preview-src-list="[formatFileUrl(item)]"
                            fit="cover"
                            class="evidence-image"
                          />
                          <div v-else class="evidence-file">
                            <div class="evidence-file-name">{{ getEvidenceName(item) }}</div>
                            <div class="evidence-file-actions">
                              <el-button v-if="isPdfEvidence(item)" size="small" type="primary" @click="openEvidencePreview(item)">预览</el-button>
                              <el-button size="small" @click="openEvidencePreview(item)">打开</el-button>
                            </div>
                          </div>
                        </div>
                      </div>
                      <div class="evidence-timeline-wrap">
                        <div class="evidence-timeline-title">证据时间线</div>
                        <el-timeline>
                          <el-timeline-item
                            v-for="(item, idx) in currentEvidenceTimeline"
                            :key="`${item.fileUrl}-${idx}`"
                            :timestamp="item.createTime || `序号 #${item.sortOrder || idx + 1}`"
                            placement="top"
                          >
                            <div class="evidence-timeline-item">
                              <el-tag size="small" effect="plain">
                                {{ evidenceTypeText(item.fileType, item.fileUrl) }}
                              </el-tag>
                              <el-button type="primary" link @click="openEvidencePreview(item.fileUrl)">
                                {{ getEvidenceName(item.fileUrl) }}
                              </el-button>
                            </div>
                          </el-timeline-item>
                        </el-timeline>
                      </div>
                    </template>
                    <el-empty v-else description="未上传证据" :image-size="80" />
                  </div>
                </el-collapse-item>

                <el-collapse-item name="handle" title="处理信息">
                  <el-descriptions :column="1" border size="small">
                    <el-descriptions-item label="处理状态">
                      {{ statusText(currentReport.status) }}
                    </el-descriptions-item>
                    <el-descriptions-item label="处理动作">
                      {{ actionText(currentReport.actionCode) }}
                    </el-descriptions-item>
                    <el-descriptions-item label="处理结果">
                      <div class="long-text">{{ formatEmpty(currentReport.result) }}</div>
                    </el-descriptions-item>
                    <el-descriptions-item label="处理人">
                      {{ currentReport.handledBy ? `管理员#${currentReport.handledBy}` : '—' }}
                    </el-descriptions-item>
                    <el-descriptions-item label="处理时间">
                      {{ formatEmpty(currentReport.handledTime) }}
                    </el-descriptions-item>
                  </el-descriptions>
                </el-collapse-item>

                <el-collapse-item name="logs" title="操作记录">
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
                </el-collapse-item>
              </el-collapse>
            </div>
          </div>

          <aside class="workspace-side">
            <el-card class="decision-card" shadow="never">
              <div class="decision-title">快速处理</div>
              <div class="decision-row"><span>举报类型</span><strong>{{ typeText(currentReport.type) }}</strong></div>
              <div class="decision-row"><span>处理状态</span><strong>{{ statusText(currentReport.status) }}</strong></div>
              <div class="decision-row"><span>被举报次数</span><strong>{{ currentReport.reportCount ?? 0 }} 次</strong></div>
              <div class="decision-row"><span>证据数量</span><strong>{{ currentEvidenceList.length }} 个</strong></div>
              <div class="decision-tip">{{ decisionHintText }}</div>
              <div class="decision-actions">
                <el-button
                  type="danger"
                  plain
                  :disabled="!canHandleCurrentReport"
                  @click="rejectFromDetail"
                >
                  直接驳回
                </el-button>
                <el-button
                  type="primary"
                  :disabled="!canHandleCurrentReport"
                  @click="openHandleFromDetail"
                >
                  立即处理
                </el-button>
              </div>
            </el-card>
          </aside>
        </div>
      </div>
      <template #footer>
        <div class="detail-footer">
          <div class="detail-footer-hint">已改为工作台模式：左侧看详情，右侧快速处理。</div>
          <el-button @click="detailVisible = false">关闭</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="handleVisible" title="处理举报" width="420px">
      <el-select v-model="handleForm.action" placeholder="处理动作" style="width: 100%">
        <el-option
          v-for="option in actionOptions"
          :key="option.value"
          :label="option.label"
          :value="option.value"
        />
      </el-select>
      <div class="action-tip">处理后将自动执行对应动作并记录审计日志。</div>
      <el-input
        v-model="handleForm.note"
        type="textarea"
        rows="3"
        placeholder="处理说明（可选）"
        style="margin-top: 12px"
      />
      <template #footer>
        <el-button @click="handleVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmHandle">确认处理</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="batchHandleVisible" title="批量处理举报" width="420px">
      <el-select v-model="batchHandleForm.action" placeholder="处理动作" style="width: 100%">
        <el-option
          v-for="option in batchActionOptions"
          :key="option.value"
          :label="option.label"
          :value="option.value"
        />
      </el-select>
      <div class="action-tip">批量处理会应用到所有选中举报。</div>
      <el-input
        v-model="batchHandleForm.note"
        type="textarea"
        rows="3"
        placeholder="处理说明（可选）"
        style="margin-top: 12px"
      />
      <template #footer>
        <el-button @click="batchHandleVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmBatchHandle">确认处理</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="evidencePreviewVisible" width="70%" :title="`证据预览 - ${evidencePreviewName || '附件'}`">
      <div class="evidence-preview">
        <img
          v-if="evidencePreviewType === 'image' && evidencePreviewUrl"
          :src="evidencePreviewUrl"
          :alt="evidencePreviewName || '证据预览'"
          class="evidence-preview-image"
        />
        <iframe
          v-else-if="evidencePreviewType === 'pdf' && evidencePreviewUrl"
          :src="evidencePreviewUrl"
          class="evidence-preview-frame"
        />
        <el-empty
          v-else-if="evidencePreviewUrl"
          description="当前附件不支持弹层内预览，请使用“新窗口打开”查看"
        />
        <el-empty v-else description="暂无预览内容" />
      </div>
      <template #footer>
        <el-button @click="evidencePreviewVisible = false">关闭</el-button>
        <el-button type="primary" @click="openEvidenceInNewTab">新窗口打开</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getAdminReports, handleReport, handleReportBatch, getReportLogs } from '@/api/admin'
import { formatFileUrl, getFilePreviewType } from '@/utils/file'

const router = useRouter()

const query = ref({
  type: '',
  status: null
})

const reports = ref([])
const loading = ref(false)
const reportTableRef = ref(null)
const selectedReports = ref([])
const pagination = ref({
  current: 1,
  size: 20,
  total: 0
})

const currentReport = ref(null)
const detailVisible = ref(false)
const activeDetailPanels = ref(['target', 'report'])

const handleVisible = ref(false)
const handleForm = ref({
  action: '',
  note: ''
})
const actionOptions = ref([])

const batchHandleVisible = ref(false)
const batchHandleForm = ref({
  action: '',
  note: ''
})
const batchActionOptions = ref([])

const auditLogs = ref([])
const logsLoading = ref(false)

const evidencePreviewVisible = ref(false)
const evidencePreviewUrl = ref('')
const evidencePreviewName = ref('')
const evidencePreviewType = computed(() => getFilePreviewType(evidencePreviewUrl.value))

const currentEvidenceTimeline = computed(() => parseEvidenceTimeline(currentReport.value))
const currentEvidenceList = computed(() => {
  return currentEvidenceTimeline.value
    .map(item => item.fileUrl)
    .filter(Boolean)
})
const currentTargetSnapshot = computed(() => parseTargetSnapshot(currentReport.value?.targetSnapshot))
const currentTargetSnapshotCards = computed(() => buildSnapshotCards(currentReport.value, currentTargetSnapshot.value))
const canHandleCurrentReport = computed(() => Number(currentReport.value?.status) === 0)
const decisionHintText = computed(() => {
  const report = currentReport.value
  if (!report) return '请先查看举报详情。'
  if (report.type === 'JOB') {
    return '建议按风险分级处理：轻度警告，中度下架，重度可下架并限制/封禁企业。'
  }
  if (report.type === 'MERCHANT') {
    return '建议结合历史举报次数和证据完整度，决定警告、限制发布或封禁。'
  }
  if (report.type === 'USER') {
    if (!currentEvidenceList.value.length) {
      return '该账号举报暂无证据，建议谨慎处理或先驳回并要求补充截图。'
    }
    return '证据已提供，建议结合违规严重程度选择禁用或封禁。'
  }
  return '请综合对象详情与证据后再做决定。'
})

const statusText = (status) => {
  const map = { 0: '待处理', 1: '已处理', 2: '已驳回' }
  return map[status] || '未知'
}

const actionText = (actionCode) => {
  const map = {
    JOB_WARN: '警告发布方（保留职位）',
    JOB_OFFLINE: '下架职位',
    JOB_OFFLINE_LIMIT_MERCHANT: '下架并限制企业发布',
    JOB_OFFLINE_BAN_MERCHANT: '下架并封禁企业',
    MERCHANT_WARN: '警告企业',
    MERCHANT_LIMIT: '限制发布',
    MERCHANT_BAN: '封禁企业',
    USER_WARN: '警告账号',
    USER_DISABLE: '禁用账号',
    USER_BAN: '封禁账号',
    USER_BLACKLIST: '拉黑账号',
    REJECT: '驳回举报'
  }
  if (!actionCode) return '—'
  return map[actionCode] || actionCode
}

const statusTag = (status) => {
  const map = { 0: 'info', 1: 'success', 2: 'danger' }
  return map[status] || 'info'
}

const typeText = (type) => {
  const map = { JOB: '职位', MERCHANT: '企业', USER: '个人账号' }
  return map[type] || '未知'
}

const roleText = (role) => {
  const map = { ADMIN: '管理员', MERCHANT: '商家', APPLICANT: '求职者' }
  return map[role] || (role ? String(role) : '—')
}

const auditStatusText = (status) => {
  const map = { 0: '待审核', 1: '已通过', 2: '已驳回' }
  return map[status] || '未知'
}

const jobStatusText = (status) => {
  const map = { 0: '已下架', 1: '招聘中' }
  return map[status] || '未知'
}

const merchantPublishText = (status) => {
  const map = { 0: '限制发布', 1: '正常', 2: '封禁' }
  return map[status] || '未知'
}

const userStatusText = (status) => {
  const map = { 0: '禁用', 1: '正常' }
  return map[status] || '未知'
}

const userBanText = (status) => {
  const map = { 0: '正常', 1: '限制', 2: '封禁/拉黑' }
  return map[status] || '未知'
}

const formatEmpty = (value) => {
  if (value === null || value === undefined) return '—'
  const text = String(value).trim()
  return text ? text : '—'
}

// 快照字段兼容：可能是 JSON 字符串，也可能已被前端解析为对象
const parseTargetSnapshot = (snapshot) => {
  if (!snapshot) return null
  if (typeof snapshot === 'object') return snapshot
  try {
    return JSON.parse(snapshot)
  } catch (error) {
    return null
  }
}

// 将快照字段整理成展示卡片，帮助管理员快速了解“提交当时”的对象状态
const buildSnapshotCards = (row, snapshot) => {
  if (!row || !snapshot) return []
  if (row.type === 'JOB') {
    return [
      { label: '职位标题', value: snapshot.title },
      { label: '薪资', value: snapshot.salary },
      { label: '工作地点', value: [snapshot.workLocation, snapshot.district].filter(Boolean).join(' ') },
      { label: '经验要求', value: snapshot.experience },
      { label: '学历要求', value: snapshot.degree },
      { label: '招聘人数', value: snapshot.headcount ? `${snapshot.headcount} 人` : '' },
      { label: '企业名称', value: snapshot.companyName },
      { label: '企业行业', value: snapshot.companyIndustry },
      { label: '企业规模', value: snapshot.companyScale }
    ]
  }
  if (row.type === 'MERCHANT') {
    return [
      { label: '企业名称', value: snapshot.companyName },
      { label: '所属行业', value: snapshot.industry },
      { label: '企业规模', value: snapshot.scale },
      { label: '融资阶段', value: snapshot.financing },
      { label: '联系人', value: snapshot.contactName },
      { label: '联系电话', value: snapshot.contactPhone },
      { label: '企业主账号', value: snapshot.ownerName }
    ]
  }
  if (row.type === 'USER') {
    return [
      { label: '账号昵称', value: snapshot.nickname },
      { label: '账号名', value: snapshot.username },
      { label: '账号角色', value: roleText(snapshot.role) },
      { label: '手机号', value: snapshot.phone },
      { label: '邮箱', value: snapshot.email },
      { label: '账号状态', value: userStatusText(snapshot.status) },
      { label: '封禁状态', value: userBanText(snapshot.banStatus) }
    ]
  }
  return []
}

const parseTagList = (tagText) => {
  if (!tagText) return []
  return String(tagText)
    .replace(/["']/g, '')
    .split(/[,，]/)
    .map(item => item.trim())
    .filter(Boolean)
}

// 列表中的对象快照：让管理员在不打开抽屉时也能快速判断风险
const objectSummary = (row) => {
  if (!row) return '—'
  const snapshot = parseTargetSnapshot(row.targetSnapshot)
  if (row.type === 'JOB') {
    const salary = row.jobSalary || snapshot?.salary || '薪资未知'
    const location = row.jobLocation || [snapshot?.workLocation, snapshot?.district].filter(Boolean).join(' ') || '地点未知'
    const company = row.jobCompanyName || snapshot?.companyName || '企业未知'
    return `${salary} / ${location} / ${company}`
  }
  if (row.type === 'MERCHANT') {
    const industry = row.merchantIndustry || snapshot?.industry || '行业未知'
    const publish = merchantPublishText(row.merchantPublishStatus)
    return `${industry} / ${publish}`
  }
  if (row.type === 'USER') {
    const role = roleText(row.userRole || snapshot?.role)
    const userStatus = userStatusText(row.userStatus ?? snapshot?.status)
    return `${role} / ${userStatus}`
  }
  return '—'
}

const normalizeList = (data) => {
  if (Array.isArray(data)) return data
  return data?.records || []
}

const rowSelectable = (row) => {
  return Number(row?.status) === 0
}

const handleSelectionChange = (rows) => {
  selectedReports.value = rows || []
}

const fetchReports = async () => {
  loading.value = true
  try {
    const res = await getAdminReports({
      type: query.value.type,
      status: query.value.status,
      current: pagination.value.current,
      size: pagination.value.size
    })
    if (res.code === 200) {
      reports.value = normalizeList(res.data)
      pagination.value.total = res.data?.total || 0
    } else {
      reports.value = []
      pagination.value.total = 0
    }
  } catch (error) {
    console.error('获取举报列表失败', error)
    reports.value = []
    pagination.value.total = 0
  } finally {
    loading.value = false
    await nextTick()
    if (reportTableRef.value) {
      reportTableRef.value.clearSelection()
    }
    selectedReports.value = []
  }
}

const applyFilter = () => {
  pagination.value.current = 1
  fetchReports()
}

const resetFilter = () => {
  query.value.type = ''
  query.value.status = null
  pagination.value.current = 1
  fetchReports()
}

const openDetail = (row) => {
  currentReport.value = row
  detailVisible.value = true
  activeDetailPanels.value = ['target', 'report']
  fetchAuditLogs(row.id)
}

const openJobDetail = (jobId) => {
  if (!jobId) return
  const routeData = router.resolve({ name: 'job-detail', params: { id: jobId } })
  window.open(routeData.href, '_blank')
}

const openHandle = (row) => {
  currentReport.value = row
  actionOptions.value = getActionOptions(row.type)
  handleForm.value = { action: actionOptions.value[0]?.value || '', note: '' }
  handleVisible.value = true
}

const openHandleFromDetail = () => {
  if (!currentReport.value) return
  if (Number(currentReport.value.status) !== 0) {
    ElMessage.warning('当前举报已处理，无需重复操作')
    return
  }
  openHandle(currentReport.value)
}

const openBatchHandle = () => {
  if (!selectedReports.value.length) {
    ElMessage.warning('请先选择待处理举报')
    return
  }
  const types = Array.from(new Set(selectedReports.value.map(item => item.type)))
  if (types.length > 1) {
    ElMessage.warning('批量处理需选择相同类型的举报')
    return
  }
  batchActionOptions.value = getActionOptions(types[0])
  batchHandleForm.value = { action: batchActionOptions.value[0]?.value || '', note: '' }
  batchHandleVisible.value = true
}

const confirmHandle = async () => {
  if (!currentReport.value) return
  const action = handleForm.value.action || ''
  if (!action) {
    ElMessage.warning('请选择处理动作')
    return
  }
  const actionLabel = actionOptions.value.find((item) => item.value === action)?.label || action
  const note = handleForm.value.note || ''
  try {
    await handleReport(currentReport.value.id, {
      status: 1,
      action,
      result: note ? `${actionLabel}（${note}）` : actionLabel
    })
    currentReport.value = {
      ...currentReport.value,
      status: 1,
      actionCode: action,
      result: note ? `${actionLabel}（${note}）` : actionLabel
    }
    handleVisible.value = false
    ElMessage.success('举报已处理')
    fetchReports()
  } catch (error) {
    console.error('处理举报失败', error)
  }
}

const confirmBatchHandle = async () => {
  if (!selectedReports.value.length) return
  const action = batchHandleForm.value.action || ''
  if (!action) {
    ElMessage.warning('请选择处理动作')
    return
  }
  const actionLabel = batchActionOptions.value.find((item) => item.value === action)?.label || action
  const note = batchHandleForm.value.note || ''
  try {
    await handleReportBatch({
      ids: selectedReports.value.map(item => item.id),
      status: 1,
      action,
      result: note ? `${actionLabel}（${note}）` : actionLabel
    })
    batchHandleVisible.value = false
    ElMessage.success('批量处理成功')
    fetchReports()
  } catch (error) {
    console.error('批量处理失败', error)
  }
}

const reject = async (row) => {
  try {
    await handleReport(row.id, { status: 2, action: '驳回举报', result: '驳回举报' })
    ElMessage.success('已驳回')
    if (currentReport.value && currentReport.value.id === row.id) {
      currentReport.value = {
        ...currentReport.value,
        status: 2,
        actionCode: 'REJECT',
        result: '驳回举报'
      }
    }
    fetchReports()
    return true
  } catch (error) {
    console.error('驳回举报失败', error)
    return false
  }
}

const rejectFromDetail = async () => {
  if (!currentReport.value) return
  if (Number(currentReport.value.status) !== 0) {
    ElMessage.warning('当前举报已处理，无需重复操作')
    return
  }
  const success = await reject(currentReport.value)
  if (success) {
    detailVisible.value = false
  }
}

const batchReject = async () => {
  if (!selectedReports.value.length) {
    ElMessage.warning('请先选择待处理举报')
    return
  }
  try {
    await handleReportBatch({
      ids: selectedReports.value.map(item => item.id),
      status: 2,
      action: '驳回举报',
      result: '驳回举报'
    })
    ElMessage.success('批量驳回成功')
    fetchReports()
  } catch (error) {
    console.error('批量驳回失败', error)
  }
}

// 解析证据文件列表（新接口为 JSON 字符串；旧接口可能为数组）
const parseEvidenceFiles = (value) => {
  if (!value) return []
  if (Array.isArray(value)) return value
  if (typeof value === 'string') {
    const text = value.trim()
    if (!text) return []
    try {
      const parsed = JSON.parse(text)
      return Array.isArray(parsed) ? parsed : []
    } catch (error) {
      return []
    }
  }
  return []
}

// 证据时间线：优先使用规范化字段 evidenceFiles，兼容旧 evidence 字符串
const parseEvidenceTimeline = (row) => {
  if (!row) return []

  const files = parseEvidenceFiles(row.evidenceFiles)
  if (files.length > 0) {
    return files
      .map((item, idx) => {
        if (typeof item === 'string') {
          return {
            fileUrl: item.trim(),
            fileType: '',
            sortOrder: idx + 1,
            createTime: ''
          }
        }
        return {
          fileUrl: item?.fileUrl ? String(item.fileUrl).trim() : '',
          fileType: item?.fileType ? String(item.fileType).trim().toUpperCase() : '',
          sortOrder: Number(item?.sortOrder ?? idx + 1),
          createTime: item?.createTime ? String(item.createTime).trim() : ''
        }
      })
      .filter(item => item.fileUrl)
      .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
  }

  // 兼容旧版：仅有逗号拼接地址
  return parseEvidenceList(row.evidence).map((fileUrl, idx) => ({
    fileUrl,
    fileType: '',
    sortOrder: idx + 1,
    createTime: ''
  }))
}

const parseEvidenceList = (value) => {
  if (!value) return []
  return String(value)
    .split(',')
    .map(item => item.trim())
    .filter(Boolean)
}

const isImageEvidence = (url) => {
  if (!url) return false
  const lower = url.toLowerCase()
  return lower.endsWith('.jpg') || lower.endsWith('.jpeg') || lower.endsWith('.png')
}

const isPdfEvidence = (url) => {
  if (!url) return false
  return url.toLowerCase().endsWith('.pdf')
}

const getEvidenceName = (url) => {
  if (!url) return '证据'
  const parts = url.split('/')
  return parts[parts.length - 1] || '证据'
}

const evidenceTypeText = (fileType, fileUrl) => {
  if (fileType === 'IMAGE') return '图片'
  if (fileType === 'PDF') return 'PDF'
  if (isImageEvidence(fileUrl)) return '图片'
  if (isPdfEvidence(fileUrl)) return 'PDF'
  return '附件'
}

const openEvidencePreview = (url) => {
  if (!url) return
  evidencePreviewUrl.value = formatFileUrl(url)
  evidencePreviewName.value = getEvidenceName(url)
  evidencePreviewVisible.value = true
}

const openEvidenceInNewTab = () => {
  if (!evidencePreviewUrl.value) return
  window.open(evidencePreviewUrl.value, '_blank')
}

const getActionOptions = (type) => {
  if (type === 'JOB') {
    return [
      { label: '警告发布方（保留职位）', value: 'JOB_WARN' },
      { label: '下架职位', value: 'JOB_OFFLINE' },
      { label: '下架并限制企业发布', value: 'JOB_OFFLINE_LIMIT_MERCHANT' },
      { label: '下架并封禁企业', value: 'JOB_OFFLINE_BAN_MERCHANT' }
    ]
  }
  if (type === 'MERCHANT') {
    return [
      { label: '警告企业', value: 'MERCHANT_WARN' },
      { label: '限制发布', value: 'MERCHANT_LIMIT' },
      { label: '封禁企业', value: 'MERCHANT_BAN' }
    ]
  }
  if (type === 'USER') {
    return [
      { label: '警告账号', value: 'USER_WARN' },
      { label: '禁用账号', value: 'USER_DISABLE' },
      { label: '封禁账号', value: 'USER_BAN' },
      { label: '拉黑账号', value: 'USER_BLACKLIST' }
    ]
  }
  return []
}

const fetchAuditLogs = async (reportId) => {
  logsLoading.value = true
  try {
    const res = await getReportLogs(reportId)
    auditLogs.value = Array.isArray(res.data) ? res.data : (res.data?.records || [])
  } catch (error) {
    auditLogs.value = []
  } finally {
    logsLoading.value = false
  }
}

const formatLogAction = (item) => {
  const actionMap = {
    SUBMIT: '提交举报',
    HANDLE: '处理举报',
    AUDIT: '审核处理',
    STATUS: '状态调整'
  }
  const action = actionMap[item?.action] || item?.action || '操作'
  return `${action}`
}

onMounted(() => {
  fetchReports()
})

const handlePageChange = (page) => {
  pagination.value.current = page
  fetchReports()
}

const handleSizeChange = (size) => {
  pagination.value.size = size
  pagination.value.current = 1
  fetchReports()
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

.target-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.target-title {
  color: #1f2937;
  font-weight: 600;
  line-height: 1.35;
}

.target-sub {
  color: #94a3b8;
  font-size: 12px;
}

.drawer-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.report-detail-dialog :deep(.el-dialog) {
  margin-bottom: 0;
}

.report-detail-dialog :deep(.el-dialog__header) {
  padding-bottom: 10px;
  border-bottom: 1px solid #e2e8f0;
  background: linear-gradient(90deg, #f8fbff 0%, #ffffff 72%);
}

.report-detail-dialog :deep(.el-dialog__title) {
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
}

.report-detail-dialog :deep(.el-dialog__body) {
  padding-top: 10px;
  padding-bottom: 10px;
}

.detail-dialog-body {
  max-height: calc(100vh - 220px);
  overflow-y: auto;
  padding-right: 4px;
}

.workspace-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 14px;
  align-items: start;
}

.workspace-main {
  min-width: 0;
}

.workspace-side {
  position: sticky;
  top: 0;
  align-self: start;
}

.decision-card {
  border-radius: 12px;
  border: 1px solid #dbeafe;
  background: linear-gradient(180deg, #f8fbff 0%, #ffffff 48%);
}

.decision-title {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
  margin-bottom: 10px;
}

.decision-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  padding: 7px 0;
  border-bottom: 1px dashed #e2e8f0;
  font-size: 13px;
  color: #475569;
}

.decision-row:last-of-type {
  border-bottom: 0;
}

.decision-row strong {
  color: #0f172a;
  font-weight: 700;
}

.decision-tip {
  margin-top: 10px;
  border: 1px solid #e2e8f0;
  background: #ffffff;
  border-radius: 10px;
  padding: 9px 10px;
  font-size: 12px;
  line-height: 1.55;
  color: #475569;
}

.decision-actions {
  margin-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.decision-actions .el-button {
  margin-left: 0;
}

.detail-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.detail-footer-hint {
  font-size: 12px;
  color: #64748b;
}

.detail-footer-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.detail-summary {
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  border-radius: 10px;
  padding: 12px;
}

.summary-title {
  font-size: 15px;
  font-weight: 600;
  color: #0f172a;
  line-height: 1.4;
}

.summary-tags {
  margin-top: 8px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.detail-collapse {
  border-top: 0;
}

.snapshot-card {
  margin-bottom: 10px;
  border: 1px solid #dbeafe;
  border-radius: 12px;
  background: linear-gradient(180deg, #f8fbff 0%, #ffffff 60%);
  padding: 10px;
}

.snapshot-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}

.snapshot-title {
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
}

.snapshot-time {
  font-size: 12px;
  color: #64748b;
}

.snapshot-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 8px;
}

.snapshot-item {
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 8px 10px;
  background: #fff;
}

.snapshot-label {
  font-size: 12px;
  color: #64748b;
}

.snapshot-value {
  margin-top: 4px;
  font-size: 13px;
  color: #1f2937;
  word-break: break-word;
}

.job-detail-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.job-detail-hero {
  border: 1px solid #dbeafe;
  border-radius: 12px;
  background: #f8fbff;
  padding: 12px;
}

.job-title-row {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: flex-start;
}

.job-title {
  font-size: 17px;
  font-weight: 700;
  color: #0f172a;
  line-height: 1.35;
}

.job-salary {
  color: #ea580c;
  font-size: 16px;
  font-weight: 700;
  white-space: nowrap;
}

.job-meta-row {
  margin-top: 10px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.job-meta-chip {
  font-size: 12px;
  color: #334155;
  padding: 5px 10px;
  border-radius: 999px;
  border: 1px solid #cbd5e1;
  background: #fff;
}

.job-tag-row {
  margin-top: 10px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.job-status-row {
  margin-top: 10px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.job-company-card {
  display: flex;
  gap: 10px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 10px;
  background: #fff;
}

.job-company-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.job-company-name {
  font-weight: 600;
  color: #0f172a;
  line-height: 1.35;
}

.job-company-sub {
  font-size: 12px;
  color: #64748b;
  line-height: 1.4;
  word-break: break-word;
}

.long-text {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.5;
}

.target-actions {
  margin-top: 10px;
}

.action-tip {
  margin-top: 8px;
  font-size: 12px;
  color: #94a3b8;
}

.evidence-section {
  margin-top: 12px;
}

.evidence-title {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
  margin-bottom: 8px;
}

.evidence-warning {
  margin-bottom: 10px;
}

.evidence-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 10px;
}

.evidence-item {
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 6px;
  background: #f8fafc;
}

.evidence-image {
  width: 100%;
  height: 96px;
  border-radius: 8px;
}

.evidence-file {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 12px;
}

.evidence-file-name {
  color: #1f2937;
  word-break: break-all;
}

.evidence-file-actions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.evidence-timeline-wrap {
  margin-top: 10px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 10px;
  background: #fff;
}

.evidence-timeline-title {
  font-size: 12px;
  color: #64748b;
  margin-bottom: 8px;
}

.evidence-timeline-item {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
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

.evidence-preview {
  min-height: 60vh;
  display: flex;
  align-items: center;
  justify-content: center;
}

.evidence-preview-image {
  max-width: 100%;
  max-height: 60vh;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  object-fit: contain;
  background: #f8fafc;
}

.evidence-preview-frame {
  width: 100%;
  height: 60vh;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
}

.pagination-row {
  padding-top: 12px;
}

@media (max-width: 900px) {
  .detail-dialog-body {
    max-height: calc(100vh - 250px);
  }

  .workspace-grid {
    grid-template-columns: 1fr;
  }

  .workspace-side {
    position: static;
  }

  .detail-footer {
    flex-direction: column;
    align-items: flex-start;
  }

  .detail-footer :deep(.el-button) {
    width: 100%;
  }
}
</style>
