<!--
文件速览：
1. 文件职责：公共职位详情页，负责展示职位信息、企业信息、投递沟通与举报入口。
2. 页面入口：职位详情路由，供求职者查看单个职位的完整内容。
3. 关键结构：detail-shell、hero-card、content-grid、reportDialogVisible、companyDialogVisible。
4. 阅读建议：先看顶部 hero 与右侧操作区，再看内容双栏，最后看两个详情弹窗与底部断点。
-->
<template>
  <div class="detail-page" v-if="job.id">
    <div class="detail-glow detail-glow-left"></div>
    <div class="detail-glow detail-glow-right"></div>

    <div class="detail-shell">
      <section class="hero-card">
        <div class="hero-main">
          <div class="hero-top">
            <div class="hero-top-left">
              <el-tag size="small" effect="plain" class="scene-tag">职位详情</el-tag>
              <span class="publish-time">发布于 {{ formatDateYMD(job.createTime) }}</span>
            </div>
            <el-tag
              v-if="deliveryStatus !== null"
              :type="getDeliveryStatusTag(deliveryStatus)"
              size="small"
              effect="dark"
              class="status-tag"
            >
              {{ getDeliveryStatusText(deliveryStatus) }}
            </el-tag>
            <el-tag
              v-else-if="job.status === 1"
              type="success"
              size="small"
              effect="dark"
              class="status-tag"
            >
              招聘中
            </el-tag>
            <el-tag v-else type="danger" size="small" effect="dark" class="status-tag">
              已下架
            </el-tag>
          </div>

          <div class="title-row">
            <h1>{{ job.title }}</h1>
            <span class="salary-pill">{{ formatSalaryK(job.minSalary, job.maxSalary) }}</span>
          </div>

          <div class="meta-row">
            <div class="meta-chip">
              <el-icon><Location /></el-icon>
              <span>{{ job.workLocation }}<span v-if="job.district"> · {{ job.district }}</span></span>
            </div>
            <div class="meta-chip">
              <el-icon><Suitcase /></el-icon>
              <span>{{ job.experience || '经验不限' }}</span>
            </div>
            <div class="meta-chip">
              <el-icon><School /></el-icon>
              <span>{{ job.degree || '学历不限' }}</span>
            </div>
            <div class="meta-chip">
              <el-icon><User /></el-icon>
              <span>招聘 {{ job.headcount || 1 }} 人</span>
            </div>
          </div>

          <div class="tag-row" v-if="job.tags">
            <el-tag
              v-for="tag in parseTags(job.tags)"
              :key="tag"
              size="small"
              effect="plain"
              class="skill-tag"
            >
              {{ tag }}
            </el-tag>
          </div>
        </div>

        <div class="hero-actions">
          <div class="action-main-row">
            <el-button type="primary" size="large" :icon="ChatDotRound" class="action-btn action-btn-primary" @click="handleChat">
              立即沟通
            </el-button>
            <el-button
              size="large"
              :icon="Document"
              class="action-btn action-btn-secondary"
              :disabled="hasApplied"
              @click="handleApply"
            >
              {{ deliveryStatus !== null ? getDeliveryStatusText(deliveryStatus) : '投递简历' }}
            </el-button>
          </div>

          <div class="risk-tip">
            <el-icon><Warning /></el-icon>
            <span>如遇虚假招聘、诱导收费等，请及时举报。</span>
          </div>

          <div class="report-actions">
            <el-button size="small" type="danger" plain @click="openReportDialog('JOB')">举报职位</el-button>
            <el-button size="small" type="warning" plain @click="openReportDialog('MERCHANT')">举报企业</el-button>
          </div>

          <div class="resume-tip" v-if="showResumeTip">
            <span>在线简历完善度 {{ resumeCompleteness ?? 0 }}%，建议达到 {{ RESUME_TIP_THRESHOLD }}% 以上</span>
            <el-button type="primary" link @click="goResume">去完善</el-button>
          </div>
        </div>
      </section>

      <section class="content-grid">
        <el-card shadow="never" class="content-card">
          <div class="content-section">
            <h3>职位描述</h3>
            <div class="rich-text" v-html="formatRichText(job.description || '暂无职位描述')"></div>
          </div>

          <el-divider />

          <div class="content-section">
            <h3>任职要求</h3>
            <div class="rich-text" v-html="formatRichText(job.requirement || '暂无详细要求')"></div>
          </div>

          <el-divider />

          <div class="content-section">
            <h3>工作地点</h3>
            <div class="location-box">
              <el-icon class="loc-icon"><MapLocation /></el-icon>
              <span>
                <template v-if="job.companyAddress && job.companyAddress.includes(job.workLocation)">
                  {{ job.companyAddress }}
                </template>
                <template v-else>
                  {{ job.workLocation }}
                  <span v-if="job.district"> · {{ job.district }}</span>
                  <span v-if="job.companyAddress"> · {{ job.companyAddress }}</span>
                </template>
              </span>
            </div>
          </div>
        </el-card>

        <div class="side-column">
          <el-card shadow="hover" class="side-card">
            <div class="hr-header">
              <el-avatar
                :size="56"
                :src="formatFileUrl(job.publisherAvatar) || 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'"
              />
              <div class="hr-info">
                <div class="hr-name">
                  {{ job.publisherName || '招聘官' }}
                  <el-icon class="verified-icon"><CircleCheckFilled /></el-icon>
                </div>
                <div class="hr-title">招聘负责人 · 在线</div>
              </div>
            </div>
            <el-button class="side-chat-btn" :icon="ChatLineRound" @click="handleChat">和 TA 聊聊</el-button>
          </el-card>

          <el-card shadow="hover" class="side-card company-card" @click="openCompanyDialog">
            <div class="comp-header">
              <img
                :src="formatFileUrl(job.companyLogo) || 'https://cube.elemecdn.com/9/c2/f0ee8a3c7c9638a54940382568c9dpng.png'"
                class="comp-logo"
                alt="公司logo"
              />
              <div class="comp-info">
                <div class="comp-name">{{ job.companyName || '企业名称待完善' }}</div>
                <div class="comp-props">{{ job.companyIndustry || '行业未填写' }} · {{ job.companyScale || '规模未填写' }}</div>
              </div>
            </div>

            <div class="company-location">
              <el-icon><LocationInformation /></el-icon>
              <span>{{ job.companyAddress || '地址待完善' }}</span>
            </div>

            <div class="company-cta">
              <span>点击查看企业详情</span>
              <el-icon><ArrowRight /></el-icon>
            </div>
          </el-card>
        </div>
      </section>
    </div>
  </div>

  <el-dialog v-model="reportDialogVisible" title="举报信息" width="min(520px, 92vw)">
    <el-form :model="reportForm" label-width="90px">
      <el-form-item label="举报对象">
        <el-tag v-if="reportForm.type === 'JOB'" type="danger">职位：{{ job.title || '未知职位' }}</el-tag>
        <el-tag v-else-if="reportForm.type === 'MERCHANT'" type="warning">企业：{{ job.companyName || '未知企业' }}</el-tag>
      </el-form-item>
      <el-form-item label="举报原因">
        <el-select v-model="reportForm.reasonType" placeholder="请选择原因" style="width: 100%">
          <el-option
            v-for="option in reportReasonOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="详细说明">
        <el-input
          v-model="reportForm.detail"
          type="textarea"
          rows="4"
          maxlength="300"
          show-word-limit
          placeholder="可补充虚假描述、联系方式、聊天截图等情况"
        />
      </el-form-item>
      <el-form-item label="证据附件">
        <el-upload
          class="report-upload"
          :file-list="reportUploadList"
          :http-request="handleReportUpload"
          :on-remove="handleReportRemove"
          :limit="3"
          multiple
          accept=".jpg,.jpeg,.png,.pdf"
          list-type="text"
        >
          <el-button size="small" type="primary">上传证据</el-button>
          <template #tip>
            <div class="report-upload-tip">支持 JPG/PNG/PDF，最多 3 个文件，单个不超过 5MB。</div>
          </template>
        </el-upload>
      </el-form-item>
    </el-form>
    <div class="report-tip">请确保举报信息真实有效，提交后将进入管理员处理队列。</div>
    <template #footer>
      <el-button @click="reportDialogVisible = false">取消</el-button>
      <el-button type="danger" @click="submitReportForm">提交举报</el-button>
    </template>
  </el-dialog>

  <el-dialog
    v-model="companyDialogVisible"
    title="企业详情"
    width="min(680px, 94vw)"
    align-center
    class="company-dialog"
  >
    <div class="company-dialog-body">
      <div class="company-dialog-head">
        <img
          :src="formatFileUrl(job.companyLogo) || 'https://cube.elemecdn.com/9/c2/f0ee8a3c7c9638a54940382568c9dpng.png'"
          class="company-dialog-logo"
          alt="企业logo"
        />
        <div class="company-dialog-main">
          <h4>{{ job.companyName || '企业名称待完善' }}</h4>
          <p class="company-dialog-sub">{{ job.companyIndustry || '行业未填写' }} · {{ job.companyScale || '规模未填写' }}</p>
        </div>
      </div>

      <div class="company-dialog-grid">
        <div class="dialog-field">
          <span class="field-label">所在地址</span>
          <span class="field-value">{{ job.companyAddress || '地址待完善' }}</span>
        </div>
        <div class="dialog-field">
          <span class="field-label">招聘负责人</span>
          <span class="field-value">{{ job.publisherName || '招聘官' }}</span>
        </div>
        <div class="dialog-field">
          <span class="field-label">负责人身份</span>
          <span class="field-value">{{ job.publisherTitle || '招聘负责人' }}</span>
        </div>
        <div class="dialog-field">
          <span class="field-label">当前在招岗位</span>
          <span class="field-value">{{ job.title || '职位待完善' }}</span>
        </div>
      </div>

      <div class="dialog-desc">
        <div class="dialog-section-title">企业信息补充</div>
        <p>{{ companyDescriptionText }}</p>
      </div>
    </div>

    <template #footer>
      <el-button @click="companyDialogVisible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, onMounted, reactive } from 'vue';
