<!--
文件速览：
1. 文件职责：商家企业信息管理页，负责企业资料维护、整改提示承接、复核结果提示、材料上传与重新提交审核。
2. 页面入口：商家路由 `/merchant/company`，支持从平台通知中心携带 `noticeId` 进入。
3. 关键结构：profile-hero、rectify-banner、rectify-insight-card、summary-panel、company-form-card。
4. 阅读建议：先看顶部审核状态和整改阶段卡，再看表单提交逻辑，最后看上传与治理刷新联动。
-->
<template>
  <div class="company-profile-page">
    <section class="profile-hero">
      <div>
        <div class="profile-hero__eyebrow">企业信息管理</div>
        <h1 class="profile-hero__title">{{ heroTitle }}</h1>
        <p class="profile-hero__desc">{{ heroDesc }}</p>
      </div>
      <div class="profile-hero__meta">
        <el-tag :type="auditStatusTag" effect="dark" round>{{ auditStatusText }}</el-tag>
        <span class="profile-hero__meta-text">{{ auditStatusNote }}</span>
      </div>
    </section>

    <section
      v-if="rectifyNotice"
      class="rectify-banner"
      :class="{ 'rectify-banner--warning': isRectifyCritical }"
    >
      <div class="rectify-banner__main">
        <div class="rectify-banner__eyebrow">平台整改提醒</div>
        <div class="rectify-banner__headline">
          <h2 class="rectify-banner__title">{{ rectifyNotice.title }}</h2>
          <el-tag :type="rectifyStageTagType" effect="plain" round>{{ rectifyStageTitle }}</el-tag>
        </div>
        <p class="rectify-banner__summary">{{ rectifyStageDesc }}</p>
        <div class="rectify-banner__meta">
          <span>状态：{{ governanceStatusText(rectifyNotice.status) }}</span>
          <span>截止：{{ rectifyDeadlineText }}</span>
          <span>{{ rectifyNextStep }}</span>
        </div>
      </div>
      <div class="rectify-banner__actions">
        <el-button v-if="rectifyNotice.canAcknowledge" type="primary" plain @click="handleRectifyAcknowledge">确认已读</el-button>
        <el-button type="primary" @click="goGovernanceDetail">查看通知详情</el-button>
      </div>
    </section>

    <section class="company-profile-shell">
      <aside class="summary-panel">
        <div class="summary-card">
          <div class="summary-card__eyebrow">资料概览</div>
          <div class="summary-progress">
            <div class="summary-progress__bar" :style="{ width: `${formCompletion}%` }"></div>
          </div>
          <div class="summary-progress__meta">
            <span>完成度</span>
            <strong>{{ formCompletion }}%</strong>
          </div>
          <div class="summary-item">
            <span class="summary-item__label">营业执照</span>
            <span class="summary-item__value">{{ form.licenseUrl ? '已上传' : '未上传' }}</span>
          </div>
          <div class="summary-item">
            <span class="summary-item__label">资质材料</span>
            <span class="summary-item__value">{{ form.qualificationUrls.length }} 项</span>
          </div>
          <div class="summary-item">
            <span class="summary-item__label">当前建议</span>
            <span class="summary-item__value">{{ adviceText }}</span>
          </div>
        </div>

        <div
          v-if="rectifyNotice"
          class="summary-card rectify-insight-card"
          :class="`rectify-insight-card--${rectifyStageTone}`"
        >
          <div class="summary-card__eyebrow">整改进度</div>
          <div class="rectify-insight__headline">
            <div>
              <div class="rectify-insight__title">{{ rectifyStageTitle }}</div>
              <p class="rectify-insight__desc">{{ rectifyStageDesc }}</p>
            </div>
            <el-tag :type="rectifyStageTagType" effect="light">{{ governanceStatusText(rectifyNotice.status) }}</el-tag>
          </div>
          <div class="summary-item">
            <span class="summary-item__label">平台最近反馈</span>
            <span class="summary-item__value">{{ rectifyPlatformFeedback }}</span>
            <span class="summary-item__sub">{{ latestRectifyAdminAction?.createTime ? `平台最近更新于 ${formatText(latestRectifyAdminAction.createTime)}` : '如需完整时间线，可进入通知详情查看。' }}</span>
          </div>
          <div class="summary-item">
            <span class="summary-item__label">你上次提交</span>
            <span class="summary-item__value">{{ rectifyMerchantFeedback }}</span>
            <span class="summary-item__sub">{{ latestRectifyMerchantAction?.createTime ? `你最近一次提交于 ${formatText(latestRectifyMerchantAction.createTime)}` : '当前还没有提交复核说明，保存本页资料后系统会自动重新进入待复核。' }}</span>
          </div>
          <div class="summary-item">
            <span class="summary-item__label">下一步动作</span>
            <span class="summary-item__value">{{ rectifyNextStep }}</span>
            <span class="summary-item__sub">{{ rectifyDeadlineNote }}</span>
          </div>
        </div>
      </aside>

      <div class="company-form-card">
        <div class="company-form-card__header">
          <div>
            <h2 class="company-form-card__title">企业资料编辑</h2>
            <p class="company-form-card__desc">保存后会同步影响企业展示、审核判断和整改复核进度。</p>
          </div>
          <div class="company-form-card__tags">
            <el-tag effect="plain">资质材料 {{ form.qualificationUrls.length }} 项</el-tag>
            <el-tag effect="plain" type="info">营业执照 {{ form.licenseUrl ? '已上传' : '未上传' }}</el-tag>
          </div>
        </div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-width="100px"
          label-position="left"
          size="large"
        >
          <div class="form-section">
            <div class="form-section__title">基础展示信息</div>
            <el-form-item label="企业 Logo" prop="companyLogo">
              <div class="logo-upload-row">
                <el-upload class="avatar-uploader" action="#" :show-file-list="false" :http-request="customUpload">
                  <img v-if="form.companyLogo" :src="formatFileUrl(form.companyLogo)" class="logo-preview" />
                  <div v-else class="logo-upload-trigger">
                    <el-icon><Plus /></el-icon>
                    <span>上传 Logo</span>
                  </div>
                </el-upload>
                <div class="upload-copy">支持 JPG/PNG，建议尺寸 200x200px。</div>
              </div>
            </el-form-item>

            <el-form-item label="企业全称" prop="companyName">
              <el-input v-model="form.companyName" placeholder="请输入营业执照上的完整名称" />
            </el-form-item>

            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="所属行业" prop="industry">
                  <el-select v-model="form.industry" placeholder="请选择所属行业" class="w-full">
                    <el-option v-for="item in industries" :key="item" :label="item" :value="item" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="人员规模" prop="scale">
                  <el-select v-model="form.scale" placeholder="请选择人员规模" class="w-full">
                    <el-option label="0-20人" value="0-20人" />
                    <el-option label="20-99人" value="20-99人" />
                    <el-option label="100-499人" value="100-499人" />
                    <el-option label="500-999人" value="500-999人" />
                    <el-option label="1000人以上" value="1000人以上" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>

            <el-form-item label="融资阶段" prop="financing">
              <el-radio-group v-model="form.financing">
                <el-radio-button value="未融资">未融资</el-radio-button>
                <el-radio-button value="天使轮">天使轮</el-radio-button>
                <el-radio-button value="A轮">A轮</el-radio-button>
                <el-radio-button value="B轮">B轮</el-radio-button>
                <el-radio-button value="C轮">C轮</el-radio-button>
                <el-radio-button value="已上市">已上市</el-radio-button>
              </el-radio-group>
            </el-form-item>

            <el-form-item label="公司介绍" prop="description">
              <el-input v-model="form.description" type="textarea" :rows="6" maxlength="2000" show-word-limit placeholder="请介绍公司的主营业务、团队优势和招聘亮点。" />
            </el-form-item>
          </div>

          <div class="form-section">
            <div class="form-section__title">审核与资质材料</div>
            <el-row :gutter="20">
              <el-col :span="12"><el-form-item label="联系人" prop="contactName"><el-input v-model="form.contactName" placeholder="负责人姓名" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="联系电话" prop="contactPhone"><el-input v-model="form.contactPhone" placeholder="负责人手机号" /></el-form-item></el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12"><el-form-item label="信用代码" prop="creditCode"><el-input v-model="form.creditCode" placeholder="统一社会信用代码" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="法人姓名" prop="legalPerson"><el-input v-model="form.legalPerson" placeholder="营业执照法定代表人" /></el-form-item></el-col>
            </el-row>

            <el-form-item label="营业执照" prop="licenseUrl">
              <div class="material-row">
                <el-upload action="#" :show-file-list="false" accept=".jpg,.jpeg,.png,.pdf" :http-request="uploadLicense">
                  <el-button type="primary" plain>上传营业执照</el-button>
                </el-upload>
                <div v-if="form.licenseUrl" class="material-actions">
                  <el-button size="small" @click="openFile(form.licenseUrl)">预览</el-button>
                  <el-button size="small" type="danger" plain @click="removeLicense">移除</el-button>
                </div>
              </div>
            </el-form-item>

            <el-form-item label="资质材料" prop="qualificationUrls">
              <div class="material-row material-row--stack">
                <el-upload action="#" :show-file-list="false" accept=".jpg,.jpeg,.png,.pdf" multiple :http-request="uploadQualification">
                  <el-button>添加材料</el-button>
                </el-upload>
                <div v-if="form.qualificationUrls.length" class="material-chip-list">
                  <button
                    v-for="(item, index) in form.qualificationUrls"
                    :key="`${item}-${index}`"
                    type="button"
                    class="material-chip"
                    @click="openFile(item)"
                  >
                    <span>材料 {{ index + 1 }}</span>
                    <el-icon class="material-chip__close" @click.stop="removeQualification(index)"><Close /></el-icon>
                  </button>
                </div>
              </div>
            </el-form-item>
          </div>

          <div class="form-section">
            <div class="form-section__title">联系地址</div>
            <el-row :gutter="20">
              <el-col :span="8"><el-form-item label="省份" prop="province"><el-input v-model="form.province" placeholder="例如：江苏省" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="所在城市" prop="city"><el-input v-model="form.city" placeholder="例如：苏州市" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="所在区域" prop="district"><el-input v-model="form.district" placeholder="例如：工业园区" /></el-form-item></el-col>
            </el-row>
            <el-form-item label="详细地址" prop="address">
              <el-input v-model="form.address" placeholder="街道、楼宇、门牌号">
                <template #append><el-button :icon="MapLocation" /></template>
              </el-input>
            </el-form-item>
          </div>

          <div class="form-actions">
            <el-button @click="router.push('/merchant/dashboard')">返回工作台</el-button>
            <el-button type="primary" :loading="loading" @click="submitForm">{{ submitButtonText }}</el-button>
          </div>
        </el-form>
      </div>
    </section>
  </div>
