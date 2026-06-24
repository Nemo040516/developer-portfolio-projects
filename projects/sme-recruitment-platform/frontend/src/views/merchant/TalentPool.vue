<!--
文件速览：
1. 文件职责：商家候选人库，负责搜索候选人、筛选条件、在线简历预览与主动沟通入口。
2. 页面入口：商家路由 `/merchant/talent`。
3. 关键结构：page-toolbar、filter-panel、候选人状态卡片、resumeDialogVisible。
4. 阅读建议：先看候选人状态映射与操作引导，再看卡片网格，最后看简历弹窗。
-->
<template>
  <div class="talent-page">
    <div class="talent-shell" v-loading="loading">
      <div class="page-toolbar adaptive-meta-row">
        <div>
          <div class="toolbar-title">候选人库</div>
          <div class="toolbar-desc">商家可在投递前主动沟通，发送“查看简历”请求</div>
        </div>
        <div class="toolbar-actions adaptive-filter-actions">
          <el-button @click="$router.push('/merchant/resumes')">简历处理台</el-button>
          <el-button type="primary" @click="refreshList">刷新推荐</el-button>
        </div>
      </div>

      <div class="filter-panel adaptive-filter-row">
        <div class="filter-fields">
          <el-input
            v-model="query.keyword"
            placeholder="搜索姓名/技能"
            clearable
            class="filter-input adaptive-filter-control adaptive-filter-control--lg"
            @clear="applyFilter"
          />
          <el-select v-model="query.expectJob" placeholder="期望职位" clearable class="filter-control adaptive-filter-control adaptive-filter-control--md" @change="applyFilter">
            <el-option label="前端开发" value="前端开发工程师" />
            <el-option label="Java 后端" value="Java 后端工程师" />
            <el-option label="运营专员" value="运营专员" />
          </el-select>
          <el-select v-model="query.city" placeholder="期望城市" clearable class="filter-control adaptive-filter-control adaptive-filter-control--md" @change="applyFilter">
            <el-option label="深圳" value="深圳" />
            <el-option label="广州" value="广州" />
            <el-option label="杭州" value="杭州" />
          </el-select>
        </div>
        <div class="filter-actions adaptive-filter-actions">
          <el-button type="primary" @click="applyFilter">筛选</el-button>
          <el-button @click="resetFilter">重置</el-button>
        </div>
      </div>

      <div v-if="filteredList.length > 0" class="talent-grid">
        <el-card
          v-for="item in filteredList"
          :key="item.userId"
          :class="['talent-card', `talent-card--${getAttachmentStatusMeta(item).tone}`]"
          shadow="hover"
        >
        <div class="card-header">
          <div class="candidate-info">
            <el-avatar :size="46" :src="item.avatar">{{ item.name.charAt(0) }}</el-avatar>
            <div>
              <div class="candidate-name">{{ item.name }}</div>
              <div class="candidate-meta">{{ item.degree }} · {{ item.workYears }} · {{ item.city }}</div>
            </div>
          </div>
          <el-tag :type="getAttachmentStatusMeta(item).tagType" size="small" effect="light" round>
            {{ getAttachmentStatusMeta(item).tagLabel }}
          </el-tag>
        </div>

        <div :class="['status-hint', `status-hint--${getAttachmentStatusMeta(item).tone}`]">
          <div class="status-hint__eyebrow">关系状态</div>
          <div class="status-hint__title">{{ getAttachmentStatusMeta(item).title }}</div>
          <div class="status-hint__desc">{{ getAttachmentStatusMeta(item).desc }}</div>
        </div>

        <div class="candidate-block">
          <div class="block-title">求职意向</div>
          <div class="block-value">{{ item.expectJob }} · {{ item.expectSalary }}</div>
        </div>

        <div class="candidate-block">
          <div class="block-title">技能关键词</div>
          <div class="skill-list">
            <el-tag v-for="skill in item.skills" :key="skill" size="small" type="info">
              {{ skill }}
            </el-tag>
          </div>
        </div>

        <div class="candidate-block">
          <div class="block-title">一句话亮点</div>
          <div class="block-value">{{ item.summary }}</div>
        </div>

        <div class="card-actions">
          <el-button size="small" type="primary" @click="startChat(item)">
            {{ getChatActionLabel(item) }}
          </el-button>
          <el-button size="small" plain @click="openOnlineResume(item)">查看在线简历</el-button>
          <el-button
            size="small"
            text
            :disabled="isRequestActionDisabled(item)"
            @click="requestAttachment(item)"
          >
            {{ getRequestActionLabel(item) }}
          </el-button>
        </div>
      </el-card>
      </div>
      <el-empty v-else-if="!loading" description="暂无候选人" />
    </div>

    <el-dialog v-model="resumeDialogVisible" title="在线简历" width="780px" class="resume-dialog">
      <div v-loading="resumeLoading">
        <div v-if="currentResume" class="resume-panel">
        <div class="resume-header">
          <el-avatar :size="56" :src="currentResume.avatar">{{ currentResume.name.charAt(0) }}</el-avatar>
          <div class="resume-base">
            <div class="resume-name">{{ currentResume.name }}</div>
            <div class="resume-meta">
              {{ formatGender(currentResume.gender) }} /
              {{ currentResume.age || '-' }}岁 /
              {{ currentResume.workYears || '-' }}
            </div>
          </div>
        </div>

        <div class="resume-layout">
          <div class="resume-left">
            <el-descriptions :column="1" border class="resume-desc">
              <el-descriptions-item label="手机号">{{ currentResume.phone || '-' }}</el-descriptions-item>
              <el-descriptions-item label="邮箱">{{ currentResume.email || '-' }}</el-descriptions-item>
              <el-descriptions-item label="当前身份">{{ formatIdentity(currentResume.currentIdentity) }}</el-descriptions-item>
              <el-descriptions-item label="求职状态">{{ currentResume.currentStatus || '-' }}</el-descriptions-item>
              <el-descriptions-item label="期望城市">{{ currentResume.city || '-' }}</el-descriptions-item>
              <el-descriptions-item label="期望职位">{{ currentResume.expectJob || '-' }}</el-descriptions-item>
              <el-descriptions-item label="期望薪资">{{ currentResume.expectSalary || '-' }}</el-descriptions-item>
              <el-descriptions-item label="毕业院校">{{ currentResume.collage || currentResume.college || '-' }}</el-descriptions-item>
              <el-descriptions-item label="专业">{{ currentResume.major || '-' }}</el-descriptions-item>
              <el-descriptions-item label="毕业年份">{{ currentResume.gradYear || '-' }}</el-descriptions-item>
            </el-descriptions>
          </div>
          <div class="resume-right">
            <el-collapse v-model="resumeCollapseActive" class="resume-collapse">
              <el-collapse-item name="skills">
                <template #title>技能标签</template>
                <div class="resume-block">
                  <div class="resume-tags">
                    <el-tag v-for="tag in currentResume.skills" :key="tag" size="small" type="info">
                      {{ tag }}
                    </el-tag>
                    <span v-if="currentResume.skills.length === 0" class="resume-empty">暂无</span>
                  </div>
                </div>
              </el-collapse-item>

              <el-collapse-item name="advantage">
                <template #title>个人优势</template>
                <div class="resume-block">
                  <div class="resume-text">{{ currentResume.summary || '暂无' }}</div>
                </div>
              </el-collapse-item>

              <el-collapse-item name="education">
                <template #title>教育经历</template>
                <div class="resume-block">
                  <div v-if="currentResume.educationList.length" class="resume-list">
                    <div v-for="(edu, index) in currentResume.educationList" :key="index" class="resume-list-item">
                      <div class="resume-item-title">{{ buildEduTitle(edu) }}</div>
                      <div class="resume-item-meta">{{ formatRange(edu.timeRange) }}</div>
                    </div>
                  </div>
                  <div v-else class="resume-empty">暂无</div>
                </div>
              </el-collapse-item>

              <el-collapse-item name="experience">
                <template #title>工作/实习经历</template>
                <div class="resume-block">
                  <div v-if="currentResume.experienceList.length" class="resume-list">
                    <div v-for="(exp, index) in currentResume.experienceList" :key="index" class="resume-list-item">
                      <div class="resume-item-title">{{ buildExpTitle(exp) }}</div>
                      <div class="resume-item-text">{{ exp.content || '暂无描述' }}</div>
                    </div>
                  </div>
                  <div v-else class="resume-empty">暂无</div>
                </div>
              </el-collapse-item>

              <el-collapse-item name="project">
                <template #title>项目经历</template>
                <div class="resume-block">
                  <div v-if="currentResume.projectList.length" class="resume-list">
                    <div v-for="(proj, index) in currentResume.projectList" :key="index" class="resume-list-item">
                      <div class="resume-item-title">{{ buildProjectTitle(proj) }}</div>
                      <div class="resume-item-meta">{{ formatRange(proj.timeRange) }}</div>
                      <div class="resume-item-text">{{ proj.description || '暂无描述' }}</div>
                      <div v-if="proj.techStack" class="resume-item-meta">技术栈：{{ proj.techStack }}</div>
                    </div>
                  </div>
                  <div v-else class="resume-empty">暂无</div>
                </div>
              </el-collapse-item>

              <el-collapse-item name="certificate">
                <template #title>证书</template>
                <div class="resume-block">
                  <div v-if="currentResume.certificateList.length" class="resume-list">
                    <div v-for="(cert, index) in currentResume.certificateList" :key="index" class="resume-list-item">
                      <div class="resume-item-title">{{ buildCertTitle(cert) }}</div>
                      <div class="resume-item-meta">{{ cert.date || '-' }}</div>
                    </div>
                  </div>
                  <div v-else class="resume-empty">暂无</div>
                </div>
              </el-collapse-item>

              <el-collapse-item name="award">
                <template #title>奖项</template>
                <div class="resume-block">
                  <div v-if="currentResume.awardList.length" class="resume-list">
                    <div v-for="(award, index) in currentResume.awardList" :key="index" class="resume-list-item">
                      <div class="resume-item-title">{{ buildAwardTitle(award) }}</div>
                      <div class="resume-item-meta">{{ award.date || '-' }}</div>
                      <div class="resume-item-text">{{ award.description || '暂无描述' }}</div>
                    </div>
                  </div>
                  <div v-else class="resume-empty">暂无</div>
                </div>
              </el-collapse-item>

              <el-collapse-item name="language">
                <template #title>语言能力</template>
                <div class="resume-block">
                  <div v-if="currentResume.languageList.length" class="resume-list">
                    <div v-for="(lang, index) in currentResume.languageList" :key="index" class="resume-list-item">
                      <div class="resume-item-title">{{ buildLangTitle(lang) }}</div>
                      <div class="resume-item-meta">{{ lang.score || '-' }}</div>
                    </div>
                  </div>
                  <div v-else class="resume-empty">暂无</div>
                </div>
              </el-collapse-item>
            </el-collapse>
          </div>
        </div>
        </div>
        <el-empty v-else-if="!resumeLoading" description="暂无简历内容" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'
