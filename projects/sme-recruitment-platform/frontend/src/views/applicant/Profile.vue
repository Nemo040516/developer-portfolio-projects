<!--
文件速览：
1. 文件职责：求职者账号中心，负责账号资料、隐私设置、账号安全、简历概览与身份认证。
2. 页面入口：求职者路由 `/applicant/profile`。
3. 关键结构：profile-hero、hero-highlights、profile-grid、focus-card、verifyDialogVisible。
4. 阅读建议：先看顶部账号概览与重点指标，再看右侧“当前重点”，最后看密码与认证弹窗逻辑。
-->
<template>
  <div class="profile-container">
    <div class="profile-hero">
      <div class="hero-left">
        <el-upload
          class="hero-avatar-uploader"
          :show-file-list="false"
          :before-upload="beforeAvatarUpload"
          :http-request="handleAvatarUpload"
        >
          <div class="hero-avatar-shell">
            <el-avatar :size="72" :src="avatarUrl" class="hero-avatar" />
            <div class="hero-avatar-mask">更换头像</div>
          </div>
        </el-upload>
        <div class="hero-text">
          <div class="hero-title">
            <span class="hero-name">{{ displayName }}</span>
            <el-tag size="small" type="info" effect="plain" class="hero-tag">{{ roleLabel }}</el-tag>
          </div>
          <div class="hero-meta">账号：{{ userInfo.username || '-' }}</div>
          <div class="hero-meta">资料用于登录与展示，求职内容在在线简历维护</div>
          <div class="hero-highlights">
            <div class="hero-highlight">
              <span class="hero-highlight-label">简历完善度</span>
              <strong>{{ resumeStats.resumeCompleteness || 0 }}%</strong>
            </div>
            <div class="hero-highlight">
              <span class="hero-highlight-label">已投递</span>
              <strong>{{ resumeStats.appliedCount || 0 }}</strong>
            </div>
            <div class="hero-highlight">
              <span class="hero-highlight-label">面试邀约</span>
              <strong>{{ resumeStats.interviewCount || 0 }}</strong>
            </div>
          </div>
        </div>
      </div>
      <div class="hero-actions">
        <el-button type="primary" @click="$router.push('/applicant/resume')">在线简历</el-button>
        <el-button @click="$router.push('/applicant/applications')">投递记录</el-button>
        <el-button @click="$router.push('/applicant/interviews')">面试日程</el-button>
      </div>
      <div class="hero-badges">
        <el-tag size="small" type="primary" effect="plain">账号完成度 {{ accountCompleteness }}%</el-tag>
        <el-tag size="small" :type="verifyStatusTag.type" effect="plain">认证：{{ verifyStatusTag.label }}</el-tag>
        <el-tag size="small" type="info" effect="plain">隐私：{{ privacyLabel }}</el-tag>
      </div>
    </div>

    <el-alert
      v-if="statusBanner.visible"
      class="status-banner"
      :type="statusBanner.type"
      :closable="true"
      show-icon
      @close="statusBanner.visible = false"
      :title="statusBanner.message"
    />

    <div class="profile-grid">
      <div class="left-column">
        <el-card shadow="hover" class="profile-card">
          <template #header>
            <div class="card-header">
              <span>账号资料</span>
              <span class="card-subtitle">用于账号联系与页面展示</span>
            </div>
          </template>
          <div class="account-summary">
            <div class="account-info">
              <div class="account-name">{{ displayName }}</div>
              <div class="account-meta">账号：{{ userInfo.username || '-' }}</div>
            </div>
          </div>
          <el-divider />
          <el-form ref="profileFormRef" :model="profileForm" :rules="profileRules" label-width="88px">
            <el-form-item label="用户名">
              <el-input :value="userInfo.username || '-'" disabled />
            </el-form-item>
            <el-form-item label="昵称" prop="nickname">
              <el-input v-model="profileForm.nickname" placeholder="设置对外展示昵称" />
            </el-form-item>
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="profileForm.phone" placeholder="用于账号联系" />
            </el-form-item>
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="profileForm.email" placeholder="用于账号联系" />
            </el-form-item>
          </el-form>
          <div class="hint-text">求职内容（真实姓名、经历等）请在在线简历维护。</div>
          <div class="form-actions">
            <el-button type="primary" :loading="saving" @click="saveProfile">保存账号资料</el-button>
            <el-button @click="resetProfileForm">重置</el-button>
          </div>
        </el-card>

        <el-card shadow="hover" class="profile-card" v-loading="privacyLoading">
          <template #header>
            <div class="card-header">
              <span>隐私与展示</span>
              <span class="card-subtitle">控制联系方式的可见范围</span>
            </div>
          </template>
          <div class="privacy-section">
            <div class="privacy-row">
              <div class="privacy-label">联系方式可见范围</div>
              <el-radio-group v-model="privacyForm.contactVisibility" class="privacy-options">
                <el-radio value="PUBLIC">
                  <div class="privacy-option">
                    <div class="privacy-option-title">公开展示</div>
                    <div class="privacy-option-desc">企业可直接看到手机号与邮箱</div>
                  </div>
                </el-radio>
                <el-radio value="DELIVERY">
                  <div class="privacy-option">
                    <div class="privacy-option-title">仅投递企业</div>
                    <div class="privacy-option-desc">仅向已投递的企业开放</div>
                  </div>
                </el-radio>
                <el-radio value="AUTH">
                  <div class="privacy-option">
                    <div class="privacy-option-title">仅授权企业</div>
                    <div class="privacy-option-desc">需主动授权后可查看</div>
                  </div>
                </el-radio>
              </el-radio-group>
            </div>
            <div class="privacy-tip">更严格的设置会提升隐私保护，但可能影响企业主动联系。</div>
            <div class="form-actions">
              <el-button type="primary" :loading="privacySaving" @click="savePrivacySettings">保存隐私设置</el-button>
            </div>
          </div>
        </el-card>

        <el-card shadow="hover" class="profile-card">
          <template #header>
            <div class="card-header">
              <span>账号安全</span>
              <span class="card-subtitle">保护账号安全，增强可信度</span>
            </div>
          </template>
          <div class="security-section" v-loading="loginLoading">
            <div class="security-item">
              <div>
                <div class="security-title">密码管理</div>
                <div class="security-desc">定期更新密码，保障账号安全</div>
              </div>
              <el-button type="primary" plain @click="passwordDialogVisible = true">修改密码</el-button>
            </div>
            <el-divider />
            <div class="security-item">
              <div>
                <div class="security-title">最近登录记录</div>
                <div class="security-desc">展示最近 3 次登录信息</div>
              </div>
              <el-button size="small" text @click="loadLoginLogs">刷新</el-button>
            </div>
            <div v-if="loginLogs.length" class="login-list">
              <div v-for="(log, index) in loginLogs" :key="index" class="login-item">
                <div class="login-meta">{{ log.time }}</div>
                <div class="login-info">{{ log.device }} · {{ log.ip }}</div>
              </div>
            </div>
            <div v-else class="login-empty">暂无登录记录</div>
          </div>
        </el-card>
      </div>

      <div class="right-column">
        <el-card shadow="hover" class="profile-card">
          <template #header>
            <div class="card-header">
              <span>简历概览</span>
              <span class="card-subtitle">展示简历状态与投递趋势</span>
            </div>
          </template>
          <div class="resume-overview">
            <div class="overview-row">
              <span class="overview-label">简历完善度</span>
              <el-progress :percentage="resumeStats.resumeCompleteness || 0" :stroke-width="10" />
            </div>
            <div class="overview-row">
              <span class="overview-label">最近更新</span>
              <span class="overview-value">{{ formatDateTime(resumeStats.resumeUpdateTime) }}</span>
            </div>
            <div class="overview-row">
              <span class="overview-label">附件简历</span>
              <span class="overview-value">{{ attachmentName || '未上传' }}</span>
            </div>
          </div>
          <div class="resume-actions">
            <el-button type="primary" @click="$router.push('/applicant/resume')">去完善简历</el-button>
            <el-button @click="$router.push('/applicant/applications')">查看投递</el-button>
          </div>
        </el-card>

        <el-card shadow="hover" class="profile-card stats-card">
          <template #header>
            <div class="card-header">
              <span>投递统计</span>
              <span class="card-subtitle">当前投递进度分布</span>
            </div>
          </template>
          <div class="stats-grid">
            <div class="stat-item">
              <div class="stat-label">已投递</div>
              <div class="stat-value">{{ resumeStats.appliedCount || 0 }}</div>
            </div>
            <div class="stat-item">
              <div class="stat-label">被查看</div>
              <div class="stat-value">{{ resumeStats.viewedCount || 0 }}</div>
            </div>
            <div class="stat-item">
              <div class="stat-label">面试邀约</div>
              <div class="stat-value">{{ resumeStats.interviewCount || 0 }}</div>
            </div>
            <div class="stat-item">
              <div class="stat-label">不合适</div>
              <div class="stat-value">{{ resumeStats.rejectedCount || 0 }}</div>
            </div>
          </div>
          <div class="stats-actions">
            <el-button type="info" plain @click="$router.push('/applicant/applications')">查看详细记录</el-button>
          </div>
        </el-card>

        <el-card shadow="hover" class="profile-card activity-card">
          <template #header>
            <div class="card-header">
              <span>最近求职动态</span>
              <span class="card-subtitle">把近期状态变化压缩成一条可读时间线</span>
            </div>
          </template>
          <div class="activity-list">
            <div v-for="item in recentActivityList" :key="item.key" class="activity-item">
              <div class="activity-dot"></div>
              <div class="activity-main">
                <div class="activity-row">
                  <div class="activity-title">{{ item.title }}</div>
                  <span class="activity-meta">{{ item.meta }}</span>
                </div>
                <div class="activity-desc">{{ item.desc }}</div>
              </div>
            </div>
          </div>
        </el-card>

        <el-card shadow="hover" class="profile-card focus-card">
          <template #header>
            <div class="card-header">
              <span>当前重点</span>
              <span class="card-subtitle">按优先级补齐求职资料与可信度</span>
            </div>
          </template>
          <div class="focus-list">
            <div v-for="item in profileFocusList" :key="item.key" class="focus-item">
              <div class="focus-item-main">
                <div class="focus-item-title">{{ item.title }}</div>
                <div class="focus-item-desc">{{ item.desc }}</div>
              </div>
              <el-tag size="small" effect="plain" :type="item.tagType">{{ item.state }}</el-tag>
            </div>
          </div>
          <div class="focus-callout">
            <div class="focus-callout-copy">
              <div class="focus-callout-title">{{ primaryFocus.title }}</div>
              <div class="focus-callout-desc">{{ primaryFocus.desc }}</div>
            </div>
            <el-button type="primary" @click="handlePrimaryFocus">{{ primaryFocus.actionLabel }}</el-button>
          </div>
        </el-card>

        <el-card shadow="hover" class="profile-card">
          <template #header>
            <div class="card-header">
              <span>身份认证</span>
              <span class="card-subtitle">提升企业信任度</span>
            </div>
          </template>
          <div class="verify-section">
            <el-steps :active="verifyStepActive" align-center class="verify-steps">
              <el-step title="提交申请" />
              <el-step title="审核中" />
              <el-step title="认证完成" />
            </el-steps>
            <div class="verify-row">
              <span class="verify-label">认证状态</span>
              <el-tag :type="verifyStatusTag.type">{{ verifyStatusTag.label }}</el-tag>
            </div>
            <div class="verify-desc">{{ verifyStatusTag.desc }}</div>
            <div v-if="verifyInfo.submittedAt" class="verify-meta">
              提交时间：{{ verifyInfo.submittedAt }}
            </div>
            <div v-if="verifyInfo.auditTime || verifyInfo.auditUserName || verifyInfo.auditUserId" class="verify-meta">
              审核时间：{{ verifyInfo.auditTime || '-' }} ·
              审核人：{{ verifyInfo.auditUserName || (verifyInfo.auditUserId ? `管理员(${verifyInfo.auditUserId})` : '-') }}
            </div>
            <div class="verify-actions">
              <el-button
                v-if="verifyStatusTag.action"
                type="primary"
                @click="openVerifyDialog"
              >{{ verifyStatusTag.action }}</el-button>
              <el-button v-else type="info" plain @click="openVerifyDialog">查看详情</el-button>
            </div>
          </div>
        </el-card>
      </div>
    </div>

    <el-dialog v-model="passwordDialogVisible" title="修改密码" width="420px" @closed="resetPasswordForm">
      <el-form :model="passwordForm" label-width="90px">
        <el-form-item label="当前密码">
          <el-input v-model="passwordForm.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="passwordForm.newPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="passwordSubmitting" @click="submitPasswordChange">确认修改</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="verifyDialogVisible" title="身份认证" width="520px" @closed="resetVerifyForm">
      <el-form :model="verifyForm" label-width="90px">
        <el-form-item label="真实姓名">
          <el-input v-model="verifyForm.realName" placeholder="与证件姓名一致" />
        </el-form-item>
        <el-form-item label="证件类型">
          <el-select v-model="verifyForm.certType" placeholder="选择证件类型">
            <el-option label="身份证" value="ID" />
            <el-option label="学生证" value="STUDENT" />
            <el-option label="工作证明" value="WORK" />
          </el-select>
        </el-form-item>
        <el-form-item label="证件号码">
          <el-input v-model="verifyForm.certNo" placeholder="示例：身份证号码" />
        </el-form-item>
        <el-form-item label="备注说明">
          <el-input v-model="verifyForm.remark" type="textarea" rows="3" placeholder="可选，补充说明" />
        </el-form-item>
      </el-form>
      <div class="hint-text">提交后将进入审核，结果会在此页展示。</div>
      <template #footer>
        <el-button @click="verifyDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="verifySubmitting" @click="submitVerify">提交认证</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, nextTick, onBeforeUnmount } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { getDashboardStats, getResumeAttachment } from '@/api/applicant'