</template>

<script setup>
/*
文件速览：
1. 文件职责：维护企业资料页的表单状态、资质上传、整改通知承接、复核结果提示与重新提交审核联动。
2. 对外入口：initData、fetchRectifyNotice、submitForm、handleRectifyAcknowledge、goGovernanceDetail。
3. 关键结构：auditStatus、rectifyNotice、rectifyStageTitle、latestRectifyAdminAction、form、formCompletion。
4. 阅读建议：先看顶部整改阶段 computed，再看 initData / fetchRectifyNotice，最后看提交与上传逻辑。
*/
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Close, MapLocation, Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getMerchantInfo, updateMerchantInfo, uploadMerchantLogo, uploadMerchantQualification } from '@/api/merchant'
import { getMyGovernanceNoticeDetail, getMyGovernanceNotices, markGovernanceNoticeRead } from '@/api/governance'
import { useGovernanceStore } from '@/stores/governance'
import { formatFileUrl } from '@/utils/file'

const route = useRoute()
const router = useRouter()
const governanceStore = useGovernanceStore()
const formRef = ref(null)
const loading = ref(false)
const auditStatus = ref(-1)
const rectifyNotice = ref(null)

const industries = ['互联网', '电子商务', '计算机软件', '金融', '教育', '房地产', '消费零售', '制造业']
const maxLogoSizeMB = 2
const maxQualificationSizeMB = 5
const allowedLogoTypes = ['image/jpeg', 'image/png', 'image/jpg']
const allowedQualificationTypes = ['image/jpeg', 'image/png', 'image/jpg', 'application/pdf']
const requiredFieldKeys = ['companyName', 'industry', 'scale', 'description', 'contactName', 'contactPhone', 'creditCode', 'legalPerson', 'city', 'licenseUrl']
const AUDIT_STATUS_TEXT_MAP = {
  0: '审核中',
  1: '已认证',
  2: '待整改'
}
const AUDIT_STATUS_TAG_MAP = {
  0: 'warning',
  1: 'success',
  2: 'danger'
}
const GOVERNANCE_STATUS_TEXT_MAP = {
  PENDING_READ: '待查看',
  PENDING_ACTION: '待处理',
  PENDING_REVIEW: '待复核',
  FINISHED: '已完成',
  REJECTED: '已驳回',
  EXPIRED: '已失效',
  CLOSED: '已关闭'
}
const RECTIFY_SETTLED_STATUSES = ['FINISHED', 'CLOSED', 'EXPIRED']
const RECTIFY_CRITICAL_STATUSES = ['REJECTED', 'EXPIRED']
const RECTIFY_PRIORITY_STATUSES = ['PENDING_READ', 'PENDING_ACTION', 'REJECTED', 'PENDING_REVIEW']
const RECTIFY_ADMIN_ACTION_TYPES = ['APPROVE', 'REJECT', 'CLOSE']
const RECTIFY_MERCHANT_ACTION_TYPES = ['SUBMIT_FIX', 'REPLY']
const RECTIFY_STAGE_TITLE_MAP = {
  finished: '本轮企业资料整改已完成',
  review: '企业资料已提交，等待平台复核',
  read: '请先查看本轮企业资料整改要求',
  rejected: '本轮复核未通过，需要继续修正资料',
  expired: '本轮整改已逾期，请尽快重新处理',
  action: '企业资料待整改，请按平台意见修正',
  default: '企业资料治理状态已同步'
}
const RECTIFY_STAGE_TONE_MAP = {
  finished: 'success',
  review: 'review',
  read: 'warning',
  rejected: 'warning',
  expired: 'warning',
  action: 'warning',
  default: 'default'
}
const RECTIFY_STAGE_TAG_TYPE_MAP = {
  success: 'success',
  review: 'primary',
  warning: 'warning',
  default: 'info'
}