import { useRouter } from 'vue-router';
import { getPublicJobDetail } from '@/api/job';
import { formatFileUrl } from '@/utils/file'
import { formatDateYMD, formatSalaryK } from '@/utils/format'
import { ElMessage, ElMessageBox } from 'element-plus'
import { submitDelivery, getDeliveryStatus } from '@/api/delivery'
import { getDashboardStats } from '@/api/applicant'
import { submitReport, uploadReportEvidence } from '@/api/report'
import { useUserStore } from '@/stores/user'
import {
  Location, Suitcase, School, ChatDotRound, Document,
  Warning, MapLocation, CircleCheckFilled, ChatLineRound, LocationInformation, ArrowRight,
  User
} from '@element-plus/icons-vue';

const router = useRouter();
const userStore = useUserStore()
const job = ref({});
const jobId = router.currentRoute.value.params.id;
const hasApplied = ref(false) // 是否已投递标记
const deliveryStatus = ref(null) // 当前用户对该职位的投递状态
const companyDialogVisible = ref(false)
const resumeCompleteness = ref(null)
const reportDialogVisible = ref(false)
const reportForm = reactive({ type: '', reasonType: '', detail: '' })
const reportEvidenceList = ref([])
const reportUploadList = ref([])
// 简历完善度达到该阈值后，隐藏“去完善简历”提示条
const RESUME_TIP_THRESHOLD = 85
const reportReasonOptions = [
  { label: '虚假招聘/诈骗', value: 'FRAUD' },
  { label: '薪资/岗位信息不符', value: 'MISLEAD' },
  { label: '违规内容/违法招聘', value: 'ILLEGAL' },
  { label: '诱导缴费/培训', value: 'CHARGE' },
  { label: '其他违规行为', value: 'OTHER' }
]