import { getResumeAttachmentPermissionStatus } from '@/api/attachment'
import { getTalentPoolList, getTalentDetail } from '@/api/talent'

const router = useRouter()
const userStore = useUserStore()

// 候选人列表（来自后端）
const talentList = ref([])
const loading = ref(false)

const query = ref({
  keyword: '',
  expectJob: '',
  city: '',
  current: 1,
  size: 24
})

const resumeDialogVisible = ref(false)
const currentResume = ref(null)
const resumeCollapseActive = ref(['skills', 'advantage', 'education', 'experience', 'project'])
const permissionMap = ref({})
const resumeLoading = ref(false)
const permissionLoading = ref(false)

const ATTACHMENT_STATUS_META = {
  CHECKING: {
    tagType: 'info',
    tagLabel: '识别中',
    tone: 'checking',
    title: '正在识别双方关系',
    desc: '系统会自动判断是否已投递，以及附件简历当前是否可申请。'
  },
  NO_DELIVERY: {
    tagType: 'info',
    tagLabel: '未投递',
    tone: 'disconnected',
    title: '尚未建立投递关系',
    desc: '建议先打招呼并引导候选人投递，再申请查看附件简历。'
  },
  NONE: {
    tagType: 'primary',
    tagLabel: '已投递',
    tone: 'ready',
    title: '已建立投递关系',
    desc: '对方已投递相关岗位，可在沟通页发起附件简历申请。'
  },
  PENDING: {
    tagType: 'warning',
    tagLabel: '待授权',
    tone: 'pending',
    title: '附件申请待处理',
    desc: '授权请求已经发送，建议先保持沟通，等待求职者确认。'
  },
  GRANTED: {
    tagType: 'success',
    tagLabel: '已授权',
    tone: 'granted',
    title: '附件简历已授权',
    desc: '你可以直接进入沟通页继续推进，并查看附件简历。'
  },
  REJECTED: {
    tagType: 'danger',
    tagLabel: '已拒绝',
    tone: 'rejected',
    title: '附件申请未通过',
    desc: '建议先补充岗位亮点与沟通信息，再决定是否重新申请。'
  },
  UNKNOWN: {
    tagType: 'info',
    tagLabel: '待刷新',
    tone: 'unknown',
    title: '状态暂不可用',
    desc: '当前未能识别授权状态，可刷新推荐后重新校验。'
  }
}