import {
  getUserProfile,
  updateUserProfile,
  uploadUserAvatar,
  getPrivacySettings,
  updatePrivacySettings,
  changePassword,
  getLoginLogs,
  getVerifyStatus,
  submitVerify as submitVerifyRequest
} from '@/api/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import { formatDateTime } from '@/utils/format'
import { formatFileUrl } from '@/utils/file'

const userStore = useUserStore()
const router = useRouter()

// 账号信息只读展示：优先从 store 获取
const userInfo = computed(() => userStore.userInfo || {})

const roleLabel = computed(() => {
  const role = (userInfo.value.role || '').toUpperCase()
  if (role === 'APPLICANT') return '求职者'
  if (role === 'MERCHANT') return '商家'
  if (role === 'ADMIN') return '管理员'
  return role || '-'
})

const profileFormRef = ref(null)
const saving = ref(false)
const privacyLoading = ref(false)
const privacySaving = ref(false)
const loginLoading = ref(false)
const passwordSubmitting = ref(false)
const verifySubmitting = ref(false)
const statusBanner = reactive({
  visible: false,
  type: 'success',
  message: ''
})
let statusTimer = null
const passwordDialogVisible = ref(false)
const verifyDialogVisible = ref(false)

const profileForm = reactive({
  nickname: '',
  phone: '',
  email: '',
  avatar: ''
})