const isApplicant = computed(() => userStore.role === 'APPLICANT')
// 仅求职者显示提示；完善度足够时不展示该引导
const showResumeTip = computed(() => {
  if (!isApplicant.value) return false
  if (resumeCompleteness.value === null || resumeCompleteness.value === undefined) return true
  return resumeCompleteness.value < RESUME_TIP_THRESHOLD
})

const companyDescriptionText = computed(() => {
  const raw = (job.value?.companyDescription || job.value?.companyIntro || '').trim()
  if (raw) return raw
  return '企业暂未公开更多介绍信息，可先发起沟通进一步了解。'
})

// 状态归一化：空值统一按 0 处理
const normalizeStatus = (status) => {
  if (status === null || status === undefined || status === '') return 0
  const num = Number(status)
  return Number.isNaN(num) ? 0 : num
}


const getDeliveryStatusText = (status) => {
  const map = { 0: '已投递', 1: '已初筛', 2: '面试邀约', 3: '不合适' }
  return map[normalizeStatus(status)] || '未知状态'
}
const getDeliveryStatusTag = (status) => {
  const map = { 0: 'info', 1: 'primary', 2: 'success', 3: 'danger' }
  return map[normalizeStatus(status)] || 'info'
}

// 标签清洗工具
const parseTags = (tagStr) => {
  if (!tagStr) return [];
  return tagStr.replace(/["']/g, '').split(/[,，]/).map(item => item.trim()).filter(Boolean);
};

// 富文本容错渲染：纯文本自动换行，HTML 直接渲染
const formatRichText = (content) => {
  const source = String(content || '').trim()
  if (!source) return '暂无内容'
  if (/<[a-z][\s\S]*>/i.test(source)) return source
  return source.replace(/\n/g, '<br/>')
}

const goResume = () => {
  router.push('/applicant/resume')
}

const openCompanyDialog = () => {
  companyDialogVisible.value = true
}

const fetchResumeCompleteness = async () => {
  if (!isApplicant.value) return
  try {
    const res = await getDashboardStats()
    const stats = res?.data || {}
    const raw = stats.resumeCompleteness ?? stats.resumeCompletion
    const value = Number(raw)
    resumeCompleteness.value = Number.isFinite(value)
      ? Math.max(0, Math.min(100, Math.round(value)))
      : null
  } catch (error) {
    resumeCompleteness.value = null
  }
}

const handleChat = () => {
  if (!job.value.publisherId) {
    ElMessage.warning('暂未获取到招聘方信息')
    return
  }
  router.push({
    path: '/chat',
    query: {
      targetId: job.value.publisherId,
      targetName: job.value.publisherName || '招聘负责人',
      targetAvatar: job.value.publisherAvatar || '',
      companyName: job.value.companyName || '',
      jobTitle: job.value.title || '',
      jobId: job.value.id || ''
    }
  })
}

// 打开举报弹窗（职位/企业）
const openReportDialog = (type) => {
  if (!job.value?.id) {
    ElMessage.warning('职位信息未加载完成')
    return
  }
  reportForm.type = type
  reportForm.reasonType = reportReasonOptions[0]?.value || ''
  reportForm.detail = ''
  reportEvidenceList.value = []
  reportUploadList.value = []
  reportDialogVisible.value = true
}

// 举报证据上传
const handleReportUpload = async (options) => {
  const file = options?.file
  if (!file) {
    options?.onError?.(new Error('未获取到文件'))
    return
  }
  try {
    const res = await uploadReportEvidence(file)
    const url = res.data
    if (url) {
      reportEvidenceList.value.push(url)
      reportUploadList.value = [
        ...reportUploadList.value,
        { name: file.name, url }
      ]
      options?.onSuccess?.(res, file)
    } else {
      options?.onError?.(new Error('上传失败'))
    }
  } catch (error) {
    options?.onError?.(error)
  }
}

// 举报证据移除
const handleReportRemove = (file) => {
  if (!file) return
  const url = file.url || ''
  reportEvidenceList.value = reportEvidenceList.value.filter(item => item !== url)
  reportUploadList.value = reportUploadList.value.filter(item => item.url !== url)
}

// 拼接举报原因
const buildReportReason = () => {
  const reasonLabel = reportReasonOptions.find((item) => item.value === reportForm.reasonType)?.label || ''
  const detail = (reportForm.detail || '').trim()
  const context =
    reportForm.type === 'JOB'
      ? (job.value?.title ? `职位：${job.value.title}` : '')
      : (job.value?.companyName ? `企业：${job.value.companyName}` : '')
  return [reasonLabel, detail, context].filter(Boolean).join('；')
}

// 提交举报
const submitReportForm = async () => {
  if (!reportForm.type) {
    ElMessage.warning('请选择举报对象')
    return
  }
  if (!reportForm.reasonType) {
    ElMessage.warning('请选择举报原因')
    return
  }
  const reason = buildReportReason()
  if (!reason) {
    ElMessage.warning('请补充举报说明')
    return
  }
  let targetId = null
  if (reportForm.type === 'JOB') {
    targetId = job.value?.id
  } else if (reportForm.type === 'MERCHANT') {
    targetId = job.value?.publisherId
  }
  if (!targetId) {
    ElMessage.warning('未获取到举报对象')
    return
  }
  try {
    await ElMessageBox.confirm('确认提交举报吗？', '提交确认', {
      confirmButtonText: '确认提交',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch (error) {
    return
  }
  try {
    await submitReport({
      type: reportForm.type,
      targetId,
      reason,
      evidenceList: reportEvidenceList.value
    })
    ElMessage.success('举报已提交')
    reportDialogVisible.value = false
  } catch (error) {
    console.error('举报提交失败', error)
    ElMessage.error('举报提交失败，请稍后重试')
  }
}

onMounted(async () => {
  fetchResumeCompleteness()
  try {
    const res = await getPublicJobDetail(jobId);
    // 兼容处理：防止 data 包裹层级不同
    job.value = res.data || res;

    // 兜底展示：后端未返回学历/经验时，避免详情页出现空白文案
    if (!job.value.experience) job.value.experience = '经验不限';
    if (!job.value.degree) job.value.degree = '学历不限';

    // 查询当前用户对该职位的投递状态
    try {
      const statusRes = await getDeliveryStatus(jobId)
      const rawStatus = statusRes.data
      deliveryStatus.value = (rawStatus === null || rawStatus === undefined) ? null : normalizeStatus(rawStatus)
      hasApplied.value = deliveryStatus.value !== null
    } catch (e) {
      // 未投递或接口异常时不影响页面展示
      deliveryStatus.value = null
      hasApplied.value = false
    }
  } catch (err) {
    console.error("加载失败", err);
  }
});

// 点击投递
const handleApply = async () => {
  try {
    if (job.value.status !== 1) {
      ElMessage.warning('该职位已下架，暂时无法投递')
      return
    }
    await submitDelivery({ jobId: jobId })
    ElMessage.success('简历投递成功！')
    hasApplied.value = true // 更新按钮状态
    deliveryStatus.value = 0
  } catch (error) {
    // 如果后端抛出“重复投递”异常，通常在这里也会被捕获
    console.error(error)
  }
};
</script>

<style scoped>
.detail-page {
  position: relative;
  min-height: 100vh;
  padding: 28px 0 52px;
  background:
    radial-gradient(circle at 8% 8%, rgba(0, 113, 227, 0.14), transparent 34%),
    radial-gradient(circle at 92% 4%, rgba(125, 211, 252, 0.11), transparent 30%),
    radial-gradient(circle at 78% 80%, rgba(96, 165, 250, 0.08), transparent 32%),
    linear-gradient(180deg, #f9fafc 0%, #f4f6f9 72%, #f2f3f7 100%);
  overflow-x: hidden;
}

.detail-glow {
  position: absolute;
  pointer-events: none;
  filter: blur(2px);
  z-index: 0;
}

.detail-glow-left {
  width: min(42vw, 440px);
  height: min(42vw, 440px);
  top: -140px;
  left: -110px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.8) 0%, rgba(255, 255, 255, 0) 72%);
}

.detail-glow-right {
  width: min(36vw, 360px);
  height: min(36vw, 360px);
  top: 120px;
  right: -90px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(191, 219, 254, 0.42) 0%, rgba(191, 219, 254, 0) 72%);
}

.detail-shell {
  position: relative;
  z-index: 1;
  width: min(calc(100% - (var(--ui-shell-gutter) * 2)), var(--ui-main-shell-max-width));
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.hero-card {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(300px, 348px);
  align-items: start;
  gap: 24px;
  padding: 22px;
  border-radius: 26px;
  border: 1px solid rgba(255, 255, 255, 0.72);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.88), rgba(252, 253, 255, 0.76)),
    linear-gradient(140deg, rgba(10, 132, 255, 0.06), rgba(10, 132, 255, 0.02) 54%, rgba(191, 219, 254, 0.08));
  backdrop-filter: blur(18px);
  box-shadow:
    0 2px 6px rgba(15, 23, 42, 0.04),
    0 16px 38px rgba(15, 23, 42, 0.08);
}

.hero-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.hero-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
}

.hero-top-left {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.scene-tag {
  border-color: rgba(0, 113, 227, 0.25);
  color: #0071e3;
  background: rgba(0, 113, 227, 0.06);
}

.publish-time {
  font-size: 13px;
  color: #6e6e73;
}

.status-tag {
  border-radius: 999px;
  font-weight: 600;
}

.title-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 14px;
  flex-wrap: wrap;
}

.title-row h1 {
  margin: 0;
  flex: 1;
  min-width: 0;
  font-size: clamp(28px, 3.1vw, 38px);
  line-height: 1.2;
  letter-spacing: -0.02em;
  color: #1d1d1f;
  word-break: break-word;
}

.salary-pill {
  padding: 8px 14px;
  border-radius: 999px;
  font-size: 24px;
  font-weight: 700;
  color: #0a84ff;
  background: linear-gradient(135deg, rgba(10, 132, 255, 0.12) 0%, rgba(96, 165, 250, 0.08) 100%);
  white-space: nowrap;
  flex-shrink: 0;
}

.meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.meta-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border-radius: 999px;
  font-size: 13px;
  color: #4b5563;
  background: rgba(255, 255, 255, 0.66);
  border: 1px solid rgba(15, 23, 42, 0.08);
  max-width: 100%;
}