const filteredList = computed(() => {
  const key = query.value.keyword.trim()
  return talentList.value.filter((item) => {
    const matchKeyword = !key || item.name.includes(key) || item.skills.join(',').includes(key)
    const matchJob = !query.value.expectJob || item.expectJob === query.value.expectJob
    const matchCity = !query.value.city || item.city === query.value.city
    return matchKeyword && matchJob && matchCity
  })
})

const normalizeSkills = (skills) => {
  if (Array.isArray(skills)) {
    return skills.filter(Boolean)
  }
  if (typeof skills !== 'string') {
    return []
  }
  const trimmed = skills.trim()
  if (trimmed.startsWith('[') && trimmed.endsWith(']')) {
    try {
      const parsed = JSON.parse(trimmed)
      if (Array.isArray(parsed)) {
        return parsed.map((item) => String(item || '').trim()).filter(Boolean)
      }
    } catch (error) {
      // JSON 解析失败时回退到分隔符拆分
    }
  }
  return trimmed
    .split(/[,，、]/)
    .map((item) => item.trim())
    .filter(Boolean)
}

const isNonEmptyValue = (value) => {
  if (value === null || value === undefined) return false
  if (Array.isArray(value)) return value.length > 0
  if (typeof value === 'object') return Object.values(value).some(isNonEmptyValue)
  return String(value).trim() !== ''
}