// 隐私设置
const privacyForm = reactive({
  contactVisibility: 'DELIVERY'
})

// 账号安全：修改密码表单
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

// 账号安全：最近登录记录
const loginLogs = ref([])

// 身份认证（后端状态）
const verifyInfo = ref({
  status: 'UNVERIFIED',
  submittedAt: '',
  auditReason: '',
  auditTime: '',
  auditUserId: null,
  auditUserName: ''
})
const verifyForm = reactive({
  realName: '',
  certType: 'ID',
  certNo: '',
  remark: ''
})

// 账号资料快照，用于判断是否有未保存变更
const profileSnapshot = ref(JSON.stringify({
  nickname: profileForm.nickname || '',
  phone: profileForm.phone || '',
  email: profileForm.email || '',
  avatar: profileForm.avatar || ''
}))

const displayName = computed(() => {
  return userInfo.value.realName
    || userInfo.value.nickname
    || userInfo.value.username
    || '求职者'
})

const avatarUrl = computed(() => {
  return formatFileUrl(profileForm.avatar || userInfo.value.avatar)
    || 'https://cube.elemecdn.com/9/c2/f0ee8a3c7c9638a54940382568c9dpng.png'
})

const profileRules = {
  nickname: [{ max: 50, message: '昵称长度不能超过50', trigger: 'blur' }],
  phone: [{ max: 20, message: '手机号长度不能超过20', trigger: 'blur' }],
  email: [
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
    { max: 100, message: '邮箱长度不能超过100', trigger: 'blur' }
  ]
}

// 账号完整度计算（仅针对账号资料）
const accountCompletenessItems = computed(() => ([
  { label: '头像', done: !!(profileForm.avatar || userInfo.value.avatar) },
  { label: '昵称', done: !!profileForm.nickname },
  { label: '手机号', done: !!profileForm.phone },
  { label: '邮箱', done: !!profileForm.email }
]))

const accountCompleteness = computed(() => {
  const list = accountCompletenessItems.value
  if (!list.length) return 0
  const doneCount = list.filter(item => item.done).length
  return Math.round((doneCount / list.length) * 100)
})