const form = reactive({
  companyName: '', companyLogo: '', industry: '', scale: '', financing: '未融资', description: '',
  contactName: '', contactPhone: '', creditCode: '', legalPerson: '',
  province: '', city: '', district: '', address: '', licenseUrl: '', qualificationUrls: []
})

const rules = {
  companyName: [{ required: true, message: '请输入企业名称', trigger: 'blur' }],
  contactName: [{ required: true, message: '请输入联系人姓名', trigger: 'blur' }],
  contactPhone: [{ required: true, message: '请输入联系人电话', trigger: 'blur' }, { pattern: /^1\d{10}$/, message: '请输入有效的手机号', trigger: 'blur' }],
  creditCode: [{ required: true, message: '请输入统一社会信用代码', trigger: 'blur' }],
  legalPerson: [{ required: true, message: '请输入法人姓名', trigger: 'blur' }],
  industry: [{ required: true, message: '请选择所属行业', trigger: 'change' }],
  scale: [{ required: true, message: '请选择人员规模', trigger: 'change' }],
  description: [{ required: true, message: '请填写公司介绍', trigger: 'blur' }],
  city: [{ required: true, message: '请输入所在城市', trigger: 'blur' }],
  licenseUrl: [{ required: true, message: '请上传营业执照', trigger: 'change' }],
  qualificationUrls: [{ validator: (_, value, callback) => (!value || value.length === 0 ? callback(new Error('请至少上传一项资质材料')) : callback()), trigger: 'change' }]
}