.meta-chip span {
  min-width: 0;
  word-break: break-word;
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.skill-tag {
  border-radius: 999px;
  color: #4b5563;
  border-color: rgba(17, 24, 39, 0.1);
  background: rgba(255, 255, 255, 0.78);
}

.hero-actions {
  position: relative;
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 10px;
  align-self: start;
  padding: 14px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(17, 24, 39, 0.08);
}

.hero-actions::before {
  content: '';
  position: absolute;
  inset: 0 auto auto 0;
  width: 100%;
  height: 2px;
  border-radius: 18px 18px 0 0;
  background: linear-gradient(90deg, rgba(0, 113, 227, 0.45), rgba(90, 200, 250, 0.26), rgba(191, 219, 254, 0.18));
}

.action-main-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.action-btn {
  width: 100%;
  height: 46px;
  display: inline-flex;
  justify-content: center;
  align-items: center;
  font-weight: 600;
}

.action-btn-primary {
  border-color: transparent;
  background: linear-gradient(135deg, #0071e3 0%, #2f8dff 100%);
}

.action-btn-secondary {
  border-color: rgba(0, 113, 227, 0.2);
  color: #0071e3;
  background: rgba(0, 113, 227, 0.05);
}

.risk-tip {
  margin-top: 4px;
  display: flex;
  align-items: flex-start;
  gap: 6px;
  padding: 8px 10px;
  border-radius: 10px;
  border: 1px solid rgba(245, 158, 11, 0.16);
  background: rgba(245, 158, 11, 0.08);
  font-size: 12px;
  color: #6e6e73;
  line-height: 1.45;
}

.report-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.resume-tip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 10px;
  border: 1px solid rgba(16, 185, 129, 0.18);
  background: rgba(16, 185, 129, 0.08);
  font-size: 12px;
  color: #065f46;
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(300px, 340px);
  align-items: start;
  gap: 20px;
}

.content-card {
  min-width: 0;
  border-radius: 22px;
  border: 1px solid var(--ui-border);
  background: rgba(255, 255, 255, 0.86);
  backdrop-filter: blur(10px);
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.06);
}