const isValidResumeItem = (item) => {
  if (!item || typeof item !== 'object') return false
  return Object.values(item).some(isNonEmptyValue)
}

const normalizeCandidate = (item) => {
  const name = item.name || item.realName || item.nickname || item.username || '候选人'
  const skills = normalizeSkills(item.skills)
  return {
    userId: item.userId,
    name,
    avatar: item.avatar || '',
    gender: item.gender ?? null,
    age: item.age ?? null,
    phone: item.phone || '',
    email: item.email || '',
    currentIdentity: item.currentIdentity || '',
    currentStatus: item.currentStatus || '',
    degree: item.degree || '未知',
    workYears: item.workYears || '暂无',
    collage: item.collage || item.college || '',
    major: item.major || '',
    gradYear: item.gradYear || null,
    city: item.city || item.expectCity || '不限',
    expectJob: item.expectJob || '',
    expectSalary: item.expectSalary || '面议',
    skills,
    summary: item.summary || item.advantage || '暂无',
    educationJson: item.educationJson || '',
    experienceJson: item.experienceJson || '',
    projectJson: item.projectJson || '',
    certificateJson: item.certificateJson || '',
    awardJson: item.awardJson || '',
    languageJson: item.languageJson || ''
  }
}

const shuffleList = (list) => {
  for (let i = list.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    const temp = list[i]
    list[i] = list[j]
    list[j] = temp
  }
}

// 解析结构化简历字段（JSON 字符串）
const parseJsonList = (json) => {
  if (!json) return []
  if (Array.isArray(json)) return json.filter(isValidResumeItem)
  try {
    const data = JSON.parse(json)
    return Array.isArray(data) ? data.filter(isValidResumeItem) : []
  } catch (error) {
    return []
  }
}