const missingHints = computed(() => {
  return accountCompletenessItems.value.filter(item => !item.done).map(item => item.label)
})

const privacyLabel = computed(() => {
  const map = {
    PUBLIC: '公开展示',
    DELIVERY: '仅投递企业',
    AUTH: '仅授权企业'
  }
  return map[privacyForm.contactVisibility] || '仅投递企业'
})

const showStatusBanner = (type, message) => {
  if (statusTimer) {
    clearTimeout(statusTimer)
  }
  statusBanner.type = type
  statusBanner.message = message
  statusBanner.visible = true
  statusTimer = setTimeout(() => {
    statusBanner.visible = false
  }, 4000)
}

const resumeStats = ref({
  resumeCompleteness: 0,
  resumeUpdateTime: '',
  appliedCount: 0,
  viewedCount: 0,
  interviewCount: 0,
  rejectedCount: 0
})
const attachmentName = ref('')

const loadResumeOverview = async () => {
  try {
    const res = await getDashboardStats()
    resumeStats.value = res?.data || res || {}
  } catch (error) {
    // 概览失败时不阻断页面展示
  }
  try {
    const res = await getResumeAttachment()
    const data = res?.data || {}
    attachmentName.value = data.fileName || (data.fileUrl ? data.fileUrl.split('/').pop() : '')
  } catch (error) {
    // 附件信息加载失败时保持空态
  }
}

const loadLoginLogs = async () => {
  loginLoading.value = true
  try {
    const res = await getLoginLogs({ limit: 3 })
    loginLogs.value = res?.data || []
  } catch (error) {
    loginLogs.value = []
  } finally {
    loginLoading.value = false
  }
}

const syncProfileForm = (data) => {
  profileForm.nickname = data?.nickname || ''
  profileForm.phone = data?.phone || ''
  profileForm.email = data?.email || ''
  profileForm.avatar = data?.avatar || ''
  commitProfileSnapshot()
}

const commitProfileSnapshot = () => {
  profileSnapshot.value = JSON.stringify({
    nickname: profileForm.nickname || '',
    phone: profileForm.phone || '',
    email: profileForm.email || '',
    avatar: profileForm.avatar || ''
  })
}

const isProfileDirty = computed(() => {
  const current = JSON.stringify({
    nickname: profileForm.nickname || '',
    phone: profileForm.phone || '',
    email: profileForm.email || '',
    avatar: profileForm.avatar || ''
  })
  return current !== profileSnapshot.value
})

const loadUserProfile = async () => {
  try {
    const res = await getUserProfile()
    const data = res?.data || res || {}
    userStore.setUserInfo({
      ...userStore.userInfo,
      ...data
    })
    syncProfileForm(data)
  } catch (error) {
    // 账号信息加载失败时保持本地缓存展示
    syncProfileForm(userStore.userInfo)
  }
}

// 保存隐私设置
const savePrivacySettings = async () => {
  privacySaving.value = true
  try {
    await updatePrivacySettings({
      contactVisibility: privacyForm.contactVisibility
    })
    ElMessage.success('隐私设置已保存')
    showStatusBanner('success', '隐私设置已保存')
  } catch (error) {
    // 由拦截器统一提示
    showStatusBanner('error', '隐私设置保存失败，请稍后重试')
  } finally {
    privacySaving.value = false
  }
}

const loadPrivacySettings = async () => {
  privacyLoading.value = true
  try {
    const res = await getPrivacySettings()
    const data = res?.data || {}
    privacyForm.contactVisibility = data.contactVisibility || 'DELIVERY'
  } catch (error) {
    privacyForm.contactVisibility = 'DELIVERY'
  } finally {
    privacyLoading.value = false
  }
}

// 修改密码
const submitPasswordChange = async () => {
  if (!passwordForm.oldPassword || !passwordForm.newPassword) {
    ElMessage.warning('请填写完整密码信息')
    return
  }
  if (passwordForm.newPassword.length < 6) {
    ElMessage.warning('新密码至少 6 位')
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.warning('两次密码输入不一致')
    return
  }
  passwordSubmitting.value = true
  try {
    await changePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword
    })
    ElMessage.success('密码已更新')
    showStatusBanner('success', '密码已更新')
    passwordDialogVisible.value = false
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
  } catch (error) {
    // 由拦截器统一提示
    showStatusBanner('error', '密码修改失败，请稍后重试')
  } finally {
    passwordSubmitting.value = false
  }
}

// 身份认证状态展示
const verifyStatusTag = computed(() => {
  const status = verifyInfo.value.status
  if (status === 'APPROVED') {
    return { label: '已认证', type: 'success', desc: '认证通过，企业将更信任你的资料', action: '' }
  }
  if (status === 'PENDING') {
    const timeText = verifyInfo.value.submittedAt ? `（${verifyInfo.value.submittedAt}）` : ''
    return { label: '审核中', type: 'warning', desc: `已提交认证${timeText}，请等待管理员审核`, action: '' }
  }
  if (status === 'REJECTED') {
    const reason = verifyInfo.value.auditReason ? `原因：${verifyInfo.value.auditReason}` : '资料未通过，请补充后重新提交'
    return { label: '已驳回', type: 'danger', desc: reason, action: '重新提交' }
  }
  return { label: '未认证', type: 'info', desc: '完成认证可提升可信度', action: '立即认证' }
})

const verifyStepActive = computed(() => {
  const status = verifyInfo.value.status
  if (status === 'APPROVED') return 3
  if (status === 'PENDING') return 2
  return 1
})