const focusNoticeId = computed(() => {
  const id = Number(route.query.noticeId)
  return Number.isFinite(id) && id > 0 ? id : null
})
const formCompletion = computed(() => Math.round((requiredFieldKeys.filter((key) => {
  const value = form[key]
  return Array.isArray(value) ? value.length > 0 : Boolean(String(value || '').trim())
}).length / requiredFieldKeys.length) * 100))
const getAuditStatusNote = (status) => {
  if (status === 2) return '平台要求你补充或修正资料，保存后会自动重新提交审核。'
  if (status === 0) return '资料已在审核队列中，平台复核期间请避免频繁大改。'
  if (status === 1) return '当前企业资料已通过平台审核，可正常用于招聘展示。'
  return '完成资料后即可提交审核，解锁职位发布与企业展示。'
}

const getHeroTitle = (status) => {
  if (status === 2) return '企业资料需要整改，请先修正后重新提交审核'
  if (status === 0) return '企业资料已提交，等待平台审核中'
  if (status === 1) return '维护企业资料，保持企业展示与审核信息一致'
  return '完善企业资料，开启招聘发布与企业展示'
}

const getHeroDesc = (status) => {
  if (status === 2) return '这一次请围绕平台给出的驳回原因集中修正，保存后系统会自动把整改事项推进到待复核。'
  if (status === 0) return '如果仍需补充材料，可直接保存更新；平台会以最新版本作为审核依据。'
  if (status === 1) return '企业资料会直接影响职位可信度和候选人转化，建议定期维护联系人、资质和公司介绍。'
  return '先完成企业基础信息、联系人和审核材料，再提交平台审核，后续即可发布职位。'
}

const getSubmitButtonText = (status) => {
  if (status === 2) return '保存并重新提交审核'
  if (status === 0) return '保存并更新审核资料'
  if (status === 1) return '保存资料'
  return '保存并提交审核'
}

const getAdviceText = (status) => {
  if (status === 0) return '保持资料稳定，等待审核结果。'
  if (status === 1) return '若更换资质或联系人，系统会自动重新审核。'
  return '建议先补齐审核材料再提交。'
}