const formatRange = (range) => {
  if (!Array.isArray(range) || range.length === 0) return '-'
  if (range.length === 1) return range[0] || '-'
  return `${range[0] || '-'} 至 ${range[1] || '-'}`
}

const buildEduTitle = (edu) => {
  if (!edu) return '未填写'
  const parts = [edu.school, edu.major, edu.degree].filter(Boolean)
  return parts.length > 0 ? parts.join(' · ') : '未填写'
}

const buildExpTitle = (exp) => {
  if (!exp) return '未填写'
  const parts = [exp.company, exp.position].filter(Boolean)
  return parts.length > 0 ? parts.join(' · ') : '未填写'
}

const buildProjectTitle = (proj) => {
  if (!proj) return '未填写'
  const parts = [proj.name, proj.role].filter(Boolean)
  return parts.length > 0 ? parts.join(' · ') : '未填写'
}

const buildCertTitle = (cert) => {
  if (!cert) return '未填写'
  const parts = [cert.name, cert.issuer].filter(Boolean)
  return parts.length > 0 ? parts.join(' · ') : '未填写'
}

const buildAwardTitle = (award) => {
  if (!award) return '未填写'
  const parts = [award.name, award.level].filter(Boolean)
  return parts.length > 0 ? parts.join(' · ') : '未填写'
}

const buildLangTitle = (lang) => {
  if (!lang) return '未填写'
  const parts = [lang.name, lang.level].filter(Boolean)
  return parts.length > 0 ? parts.join(' · ') : '未填写'
}

const formatGender = (gender) => {
  if (gender === 1) return '男'
  if (gender === 2) return '女'
  return '保密'
}

const formatIdentity = (value) => {
  if (value === 'STUDENT') return '在校生'
  if (value === 'FRESH_GRAD') return '应届生'
  if (value === 'WORKER') return '职场人士'
  return value || '-'
}

const fetchTalentList = async (options = {}) => {
  loading.value = true
  const params = {
    current: query.value.current,
    size: query.value.size,
    keyword: query.value.keyword || undefined,
    expectJob: query.value.expectJob || undefined,
    city: query.value.city || undefined
  }
  try {
    const res = await getTalentPoolList(params)
    const records = res?.data?.records || res?.data || []
    const list = records.map(normalizeCandidate)
    const shouldShuffle = options.shuffle === true
    if (shouldShuffle) {
      shuffleList(list)
    }
    talentList.value = list
    if (options.showTip) {
      ElMessage.success('已刷新候选人推荐')
    }
  } catch (error) {
    console.error('获取候选人失败:', error)
    ElMessage.error('获取候选人失败')
  } finally {
    loading.value = false
    await loadAttachmentPermissions()
  }
}

const applyFilter = () => {
  query.value.current = 1
  fetchTalentList()
}

const resetFilter = () => {
  query.value.keyword = ''
  query.value.expectJob = ''
  query.value.city = ''
  query.value.current = 1
  fetchTalentList()
}

const refreshList = () => {
  fetchTalentList({ shuffle: true, showTip: true })
}

// 打开在线简历（优先从后端读取最新数据）
const openOnlineResume = async (item) => {
  resumeDialogVisible.value = true
  resumeLoading.value = true
  currentResume.value = null
  try {
    const res = await getTalentDetail(item.userId)
    const record = normalizeCandidate(res?.data || item)
    currentResume.value = {
      ...record,
      educationList: parseJsonList(record.educationJson),
      experienceList: parseJsonList(record.experienceJson),
      projectList: parseJsonList(record.projectJson),
      certificateList: parseJsonList(record.certificateJson),
      awardList: parseJsonList(record.awardJson),
      languageList: parseJsonList(record.languageJson)
    }
  } catch (error) {
    console.error('获取在线简历失败:', error)
    ElMessage.error('获取在线简历失败')
    const fallback = normalizeCandidate(item)
    currentResume.value = {
      ...fallback,
      educationList: parseJsonList(fallback.educationJson),
      experienceList: parseJsonList(fallback.experienceJson),
      projectList: parseJsonList(fallback.projectJson),
      certificateList: parseJsonList(fallback.certificateJson),
      awardList: parseJsonList(fallback.awardJson),
      languageList: parseJsonList(fallback.languageJson)
    }
  } finally {
    resumeLoading.value = false
  }
}