.content-card :deep(.el-card__body) {
  padding: 22px 24px;
}

.content-section h3 {
  position: relative;
  padding-left: 12px;
  margin: 0 0 12px;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: -0.01em;
  color: #1d1d1f;
}

.content-section h3::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  width: 4px;
  height: 14px;
  border-radius: 999px;
  transform: translateY(-50%);
  background: linear-gradient(180deg, rgba(0, 113, 227, 0.9), rgba(90, 200, 250, 0.8));
}

.rich-text {
  font-size: 15px;
  line-height: 1.9;
  color: #334155;
  word-break: break-word;
  overflow-wrap: anywhere;
}

.rich-text :deep(p) {
  margin: 0 0 10px;
}

.rich-text :deep(img),
.rich-text :deep(video),
.rich-text :deep(table) {
  max-width: 100%;
}

.rich-text :deep(pre) {
  overflow-x: auto;
}

.location-box {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 12px;
  color: #334155;
  background: rgba(0, 113, 227, 0.06);
}

.loc-icon {
  color: #0071e3;
}

.side-column {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
  align-self: start;
  position: sticky;
  top: 92px;
}

.side-card {
  border-radius: 18px;
  border: 1px solid rgba(17, 24, 39, 0.08);
  background: rgba(255, 255, 255, 0.88);
}