const auditStatusText = computed(() => AUDIT_STATUS_TEXT_MAP[auditStatus.value] || '未提交')
const auditStatusTag = computed(() => AUDIT_STATUS_TAG_MAP[auditStatus.value] || 'info')
const auditStatusNote = computed(() => getAuditStatusNote(auditStatus.value))
const heroTitle = computed(() => getHeroTitle(auditStatus.value))
const heroDesc = computed(() => getHeroDesc(auditStatus.value))
const submitButtonText = computed(() => getSubmitButtonText(auditStatus.value))

const governanceStatusText = (status) => GOVERNANCE_STATUS_TEXT_MAP[status] || '处理中'
const formatText = (value) => value || '—'
const normalizeQualificationUrls = (value) => {
  if (Array.isArray(value)) return value
  if (typeof value !== 'string') return []
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}
const toTimestamp = (value) => {
  if (!value) return 0
  const raw = String(value).replace(' ', 'T')
  const time = new Date(raw).getTime()
  return Number.isFinite(time) ? time : 0
}
const sortActionsByTimeDesc = (actions) => {
  const list = Array.isArray(actions) ? actions : []
  return [...list].sort((a, b) => toTimestamp(b?.createTime) - toTimestamp(a?.createTime))
}

const findLatestRectifyAction = (actions, actorRole, actionTypes) => {
  return actions.find((item) => item.actorRole === actorRole && actionTypes.includes(item.actionType)) || null
}

const pickFirstText = (values, fallbackText) => {
  for (const value of values) {
    if (typeof value === 'string' && value.trim()) {
      return value
    }
  }
  return fallbackText
}

const rectifyActions = computed(() => sortActionsByTimeDesc(rectifyNotice.value?.actions || []))
const latestRectifyAdminAction = computed(() => findLatestRectifyAction(rectifyActions.value, 'ADMIN', RECTIFY_ADMIN_ACTION_TYPES))
const latestRectifyMerchantAction = computed(() => findLatestRectifyAction(rectifyActions.value, 'MERCHANT', RECTIFY_MERCHANT_ACTION_TYPES))
const rectifyStageKey = computed(() => {
  const status = rectifyNotice.value?.status
  if (status === 'FINISHED') return 'finished'
  if (status === 'PENDING_REVIEW') return 'review'
  if (status === 'PENDING_READ') return 'read'
  if (status === 'REJECTED') return 'rejected'
  if (status === 'EXPIRED') return 'expired'
  if (auditStatus.value === 2 || status === 'PENDING_ACTION') return 'action'
  return 'default'
})

const getRectifyStageDesc = (stageKey) => {
  if (stageKey === 'finished') return latestRectifyAdminAction.value?.content || '平台已完成本轮复核，当前企业资料将按最新审核结果生效。'
  if (stageKey === 'review') return latestRectifyMerchantAction.value?.content || '你的资料修改已提交，当前等待平台管理员复核。'
  if (stageKey === 'rejected') return latestRectifyAdminAction.value?.content || '平台复核后仍有内容需要补充，请继续根据要求修正企业资料。'
  if (stageKey === 'expired') return '该整改事项已经超过处理时限，建议优先修正资料并重新提交。'
  if (stageKey === 'read') return '先确认已读并理解本轮整改要求，再进入下方表单逐项修正。'
  return rectifyNotice.value?.summary || '请按平台要求修正企业资料并重新提交审核。'
}

const getRectifyNextStep = (stageKey) => {
  if (stageKey === 'review') return '当前无需重复提交，等待管理员复核即可。'
  if (stageKey === 'finished') return '本轮事项已完成，后续如更新资质或联系人，系统会自动重新进入审核。'
  if (stageKey === 'read') {
    return rectifyNotice.value?.canAcknowledge
      ? '先确认已读，再修正资料并保存提交。'
      : '请先根据平台说明修正资料并保存提交。'
  }
  if (stageKey === 'rejected') return '按最新驳回意见继续修改资料，保存后再次进入待复核。'
  if (stageKey === 'expired') return '优先补齐企业信息与材料，保存后重新发起本轮复核。'
  return rectifyNotice.value?.requiredAction || '请修改企业资料并保存，本页会自动重新提交审核。'
}