// 右侧重点区：把账号、简历、附件与认证压成清晰的优先级列表
const profileFocusList = computed(() => ([
  {
    key: 'account',
    title: '账号资料',
    desc: accountCompleteness.value >= 100
      ? '头像、昵称、手机号和邮箱已补齐'
      : `待补齐：${missingHints.value.join('、') || '基础资料'}`,
    state: `${accountCompleteness.value}%`,
    tagType: accountCompleteness.value >= 100 ? 'success' : 'info'
  },
  {
    key: 'resume',
    title: '在线简历',
    desc: (resumeStats.value.resumeCompleteness || 0) >= 85
      ? '核心模块已经覆盖，可以继续润色描述'
      : '建议补齐意向、经历与技能，提升投递转化',
    state: `${resumeStats.value.resumeCompleteness || 0}%`,
    tagType: (resumeStats.value.resumeCompleteness || 0) >= 85 ? 'success' : 'info'
  },
  {
    key: 'attachment',
    title: '附件简历',
    desc: attachmentName.value
      ? `已上传：${attachmentName.value}`
      : '建议补一份附件简历，方便企业下载流转',
    state: attachmentName.value ? '已上传' : '未上传',
    tagType: attachmentName.value ? 'success' : 'warning'
  },
  {
    key: 'verify',
    title: '身份认证',
    desc: verifyStatusTag.value.desc,
    state: verifyStatusTag.value.label,
    tagType: verifyInfo.value.status === 'APPROVED'
      ? 'success'
      : verifyInfo.value.status === 'PENDING'
        ? 'warning'
        : verifyInfo.value.status === 'REJECTED'
          ? 'danger'
          : 'info'
  }
]))

const primaryFocus = computed(() => {
  if (accountCompleteness.value < 100) {
    return {
      key: 'account',
      title: '先把账号资料补齐',
      desc: '手机号、邮箱和昵称是企业建立初步信任的最低门槛。',
      actionLabel: '完善账号资料'
    }
  }
  if ((resumeStats.value.resumeCompleteness || 0) < 85) {
    return {
      key: 'resume',
      title: '继续完善在线简历',
      desc: '先把意向、经历和技能写完整，再去批量投递会更稳。',
      actionLabel: '去完善简历'
    }
  }
  if (!attachmentName.value) {
    return {
      key: 'attachment',
      title: '补充附件简历',
      desc: '不少企业会直接下载附件流转，在线版和附件版最好同时具备。',
      actionLabel: '上传附件简历'
    }
  }
  if (verifyInfo.value.status !== 'APPROVED') {
    return {
      key: 'verify',
      title: '完成身份认证',
      desc: '认证通过后，企业对你的资料可信度会更高。',
      actionLabel: verifyStatusTag.value.action || '查看认证状态'
    }
  }
  return {
    key: 'jobs',
    title: '资料基础已经齐备',
    desc: '可以继续浏览职位、保持简历更新，并关注投递反馈。',
    actionLabel: '去职位大厅'
  }
})

const recentActivityList = computed(() => {
  const list = []

  if (resumeStats.value.resumeUpdateTime) {
    list.push({
      key: 'resume-update',
      title: '简历最近更新',
      desc: `你在 ${formatDateTime(resumeStats.value.resumeUpdateTime)} 更新了在线简历。保持近期更新时间，有助于提升企业打开意愿。`,
      meta: '简历'
    })
  }

  if (resumeStats.value.appliedCount) {
    list.push({
      key: 'applications',
      title: '投递正在推进',
      desc: `已投递 ${resumeStats.value.appliedCount} 个岗位，其中 ${resumeStats.value.viewedCount || 0} 个被查看，${resumeStats.value.interviewCount || 0} 个进入面试。`,
      meta: '投递'
    })
  }

  if (attachmentName.value) {
    list.push({
      key: 'attachment',
      title: '附件简历已就绪',
      desc: `当前附件为 ${attachmentName.value}，企业可直接下载流转，适合走内部转发场景。`,
      meta: '附件'
    })
  }

  if (verifyInfo.value.status === 'PENDING') {
    list.push({
      key: 'verify-pending',
      title: '身份认证审核中',
      desc: verifyInfo.value.submittedAt
        ? `认证资料已于 ${verifyInfo.value.submittedAt} 提交，等待管理员审核。`
        : '认证资料已提交，等待管理员审核。',
      meta: '认证'
    })
  } else if (verifyInfo.value.status === 'APPROVED') {
    list.push({
      key: 'verify-approved',
      title: '身份认证已通过',
      desc: '认证状态已经转为已通过，企业查看资料时的信任度会更高。',
      meta: '认证'
    })
  } else if (verifyInfo.value.status === 'REJECTED') {
    list.push({
      key: 'verify-rejected',
      title: '认证资料需要补充',
      desc: verifyInfo.value.auditReason || '管理员已驳回当前认证资料，建议根据原因补充后重新提交。',
      meta: '认证'
    })
  }

  if (loginLogs.value.length) {
    const latestLogin = loginLogs.value[0]
    list.push({
      key: 'login',
      title: '最近一次登录',
      desc: `${latestLogin.time || '最近'} 使用 ${latestLogin.device || '当前设备'} 登录，来源 IP ${latestLogin.ip || '-' }。`,
      meta: '安全'
    })
  }

  if (!list.length) {
    list.push({
      key: 'empty',
      title: '求职动态尚在积累',
      desc: '先完善简历并开始投递，系统会逐步把更新、投递和认证变化沉淀到这里。',
      meta: '引导'
    })
  }

  return list.slice(0, 4)
})

const openVerifyDialog = () => {
  verifyForm.realName = userInfo.value.realName || verifyForm.realName || ''
  verifyDialogVisible.value = true
}

const submitVerify = async () => {
  if (!verifyForm.realName || !verifyForm.certNo) {
    ElMessage.warning('请填写真实姓名与证件号码')
    return
  }
  verifySubmitting.value = true
  try {
    const res = await submitVerifyRequest({
      realName: verifyForm.realName,
      certType: verifyForm.certType,
      certNo: verifyForm.certNo,
      remark: verifyForm.remark
    })
    const data = res?.data || {}
    verifyInfo.value = {
      status: data.status || 'PENDING',
      submittedAt: data.submittedAt || '',
      auditReason: data.auditReason || '',
      auditTime: data.auditTime || '',
      auditUserId: data.auditUserId || null,
      auditUserName: data.auditUserName || ''
    }
    ElMessage.success('认证资料已提交，等待审核')
    showStatusBanner('success', '认证资料已提交，等待审核')
    verifyDialogVisible.value = false
  } catch (error) {
    // 由拦截器统一提示
    showStatusBanner('error', '认证提交失败，请稍后重试')
  } finally {
    verifySubmitting.value = false
  }
}