.side-card :deep(.el-card__body) {
  padding: 16px;
}

.hr-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.hr-name {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
}

.verified-icon {
  color: #0071e3;
}

.hr-title {
  margin-top: 2px;
  font-size: 12px;
  color: #6b7280;
}

.hr-info,
.comp-info,
.company-dialog-main {
  min-width: 0;
}

.side-chat-btn {
  margin-top: 14px;
  width: 100%;
}

.comp-header {
  display: flex;
  align-items: center;
  gap: 10px;
}

.comp-logo {
  width: 50px;
  height: 50px;
  border-radius: 14px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  object-fit: cover;
}

.comp-name {
  font-size: 16px;
  font-weight: 650;
  color: #111827;
}

.comp-props {
  margin-top: 3px;
  font-size: 12px;
  color: #6b7280;
}

.company-location {
  margin-top: 14px;
  padding: 10px 12px;
  border-radius: 12px;
  display: flex;
  align-items: flex-start;
  gap: 7px;
  color: #475569;
  background: rgba(10, 132, 255, 0.06);
  font-size: 13px;
  line-height: 1.5;
}

.company-location span {
  word-break: break-word;
}

.company-card {
  cursor: pointer;
}

.company-card:hover {
  border-color: rgba(0, 113, 227, 0.22);
  box-shadow: 0 12px 28px rgba(0, 113, 227, 0.09);
}