const isRectifyOverdue = computed(() => {
  if (!rectifyNotice.value?.dueTime) return false
  if (RECTIFY_SETTLED_STATUSES.includes(rectifyNotice.value.status)) return false
  return toTimestamp(rectifyNotice.value.dueTime) < Date.now()
})
const isRectifyCritical = computed(() => Boolean(rectifyNotice.value && (auditStatus.value === 2 || RECTIFY_CRITICAL_STATUSES.includes(rectifyNotice.value.status) || isRectifyOverdue.value)))
const rectifyStageTone = computed(() => RECTIFY_STAGE_TONE_MAP[rectifyStageKey.value] || 'default')
const rectifyStageTagType = computed(() => RECTIFY_STAGE_TAG_TYPE_MAP[rectifyStageTone.value] || 'info')
const rectifyStageTitle = computed(() => RECTIFY_STAGE_TITLE_MAP[rectifyStageKey.value] || RECTIFY_STAGE_TITLE_MAP.default)
const rectifyPlatformFeedback = computed(() => pickFirstText([
  latestRectifyAdminAction.value?.content,
  rectifyNotice.value?.detail,
  rectifyNotice.value?.requiredAction,
  rectifyNotice.value?.summary
], '平台暂未填写更详细的复核意见。'))
const rectifyMerchantFeedback = computed(() => pickFirstText([
  latestRectifyMerchantAction.value?.content
], '当前还没有提交过整改说明。'))
const rectifyStageDesc = computed(() => getRectifyStageDesc(rectifyStageKey.value))
const rectifyNextStep = computed(() => getRectifyNextStep(rectifyStageKey.value))
const rectifyDeadlineText = computed(() => formatText(rectifyNotice.value?.dueTime))
const rectifyDeadlineNote = computed(() => {
  if (!rectifyNotice.value?.dueTime) return '当前事项未设置明确截止时间，但仍建议尽快处理。'
  return isRectifyOverdue.value
    ? '当前已经超过处理时限，建议立即处理，避免影响企业展示与职位发布。'
    : `请在 ${formatText(rectifyNotice.value?.dueTime)} 前完成本轮整改并重新提交。`
})
const adviceText = computed(() => {
  if (rectifyNotice.value) return rectifyNextStep.value
  return getAdviceText(auditStatus.value)
})

const initData = async () => {
  try {
    const res = await getMerchantInfo()
    if (res.code === 200 && res.data) {
      Object.assign(form, { ...form, ...res.data, qualificationUrls: normalizeQualificationUrls(res.data.qualificationUrls) })
      auditStatus.value = Number(res.data.auditStatus ?? -1)
      return
    }
  } catch (error) {}
  auditStatus.value = -1
}

const selectRectifyNotice = (records) => {
  if (focusNoticeId.value) {
    const matched = records.find((item) => Number(item.id) === focusNoticeId.value)
    if (matched) return matched
  }
  return records.find((item) => RECTIFY_PRIORITY_STATUSES.includes(item.status)) || records[0] || null
}

const loadRectifyNoticeDetail = async (notice) => {
  if (!notice?.id) return null
  try {
    const detailRes = await getMyGovernanceNoticeDetail(notice.id)
    if (detailRes.code === 200 && detailRes.data) {
      return {
        ...notice,
        ...detailRes.data,
        actions: Array.isArray(detailRes.data.actions) ? detailRes.data.actions : []
      }
    }
  } catch (error) {
  }
  return notice
}

const fetchRectifyNotice = async () => {
  try {
    const res = await getMyGovernanceNotices({ current: 1, size: 6, noticeType: 'MERCHANT_RECTIFY' })
    const selected = res.code === 200 ? selectRectifyNotice(res.data?.records || []) : null
    rectifyNotice.value = selected ? await loadRectifyNoticeDetail(selected) : null
  } catch (error) {
    rectifyNotice.value = null
  }
}

const handleRectifyAcknowledge = async () => {
  if (!rectifyNotice.value?.id) return
  try {
    const res = await markGovernanceNoticeRead(rectifyNotice.value.id)
    if (res.code === 200) {
      ElMessage.success('已确认已读')
      await Promise.all([fetchRectifyNotice(), governanceStore.fetchSummary('MERCHANT', { force: true })])
      return
    }
    ElMessage.error(res.msg || '确认已读失败')
  } catch (error) {
    ElMessage.error(error?.message || '确认已读失败')
  }
}

const validateUploadFile = (file, allowedTypes, maxSizeMB, typeMessage, sizeMessage) => {
  if (!file) return ElMessage.warning('请先选择文件'), false
  if (!allowedTypes.includes(file.type)) return ElMessage.warning(typeMessage), false
  if (file.size / 1024 / 1024 > maxSizeMB) return ElMessage.warning(sizeMessage), false
  return true
}

const customUpload = async ({ file, onSuccess, onError }) => {
  if (!validateUploadFile(file, allowedLogoTypes, maxLogoSizeMB, '仅支持 JPG/PNG 图片格式', 'Logo 图片大小不能超过 2MB')) return onError?.(new Error('logo 上传校验失败'))
  try {
    const res = await uploadMerchantLogo(file)
    form.companyLogo = res.data || ''
    ElMessage.success('Logo 上传成功')
    onSuccess?.(res)
  } catch (error) { onError?.(error) }
}