const loadVerifyStatus = async () => {
  try {
    const res = await getVerifyStatus()
    const data = res?.data || {}
    verifyInfo.value = {
      status: data.status || 'UNVERIFIED',
      submittedAt: data.submittedAt || '',
      auditReason: data.auditReason || '',
      auditTime: data.auditTime || '',
      auditUserId: data.auditUserId || null,
      auditUserName: data.auditUserName || ''
    }
  } catch (error) {
    verifyInfo.value = {
      status: 'UNVERIFIED',
      submittedAt: '',
      auditReason: '',
      auditTime: '',
      auditUserId: null,
      auditUserName: ''
    }
  }
}

const resetPasswordForm = () => {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
}

const resetVerifyForm = () => {
  verifyForm.realName = userInfo.value.realName || ''
  verifyForm.certType = 'ID'
  verifyForm.certNo = ''
  verifyForm.remark = ''
}

const scrollToAccountForm = async () => {
  await nextTick()
  const target = document.querySelector('.profile-grid')
  if (!target) return
  target.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

const handlePrimaryFocus = () => {
  const key = primaryFocus.value.key
  if (key === 'account') {
    scrollToAccountForm()
    return
  }
  if (key === 'resume' || key === 'attachment') {
    router.push('/applicant/resume')
    return
  }
  if (key === 'verify') {
    openVerifyDialog()
    return
  }
  router.push('/jobs')
}

const resetProfileForm = () => {
  syncProfileForm(userStore.userInfo)
}

const saveProfile = async () => {
  if (!profileFormRef.value) return
  await profileFormRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      const res = await updateUserProfile({
        nickname: profileForm.nickname,
        phone: profileForm.phone,
        email: profileForm.email,
        avatar: profileForm.avatar
      })
      const data = res?.data || res || {}
      userStore.setUserInfo({
        ...userStore.userInfo,
        ...data
      })
      syncProfileForm(data)
      ElMessage.success('账号资料已保存')
      showStatusBanner('success', '账号资料已保存')
    } catch (error) {
      // 由拦截器统一提示
      showStatusBanner('error', '账号资料保存失败，请稍后重试')
    } finally {
      saving.value = false
    }
  })
}

const beforeAvatarUpload = (file) => {
  const isImage = file.type === 'image/jpeg' || file.type === 'image/png'
  if (!isImage) {
    ElMessage.warning('仅支持 JPG/PNG 图片')
    return false
  }
  const isLt2M = file.size / 1024 / 1024 <= 2
  if (!isLt2M) {
    ElMessage.warning('头像大小不能超过 2MB')
    return false
  }
  return true
}

const handleAvatarUpload = async (options) => {
  if (!options?.file) return
  try {
    const res = await uploadUserAvatar(options.file)
    const url = res?.data || res
    if (url) {
      profileForm.avatar = url
      userStore.setUserInfo({
        ...userStore.userInfo,
        avatar: url
      })
      commitProfileSnapshot()
      ElMessage.success('头像已更新')
      showStatusBanner('success', '头像已更新')
    }
  } catch (error) {
    // 由拦截器统一提示
    showStatusBanner('error', '头像更新失败，请稍后重试')
  }
}

onMounted(() => {
  loadUserProfile()
  loadResumeOverview()
  loadPrivacySettings()
  loadVerifyStatus()
  loadLoginLogs()
  window.addEventListener('beforeunload', handleBeforeUnload)
})

onBeforeRouteLeave(async () => {
  if (!isProfileDirty.value) {
    return true
  }
  try {
    await ElMessageBox.confirm(
      '账号资料尚未保存，确定要离开吗？',
      '未保存提示',
      {
        confirmButtonText: '离开',
        cancelButtonText: '继续编辑',
        type: 'warning'
      }
    )
    return true
  } catch (error) {
    return false
  }
})

const handleBeforeUnload = (event) => {
  if (!isProfileDirty.value) return
  event.preventDefault()
  event.returnValue = ''
}

onBeforeUnmount(() => {
  if (statusTimer) {
    clearTimeout(statusTimer)
  }
  window.removeEventListener('beforeunload', handleBeforeUnload)
})
</script>

<style scoped>
.profile-container {
  width: min(calc(100% - (var(--ui-shell-gutter) * 2)), var(--ui-main-shell-max-width));
  margin: 20px auto;
  padding: 4px 0 20px;
  position: relative;
  overflow: visible;
}

.profile-container::before,
.profile-container::after {
  content: '';
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
  filter: blur(6px);
  z-index: 0;
}

.profile-container::before {
  top: 0;
  left: 2%;
  width: min(24vw, 240px);
  height: min(24vw, 240px);
  background: radial-gradient(circle, rgba(10, 132, 255, 0.11), rgba(10, 132, 255, 0) 72%);
}

.profile-container::after {
  top: 160px;
  right: 2%;
  width: min(22vw, 220px);
  height: min(22vw, 220px);
  background: radial-gradient(circle, rgba(147, 197, 253, 0.11), rgba(147, 197, 253, 0) 72%);
}

.profile-container :deep(.el-card) {
  border: none;
  background: transparent;
  box-shadow: none;
}

.profile-container :deep(.el-card__header) {
  padding: 18px 20px 14px;
  border-bottom: 1px solid rgba(191, 219, 254, 0.62);
  background: linear-gradient(180deg, rgba(248, 251, 255, 0.96), rgba(255, 255, 255, 0.74));
}