// 发起沟通：带上候选人与职位信息，并附带打招呼话术
const startChat = (item, options = {}) => {
  const companyName = userStore.userInfo?.companyName || '贵公司'
  const initMsg = options.initMsg || `您好，我们是${companyName}，想了解您的求职意向，方便查看您的简历吗？`

  openChatWithCandidate(item, {
    initMsg,
    ...options
  })
}

// 从 Token 中解析用户 ID（用于授权记录的标识）
const getUserIdFromToken = () => {
  const token = userStore.token
  if (!token) return 0
  const parts = token.split('.')
  if (parts.length < 2) return 0
  try {
    let base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/')
    const pad = base64.length % 4
    if (pad) {
      base64 += '='.repeat(4 - pad)
    }
    const payload = JSON.parse(atob(base64))
    return Number(payload.sub || 0)
  } catch (error) {
    return 0
  }
}

const getMerchantId = () => {
  return Number(userStore.userInfo?.id || getUserIdFromToken() || 0)
}

const getAttachmentPermissionState = (item) => {
  const currentStatus = permissionMap.value[item.userId]
  if (currentStatus) {
    return currentStatus
  }
  return permissionLoading.value ? 'CHECKING' : 'UNKNOWN'
}

const getAttachmentStatusMeta = (item) => {
  const state = getAttachmentPermissionState(item)
  return ATTACHMENT_STATUS_META[state] || ATTACHMENT_STATUS_META.UNKNOWN
}

const isAttachmentPending = (item) => {
  return getAttachmentPermissionState(item) === 'PENDING'
}

const getChatActionLabel = (item) => {
  const state = getAttachmentPermissionState(item)
  if (state === 'NO_DELIVERY') {
    return '先沟通'
  }
  if (state === 'GRANTED') {
    return '继续沟通'
  }
  return '打招呼'
}

const getRequestActionLabel = (item) => {
  const state = getAttachmentPermissionState(item)
  if (state === 'GRANTED') return '已获授权'
  if (state === 'PENDING') return '等待同意'
  if (state === 'NO_DELIVERY') return '先建立投递'
  if (state === 'REJECTED') return '再次申请'
  if (state === 'CHECKING') return '状态识别中'
  if (state === 'UNKNOWN') return '稍后重试'
  return '申请附件'
}

const isRequestActionDisabled = (item) => {
  const state = getAttachmentPermissionState(item)
  return ['GRANTED', 'PENDING', 'CHECKING', 'UNKNOWN'].includes(state)
}

const openChatWithCandidate = (item, extraQuery = {}) => {
  router.push({
    path: '/merchant/chat',
    query: {
      targetId: item.userId,
      targetName: item.name,
      jobTitle: item.expectJob,
      jobId: item.jobId || '',
      ...extraQuery
    }
  })
}

// 申请查看附件简历（发送沟通请求）
const requestAttachment = async (item) => {
  const state = getAttachmentPermissionState(item)

  if (state === 'CHECKING') {
    ElMessage.info('正在识别双方关系，请稍后再试')
    return
  }

  if (state === 'UNKNOWN') {
    ElMessage.info('当前状态暂不可用，请刷新推荐后重试')
    return
  }

  if (state === 'GRANTED') {
    ElMessage.success('对方已授权附件简历，可直接进入沟通页继续查看')
    startChat(item)
    return
  }

  if (isAttachmentPending(item)) {
    ElMessage.info('附件申请已提交，请等待对方同意')
    return
  }

  if (state === 'NO_DELIVERY') {
    ElMessage.info('当前未建立投递关系，建议先沟通并引导候选人投递')
    startChat(item, {
      initMsg: `您好，我们是${userStore.userInfo?.companyName || '贵公司'}，觉得您的背景与我们的岗位比较匹配，欢迎投递后进一步沟通。`
    })
    return
  }

  if (state === 'REJECTED') {
    ElMessage.info('对方暂未同意附件授权，建议补充沟通后再次申请')
  } else {
    ElMessage.info('已进入沟通，请选择岗位后发起附件申请')
  }

  openChatWithCandidate(item, {
    autoRequest: 1
  })
}