const uploadLicense = async ({ file, onSuccess, onError }) => {
  if (!validateUploadFile(file, allowedQualificationTypes, maxQualificationSizeMB, '仅支持 JPG/PNG/PDF 格式', '材料大小不能超过 5MB')) return onError?.(new Error('营业执照上传校验失败'))
  try {
    const res = await uploadMerchantQualification(file)
    form.licenseUrl = res.data || ''
    formRef.value?.validateField('licenseUrl')
    ElMessage.success('营业执照上传成功')
    onSuccess?.(res)
  } catch (error) { onError?.(error) }
}

const uploadQualification = async ({ file, onSuccess, onError }) => {
  if (!validateUploadFile(file, allowedQualificationTypes, maxQualificationSizeMB, '仅支持 JPG/PNG/PDF 格式', '材料大小不能超过 5MB')) return onError?.(new Error('资质材料上传校验失败'))
  try {
    const res = await uploadMerchantQualification(file)
    if (res.data) form.qualificationUrls.push(res.data)
    formRef.value?.validateField('qualificationUrls')
    ElMessage.success('资质材料上传成功')
    onSuccess?.(res)
  } catch (error) { onError?.(error) }
}

const removeQualification = (index) => {
  form.qualificationUrls.splice(index, 1)
  formRef.value?.validateField('qualificationUrls')
}
const removeLicense = () => {
  form.licenseUrl = ''
  formRef.value?.validateField('licenseUrl')
}
const openFile = (url) => {
  const target = formatFileUrl(url)
  if (target) window.open(target, '_blank')
}
const goGovernanceDetail = () => router.push(rectifyNotice.value?.id ? { path: '/merchant/governance', query: { noticeId: String(rectifyNotice.value.id) } } : '/merchant/governance')

const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      const res = await updateMerchantInfo({ ...form, qualificationUrls: JSON.stringify(form.qualificationUrls || []) })
      if (res.code === 200) {
        ElMessage.success(auditStatus.value === 2 ? '企业资料已重新提交审核' : '企业资料已保存并提交审核')
        await Promise.all([initData(), fetchRectifyNotice(), governanceStore.fetchSummary('MERCHANT', { force: true })])
        return
      }
      ElMessage.error(res.msg || '保存失败')
    } catch (error) {
      // 请求错误由统一拦截器处理。
    } finally {
      loading.value = false
    }
  })
}

onMounted(async () => {
  await Promise.all([initData(), fetchRectifyNotice(), governanceStore.fetchSummary('MERCHANT', { force: true })])
})
</script>