.profile-container :deep(.el-card__body) {
  padding: 18px 20px 20px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.9), rgba(247, 250, 255, 0.82));
}

.profile-container :deep(.el-input__wrapper),
.profile-container :deep(.el-textarea__inner) {
  border-radius: 12px;
  background: rgba(248, 251, 255, 0.92);
  border: 1px solid rgba(191, 219, 254, 0.76);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.78) !important;
  transition: border-color 0.2s ease, background-color 0.2s ease, box-shadow 0.2s ease;
}

.profile-container :deep(.el-input__wrapper:hover),
.profile-container :deep(.el-textarea__inner:hover) {
  background: rgba(244, 248, 255, 0.98);
  border-color: rgba(147, 197, 253, 0.92);
}

.profile-container :deep(.el-input__wrapper.is-focus),
.profile-container :deep(.el-textarea__inner:focus) {
  background: #ffffff;
  border-color: #0a84ff;
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.92),
    0 0 0 4px rgba(10, 132, 255, 0.14) !important;
}

.profile-container :deep(.el-input.is-disabled .el-input__wrapper) {
  background: rgba(242, 246, 252, 0.92);
  border-color: rgba(203, 213, 225, 0.88);
}

.profile-container :deep(.el-button) {
  border-radius: 999px;
}

.profile-container :deep(.el-button:not(.el-button--primary):not(.is-link):not(.el-button--text)) {
  background: rgba(255, 255, 255, 0.82);
  border-color: rgba(15, 23, 42, 0.08);
  color: #475569;
}

.profile-container :deep(.el-button:not(.el-button--primary):not(.is-link):not(.el-button--text):hover) {
  background: #ffffff;
  border-color: rgba(10, 132, 255, 0.2);
  color: #0a84ff;
}

.profile-container :deep(.el-button--primary) {
  box-shadow: 0 10px 22px rgba(10, 132, 255, 0.16);
}

.status-banner {
  margin: 0 0 16px;
  border-radius: 12px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.08);
  backdrop-filter: blur(12px);
  position: relative;
  z-index: 1;
}

.profile-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 20px 22px;
  border-radius: 16px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.9), rgba(250, 252, 255, 0.82)),
    linear-gradient(135deg, rgba(10, 132, 255, 0.08), rgba(10, 132, 255, 0.03) 48%, rgba(191, 219, 254, 0.12));
  border: 1px solid rgba(191, 219, 254, 0.78);
  box-shadow:
    0 18px 40px rgba(15, 23, 42, 0.08),
    inset 0 1px 0 rgba(255, 255, 255, 0.86);
  margin-bottom: 18px;
  position: relative;
  z-index: 1;
}

.hero-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.hero-text {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.hero-avatar-uploader {
  display: inline-flex;
}

.hero-avatar-shell {
  position: relative;
  border-radius: 50%;
  overflow: hidden;
  cursor: pointer;
  border: 3px solid rgba(0, 113, 227, 0.16);
  transition: box-shadow 0.2s ease;
}

.hero-avatar-shell:hover {
  box-shadow: 0 0 0 6px rgba(96, 165, 250, 0.18);
}

.hero-avatar {
  display: block;
}

.hero-avatar-mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #ffffff;
  background: rgba(15, 23, 42, 0.55);
  opacity: 0;
  transition: opacity 0.2s ease;
}

.hero-avatar-shell:hover .hero-avatar-mask {
  opacity: 1;
}

.hero-title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.hero-name {
  font-size: 20px;
  font-weight: 600;
  color: #0f172a;
}

.hero-tag {
  border-radius: 999px;
}

.hero-meta {
  font-size: 13px;
  color: #475569;
  margin-top: 4px;
}

.hero-highlights {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-top: 12px;
  min-width: min(100%, 420px);
}

.hero-highlight {
  padding: 10px 12px;
  border-radius: 14px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.92), rgba(244, 248, 255, 0.86));
  border: 1px solid rgba(191, 219, 254, 0.72);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.82);
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.hero-highlight-label {
  font-size: 11px;
  color: #64748b;
}

.hero-highlight strong {
  font-size: 18px;
  font-weight: 600;
  color: #0f172a;
}

.hero-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.hero-actions :deep(.el-button:not(.el-button--primary)) {
  background: rgba(255, 255, 255, 0.78);
}

.hero-badges {
  position: absolute;
  right: 18px;
  bottom: 16px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.profile-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.18fr) minmax(320px, 0.82fr);
  gap: clamp(16px, 1.5vw, 24px);
  align-items: start;
  position: relative;
}

.left-column {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 10px;
  border-radius: 22px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.3), rgba(245, 249, 255, 0.16));
  border: 1px solid rgba(191, 219, 254, 0.28);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.74);
}

.profile-card {
  position: relative;
  padding: 0;
  border-radius: 16px;
  overflow: hidden;
  border: 1px solid rgba(177, 196, 221, 0.74);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(244, 248, 255, 0.93));
  box-shadow:
    0 18px 36px rgba(15, 23, 42, 0.07),
    inset 0 1px 0 rgba(255, 255, 255, 0.86);
}

.profile-card::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.42);
  pointer-events: none;
}

.profile-card::after {
  content: '';
  position: absolute;
  top: 0;
  left: 18px;
  right: 18px;
  height: 3px;
  border-radius: 0 0 999px 999px;
  background: linear-gradient(90deg, rgba(0, 113, 227, 0.18), rgba(147, 197, 253, 0.48), rgba(0, 113, 227, 0.18));
  pointer-events: none;
}

.card-header {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-weight: 600;
  color: #0f172a;
}

.card-subtitle {
  font-size: 12px;
  color: #64748b;
}

.account-summary {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  border-radius: 16px;
  background: linear-gradient(180deg, rgba(250, 252, 255, 0.98), rgba(241, 246, 255, 0.9));
  border: 1px solid rgba(191, 219, 254, 0.52);
}