const loadAttachmentPermissions = async () => {
  const merchantId = getMerchantId()
  if (!merchantId) return
  if (talentList.value.length === 0) {
    permissionMap.value = {}
    return
  }
  permissionLoading.value = true
  const map = {}
  try {
    for (const item of talentList.value) {
      try {
        const res = await getResumeAttachmentPermissionStatus(
          {
            applicantId: item.userId,
            merchantId
          },
          {
            // 候选人库会批量探测权限状态，未投递时静默处理，再转成卡片上的引导状态。
            skipBusinessErrorMessage: true,
            skipHttpErrorMessage: true
          }
        )
        map[item.userId] = res?.data?.status || 'NONE'
      } catch (error) {
        map[item.userId] = error?.code === 403 ? 'NO_DELIVERY' : 'UNKNOWN'
      }
    }
  } finally {
    permissionLoading.value = false
  }
  permissionMap.value = map
}

onMounted(() => {
  fetchTalentList()
})
</script>

<style scoped>
.talent-page {
  min-height: 100vh;
}

.talent-shell {
  background: #ffffff;
  border-radius: 16px;
  border: 1px solid #e2e8f0;
  padding: 16px;
  box-shadow: 0 6px 16px rgba(15, 23, 42, 0.04);
  min-height: calc(100vh - 140px);
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-toolbar {
  gap: 12px;
}

.toolbar-title {
  font-size: 18px;
  font-weight: 600;
  color: #1f2937;
}

.toolbar-desc {
  font-size: 12px;
  color: #64748b;
  margin-top: 4px;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.filter-panel {
  gap: 12px;
  padding: 12px;
  border-radius: 12px;
  border: 1px solid #eef2f7;
  background: #f8fafc;
}

.filter-fields {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  flex: 1;
  min-width: 0;
}

.filter-control {
  width: auto;
}

.filter-input {
  width: auto;
}

.filter-actions {
  gap: 8px;
}

.talent-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
  align-items: stretch;
}

.talent-card {
  position: relative;
  overflow: hidden;
  border-radius: 16px;
  border: 1px solid #e5e7eb;
  height: 100%;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(248, 250, 252, 0.96)),
    #ffffff;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}

.talent-card::before {
  content: '';
  position: absolute;
  inset: 0 0 auto;
  height: 3px;
  background: linear-gradient(90deg, rgba(148, 163, 184, 0.35), rgba(148, 163, 184, 0.08));
}

.talent-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 16px 36px rgba(15, 23, 42, 0.08);
}

.talent-card--checking::before,
.talent-card--unknown::before {
  background: linear-gradient(90deg, rgba(148, 163, 184, 0.5), rgba(148, 163, 184, 0.12));
}

.talent-card--disconnected::before {
  background: linear-gradient(90deg, rgba(59, 130, 246, 0.45), rgba(148, 163, 184, 0.12));
}

.talent-card--ready::before {
  background: linear-gradient(90deg, rgba(37, 99, 235, 0.75), rgba(96, 165, 250, 0.16));
}

.talent-card--pending::before {
  background: linear-gradient(90deg, rgba(245, 158, 11, 0.75), rgba(251, 191, 36, 0.16));
}

.talent-card--granted::before {
  background: linear-gradient(90deg, rgba(34, 197, 94, 0.72), rgba(74, 222, 128, 0.16));
}

.talent-card--rejected::before {
  background: linear-gradient(90deg, rgba(239, 68, 68, 0.75), rgba(248, 113, 113, 0.16));
}

.talent-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  height: 100%;
  gap: 12px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.candidate-info {
  display: flex;
  gap: 12px;
  align-items: center;
}

.candidate-name {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
}

.candidate-meta {
  font-size: 12px;
  color: #94a3b8;
}

.status-hint {
  position: relative;
  padding: 12px 14px;
  border-radius: 14px;
  border: 1px solid #e2e8f0;
  background: linear-gradient(135deg, rgba(248, 250, 252, 0.95), rgba(255, 255, 255, 0.92));
  overflow: hidden;
}