.company-cta {
  margin-top: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  border-radius: 10px;
  background: rgba(0, 113, 227, 0.08);
  color: #0071e3;
  font-size: 12px;
  font-weight: 500;
}

.report-tip {
  margin-top: 6px;
  font-size: 12px;
  color: #94a3b8;
}

.report-upload-tip {
  margin-top: 6px;
  font-size: 12px;
  color: #94a3b8;
}

.company-dialog-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.company-dialog-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  padding: 14px;
  border-radius: 14px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: linear-gradient(135deg, rgba(0, 113, 227, 0.07), rgba(90, 200, 250, 0.04));
}

.company-dialog-logo {
  width: 58px;
  height: 58px;
  object-fit: cover;
  border-radius: 14px;
  border: 1px solid rgba(15, 23, 42, 0.12);
}

.company-dialog-main h4 {
  margin: 0;
  font-size: 20px;
  letter-spacing: -0.01em;
  color: #111827;
}

.company-dialog-sub {
  margin: 4px 0 0;
  font-size: 13px;
  color: #6b7280;
}

.company-dialog-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.dialog-field {
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(255, 255, 255, 0.88);
}

.field-label {
  display: block;
  margin-bottom: 4px;
  font-size: 12px;
  color: #6b7280;
}

.field-value {
  font-size: 14px;
  color: #111827;
  line-height: 1.6;
  word-break: break-word;
}

.dialog-desc {
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(255, 255, 255, 0.88);
}

.dialog-section-title {
  margin: 0 0 6px;
  font-size: 13px;
  font-weight: 600;
  color: #374151;
}

.dialog-desc p {
  margin: 0;
  font-size: 14px;
  line-height: 1.7;
  color: #475569;
}

/* 弹窗内边距微调，保持与主页面卡片一致的留白风格 */
.company-dialog :deep(.el-dialog__body) {
  padding-top: 8px;
}

@media (min-width: 1500px) {
  .hero-card {
    grid-template-columns: minmax(0, 1.22fr) minmax(320px, 360px);
  }

  .content-grid {
    grid-template-columns: minmax(0, 1fr) minmax(320px, 360px);
  }
}

@media (max-width: 1120px) {
  .hero-card,
  .content-grid {
    grid-template-columns: 1fr;
  }

  .side-column {
    position: static;
    top: auto;
  }
}

@media (max-width: 920px) {
  .company-dialog-grid {
    grid-template-columns: 1fr;
  }

  .company-dialog-head {
    align-items: flex-start;
  }
}

@media (max-width: 760px) {
  .detail-page {
    padding: 18px 0 38px;
  }

  .detail-shell {
    width: min(calc(100% - 24px), var(--ui-main-shell-max-width));
    gap: 14px;
  }

  .hero-card {
    padding: 16px;
    border-radius: 20px;
  }

  .action-main-row {
    grid-template-columns: 1fr;
  }

  .content-card :deep(.el-card__body),
  .side-card :deep(.el-card__body) {
    padding: 14px;
  }

  .title-row h1 {
    font-size: 24px;
  }

  .salary-pill {
    font-size: 18px;
  }

  .meta-chip {
    font-size: 12px;
    padding: 7px 10px;
  }

  .report-actions {
    grid-template-columns: 1fr;
  }

  .content-card {
    border-radius: 18px;
  }

  .company-dialog-grid {
    grid-template-columns: 1fr;
  }
}
</style>