.account-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.account-name {
  font-size: 16px;
  font-weight: 600;
}

.account-meta {
  font-size: 12px;
  color: #6b7280;
}

.hint-text {
  margin-top: 12px;
  font-size: 12px;
  color: #9ca3af;
}

.form-actions {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding-top: 14px;
  border-top: 1px solid rgba(191, 219, 254, 0.44);
}

.privacy-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.privacy-row {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.privacy-options :deep(.el-radio) {
  display: flex;
  align-items: flex-start;
  margin-bottom: 8px;
  white-space: normal;
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(248, 250, 255, 0.82);
  transition: border-color 0.2s ease, background-color 0.2s ease, box-shadow 0.2s ease;
}

.privacy-options :deep(.el-radio:hover) {
  border-color: rgba(10, 132, 255, 0.16);
}

.privacy-options :deep(.el-radio.is-checked) {
  border-color: rgba(10, 132, 255, 0.2);
  background: rgba(239, 246, 255, 0.84);
  box-shadow: 0 8px 18px rgba(10, 132, 255, 0.08);
}

.privacy-options :deep(.el-radio__label) {
  padding-left: 8px;
  line-height: 1.4;
  white-space: normal;
}

.privacy-option {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.privacy-option-title {
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
}

.privacy-option-desc {
  font-size: 12px;
  color: #64748b;
}

.privacy-label {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
}

.privacy-tip {
  font-size: 12px;
  color: #64748b;
}

.security-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.security-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.security-title {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
}

.security-desc {
  font-size: 12px;
  color: #64748b;
}

.login-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.login-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid rgba(191, 219, 254, 0.54);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(242, 247, 255, 0.9));
  font-size: 12px;
  color: #475569;
}

.login-meta {
  font-weight: 600;
}

.login-info {
  color: #64748b;
}

.login-empty {
  font-size: 12px;
  color: #94a3b8;
}

.resume-overview {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.overview-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 14px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(244, 248, 255, 0.88));
  border: 1px solid rgba(191, 219, 254, 0.48);
}

.overview-label {
  font-size: 13px;
  color: #6b7280;
  min-width: 72px;
}

.overview-value {
  font-size: 13px;
  color: #111827;
}

.resume-actions {
  margin-top: 14px;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.focus-card {
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(241, 246, 255, 0.92));
}

.focus-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.focus-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 14px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(242, 247, 255, 0.92));
  border: 1px solid rgba(177, 196, 221, 0.62);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.76);
}

.focus-item-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.focus-item-title {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
}

.focus-item-desc {
  font-size: 12px;
  line-height: 1.7;
  color: #64748b;
}

.focus-callout {
  margin-top: 14px;
  padding: 14px 16px;
  border-radius: 16px;
  background: linear-gradient(135deg, rgba(0, 113, 227, 0.08), rgba(191, 219, 254, 0.18));
  border: 1px solid rgba(147, 197, 253, 0.6);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.focus-callout-copy {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.focus-callout-title {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
}

.focus-callout-desc {
  font-size: 12px;
  line-height: 1.7;
  color: #56718f;
}

.right-column {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 10px;
  border-radius: 22px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.3), rgba(245, 249, 255, 0.16));
  border: 1px solid rgba(191, 219, 254, 0.28);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.74);
}

.stats-card {
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(240, 246, 255, 0.92));
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.stat-item {
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(244, 248, 255, 0.92));
  border-radius: 12px;
  padding: 12px 14px;
  border: 1px solid rgba(191, 219, 254, 0.52);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.74);
}

.stat-label {
  font-size: 12px;
  color: #64748b;
}

.stat-value {
  font-size: 20px;
  font-weight: 600;
  color: #0f172a;
  margin-top: 6px;
}

.stats-actions {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

.activity-card {
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(243, 247, 255, 0.93));
}

.activity-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.activity-item {
  display: grid;
  grid-template-columns: 14px minmax(0, 1fr);
  gap: 12px;
  align-items: flex-start;
}

.activity-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  margin-top: 6px;
  background: #0071e3;
  box-shadow: 0 0 0 5px rgba(0, 113, 227, 0.12);
}

.activity-main {
  padding-bottom: 14px;
  padding-right: 2px;
  border-bottom: 1px solid rgba(191, 219, 254, 0.58);
}

.activity-item:last-child .activity-main {
  padding-bottom: 0;
  border-bottom: 0;
}

.activity-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.activity-title {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
}

.activity-meta {
  font-size: 11px;
  color: #6b85a5;
  padding: 4px 8px;
  border-radius: 999px;
  background: rgba(0, 113, 227, 0.08);
  border: 1px solid rgba(191, 219, 254, 0.6);
  flex-shrink: 0;
}

.activity-desc {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.75;
  color: #64748b;
}

.verify-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.verify-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.verify-label {
  font-size: 13px;
  color: #6b7280;
}

.verify-desc {
  font-size: 12px;
  color: #64748b;
}

.verify-actions {
  display: flex;
  justify-content: flex-end;
}

.verify-steps {
  margin-bottom: 12px;
}

.verify-meta {
  font-size: 12px;
  color: #94a3b8;
}

@media (max-width: 980px) {
  .profile-hero {
    flex-direction: column;
    align-items: flex-start;
  }

  .profile-grid {
    grid-template-columns: 1fr;
  }

  .left-column,
  .right-column {
    padding: 0;
    background: transparent;
    border: 0;
    box-shadow: none;
  }

  .hero-actions {
    width: 100%;
    justify-content: flex-start;
  }

  .hero-badges {
    position: static;
    margin-top: 12px;
  }

  .hero-highlights {
    grid-template-columns: 1fr;
    min-width: 0;
    width: 100%;
  }

  .focus-callout {
    flex-direction: column;
    align-items: flex-start;
  }

  .activity-row {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