.status-hint::after {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: 4px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.4);
}

.status-hint--checking,
.status-hint--unknown {
  background: linear-gradient(135deg, rgba(248, 250, 252, 0.96), rgba(255, 255, 255, 0.92));
}

.status-hint--checking::after,
.status-hint--unknown::after {
  background: rgba(148, 163, 184, 0.55);
}

.status-hint--disconnected {
  border-color: rgba(191, 219, 254, 0.9);
  background: linear-gradient(135deg, rgba(239, 246, 255, 0.96), rgba(255, 255, 255, 0.9));
}

.status-hint--disconnected::after {
  background: rgba(59, 130, 246, 0.7);
}

.status-hint--ready {
  border-color: rgba(191, 219, 254, 0.92);
  background: linear-gradient(135deg, rgba(239, 246, 255, 0.96), rgba(248, 250, 252, 0.92));
}

.status-hint--ready::after {
  background: rgba(37, 99, 235, 0.78);
}

.status-hint--pending {
  border-color: rgba(253, 230, 138, 0.92);
  background: linear-gradient(135deg, rgba(255, 251, 235, 0.98), rgba(255, 255, 255, 0.92));
}

.status-hint--pending::after {
  background: rgba(245, 158, 11, 0.74);
}

.status-hint--granted {
  border-color: rgba(187, 247, 208, 0.92);
  background: linear-gradient(135deg, rgba(240, 253, 244, 0.98), rgba(255, 255, 255, 0.92));
}

.status-hint--granted::after {
  background: rgba(34, 197, 94, 0.74);
}

.status-hint--rejected {
  border-color: rgba(254, 202, 202, 0.92);
  background: linear-gradient(135deg, rgba(254, 242, 242, 0.98), rgba(255, 255, 255, 0.92));
}

.status-hint--rejected::after {
  background: rgba(239, 68, 68, 0.74);
}

.status-hint__eyebrow {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
  color: #94a3b8;
  text-transform: uppercase;
}

.status-hint__title {
  margin-top: 6px;
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
}

.status-hint__desc {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.6;
  color: #64748b;
}

.candidate-block {
  margin-bottom: 0;
}

.block-title {
  font-size: 12px;
  color: #94a3b8;
  margin-bottom: 4px;
}

.block-value {
  color: #1f2937;
  font-size: 14px;
}

.skill-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.card-actions {
  display: flex;
  gap: 10px;
  margin-top: 8px;
  flex-wrap: wrap;
  margin-top: auto;
  padding-top: 8px;
  border-top: 1px solid #eef2f7;
}

.resume-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.resume-header {
  display: flex;
  align-items: center;
  gap: 16px;
}

.resume-name {
  font-size: 18px;
  font-weight: 600;
  color: #1f2937;
}

.resume-meta {
  font-size: 13px;
  color: #94a3b8;
}

.resume-desc {
  margin-top: 8px;
}

.resume-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.1fr);
  gap: 16px;
}

.resume-left {
  min-width: 0;
}

.resume-right {
  min-width: 0;
}

.resume-block {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.resume-text {
  color: #475569;
  line-height: 1.6;
}

.resume-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.resume-empty {
  color: #94a3b8;
  font-size: 12px;
}

.resume-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.resume-list-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 8px 10px;
  background: #ffffff;
  border-radius: 10px;
  border: 1px solid #eef2f7;
}

.resume-item-title {
  font-weight: 600;
  color: #111827;
}

.resume-item-meta {
  color: #6b7280;
  font-size: 12px;
}

.resume-item-text {
  color: #4b5563;
  font-size: 13px;
  line-height: 1.5;
}

.resume-dialog :deep(.el-dialog__body) {
  padding: 12px 20px 20px;
  max-height: 70vh;
  overflow: auto;
}

@media (max-width: 1024px) {
  .toolbar-actions {
    width: 100%;
    margin-left: 0;
  }

  .filter-control,
  .filter-input {
    width: 100%;
  }
  .filter-panel {
    align-items: stretch;
  }
  .resume-layout {
    grid-template-columns: 1fr;
  }
}
</style>
