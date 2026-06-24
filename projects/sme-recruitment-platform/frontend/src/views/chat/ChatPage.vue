<!--
文件速览：
1. 文件职责：聊天主页面，负责会话列表、消息流、附件简历沟通与在线预览。
2. 页面入口：聊天路由页面，供求职者与商家围绕岗位进行沟通。
3. 关键结构：session-panel、chat-panel、attachmentPermission/requestButtonState、resumeDialogVisible、previewVisible。
4. 阅读建议：先看会话标准化与附件权限状态映射，再看模板区和底部聊天/简历相关样式断点。
-->
<template>
  <div class="chat-page">
    <div :class="['chat-shell', { mobile: isMobile }]">
      <!-- 会话列表面板 (Apple Sidebar Style) -->
      <aside class="session-panel" v-show="!isMobile || showSessionList">
        <div class="panel-header">
          <div class="panel-title">会话消息</div>
          <div class="search-box">
            <el-input
              v-model="keyword"
              placeholder="搜索联系人或职位"
              clearable
              prefix-icon="Search"
            />
          </div>
        </div>

        <div class="session-list" v-loading="loadingSessions">
          <div
            v-for="session in filteredSessions"
            :key="session.id"
            :class="['session-item', { active: session.id === activeSessionId }]"
            @click="selectSession(session)"
          >
            <el-badge
              :value="session.unreadCount"
              :hidden="!session.unreadCount"
              class="session-badge"
            >
              <el-avatar :size="48" :src="session.peerAvatar || defaultAvatar" class="session-avatar" />
            </el-badge>
            <div class="session-info">
              <div class="session-row">
                <span class="session-name">{{ getDisplayName(session) }}</span>
                <span v-if="getSessionDisplayTime(session)" class="session-time">
                  {{ formatListTime(getSessionDisplayTime(session)) }}
                </span>
              </div>
              <div class="session-row">
                <div class="session-job-tag" :class="{ muted: getSessionTag(session).muted }">
                  <el-icon v-if="!getSessionTag(session).muted" size="12"><Briefcase /></el-icon>
                  <el-icon v-else size="12"><ChatLineRound /></el-icon>
                  <span class="job-text">{{ getSessionTag(session).text }}</span>
                </div>
              </div>
            </div>
          </div>
          <el-empty v-if="!filteredSessions.length && !loadingSessions" :image-size="80" description="暂无会话" />
        </div>
      </aside>

      <!-- 对话详情面板 (Apple Detail View Style) -->
      <section class="chat-panel" v-show="!isMobile || !showSessionList">
        <!-- 头部信息栏 -->
        <div v-if="activeSession" class="chat-header">
          <div class="header-left">
            <el-button
              v-if="isMobile"
              icon="ArrowLeft"
              circle
              @click="showSessionList = true"
              class="mobile-back"
            />
            <div
              class="peer-brand"
              :class="{ clickable: !!activeSession }"
              @click.stop="openOnlineResumeFromChat"
            >
              <el-avatar :size="40" :src="activeSession.peerAvatar || defaultAvatar" />
              <div class="peer-text">
                <div class="peer-title">
                  {{ getDisplayName(activeSession) }}
                  <el-tag v-if="activeSession.jobTitle" size="small" effect="plain" class="job-tag">
                    {{ activeSession.jobTitle }}
                  </el-tag>
                </div>
                <div class="peer-status">
                  <span class="status-dot" :class="wsStatus"></span>
                  {{ wsStatusText }}
                </div>
              </div>
            </div>
          </div>
          <div class="header-right">
            <el-button v-if="canSwitchJob" plain size="small" round @click="openJobSelectDialog('switch')">
              切换岗位
            </el-button>
          </div>
        </div>

        <!-- 消息列表区 -->
        <div class="chat-body" ref="messageWrap" @scroll.passive="handleMessageScroll">
          <el-empty v-if="!activeSession" description="选择一个会话开始沟通" />
          <template v-else>
            <div class="history-loader">
              <span v-if="loadingMessages">加载消息中...</span>
              <span v-else-if="!hasMoreHistory" class="end-tip">没有更多历史消息了</span>
              <span v-else class="load-more-tip">上拉查看更多消息</span>
            </div>

            <div class="message-list">
              <TransitionGroup name="msg-fade">
                <div
                  v-for="msg in activeMessages"
                  :key="msg.id"
                  :class="['message-item', msg.fromUserId === currentUserId ? 'mine' : 'other']"
                >
                  <el-avatar
                    v-if="msg.fromUserId !== currentUserId"
                    :size="32"
                    :src="activeSession.peerAvatar || defaultAvatar"
                    class="msg-avatar"
                  />
                  <div class="bubble-group">
                    <div class="message-bubble">
                      <!-- 消息内容渲染 -->
                      <template v-if="getResumeRequestPayload(msg)">
                        <div class="action-card request-card">
                          <div class="card-header">
                            <el-icon color="#0071e3"><DocumentChecked /></el-icon>
                            <span>附件简历申请</span>
                          </div>
                          <p class="card-text">{{ getResumeRequestPayload(msg).note }}</p>
                          <div class="card-footer" v-if="isApplicant && canGrantAttachment">
                            <el-button type="primary" size="small" round @click="grantAttachment">同意授权</el-button>
                          </div>
                          <div class="card-status" v-else>{{ attachStatus.label }}</div>
                        </div>
                      </template>

                      <template v-else-if="getJobSwitchPayload(msg)">
                        <div class="action-card info-card">
                          <div class="card-header">
                            <el-icon color="#f59e0b"><Refresh /></el-icon>
                            <span>岗位已变更</span>
                          </div>
                          <p class="card-text">当前沟通岗位已更新为：<b>{{ getJobSwitchPayload(msg).jobTitle }}</b></p>
                        </div>
                      </template>

                      <template v-else-if="getResumePayload(msg)">
                        <div class="action-card resume-card">
                          <div class="card-header">
                            <el-icon color="#10b981"><Files /></el-icon>
                            <span>附件简历</span>
                          </div>
                          <p class="card-text">{{ getResumePayload(msg).fileName }}</p>
                          <div class="attachment-actions">
                            <el-button plain size="small" round @click="openAttachmentPreview(getResumePayload(msg).fileUrl, getResumePayload(msg).fileName)">在线查看</el-button>
                            <el-button size="small" round type="primary" plain @click="downloadAttachmentFile(getResumePayload(msg).fileUrl, getResumePayload(msg).fileName)">下载附件</el-button>
                          </div>
                        </div>
                      </template>

                      <template v-else>
                        <span class="text-content">{{ msg.content }}</span>
                      </template>
                    </div>
                    <div class="message-meta">
                      <span class="msg-time">{{ formatMessageTime(msg.createTime) }}</span>
                      <template v-if="msg.fromUserId === currentUserId">
                        <span v-if="msg.status === 'sending'" class="msg-status">发送中</span>
                        <span v-else-if="msg.status === 'failed'" class="msg-status failed">
                          <el-button text size="small" @click="retrySend(msg)">重试</el-button>
                        </span>
                        <span v-else class="msg-status">{{ getReadLabel(msg) }}</span>
                      </template>
                    </div>
                  </div>
                </div>
              </TransitionGroup>
            </div>
          </template>
        </div>

        <!-- 输入区 (Apple Input Design) -->
        <div v-if="activeSession" class="chat-input-area">
          <div class="input-toolbar">
            <el-button 
              v-if="showSendAttachment" 
              class="action-pill-btn resume-btn"
              @click="sendAttachmentResume"
            >
              <el-icon><Files /></el-icon>
              <span>发送附件简历</span>
            </el-button>

            <el-button 
              v-if="requestButtonState" 
              :disabled="requestButtonState.disabled" 
              class="action-pill-btn request-btn"
              @click="requestAttachment"
            >
              <el-icon><DocumentChecked /></el-icon>
              <span>{{ requestButtonState.label }}</span>
            </el-button>

            <div
              v-if="attachmentGuide"
              :class="['attachment-guide', `attachment-guide--${attachmentGuide.tone}`]"
            >
              <el-icon><InfoFilled /></el-icon>
              <span>{{ attachmentGuide.text }}</span>
            </div>
          </div>
          <div class="input-wrapper">
            <el-input
              v-model="draft"
              type="textarea"
              :autosize="{ minRows: 1, maxRows: 5 }"
              placeholder="输入消息..."
              @keydown="handleKeydown"
            />
            <el-button 
              type="primary" 
              class="send-btn" 
              icon="Top"
              circle
              :disabled="!draft.trim()"
              @click="sendMessage"
            />
          </div>
          <div class="input-footer">
            <span>Enter 发送 · Shift + Enter 换行</span>
          </div>
        </div>
      </section>
    </div>

    <!-- 弹窗部分 -->
    <el-dialog v-model="jobSelectVisible" title="切换当前沟通岗位" width="min(520px, 92vw)" center round custom-class="apple-job-dialog">
      <div v-loading="jobSelectLoading" class="job-selection-body">
        <p class="dialog-desc">更改后，你们的沟通将以此岗位为上下文。求职者端将收到变更通知。</p>
        <el-radio-group v-model="selectedJobId" class="job-card-group">
          <div 
            v-for="job in availableJobOptions" 
            :key="job.id" 
            :class="['job-card-item', { active: selectedJobId === job.id }]"
            @click="selectedJobId = job.id"
          >
            <el-radio :label="job.id">
              <div class="job-card-content">
                <div class="job-card-title">{{ job.title }}</div>
                <div class="job-card-meta">
                  <span class="status-indicator active">进行中</span>
                  <span class="dot">·</span>
                  <span class="salary">{{ formatJobSalary(job) }}</span>
                </div>
              </div>
            </el-radio>
          </div>
        </el-radio-group>
        <el-empty v-if="!availableJobOptions.length && !jobSelectLoading" description="暂无可用职位" />
      </div>
      <template #footer>
        <div class="dialog-footer-btns">
          <el-button round class="cancel-btn" @click="jobSelectVisible = false">暂不切换</el-button>
          <el-button type="primary" round class="confirm-btn" :disabled="!selectedJobId" @click="confirmJobSelection">确认切换</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="resumeDialogVisible" title="在线简历" width="min(920px, 94vw)" top="5vh" round class="apple-resume-dialog">
      <div v-loading="resumeLoading">
        <div v-if="currentResume" class="resume-panel">
          <div class="resume-hero">
            <div class="resume-hero-main">
              <el-avatar :size="64" :src="currentResume.avatar" class="resume-avatar">
                {{ currentResume.name.charAt(0) }}
              </el-avatar>
              <div class="resume-base">
                <div class="resume-name">{{ currentResume.name }}</div>
                <div class="resume-meta">
                  {{ formatGender(currentResume.gender) }} /
                  {{ currentResume.age || '-' }}岁 /
                  {{ currentResume.workYears || '-' }}
                </div>
                <div class="resume-tags-row">
                  <el-tag v-if="currentResume.expectJob" size="small" type="primary" round>期望：{{ currentResume.expectJob }}</el-tag>
                  <el-tag v-if="currentResume.expectSalary" size="small" type="primary" round>薪资：{{ currentResume.expectSalary }}</el-tag>
                  <el-tag v-if="currentResume.city" size="small" type="info" round>城市：{{ currentResume.city }}</el-tag>
                  <el-tag v-if="currentResume.degree" size="small" round>学历：{{ currentResume.degree }}</el-tag>
                  <el-tag v-if="currentResume.currentStatus" size="small" type="info" round>状态：{{ currentResume.currentStatus }}</el-tag>
                </div>
              </div>
            </div>
            <div class="resume-hero-side">
              <div class="hero-item">
                <div class="hero-label">手机号</div>
                <div class="hero-value">{{ currentResume.phone || '-' }}</div>
              </div>
              <div class="hero-item">
                <div class="hero-label">邮箱</div>
                <div class="hero-value">{{ currentResume.email || '-' }}</div>
              </div>
            </div>
          </div>

          <div class="resume-layout">
            <div class="resume-left">
              <div class="resume-section">
                <div class="section-title">基础信息</div>
                <el-descriptions :column="1" border class="resume-desc">
                  <el-descriptions-item label="当前身份">{{ formatIdentity(currentResume.currentIdentity) }}</el-descriptions-item>
                  <el-descriptions-item label="求职状态">{{ currentResume.currentStatus || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="期望城市">{{ currentResume.city || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="期望职位">{{ currentResume.expectJob || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="期望薪资">{{ currentResume.expectSalary || '-' }}</el-descriptions-item>
                </el-descriptions>
              </div>

              <div class="resume-section">
                <div class="section-title">教育信息</div>
                <el-descriptions :column="1" border class="resume-desc">
                  <el-descriptions-item label="毕业院校">{{ currentResume.collage || currentResume.college || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="专业">{{ currentResume.major || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="毕业年份">{{ currentResume.gradYear || '-' }}</el-descriptions-item>
                </el-descriptions>
              </div>
            </div>
            <div class="resume-right">
              <el-collapse v-model="resumeCollapseActive" class="resume-collapse">
                <el-collapse-item name="skills">
                  <template #title>技能标签</template>
                  <div class="resume-block">
                    <div class="resume-tags">
                      <el-tag v-for="tag in currentResume.skills" :key="tag" size="small" type="info" round>
                        {{ tag }}
                      </el-tag>
                      <span v-if="!currentResume.skills || currentResume.skills.length === 0" class="resume-empty">暂无</span>
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
                    <div v-if="currentResume.educationList && currentResume.educationList.length" class="resume-list">
                      <div v-for="(edu, index) in currentResume.educationList" :key="index" class="resume-list-item">
                        <div class="resume-item-title">{{ buildEduTitle(edu) }}</div>
                        <div class="resume-item-meta">{{ formatRange(edu.timeRange) }}</div>
                      </div>
                    </div>
                    <div v-else class="resume-empty">暂无</div>
                  </div>
                </el-collapse-item>

                <el-collapse-item name="experience">
                  <template #title>工作经历</template>
                  <div class="resume-block">
                    <div v-if="currentResume.experienceList && currentResume.experienceList.length" class="resume-list">
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
                    <div v-if="currentResume.projectList && currentResume.projectList.length" class="resume-list">
                      <div v-for="(proj, index) in currentResume.projectList" :key="index" class="resume-list-item">
                        <div class="resume-item-title">{{ buildProjectTitle(proj) }}</div>
                        <div class="resume-item-meta">{{ formatRange(proj.timeRange) }}</div>
                        <div class="resume-item-text">{{ proj.description || '暂无描述' }}</div>
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
    
        <!-- 公司详情弹窗 (Apple Style) -->
        <el-dialog v-model="companyDialogVisible" title="公司信息" width="min(500px, 92vw)" center round class="apple-company-dialog">
          <div v-loading="companyLoading" class="company-detail-content">
            <div v-if="currentCompany" class="company-card">
              <div class="company-main">
                <el-avatar :size="72" :src="currentCompany.logo || defaultAvatar" class="company-logo" />
                <h2 class="company-name">{{ currentCompany.name || '公司名称' }}</h2>
                <div class="company-tags">
                  <el-tag size="small" round effect="plain">{{ currentCompany.industry || '行业未填' }}</el-tag>
                  <el-tag size="small" type="info" round effect="plain">{{ currentCompany.scale || '规模未填' }}</el-tag>
                </div>
              </div>
              <el-divider />
              <div class="company-section">
                <h4 class="section-title">公司介绍</h4>
                <p class="company-intro">{{ currentCompany.intro || '暂无介绍' }}</p>
              </div>
              <div class="company-section" v-if="currentCompany.address">
                <h4 class="section-title">办公地址</h4>
                <p class="company-addr">{{ currentCompany.address }}</p>
              </div>
            </div>
          </div>
          <template #footer>
            <el-button round type="primary" @click="companyDialogVisible = false">了解了</el-button>
          </template>
        </el-dialog>
    
        <el-dialog v-model="previewVisible"
     width="92%" top="2vh" :show-close="false" destroy-on-close @closed="cleanupPreview" class="apple-preview-dialog">
      <div class="preview-header">
        <span class="preview-title">{{ previewName }}</span>
        <div class="preview-actions">
           <el-button icon="Minus" circle size="small" @click="zoomOut" />
           <span class="zoom-text">{{ Math.round(previewScale * 100) }}%</span>
           <el-button icon="Plus" circle size="small" @click="zoomIn" />
           <el-divider direction="vertical" />
           <el-button size="small" plain @click="downloadAttachmentFile(previewSourceUrl, previewName)">下载附件</el-button>
           <el-button type="danger" icon="Close" circle size="small" @click="previewVisible = false" />
        </div>
      </div>
      <div class="preview-container">
        <div class="preview-zoom-layer" :style="{ zoom: previewScale }">
          <iframe
            v-if="previewUrl && previewType === 'pdf'"
            :src="previewUrl"
            frameborder="0"
            class="preview-iframe"
          ></iframe>
          <div v-else-if="previewUrl && previewType === 'image'" class="preview-image-wrap">
            <img :src="previewUrl" :alt="previewName" class="preview-image" />
          </div>
          <el-empty v-else description="暂无可预览内容" />
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch, nextTick, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import { InfoFilled, DocumentChecked, Refresh, Files, Briefcase, ChatLineRound } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { useChatStore } from '@/stores/chat'
import request from '@/utils/request'
import { formatDateTime, formatDateYMD, formatSalaryK, formatTimeHM } from '@/utils/format'
import { formatFileUrl, canPreviewFile, getFilePreviewType, getFileName, downloadFileByUrl } from '@/utils/file'
import { getChatSessionList, getChatMessageList, markChatRead, updateChatSessionJob, sendChatMessage } from '@/api/chat'
import { getTalentDetail } from '@/api/talent'
import { getMerchantJobs } from '@/api/job'
import { getResumeAttachment } from '@/api/applicant'
import { getResumeAttachmentPermissionStatus, grantResumeAttachmentPermission, requestResumeAttachmentPermission } from '@/api/attachment'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const chatStore = useChatStore()
const defaultAvatar = 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'

// --- 状态定义 ---
const attachmentPermission = ref({ status: 'NONE', expireTime: '' })
const attachmentPermissionLoading = ref(false)
const sessions = ref([])
const activeSessionId = ref(null)
const messageMap = reactive({})
const pageStateMap = reactive({})
const keyword = ref('')
const draft = ref('')
const loadingSessions = ref(false)
const loadingMessages = ref(false)
const attachmentInfo = ref({ fileUrl: '', fileName: '' })
const resumeDialogVisible = ref(false)
const resumeLoading = ref(false)
const currentResume = ref(null)
const resumeCollapseActive = ref(['skills', 'advantage', 'education', 'experience'])
const previewVisible = ref(false)
const previewUrl = ref('')
const previewSourceUrl = ref('')
const previewObjectUrl = ref('')
const previewName = ref('')
const previewType = ref('pdf')
const previewScale = ref(1)
const jobSelectVisible = ref(false)
const jobSelectLoading = ref(false)
const jobOptions = ref([])
const selectedJobId = ref(null)
const jobSelectMode = ref('request')
const isMobile = ref(false)
const showSessionList = ref(true)
const wsStatus = ref('disabled')
const wsInstance = ref(null)
const wsHeartbeatTimer = ref(null)
const wsReconnectTimer = ref(null)
const wsManualClose = ref(false)
let resizeHandler = null

const RESUME_MESSAGE_PREFIX = '[附件简历]'
const RESUME_REQUEST_PREFIX = '[附件申请]'
const JOB_SWITCH_PREFIX = '[岗位变更]'

// --- 核心工具函数 ---
const toSafeNumber = (value) => {
  const num = Number(value)
  return Number.isFinite(num) ? num : 0
}

const toPositiveNumber = (value) => {
  const num = Number(value)
  return Number.isFinite(num) && num > 0 ? num : null
}

const toSafeString = (value) => {
  if (value === null || value === undefined) return ''
  return String(value).trim()
}

const isTruthyQueryValue = (value) => {
  const normalized = String(value ?? '').trim().toLowerCase()
  return normalized === '1' || normalized === 'true' || normalized === 'yes'
}

const getUserIdFromToken = () => {
  const token = userStore.token
  if (!token) return 0
  try {
    const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')))
    return Number(payload.sub || 0)
  } catch (e) { return 0 }
}

const extractList = (raw) => {
  if (Array.isArray(raw)) return raw
  if (Array.isArray(raw?.records)) return raw.records
  if (Array.isArray(raw?.list)) return raw.list
  return []
}

const inferSessionId = (item) => {
  const explicitSessionId = toPositiveNumber(item.sessionId ?? item.chatSessionId)
  if (explicitSessionId) return explicitSessionId

  const itemId = toPositiveNumber(item.id ?? item.chatId)
  const peerId = toPositiveNumber(item.peerId ?? item.targetId ?? item.toUserId ?? 0)
  if (itemId && peerId && itemId !== peerId) {
    return itemId
  }
  return null
}

const buildSessionRuntimeId = ({ sessionId, peerId }) => {
  if (toPositiveNumber(sessionId)) return `session:${toPositiveNumber(sessionId)}`
  if (toPositiveNumber(peerId)) return `peer:${toPositiveNumber(peerId)}`
  return `temp:${Date.now()}-${Math.random()}`
}

const normalizeSession = (item) => {
  const sessionId = inferSessionId(item)
  const peerId = toSafeNumber(item.peerId ?? item.targetId ?? item.toUserId ?? 0)
  return {
    id: buildSessionRuntimeId({ sessionId, peerId }),
    sessionId,
    peerId,
    peerName: item.peerName ?? item.targetName ?? item.nickname ?? '未知用户',
    companyName: item.companyName ?? item.merchantName ?? '',
    peerAvatar: item.peerAvatar ?? item.avatar ?? '',
    jobId: toPositiveNumber(item.jobId),
    jobTitle: item.jobTitle ?? '',
    jobKey: item.jobKey ?? '',
    lastMessage: item.lastMessage ?? item.lastContent ?? '',
    lastTime: item.lastTime ?? item.updateTime ?? '',
    unreadCount: toSafeNumber(item.unreadCount ?? 0)
  }
}

const normalizeMessage = (item) => {
  return {
    id: item.id ?? Date.now() + Math.random(),
    fromUserId: item.fromUserId ?? item.senderId ?? 0,
    toUserId: item.toUserId ?? item.receiverId ?? 0,
    content: item.content ?? item.message ?? '',
    createTime: item.createTime ?? item.sendTime ?? new Date().toISOString(),
    status: item.status ?? 'sent',
    isRead: item.isRead ?? 0
  }
}

// --- 简历相关解析函数恢复 ---
const normalizeSkills = (skills) => {
  if (Array.isArray(skills)) return skills.filter(Boolean)
  if (typeof skills !== 'string') return []
  const trimmed = skills.trim()
  if (trimmed.startsWith('[') && trimmed.endsWith(']')) {
    try {
      const parsed = JSON.parse(trimmed)
      if (Array.isArray(parsed)) {
        return parsed.map(item => String(item || '').trim()).filter(Boolean)
      }
    } catch (e) {
      // JSON 解析失败时回退分隔符拆分
    }
  }
  return trimmed.split(/[,，、]/).map(s => s.trim()).filter(Boolean)
}

const isSameSessionIdentity = (left, right) => {
  if (!left || !right) return false
  if (toPositiveNumber(left.sessionId) && toPositiveNumber(right.sessionId)) {
    return Number(left.sessionId) === Number(right.sessionId)
  }
  return Number(left.peerId) === Number(right.peerId)
}

const moveSessionRuntimeState = (fromId, toId) => {
  if (!fromId || !toId || fromId === toId) return
  if (messageMap[fromId] && !messageMap[toId]) {
    messageMap[toId] = messageMap[fromId]
  }
  if (pageStateMap[fromId] && !pageStateMap[toId]) {
    pageStateMap[toId] = pageStateMap[fromId]
  }
  delete messageMap[fromId]
  delete pageStateMap[fromId]
  if (activeSessionId.value === fromId) {
    activeSessionId.value = toId
  }
}

const mergeSessionPresentation = (target, source) => {
  if (!target || !source) return target
  if (!target.sessionId && source.sessionId) target.sessionId = source.sessionId
  if (!target.peerName && source.peerName) target.peerName = source.peerName
  if (!target.companyName && source.companyName) target.companyName = source.companyName
  if (!target.peerAvatar && source.peerAvatar) target.peerAvatar = source.peerAvatar
  if (!target.jobId && source.jobId) target.jobId = source.jobId
  if (!target.jobTitle && source.jobTitle) target.jobTitle = source.jobTitle
  return target
}

const applySessionIdentity = (session, payload = {}) => {
  if (!session) return null
  const previousId = session.id
  const nextSessionId = inferSessionId(payload) || session.sessionId
  const nextPeerId = toPositiveNumber(payload.peerId) || session.peerId
  session.sessionId = nextSessionId
  session.peerId = nextPeerId
  session.id = buildSessionRuntimeId({ sessionId: nextSessionId, peerId: nextPeerId })
  if (Object.prototype.hasOwnProperty.call(payload, 'jobId')) {
    session.jobId = toPositiveNumber(payload.jobId)
  }
  if (Object.prototype.hasOwnProperty.call(payload, 'jobTitle') && payload.jobTitle) {
    session.jobTitle = payload.jobTitle
  }
  if (Object.prototype.hasOwnProperty.call(payload, 'jobKey') && payload.jobKey) {
    session.jobKey = payload.jobKey
  }
  moveSessionRuntimeState(previousId, session.id)
  return session
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

const parseResumeJsonList = (json) => {
  if (!json) return []
  if (Array.isArray(json)) return json.filter(isValidResumeItem)
  try {
    const parsed = JSON.parse(json)
    return Array.isArray(parsed) ? parsed.filter(isValidResumeItem) : []
  } catch (e) {
    return []
  }
}

const normalizeResumeCandidate = (item) => {
  const name = item?.name || item?.realName || item?.nickname || item?.username || '求职者'
  return {
    userId: item?.userId,
    name,
    avatar: item?.avatar || '',
    gender: item?.gender ?? null,
    age: item?.age ?? null,
    phone: item?.phone || '',
    email: item?.email || '',
    currentIdentity: item?.currentIdentity || '',
    currentStatus: item?.currentStatus || '',
    degree: item?.degree || '未知',
    workYears: item?.workYears || '暂无',
    collage: item?.collage || item?.college || '',
    major: item?.major || '',
    gradYear: item?.gradYear || null,
    city: item?.city || item?.expectCity || '不限',
    expectJob: item?.expectJob || '',
    expectSalary: item?.expectSalary || '面议',
    skills: normalizeSkills(item?.skills),
    summary: item?.summary || item?.advantage || '暂无',
    educationList: parseResumeJsonList(item?.educationJson),
    experienceList: parseResumeJsonList(item?.experienceJson),
    projectList: parseResumeJsonList(item?.projectJson)
  }
}

const formatRange = (range) => {
  if (!Array.isArray(range) || range.length < 2) return '-'
  return `${range[0] || '-'} 至 ${range[1] || '-'}`
}

const buildEduTitle = (e) => [e.school, e.major, e.degree].filter(Boolean).join(' · ') || '未填写'
const buildExpTitle = (e) => [e.company, e.position].filter(Boolean).join(' · ') || '未填写'
const buildProjectTitle = (e) => [e.name, e.role].filter(Boolean).join(' · ') || '未填写'
const formatGender = (g) => g === 1 ? '男' : (g === 2 ? '女' : '保密')
const formatIdentity = (v) => {
  const map = { 'STUDENT': '在校生', 'FRESH_GRAD': '应届生', 'WORKER': '职场人士' }
  return map[v] || v || '-'
}

// --- 计算属性 ---
const isApplicant = computed(() => userStore.role === 'APPLICANT')
const currentUserId = computed(() => Number(userStore.userInfo?.id || userStore.userInfo?.userId || getUserIdFromToken() || 0))
const activeSession = computed(() => sessions.value.find(item => item.id === activeSessionId.value) || null)
const activeMessages = computed(() => messageMap[activeSessionId.value] || [])
const filteredSessions = computed(() => {
  const key = keyword.value.trim().toLowerCase()
  let list = sessions.value
  if (key) {
    list = list.filter(s => 
      s.peerName.toLowerCase().includes(key) || 
      (s.jobTitle || '').toLowerCase().includes(key) ||
      (s.companyName || '').toLowerCase().includes(key)
    )
  }
  return [...list].sort((a, b) => {
    const timeA = parseDate(a.lastTime || a.enterTime || a.createTime)?.getTime() || 0
    const timeB = parseDate(b.lastTime || b.enterTime || b.createTime)?.getTime() || 0
    return timeB - timeA
  })
})

const wsStatusText = computed(() => {
  const map = { disabled: '实时通讯未启用', connecting: '正在连接...', connected: '实时在线', disconnected: '连接已断开' }
  return map[wsStatus.value] || '离线模式'
})

const attachmentPermissionState = computed(() => {
  if (attachmentPermissionLoading.value) return 'CHECKING'
  return attachmentPermission.value?.status || 'NONE'
})

const attachStatus = computed(() => {
  const status = attachmentPermissionState.value
  const map = {
    GRANTED: '已授权',
    PENDING: '待处理',
    REJECTED: '已拒绝',
    NONE: '未申请',
    NO_DELIVERY: '待投递',
    CHECKING: '识别中',
    UNKNOWN: '状态异常'
  }
  return { label: map[status] || '未知', type: status === 'GRANTED' ? 'success' : 'info' }
})

const canGrantAttachment = computed(() => isApplicant.value && attachmentPermission.value?.status === 'PENDING')

const companyDialogVisible = ref(false)
const companyLoading = ref(false)
const currentCompany = ref(null)

const openOnlineResumeFromChat = async () => {
  if (!activeSession.value) return
  
  if (isApplicant.value) {
    companyDialogVisible.value = true
    companyLoading.value = true
    try {
      const peerId = activeSession.value.peerId
      // 使用 request 库，确保携带 Token 且遵循 baseURL
      const res = await request.get(`/merchant/detail/${peerId}`)
      
      if (res.code === 200) {
        const rawData = res.data || {}
        currentCompany.value = {
          name: rawData.companyName || activeSession.value.companyName,
          logo: rawData.companyLogo || activeSession.value.peerAvatar,
          industry: rawData.industry || '行业未填',
          scale: rawData.scale || '规模未填',
          financing: rawData.financing || '未融资',
          intro: rawData.description || '该商家暂未填写详细介绍',
          address: rawData.address || '地址暂未公开'
        }
      } else {
        throw new Error(res.msg || '后端返回错误')
      }
    } catch (e) {
      console.error('[IM] 获取公司详情彻底失败:', e)
      currentCompany.value = { 
        name: activeSession.value.companyName || '未知企业',
        logo: activeSession.value.peerAvatar,
        industry: '互联网',
        scale: '20-99人',
        intro: `无法加载详细内容。可能原因：\n1. 后端代码修改后未重启\n2. 数据库中该商家(ID:${activeSession.value.peerId})资料不完整\n\n错误信息: ${e.message}`,
        address: '请稍后再试'
      }
    } finally { companyLoading.value = false }
  } else {
    resumeDialogVisible.value = true
    resumeLoading.value = true
    try {
      const res = await getTalentDetail(activeSession.value.peerId)
      currentResume.value = normalizeResumeCandidate(res.data || res)
    } finally { resumeLoading.value = false }
  }
}
const canSwitchJob = computed(() => !isApplicant.value && !!activeSession.value)

const requestButtonState = computed(() => {
  if (isApplicant.value || !activeSession.value) return null
  const status = attachmentPermissionState.value
  if (status === 'CHECKING') return { label: '识别中', disabled: true }
  if (status === 'PENDING') return { label: '申请中', disabled: true }
  if (status === 'GRANTED') return { label: '已授权', disabled: true }
  if (status === 'NO_DELIVERY') return { label: '待投递后可申请', disabled: true }
  if (status === 'UNKNOWN') return { label: '状态待刷新', disabled: true }
  return { label: '申请附件', disabled: false }
})

const attachmentGuide = computed(() => {
  if (isApplicant.value || !activeSession.value) return null
  const guideMap = {
    CHECKING: { tone: 'checking', text: '正在识别双方关系与附件授权状态。' },
    NO_DELIVERY: { tone: 'disconnected', text: '当前未建立投递关系，可继续聊天，但需待对方投递后才能申请附件简历。' },
    NONE: { tone: 'ready', text: '已建立投递关系，可在当前会话申请查看附件简历。' },
    PENDING: { tone: 'pending', text: '附件申请已发送，等待求职者确认。' },
    GRANTED: { tone: 'granted', text: '对方已授权，可在消息中查看或下载附件简历。' },
    REJECTED: { tone: 'rejected', text: '对方暂未同意授权，建议继续沟通后再申请。' },
    UNKNOWN: { tone: 'unknown', text: '附件状态暂不可用，可稍后切换会话或刷新后重试。' }
  }
  return guideMap[attachmentPermissionState.value] || guideMap.UNKNOWN
})

const showSendAttachment = computed(() => isApplicant.value && !!activeSession.value)
const hasResumeAttachment = computed(() => !!attachmentInfo.value?.fileUrl)
const availableJobOptions = computed(() => jobOptions.value.filter(j => (j.status ?? 1) === 1 && (j.auditStatus ?? 1) === 1))

const clearReconnectTimer = () => {
  if (wsReconnectTimer.value) {
    clearTimeout(wsReconnectTimer.value)
    wsReconnectTimer.value = null
  }
}

const scheduleReconnect = () => {
  if (wsManualClose.value) return
  clearReconnectTimer()
  wsReconnectTimer.value = setTimeout(() => {
    wsReconnectTimer.value = null
    connectWebSocket()
  }, 3000)
}

const registerChatE2EHooks = () => {
  if (typeof window === 'undefined' || window.__PLAYWRIGHT_E2E__ !== true) return

  // 仅在 Playwright E2E 模式下暴露最小测试钩子，便于稳定模拟断线重连。
  window.__chatE2E = {
    forceCloseWebSocket() {
      try {
        wsInstance.value?.close()
      } catch (e) {}
    },
    getWsStatus() {
      return wsStatus.value
    }
  }
}

const cleanupChatE2EHooks = () => {
  if (typeof window === 'undefined' || window.__PLAYWRIGHT_E2E__ !== true) return
  delete window.__chatE2E
}

// --- WebSocket ---
const connectWebSocket = () => {
  if (import.meta.env.VITE_ENABLE_CHAT_WS === 'false') { wsStatus.value = 'disabled'; return }
  const baseUrl = import.meta.env.VITE_APP_WS_URL || (import.meta.env.VITE_APP_BASE_URL || 'http://localhost:8080').replace(/^http/, 'ws')
  const token = userStore.token
  if (!token) return
  clearReconnectTimer()
  if (wsInstance.value && wsInstance.value.readyState !== WebSocket.CLOSED) {
    try {
      wsInstance.value.close()
    } catch (e) {}
  }
  wsStatus.value = 'connecting'
  wsInstance.value = new WebSocket(`${baseUrl}/ws?token=${encodeURIComponent(token)}`)
  wsInstance.value.onopen = () => {
    clearReconnectTimer()
    wsStatus.value = 'connected'
    startHeartbeat()
  }
  wsInstance.value.onmessage = (e) => {
    if (e.data === 'PONG') return
    try { handleIncomingMessage(JSON.parse(e.data)) } catch (err) {}
  }
  wsInstance.value.onclose = () => {
    wsStatus.value = 'disconnected'
    if (wsHeartbeatTimer.value) {
      clearInterval(wsHeartbeatTimer.value)
      wsHeartbeatTimer.value = null
    }
    wsInstance.value = null
    scheduleReconnect()
  }
}

const handleIncomingMessage = (data) => {
  const msg = normalizeMessage(data)
  const peerId = msg.fromUserId === currentUserId.value ? msg.toUserId : msg.fromUserId
  let session = sessions.value.find(s => s.peerId === peerId)
  if (!session) { loadSessions(); return }
  if (!messageMap[session.id]) messageMap[session.id] = []
  messageMap[session.id].push(msg)
  session.lastMessage = msg.content; session.lastTime = msg.createTime
  if (activeSessionId.value === session.id) {
    // 当前会话正在可见时，实时消息虽然不会先累加本地未读，
    // 但仍需要主动同步后端已读状态，避免刷新后未读数重新出现。
    void markSessionRead(session, { forceSync: true })
    scrollToBottom()
  }
  else { session.unreadCount++; syncTotalUnread() }
}

const syncTotalUnread = () => {
  const total = sessions.value.reduce((s, i) => s + (i.unreadCount || 0), 0)
  chatStore.setUnreadCount(total)
}

const startHeartbeat = () => {
  if (wsHeartbeatTimer.value) clearInterval(wsHeartbeatTimer.value)
  wsHeartbeatTimer.value = setInterval(() => {
    if (wsInstance.value?.readyState === WebSocket.OPEN) wsInstance.value.send('PING')
  }, 25000)
}

// --- 业务逻辑 ---
const scrollToBottom = () => {
  nextTick(() => {
    setTimeout(() => {
      if (messageWrap.value) {
        messageWrap.value.scrollTo({ top: messageWrap.value.scrollHeight, behavior: 'smooth' })
      }
    }, 150)
  })
}

const sendMessage = async () => {
  const content = draft.value.trim()
  if (!content || !activeSession.value) return
  const msg = reactive({
    id: Date.now(), fromUserId: currentUserId.value, toUserId: activeSession.value.peerId,
    content, createTime: new Date().toISOString(), status: 'sending', isRead: 0
  })
  if (!messageMap[activeSessionId.value]) messageMap[activeSessionId.value] = []
  messageMap[activeSessionId.value].push(msg)
  draft.value = ''; scrollToBottom()
  try {
    if (wsStatus.value === 'connected') {
      wsInstance.value.send(JSON.stringify({ toUserId: msg.toUserId, content: msg.content }))
      msg.status = 'sent'
    } else {
      await sendChatMessage({ toUserId: msg.toUserId, content: msg.content })
      msg.status = 'sent'
    }
    activeSession.value.lastMessage = content; activeSession.value.lastTime = msg.createTime
  } catch (e) { msg.status = 'failed'; ElMessage.error('发送失败') }
}

const loadSessions = async () => {
  loadingSessions.value = true
  try {
    const previousSessions = [...sessions.value]
    const previousActive = activeSession.value ? { ...activeSession.value } : null
    const res = await getChatSessionList()
    const serverSessions = extractList(res.data || res).map(normalizeSession)
    const localOnly = previousSessions.filter(local =>
      !serverSessions.some(server => Number(server.peerId) === Number(local.peerId))
    )

    serverSessions.forEach((serverSession) => {
      const localSession = previousSessions.find(local => Number(local.peerId) === Number(serverSession.peerId))
      if (!localSession) return
      mergeSessionPresentation(serverSession, localSession)
      moveSessionRuntimeState(localSession.id, serverSession.id)
    })

    sessions.value = [...serverSessions, ...localOnly]

    if (previousActive) {
      const resolvedActive = sessions.value.find(item => isSameSessionIdentity(item, previousActive))
      if (resolvedActive) {
        activeSessionId.value = resolvedActive.id
      }
    }
    syncTotalUnread()
  } finally { loadingSessions.value = false }
}

const loadMessages = async (session, options = {}) => {
  const wrap = messageWrap.value
  const prevScrollHeight = options.append ? (wrap?.scrollHeight || 0) : 0
  const prevScrollTop = options.append ? (wrap?.scrollTop || 0) : 0
  loadingMessages.value = true
  try {
    const params = {
      pageNum: options.pageNum || 1,
      pageSize: 20
    }
    if (session.sessionId) params.sessionId = session.sessionId
    if (session.peerId) params.peerId = session.peerId
    const res = await getChatMessageList(params)
    // 后端已按时间正序返回，这里不要再 reverse，避免历史消息顺序错乱
    const list = extractList(res.data || res).map(normalizeMessage)
    messageMap[session.id] = options.append ? [...list, ...(messageMap[session.id] || [])] : list
    pageStateMap[session.id] = { hasMore: list.length >= 20, pageNum: options.pageNum || 1 }
  } finally {
    loadingMessages.value = false
    await nextTick()
    if (options.append && wrap) {
      // 追加历史消息后保持当前阅读位置，避免“加载更多后跳到底部”
      const delta = wrap.scrollHeight - prevScrollHeight
      wrap.scrollTop = prevScrollTop + Math.max(delta, 0)
    } else {
      scrollToBottom()
    }
  }
}

const selectSession = async (session) => {
  activeSessionId.value = session.id
  if (isMobile.value) showSessionList.value = false
  await refreshAttachmentPermission()
  if (!messageMap[session.id]) await loadMessages(session)
  else scrollToBottom()
  markSessionRead(session)
}

const markSessionRead = async (session, options = {}) => {
  const forceSync = options.forceSync === true
  const hasUnread = session.unreadCount > 0

  if (hasUnread) {
    session.unreadCount = 0
    syncTotalUnread()
  }

  if (hasUnread || forceSync) {
    await markChatRead({
      sessionId: session.sessionId,
      peerId: session.peerId
    }).catch(() => {})
  }
}

// --- 时间解析器 ---
const parseDate = (val) => {
  if (!val) return null
  if (val instanceof Date) return val
  if (typeof val === 'number') return new Date(val)
  if (typeof val === 'string') {
    const s = val.replace(/-/g, '/').replace(/T/g, ' ').split('.')[0]
    const d = new Date(s)
    return isNaN(d.getTime()) ? new Date(val) : d
  }
  return null
}

const formatListTime = (val) => {
  const d = parseDate(val)
  if (!d) return ''
  const now = new Date()
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  const yesterday = today - 24 * 3600 * 1000
  const sixDaysAgo = today - 6 * 24 * 3600 * 1000
  const dTime = new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()
  if (dTime === today) return formatTimeHM(d)
  if (dTime === yesterday) return '昨天'
  if (dTime > sixDaysAgo) return ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][d.getDay()]
  if (d.getFullYear() === now.getFullYear()) return `${d.getMonth() + 1}/${d.getDate()}`
  return formatDateYMD(d).slice(2)
}

const formatMessageTime = (val) => {
  const d = parseDate(val)
  if (!d) return ''
  const now = new Date()
  const msgDateKey = new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()
  const todayKey = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  if (msgDateKey === todayKey) return formatTimeHM(d)
  return formatDateTime(d)
}

const getSessionDisplayTime = (s) => s.lastTime || s.enterTime || s.createTime || ''
const getDisplayName = (s) => isApplicant.value ? (s.companyName || s.peerName) : s.peerName
const getSessionTag = (s) => ({ text: s.jobTitle || (isApplicant.value ? '未关联职位' : '商家主动沟通'), muted: !s.jobTitle })
const getReadLabel = (m) => m.isRead === 1 ? '已读' : '未读'
const handleKeydown = (e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendMessage() } }
const formatJobSalary = (job) => {
  if (!job) return '面议'
  const salaryText = toSafeString(job.salary)
  if (salaryText) return salaryText
  return formatSalaryK(job.minSalary ?? job.min_salary, job.maxSalary ?? job.max_salary)
}
const getResumeRequestPayload = (msg) => msg.content.startsWith(RESUME_REQUEST_PREFIX) ? { note: msg.content.slice(RESUME_REQUEST_PREFIX.length) } : null
const getJobSwitchPayload = (msg) => msg.content.startsWith(JOB_SWITCH_PREFIX) ? { jobTitle: msg.content.slice(JOB_SWITCH_PREFIX.length) } : null
const getResumePayload = (msg) => {
  if (!msg.content.startsWith(RESUME_MESSAGE_PREFIX)) return null
  const [name, url] = msg.content.slice(RESUME_MESSAGE_PREFIX.length).split('|')
  return { fileName: name, fileUrl: url }
}

const messageWrap = ref(null)
const hasMoreHistory = computed(() => pageStateMap[activeSessionId.value]?.hasMore ?? false)
const handleMessageScroll = () => {
  if (messageWrap.value?.scrollTop < 50 && hasMoreHistory.value && !loadingMessages.value) {
     const state = pageStateMap[activeSessionId.value]
     loadMessages(activeSession.value, { append: true, pageNum: state.pageNum + 1 })
  }
}

const buildRouteSessionMeta = () => {
  const peerId = toPositiveNumber(route.query.targetId)
  if (!peerId) return null
  return {
    sessionId: toPositiveNumber(route.query.sessionId),
    peerId,
    peerName: toSafeString(route.query.targetName),
    companyName: toSafeString(route.query.companyName),
    peerAvatar: toSafeString(route.query.targetAvatar),
    jobId: toPositiveNumber(route.query.jobId),
    jobTitle: toSafeString(route.query.jobTitle),
    autoRequest: isTruthyQueryValue(route.query.autoRequest)
  }
}

const upsertRouteSession = (meta) => {
  if (!meta) return null
  const existing = sessions.value.find(item => Number(item.peerId) === Number(meta.peerId))
  if (existing) {
    if (!existing.sessionId && meta.sessionId) {
      applySessionIdentity(existing, { sessionId: meta.sessionId, peerId: meta.peerId })
    }
    if (!existing.peerName && meta.peerName) existing.peerName = meta.peerName
    if (!existing.companyName && meta.companyName) existing.companyName = meta.companyName
    if (!existing.peerAvatar && meta.peerAvatar) existing.peerAvatar = meta.peerAvatar
    if (!existing.jobId && meta.jobId) existing.jobId = meta.jobId
    if (!existing.jobTitle && meta.jobTitle) existing.jobTitle = meta.jobTitle
    return existing
  }
  const created = normalizeSession({
    sessionId: meta.sessionId,
    peerId: meta.peerId,
    peerName: meta.peerName || '新会话',
    companyName: meta.companyName,
    peerAvatar: meta.peerAvatar,
    jobId: meta.jobId,
    jobTitle: meta.jobTitle,
    unreadCount: 0
  })
  sessions.value.unshift(created)
  syncTotalUnread()
  return created
}

const syncSessionJobFromRoute = async (session, meta) => {
  if (!session || !meta?.jobId || session.jobId) return
  try {
    const res = await updateChatSessionJob({
      sessionId: session.sessionId,
      peerId: session.peerId,
      jobId: meta.jobId
    })
    const payload = res?.data || res || {}
    applySessionIdentity(session, {
      sessionId: payload.sessionId,
      peerId: payload.peerId || session.peerId,
      jobId: payload.jobId || meta.jobId,
      jobTitle: payload.jobTitle || meta.jobTitle || session.jobTitle,
      jobKey: payload.jobKey || session.jobKey
    })
  } catch (e) {
    // 同步失败时保留路由携带的职位信息，至少保证前端上下文可见
    session.jobId = meta.jobId
    if (!session.jobTitle && meta.jobTitle) session.jobTitle = meta.jobTitle
  }
}

const clearAutoRequestFlag = async () => {
  if (!Object.prototype.hasOwnProperty.call(route.query, 'autoRequest')) return
  const nextQuery = { ...route.query }
  delete nextQuery.autoRequest
  await router.replace({ path: route.path, query: nextQuery }).catch(() => {})
}

const maybeAutoRequestFromRoute = async (meta) => {
  if (!meta?.autoRequest || isApplicant.value || !activeSession.value) return
  const status = attachmentPermissionState.value
  if (status !== 'NONE' && status !== 'REJECTED') {
    await clearAutoRequestFlag()
    return
  }
  await requestAttachment()
  await clearAutoRequestFlag()
}

const activateSessionFromRoute = async () => {
  const meta = buildRouteSessionMeta()
  if (meta) {
    const session = upsertRouteSession(meta)
    await syncSessionJobFromRoute(session, meta)
    if (activeSessionId.value !== session.id) {
      await selectSession(session)
    } else {
      await refreshAttachmentPermission()
    }
    await maybeAutoRequestFromRoute(meta)
    return
  }
  if (!activeSession.value && sessions.value.length) {
    await selectSession(sessions.value[0])
  }
}

// --- 业务操作 ---
const refreshAttachmentPermission = async () => {
  if (!activeSession.value) {
    attachmentPermission.value = { status: 'NONE' }
    attachmentPermissionLoading.value = false
    return
  }
  const applicantId = isApplicant.value ? currentUserId.value : activeSession.value.peerId
  const merchantId = isApplicant.value ? activeSession.value.peerId : currentUserId.value
  attachmentPermissionLoading.value = true
  try {
    const res = await getResumeAttachmentPermissionStatus(
      { applicantId, merchantId },
      {
        // 聊天页会在切换会话时自动探测授权状态，未投递属于预期分支，不应打断用户。
        skipBusinessErrorMessage: true,
        skipHttpErrorMessage: true
      }
    )
    attachmentPermission.value = res?.data || { status: 'NONE' }
  } catch (e) {
    attachmentPermission.value = { status: e?.code === 403 ? 'NO_DELIVERY' : 'UNKNOWN' }
  } finally {
    attachmentPermissionLoading.value = false
  }
}

const grantAttachment = async () => {
  try {
    await grantResumeAttachmentPermission({ merchantId: activeSession.value.peerId })
    await refreshAttachmentPermission(); ElMessage.success('已授权')
  } catch (e) { ElMessage.error('授权失败') }
}

const requestAttachment = async () => {
  const status = attachmentPermissionState.value
  if (status === 'CHECKING') {
    ElMessage.info('正在识别双方关系，请稍后再试')
    return
  }
  if (status === 'NO_DELIVERY') {
    ElMessage.info('当前未建立投递关系，可继续沟通并引导对方投递后再申请附件简历')
    return
  }
  if (status === 'UNKNOWN') {
    ElMessage.info('附件状态暂不可用，请稍后重试')
    return
  }
  if (status === 'PENDING') {
    ElMessage.info('附件申请已发送，请等待对方确认')
    return
  }
  if (status === 'GRANTED') {
    ElMessage.success('对方已授权，可直接查看会话中的附件简历')
    return
  }
  if (activeSession.value?.jobId) {
    try {
      await requestResumeAttachmentPermission(
        { applicantId: activeSession.value.peerId },
        {
          // 用户点击申请时使用自定义提示，避免直接暴露后端原始报错文案。
          skipBusinessErrorMessage: true,
          skipHttpErrorMessage: true
        }
      )
      const note = `企业申请查看你的附件简历（岗位：${activeSession.value.jobTitle}）`
      draft.value = `${RESUME_REQUEST_PREFIX}${note}`
      await sendMessage()
      await refreshAttachmentPermission()
      ElMessage.success('附件申请已发送')
    } catch (e) {
      if (e?.code === 403) {
        attachmentPermission.value = { status: 'NO_DELIVERY' }
        ElMessage.info('当前未建立投递关系，可继续沟通并引导对方投递后再申请附件简历')
        return
      }
      ElMessage.error('附件申请失败，请稍后重试')
    }
  } else { openJobSelectDialog('request') }
}

const openJobSelectDialog = async (mode) => {
  jobSelectMode.value = mode; jobSelectLoading.value = true
  try {
    const res = await getMerchantJobs({ current: 1, size: 50 })
    jobOptions.value = extractList(res.data || res); jobSelectVisible.value = true
  } finally { jobSelectLoading.value = false }
}

const confirmJobSelection = async () => {
  const job = jobOptions.value.find(j => j.id === selectedJobId.value)
  if (!job) return
  const res = await updateChatSessionJob({
    sessionId: activeSession.value.sessionId,
    peerId: activeSession.value.peerId,
    jobId: job.id
  })
  const payload = res?.data || res || {}
  applySessionIdentity(activeSession.value, {
    sessionId: payload.sessionId,
    peerId: payload.peerId || activeSession.value.peerId,
    jobId: payload.jobId || job.id,
    jobTitle: payload.jobTitle || job.title,
    jobKey: payload.jobKey || activeSession.value.jobKey
  })
  jobSelectVisible.value = false
  if (jobSelectMode.value === 'switch') {
    draft.value = `${JOB_SWITCH_PREFIX}${job.title}`; await sendMessage()
  } else { requestAttachment() }
}

const sendAttachmentResume = async () => {
  if (!hasResumeAttachment.value) return ElMessage.warning('请先上传附件简历')
  draft.value = `${RESUME_MESSAGE_PREFIX}${attachmentInfo.value.fileName}|${attachmentInfo.value.fileUrl}`; await sendMessage()
}

// 在线查看：仅支持 PDF/图片，不触发下载行为
const openAttachmentPreview = async (url, name) => {
  if (!url) {
    ElMessage.warning('暂无可查看的附件')
    return
  }
  const targetType = getFilePreviewType(name || url)
  if (!canPreviewFile(name || url)) {
    ElMessage.info('该文件格式暂不支持在线查看，请使用“下载附件”')
    return
  }
  if (previewObjectUrl.value) {
    URL.revokeObjectURL(previewObjectUrl.value)
    previewObjectUrl.value = ''
  }
  previewSourceUrl.value = url
  previewName.value = name || getFileName(url)
  previewType.value = targetType
  previewScale.value = 1

  try {
    const fullUrl = formatFileUrl(url)
    // 图片可直接展示；PDF 走 Blob 预览，避免后端 attachment 头导致直接下载
    if (targetType === 'image') {
      previewUrl.value = fullUrl
      previewVisible.value = true
      return
    }
    const response = await axios.get(fullUrl, { responseType: 'blob' })
    const blobUrl = URL.createObjectURL(response.data)
    previewObjectUrl.value = blobUrl
    previewUrl.value = blobUrl
    previewVisible.value = true
  } catch (error) {
    console.error('在线查看附件失败:', error)
    // 兜底：直接使用原始链接尝试内嵌展示，避免预览流程整体不可用
    previewUrl.value = formatFileUrl(url)
    previewVisible.value = true
    ElMessage.warning('预览通道异常，已尝试直接加载附件')
  }
}

// 下载：独立按钮触发，避免“查看”动作与“下载”混用
const downloadAttachmentFile = async (url, name = '') => {
  if (!url) {
    ElMessage.warning('暂无可下载的附件')
    return
  }
  try {
    await downloadFileByUrl(url, name || getFileName(url))
    ElMessage.success('开始下载附件')
  } catch (error) {
    console.error('下载附件失败:', error)
    const fallbackUrl = formatFileUrl(url)
    window.open(fallbackUrl, '_blank')
    ElMessage.warning('下载通道异常，已尝试在新窗口打开附件')
  }
}

const zoomIn = () => { previewScale.value = Math.min(2.5, previewScale.value + 0.1) }
const zoomOut = () => { previewScale.value = Math.max(0.5, previewScale.value - 0.1) }
const cleanupPreview = () => {
  if (previewObjectUrl.value) {
    URL.revokeObjectURL(previewObjectUrl.value)
    previewObjectUrl.value = ''
  }
  previewUrl.value = ''
  previewSourceUrl.value = ''
  previewType.value = 'pdf'
  previewScale.value = 1
}

onMounted(async () => {
  registerChatE2EHooks()
  resizeHandler = () => { isMobile.value = window.innerWidth < 768 }
  resizeHandler()
  window.addEventListener('resize', resizeHandler)
  await loadSessions()
  await activateSessionFromRoute()
  connectWebSocket()
  await loadResumeAttachment()
})

watch(
  () => [route.query.targetId, route.query.jobId, route.query.jobTitle, route.query.autoRequest],
  async () => {
    if (loadingSessions.value) return
    await activateSessionFromRoute()
  }
)

onBeforeUnmount(() => {
  wsManualClose.value = true
  clearReconnectTimer()
  if (wsInstance.value) wsInstance.value.close()
  if (wsHeartbeatTimer.value) clearInterval(wsHeartbeatTimer.value)
  if (resizeHandler) window.removeEventListener('resize', resizeHandler)
  cleanupChatE2EHooks()
  cleanupPreview()
})

const loadResumeAttachment = async () => {
  if (isApplicant.value) {
    const res = await getResumeAttachment().catch(() => null)
    attachmentInfo.value = res?.data || null
  }
}
</script>

<style scoped>
.chat-page { height: calc(100vh - 84px); min-height: 0; background: linear-gradient(180deg, #f6f8fd 0%, #eef4ff 100%); padding: clamp(12px, 2vw, 20px); box-sizing: border-box; position: relative; overflow: hidden; }
.chat-page::before,
.chat-page::after { content: ''; position: absolute; border-radius: 50%; pointer-events: none; filter: blur(8px); z-index: 0; }
.chat-page::before { top: 24px; left: 2%; width: min(24vw, 240px); height: min(24vw, 240px); background: radial-gradient(circle, rgba(10, 132, 255, 0.12), rgba(10, 132, 255, 0) 72%); }
.chat-page::after { top: 140px; right: 2%; width: min(22vw, 220px); height: min(22vw, 220px); background: radial-gradient(circle, rgba(147, 197, 253, 0.11), rgba(147, 197, 253, 0) 72%); }
.chat-shell { height: 100%; width: min(100%, 1360px); min-width: 0; margin: 0 auto; display: flex; background: linear-gradient(180deg, rgba(255, 255, 255, 0.94), rgba(248, 250, 255, 0.9)); border-radius: 20px; box-shadow: 0 16px 42px rgba(15, 23, 42, 0.08); border: 1px solid rgba(255, 255, 255, 0.74); overflow: hidden; position: relative; z-index: 1; backdrop-filter: blur(18px); }
.session-panel { width: clamp(260px, 24vw, 320px); min-width: 0; border-right: 1px solid rgba(15, 23, 42, 0.06); display: flex; flex-direction: column; background: linear-gradient(180deg, rgba(255, 255, 255, 0.84), rgba(243, 247, 255, 0.92)); }
.panel-header { padding: 24px 20px 16px; }
.panel-title { font-size: 22px; font-weight: 700; color: #1d1d1f; margin-bottom: 16px; letter-spacing: -0.5px; }
.search-box :deep(.el-input__wrapper) { background-color: rgba(244, 248, 255, 0.92); border-radius: 10px; box-shadow: none !important; border: 1px solid transparent; }
.session-list { flex: 1; overflow-y: auto; padding: 0 10px 20px; }
.session-item { display: flex; align-items: center; gap: 12px; padding: 12px; margin-bottom: 2px; border-radius: 12px; cursor: pointer; transition: all var(--ui-motion-fast) var(--ui-ease-standard); }
.session-item:hover { background-color: rgba(10, 132, 255, 0.06); }
.session-item.active { background: linear-gradient(180deg, rgba(233, 243, 255, 0.96), rgba(223, 237, 255, 0.92)); box-shadow: 0 8px 18px rgba(10, 132, 255, 0.08); }
.session-name { font-weight: 600; font-size: 15px; color: #1d1d1f; flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; }
.session-time { font-size: 11px; color: #aeaeb2; font-weight: 400; white-space: nowrap; margin-left: 8px; }
.session-job-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  background-color: rgba(0, 113, 227, 0.05);
  color: #007aff;
  border-radius: 6px;
  margin-top: 4px;
  max-width: 100%;
  transition: all var(--ui-motion-fast) var(--ui-ease-standard);
}

.session-job-tag .job-text {
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  letter-spacing: -0.2px;
}

.session-job-tag.muted {
  background-color: #f2f2f7;
  color: #8e8e93;
}

.session-item.active .session-job-tag {
  background-color: rgba(255, 255, 255, 0.2);
  color: inherit;
}
.chat-panel { flex: 1; min-width: 0; display: flex; flex-direction: column; background: linear-gradient(180deg, rgba(255, 255, 255, 0.9), rgba(248, 250, 255, 0.92)); }
.chat-header { min-height: 64px; padding: 12px clamp(16px, 2vw, 24px); display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; border-bottom: 1px solid rgba(15, 23, 42, 0.06); background: linear-gradient(180deg, rgba(255, 255, 255, 0.8), rgba(246, 249, 255, 0.76)); }
.header-left { display: flex; align-items: center; flex: 1 1 320px; min-width: 0; }
.header-right { display: flex; align-items: center; justify-content: flex-end; gap: 8px; min-width: 0; }
.peer-brand { 
  display: flex; 
  align-items: center; 
  min-width: 0;
  max-width: 100%;
  gap: 12px; 
  transition: all var(--ui-motion-slow) var(--ui-ease-standard);
  padding: 6px 12px;
  margin-left: -12px;
  border-radius: 14px;
}
.peer-brand.clickable { 
  cursor: pointer; 
}
.peer-brand.clickable:hover {
  background-color: rgba(10, 132, 255, 0.06);
}
.peer-brand.clickable:hover .peer-title { 
  color: #007aff; 
}
.peer-brand.clickable:active {
  transform: scale(0.96);
  background-color: rgba(10, 132, 255, 0.1);
}
.peer-text { min-width: 0; }
.peer-title { font-weight: 600; font-size: 16px; display: flex; align-items: center; flex-wrap: wrap; min-width: 0; gap: 8px; transition: color var(--ui-motion-fast) var(--ui-ease-standard); }
.peer-status { font-size: 11px; color: #8e8e93; margin-top: 2px; display: flex; align-items: center; gap: 4px; }
.status-dot { width: 6px; height: 6px; border-radius: 50%; background-color: #d1d1d6; }
.status-dot.connected { background-color: #34c759; box-shadow: 0 0 6px rgba(52, 199, 89, 0.4); }
.chat-body { flex: 1; padding: clamp(16px, 2vw, 24px); overflow-y: auto; background: linear-gradient(180deg, rgba(252, 253, 255, 0.94), rgba(246, 249, 255, 0.96)); }
.message-item { display: flex; gap: 8px; margin-bottom: 16px; max-width: min(85%, 760px); }
.message-item.mine { align-self: flex-end; margin-left: auto; flex-direction: row-reverse; }
.message-bubble { padding: 10px 16px; border-radius: 18px; font-size: 15px; line-height: 1.4; position: relative; box-shadow: 0 4px 14px rgba(15, 23, 42, 0.06); }
.other .message-bubble { background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(243, 246, 252, 0.96)); color: #1d1d1f; border: 1px solid rgba(15, 23, 42, 0.06); border-bottom-left-radius: 4px; }
.mine .message-bubble { background: linear-gradient(180deg, #0a84ff, #3b82f6); color: #ffffff; border-bottom-right-radius: 4px; }
.message-meta { font-size: 10px; color: #aeaeb2; margin-top: 4px; display: flex; gap: 8px; }
.mine .message-meta { justify-content: flex-end; }
.action-card { background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(247, 250, 255, 0.94)); border-radius: 14px; padding: 12px; margin: 4px 0; border: 1px solid rgba(15, 23, 42, 0.06); box-shadow: 0 8px 20px rgba(15, 23, 42, 0.05); width: min(100%, 260px); max-width: 100%; }
.card-header { display: flex; align-items: center; gap: 6px; font-weight: 600; font-size: 13px; margin-bottom: 6px; }
.card-text { font-size: 12px; color: #3a3a3c; margin-bottom: 8px; }
.attachment-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.attachment-actions .el-button { flex: 1 1 120px; margin: 0; }
.chat-input-area { padding: 12px clamp(16px, 2vw, 24px) clamp(20px, 3vw, 32px); background: linear-gradient(180deg, rgba(255, 255, 255, 0.84), rgba(248, 250, 255, 0.92)); border-top: 1px solid rgba(15, 23, 42, 0.06); }
.input-toolbar { display: flex; gap: 10px; margin-bottom: 12px; flex-wrap: wrap; }
.action-pill-btn { height: 32px; max-width: 100%; padding: 0 16px; border-radius: 16px; border: 1px solid rgba(0, 113, 227, 0.15); background: var(--ui-accent-light); color: var(--ui-accent); font-size: 13px; font-weight: 500; display: flex; align-items: center; gap: 6px; transition: all var(--ui-motion-fast) var(--ui-ease-standard); }
.action-pill-btn:hover:not(:disabled) { background: rgba(0, 113, 227, 0.14); transform: translateY(-1px); box-shadow: 0 4px 12px rgba(0, 113, 227, 0.12); }
.action-pill-btn.resume-btn { background: rgba(10, 132, 255, 0.08); color: var(--ui-accent); border-color: rgba(10, 132, 255, 0.16); }
.attachment-guide {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 32px;
  padding: 0 12px;
  border-radius: 999px;
  border: 1px solid rgba(148, 163, 184, 0.2);
  background: rgba(248, 250, 252, 0.94);
  color: #64748b;
  font-size: 12px;
  line-height: 1.4;
  max-width: min(100%, 520px);
}
.attachment-guide--checking,
.attachment-guide--unknown {
  border-color: rgba(148, 163, 184, 0.22);
  background: rgba(248, 250, 252, 0.94);
  color: #64748b;
}
.attachment-guide--disconnected,
.attachment-guide--ready {
  border-color: rgba(147, 197, 253, 0.28);
  background: rgba(239, 246, 255, 0.92);
  color: #2563eb;
}
.attachment-guide--pending {
  border-color: rgba(253, 230, 138, 0.38);
  background: rgba(255, 251, 235, 0.96);
  color: #b45309;
}
.attachment-guide--granted {
  border-color: rgba(187, 247, 208, 0.4);
  background: rgba(240, 253, 244, 0.96);
  color: #15803d;
}
.attachment-guide--rejected {
  border-color: rgba(254, 202, 202, 0.42);
  background: rgba(254, 242, 242, 0.96);
  color: #dc2626;
}
.input-wrapper { display: flex; align-items: flex-end; background-color: rgba(244, 248, 255, 0.92); border-radius: 22px; padding: 6px 6px 6px 14px; border: 1px solid transparent; }
.input-wrapper:focus-within { background-color: #ffffff; box-shadow: 0 0 0 1px #007aff inset; }
.input-wrapper :deep(.el-textarea__inner) { background: transparent; border: none; box-shadow: none; padding: 8px 0; font-size: 15px; color: #1d1d1f; }
.send-btn { width: 32px; height: 32px; padding: 0; flex-shrink: 0; margin-bottom: 2px; }
.input-footer { margin-top: 8px; font-size: 11px; color: #aeaeb2; text-align: right; }
.msg-fade-enter-active { transition: all var(--ui-motion-slow) var(--ui-ease-decelerate); }
.msg-fade-enter-from { opacity: 0; transform: translateY(12px) scale(0.95); }

/* 简历预览 Apple 风格微调 */
.resume-panel { padding: clamp(16px, 2vw, 20px); }
.apple-preview-dialog :deep(.el-dialog__body) { padding: 0; }
.preview-header {
  min-height: 56px;
  padding: 12px 14px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.06);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.88), rgba(246, 249, 255, 0.82));
}
.preview-title {
  font-size: 14px;
  font-weight: 600;
  color: #1d1d1f;
  max-width: min(100%, 520px);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.preview-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}
.zoom-text {
  min-width: 50px;
  text-align: center;
  font-size: 12px;
  color: #6b7280;
}
.preview-container {
  height: min(calc(92vh - 120px), 820px);
  background: linear-gradient(180deg, #f5f8ff, #edf3ff);
  overflow: auto;
}
.preview-zoom-layer {
  width: 100%;
  height: 100%;
}
.preview-iframe {
  width: 100%;
  height: 100%;
  border: none;
  background: #ffffff;
}
.preview-image-wrap {
  width: 100%;
  min-height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  box-sizing: border-box;
}
.preview-image {
  max-width: 100%;
  max-height: calc(92vh - 160px);
  object-fit: contain;
  border-radius: 10px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
}

/* 公司详情 Apple 风格 */
.company-card { padding: 10px 20px; text-align: center; }
.company-main { display: flex; flex-direction: column; align-items: center; gap: 12px; }
.company-logo { box-shadow: 0 8px 24px rgba(0,0,0,0.08); border: 2px solid #fff; }
.company-name { font-size: 22px; font-weight: 700; color: #1d1d1f; margin: 0; }
.company-tags { display: flex; gap: 8px; justify-content: center; }
.company-section { text-align: left; margin-top: 20px; }
.section-title { font-size: 15px; font-weight: 600; color: #1d1d1f; margin-bottom: 8px; }
.company-intro { font-size: 14px; color: #3a3a3c; line-height: 1.6; white-space: pre-wrap; }
.company-addr { font-size: 13px; color: #86868b; }
.resume-hero { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; flex-wrap: wrap; padding: 24px; background: linear-gradient(180deg, rgba(248, 250, 255, 0.98), rgba(239, 246, 255, 0.92)); border-radius: 16px; margin-bottom: 24px; border: 1px solid rgba(10, 132, 255, 0.1); }
.resume-hero-main { display: flex; gap: 20px; flex: 1 1 360px; min-width: 0; }
.resume-base { min-width: 0; }
.resume-name { font-size: 24px; font-weight: 700; color: #1d1d1f; margin-bottom: 4px; }
.resume-meta { font-size: 14px; color: #86868b; margin-bottom: 12px; }
.resume-tags-row { display: flex; flex-wrap: wrap; gap: 8px; }
.resume-hero-side { flex: 0 1 240px; text-align: right; }
.hero-label { font-size: 12px; color: #86868b; margin-bottom: 2px; }
.hero-value { font-size: 14px; font-weight: 600; color: #1d1d1f; margin-bottom: 12px; }
.resume-layout { display: grid; grid-template-columns: minmax(240px, 300px) minmax(0, 1fr); gap: 24px; align-items: start; }
.resume-left { width: auto; min-width: 0; }
.resume-right { flex: 1; min-width: 0; }
.section-title { font-size: 17px; font-weight: 700; color: #1d1d1f; margin-bottom: 16px; padding-bottom: 8px; border-bottom: 1px solid #f2f2f7; }
.resume-list-item { padding: 12px 0; border-bottom: 1px dashed #f2f2f7; }
.resume-item-title { font-weight: 600; font-size: 15px; margin-bottom: 4px; }
.resume-item-meta { font-size: 13px; color: #86868b; }
.resume-item-text { font-size: 14px; color: #3a3a3c; margin-top: 8px; line-height: 1.6; }

/* 切换岗位弹窗 - Apple 风格网格 */
.job-selection-body { padding: 0 4px; }
.dialog-desc { font-size: 13px; color: #86868b; margin-bottom: 24px; line-height: 1.5; text-align: center; }

.job-card-group { 
  display: grid; 
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); 
  gap: 12px; 
  width: 100%; 
}

.job-card-item {
  padding: 16px;
  border-radius: 16px;
  background-color: #f5f5f7;
  border: 1px solid transparent;
  cursor: pointer;
  transition: all var(--ui-motion-fast) var(--ui-ease-standard);
  display: flex;
  flex-direction: column;
  min-height: 80px;
  box-sizing: border-box;
}

.job-card-item:hover {
  background-color: #efeff0;
  transform: scale(1.02);
}

.job-card-item.active {
  background-color: rgba(0, 122, 255, 0.05);
  border-color: rgba(0, 122, 255, 0.3);
  box-shadow: 0 4px 12px rgba(0, 122, 255, 0.08);
}

/* 隐藏原始 Radio，通过卡片本身传达选中感 */
.job-card-item :deep(.el-radio) {
  margin-right: 0;
  height: 100%;
}

.job-card-item :deep(.el-radio__input) {
  position: absolute;
  top: 12px;
  right: 12px;
}

.job-card-item :deep(.el-radio__label) {
  padding-left: 0;
  display: block;
}

.job-card-title {
  font-size: 14px;
  font-weight: 600;
  color: #1d1d1f;
  margin-bottom: 8px;
  line-height: 1.3;
  /* 两行截断，保持高度一致 */
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
  height: 36px;
}

.job-card-meta {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  margin-top: auto;
}
.status-indicator { display: inline-flex; align-items: center; gap: 4px; color: #34c759; }
.status-indicator::before { content: ''; width: 6px; height: 6px; background-color: currentColor; border-radius: 50%; }
.salary { color: #0a84ff; font-weight: 500; }
.dot { color: #d1d1d6; }
.dialog-footer-btns { display: flex; gap: 12px; justify-content: center; flex-wrap: wrap; width: 100%; margin-top: 10px; }
.dialog-footer-btns .el-button { flex: 1 1 160px; max-width: none; height: 40px; font-size: 14px; }
.cancel-btn { background-color: #f2f2f7 !important; border: none !important; color: #1d1d1f !important; }

@media (max-width: 1180px) {
  .resume-layout {
    grid-template-columns: 1fr;
  }

  .resume-hero-side {
    flex-basis: 100%;
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
    gap: 12px;
    text-align: left;
  }

  .hero-value {
    margin-bottom: 0;
  }
}

@media (max-width: 900px) {
  .chat-page {
    height: auto;
    min-height: calc(100vh - 84px);
    padding: 12px;
  }

  .chat-shell {
    width: 100%;
    min-height: calc(100vh - 108px);
  }

  .attachment-guide {
    width: 100%;
    max-width: none;
  }

  .session-panel {
    width: 100%;
    max-height: min(38vh, 320px);
    border-right: none;
    border-bottom: 1px solid #f2f2f7;
  }

  .message-item {
    max-width: 100%;
  }

  .preview-actions {
    justify-content: flex-start;
  }
}

@media (max-width: 640px) {
  .chat-header {
    align-items: flex-start;
  }

  .header-right,
  .header-right :deep(.el-button) {
    width: 100%;
  }

  .input-wrapper {
    padding-left: 12px;
  }

  .attachment-actions .el-button,
  .dialog-footer-btns .el-button {
    flex-basis: 100%;
  }

  .input-footer {
    text-align: left;
  }
}
</style>