<style scoped>
.company-profile-page { width: min(100%, 1280px); margin: 0 auto; display: grid; gap: 20px; }
.profile-hero { display: flex; justify-content: space-between; align-items: flex-end; gap: 16px; padding-top: 6px; }
.profile-hero__eyebrow, .rectify-banner__eyebrow, .summary-card__eyebrow { font-size: 12px; font-weight: 700; letter-spacing: .08em; text-transform: uppercase; color: #64748b; }
.profile-hero__title { margin: 0; font-size: 30px; line-height: 1.25; color: #0f172a; }
.profile-hero__desc, .profile-hero__meta-text, .upload-copy, .company-form-card__desc { margin: 0; color: #64748b; line-height: 1.7; }
.profile-hero__meta { display: inline-flex; flex-direction: column; align-items: flex-end; gap: 8px; }
.rectify-banner, .summary-card, .company-form-card { border-radius: 24px; border: 1px solid rgba(148,163,184,.16); background: linear-gradient(180deg, rgba(255,255,255,.98), rgba(248,250,252,.95)); box-shadow: 0 18px 36px rgba(15,23,42,.04); }
.rectify-banner { display: flex; justify-content: space-between; gap: 18px; padding: 20px 22px; }
.rectify-banner--warning { border-color: rgba(239,68,68,.18); background: radial-gradient(circle at top left, rgba(239,68,68,.08), transparent 30%), linear-gradient(180deg, rgba(255,255,255,.98), rgba(254,242,242,.94)); }
.rectify-banner__main { display: grid; gap: 10px; flex: 1; }
.rectify-banner__headline { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; flex-wrap: wrap; }
.rectify-banner__title, .company-form-card__title { margin: 0; color: #0f172a; }
.rectify-banner__summary { margin: 0; color: #475569; line-height: 1.7; }
.rectify-banner__meta { display: flex; flex-wrap: wrap; gap: 14px; font-size: 13px; color: #64748b; }
.rectify-banner__actions { display: flex; flex-wrap: wrap; align-items: flex-start; gap: 10px; }
.company-profile-shell { display: grid; grid-template-columns: minmax(280px,.78fr) minmax(0,1.32fr); gap: 20px; align-items: start; }
.summary-panel { position: sticky; top: calc(var(--ui-shell-gap) + var(--ui-header-height) + 12px); display: grid; gap: 16px; }
.summary-card, .company-form-card { padding: 22px; }
.summary-progress { height: 10px; border-radius: 999px; background: rgba(226,232,240,.78); overflow: hidden; margin-top: 10px; }
.summary-progress__bar { height: 100%; background: linear-gradient(90deg, #0a84ff, #5ac8fa); }
.summary-progress__meta { display: flex; justify-content: space-between; align-items: baseline; margin-top: 10px; color: #475569; }
.summary-progress__meta strong { font-size: 22px; color: #0f172a; }
.summary-item { display: grid; gap: 4px; padding: 14px 0; border-top: 1px solid rgba(148,163,184,.14); }
.summary-item__label { font-size: 12px; color: #94a3b8; }
.summary-item__value { font-size: 15px; font-weight: 700; color: #0f172a; line-height: 1.6; }
.summary-item__sub { font-size: 12px; color: #64748b; line-height: 1.7; }
.rectify-insight-card { position: relative; overflow: hidden; }
.rectify-insight-card--warning { border-color: rgba(245, 158, 11, .24); background: radial-gradient(circle at top right, rgba(245,158,11,.08), transparent 32%), linear-gradient(180deg, rgba(255,255,255,.98), rgba(255,251,235,.95)); }
.rectify-insight-card--review { border-color: rgba(0, 113, 227, .22); background: radial-gradient(circle at top right, rgba(0,113,227,.08), transparent 32%), linear-gradient(180deg, rgba(255,255,255,.98), rgba(239,246,255,.95)); }
.rectify-insight-card--success { border-color: rgba(34, 197, 94, .2); background: radial-gradient(circle at top right, rgba(34,197,94,.08), transparent 32%), linear-gradient(180deg, rgba(255,255,255,.98), rgba(240,253,244,.95)); }
.rectify-insight__headline { display: flex; justify-content: space-between; gap: 12px; align-items: flex-start; flex-wrap: wrap; margin-top: 10px; }
.rectify-insight__title { font-size: 18px; font-weight: 700; color: #0f172a; }
.rectify-insight__desc { margin: 6px 0 0; color: #64748b; line-height: 1.7; }
.company-form-card__header { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 18px; }
.company-form-card__tags, .form-actions, .material-actions { display: flex; flex-wrap: wrap; gap: 10px; }
.form-section { display: grid; gap: 16px; padding: 18px; border-radius: 20px; border: 1px solid rgba(148,163,184,.12); background: rgba(255,255,255,.78); margin-bottom: 18px; }
.form-section__title { font-size: 16px; font-weight: 700; color: #0f172a; }
.logo-upload-row { display: flex; align-items: center; flex-wrap: wrap; gap: 18px; }
.avatar-uploader :deep(.el-upload) { border: 1px dashed rgba(148,163,184,.42); border-radius: 22px; overflow: hidden; cursor: pointer; transition: border-color .24s ease, transform .24s ease; }
.avatar-uploader :deep(.el-upload:hover) { border-color: rgba(0,113,227,.46); transform: translateY(-1px); }
.logo-preview, .logo-upload-trigger { width: 108px; height: 108px; border-radius: 20px; }
.logo-preview { object-fit: contain; background: rgba(248,250,252,.92); }
.logo-upload-trigger { display: grid; place-content: center; gap: 6px; background: rgba(248,250,252,.92); color: #64748b; }
.material-row { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; }
.material-row--stack { align-items: flex-start; }
.material-chip-list { display: flex; flex-wrap: wrap; gap: 10px; }
.material-chip { display: inline-flex; align-items: center; gap: 8px; padding: 10px 12px; border-radius: 999px; border: 1px solid rgba(148,163,184,.16); background: rgba(255,255,255,.92); color: #0f172a; cursor: pointer; }
.material-chip__close { color: #94a3b8; }
.w-full { width: 100%; }
@media (max-width: 1180px) { .company-profile-shell { grid-template-columns: 1fr; } .summary-panel { position: static; } }
@media (max-width: 900px) { .profile-hero, .rectify-banner, .company-form-card__header { flex-direction: column; align-items: flex-start; } .profile-hero__meta { align-items: flex-start; } .rectify-banner__headline, .rectify-insight__headline { width: 100%; } }
</style>
