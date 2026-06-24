<!--
文件速览：
1. 文件职责：管理员看板首页，组织治理总览、快捷入口、待办队列与账号安全策略。
2. 页面入口：管理员路由 `/admin/dashboard` 对应页面组件。
3. 关键结构：focus-card、primaryStatCards、taskGuides、queue-grid、security-card、log-card。
4. 阅读建议：先看顶部任务总览，再看主次指标分层，最后看执行区与独立日志区。
-->
<template>
  <div class="admin-dashboard">
    <el-card class="focus-card">
      <div class="page-header">
        <div>
          <div class="page-kicker">Admin Workbench</div>
          <h2 class="page-title">管理员看板</h2>
          <p class="page-desc">优先看风险，再处理审核，最后检查账号安全策略，让管理员始终知道下一步该做什么。</p>
        </div>
        <div class="page-actions">
          <el-button @click="refresh">刷新数据</el-button>
        </div>
      </div>

      <div class="focus-grid">
        <div class="focus-summary">
          <span class="focus-label">当前治理总量</span>
          <div class="focus-total">{{ pendingTaskCount }}</div>
          <div class="focus-caption">待处理事项</div>
          <p class="focus-desc">
            {{ priorityPanel.desc }}
          </p>

          <div class="focus-meta">
            <div
              v-for="item in overviewHighlights"
              :key="item.label"
              class="focus-meta-item"
            >
              <span class="focus-meta-label">{{ item.label }}</span>
              <strong class="focus-meta-value">{{ item.value }}</strong>
            </div>
          </div>
        </div>

        <div class="focus-priority" :class="`focus-priority--${priorityPanel.tone}`">
          <div class="focus-priority-top">
            <span class="focus-priority-label">当前优先任务</span>
            <div class="focus-priority-title">{{ priorityPanel.title }}</div>
            <div class="focus-priority-count">{{ priorityPanel.countText }}</div>
          </div>
          <div class="guide-list">
            <div
              v-for="item in taskGuides"
              :key="item.step"
              class="guide-item"
            >
              <span class="guide-step">{{ item.step }}</span>
              <div class="guide-main">
                <div class="guide-title">{{ item.title }}</div>
                <div class="guide-desc">{{ item.desc }}</div>
              </div>
            </div>
          </div>
          <el-button :type="priorityPanel.buttonType" @click="goToAdminPage(priorityPanel.path)">
            {{ priorityPanel.actionText }}
          </el-button>
        </div>

        <div class="focus-actions">
          <div class="focus-actions-head">
            <div class="panel-eyebrow">高频入口</div>
            <div class="panel-title">快速处理</div>
          </div>

          <button
            v-for="entry in quickEntries"
            :key="entry.key"
            type="button"
            class="action-entry"
            :class="`action-entry--${entry.tone}`"
            @click="goToAdminPage(entry.path)"
          >
            <span class="action-entry-icon">
              <el-icon>
                <component :is="entry.icon" />
              </el-icon>
            </span>
            <span class="action-entry-main">
              <span class="action-entry-title">{{ entry.title }}</span>
              <span class="action-entry-desc">{{ entry.desc }}</span>
            </span>
            <span class="action-entry-action">{{ entry.actionText }}</span>
          </button>
        </div>
      </div>
    </el-card>

    <section class="metric-section">
      <div class="section-head">
        <div>
          <div class="panel-eyebrow">治理态势</div>
          <div class="section-title">先看待办，再看今日新增</div>
        </div>
        <div class="section-side">
          <el-tag type="info" effect="plain">{{ governanceStatusText }}</el-tag>
          <span class="section-side-note">今日新增 {{ todayTotalCount }} 条</span>
        </div>
      </div>

      <div class="metric-primary-grid">
        <el-card
          v-for="card in primaryStatCards"
          :key="card.key"
          class="metric-card metric-card--interactive"
          :class="`metric-card--${card.tone}`"
          shadow="hover"
          role="button"
          tabindex="0"
          @click="handleStatCardNavigate(card)"
          @keydown.enter.prevent="handleStatCardNavigate(card)"
          @keydown.space.prevent="handleStatCardNavigate(card)"
        >
          <div class="metric-card-head">
            <span class="metric-badge">{{ card.badge }}</span>
            <span class="metric-link">{{ card.linkText }}</span>
          </div>
          <div class="metric-value">{{ card.value }}</div>
          <div class="metric-label">{{ card.label }}</div>
        </el-card>
      </div>

      <div class="metric-secondary-grid">
        <button
          v-for="card in secondaryStatCards"
          :key="card.key"
          type="button"
          class="secondary-card"
          @click="handleStatCardNavigate(card)"
        >
          <span class="secondary-card-label">{{ card.label }}</span>
          <strong class="secondary-card-value">{{ card.value }}</strong>
          <span class="secondary-card-action">{{ card.linkText }}</span>
        </button>
      </div>

      <div v-if="stats.reportPending > 0" class="report-banner">
        <div class="report-banner-main">
          <span class="report-banner-label">高风险提醒</span>
          <strong class="report-banner-title">
            举报事项仍有 {{ stats.reportPending }} 条未处理，建议优先清理
          </strong>
          <span class="report-banner-desc">管理员处理举报时应先核查对象快照与证据，再执行处置动作。</span>
        </div>
        <el-button type="warning" plain @click="goToAdminPage('/admin/reports')">
          去处理举报
        </el-button>
      </div>
    </section>

    <section class="workspace-grid">
      <div class="workspace-main">
        <div class="queue-grid">
          <el-card class="panel-card queue-card">
            <div class="queue-header">
              <div>
                <div class="panel-eyebrow">审核任务</div>
                <div class="panel-title">最新待审核职位</div>
              </div>
              <div class="queue-header-right">
                <el-tag type="primary" effect="light">{{ stats.jobPending }} 待处理</el-tag>
                <el-button text @click="goToAdminPage('/admin/jobs')">查看全部</el-button>
              </div>
            </div>

            <div v-if="pendingJobs.length" class="queue-list">
              <div
                v-for="job in pendingJobs"
                :key="`job-${job.id || job.title || job.createdAt}`"
                class="queue-item"
              >
                <div class="queue-main">
                  <div class="queue-title">{{ job.title || '未命名职位' }}</div>
                  <div class="queue-sub">{{ job.companyName || '企业信息待补充' }}</div>
                </div>
                <div class="queue-side">
                  <span class="queue-time">{{ formatQueueTime(job.createdAt) }}</span>
                  <el-button size="small" text @click="goToAdminPage('/admin/jobs')">去处理</el-button>
                </div>
              </div>
            </div>
            <el-empty v-else description="暂无待审核职位" :image-size="72" />
          </el-card>

          <el-card class="panel-card queue-card">
            <div class="queue-header">
              <div>
                <div class="panel-eyebrow">商家准入</div>
                <div class="panel-title">最新待审核商家</div>
              </div>
              <div class="queue-header-right">
                <el-tag type="success" effect="light">{{ stats.merchantPending }} 待处理</el-tag>
                <el-button text @click="goToAdminPage('/admin/merchants')">查看全部</el-button>
              </div>
            </div>

            <div v-if="pendingMerchants.length" class="queue-list">
              <div
                v-for="merchant in pendingMerchants"
                :key="`merchant-${merchant.id || merchant.companyName || merchant.submittedAt}`"
                class="queue-item"
              >
                <div class="queue-main">
                  <div class="queue-title">{{ merchant.companyName || '未命名商家' }}</div>
                  <div class="queue-sub">联系人：{{ merchant.contact || '未填写' }}</div>
                </div>
                <div class="queue-side">
                  <span class="queue-time">{{ formatQueueTime(merchant.submittedAt) }}</span>
                  <el-button size="small" text @click="goToAdminPage('/admin/merchants')">去处理</el-button>
                </div>
              </div>
            </div>
            <el-empty v-else description="暂无待审核商家" :image-size="72" />
          </el-card>
        </div>
      </div>

      <aside class="workspace-side">
        <el-card class="security-card" v-loading="securityLoading">
          <div class="security-heading">
            <div>
              <div class="panel-eyebrow">账号策略</div>
              <div class="panel-title">安全开关</div>
              <div class="security-subtitle">这部分只保留管理员当前真正需要判断和操作的信息。</div>
            </div>
            <el-switch
              v-model="securitySettings.forcePasswordChangeEnabled"
              :loading="securitySubmitting"
              :before-change="handleForcePasswordChangeToggle"
              inline-prompt
              active-text="开"
              inactive-text="关"
            />
          </div>

          <div class="security-state-card">
            <span class="security-state-label">当前运行</span>
            <strong class="security-state-value">{{ securityRuntimeText }}</strong>
            <span class="security-state-note">
              默认配置：{{ securityDefaultText }}<template v-if="securitySettings.runtimeOverrideActive">，当前已偏离默认值</template>
            </span>
          </div>

          <div class="security-meta">
            <el-tag :type="securitySettings.forcePasswordChangeEnabled ? 'warning' : 'success'" effect="light">
              {{ securitySettings.forcePasswordChangeEnabled ? '开启强制改密' : '关闭强制改密' }}
            </el-tag>
            <el-tag v-if="securitySettings.runtimeOverrideActive" type="danger" effect="plain">
              运行中已偏离默认配置
            </el-tag>
          </div>

          <div class="security-tips compact">
            <p>开启后，管理员重置过密码且尚未自行修改的账号，下次登录会先进入改密页。</p>
            <p>关闭后，用户可直接使用临时密码登录，但不会被强制改密。</p>
          </div>

          <div class="security-shortcut">
            <el-button type="primary" plain @click="goToAdminPage('/admin/users')">进入账号风控</el-button>
          </div>
        </el-card>
      </aside>
    </section>

    <el-card class="log-card">
      <div class="log-header">
        <div>
          <div class="panel-eyebrow">审计留痕</div>
          <div class="panel-title">最近策略变更记录</div>
        </div>
        <span class="log-hint">展示最近 10 条强制改密开关操作记录</span>
      </div>

      <div v-if="securityLogs.length" class="log-list">
        <div v-for="log in securityLogs" :key="log.id" class="log-item">
          <div class="log-item-top">
            <el-tag
              size="small"
              :type="log.enabledValue === true ? 'warning' : log.enabledValue === false ? 'success' : 'info'"
              effect="light"
            >
              {{ log.enabledValue === true ? '已开启' : log.enabledValue === false ? '已关闭' : '已变更' }}
            </el-tag>
            <span class="log-item-title">{{ log.detail || '已更新强制改密开关' }}</span>
          </div>
          <div class="log-item-meta">
            <span>时间：{{ formatDateTime(log.createTime) || '—' }}</span>
            <span>操作人：{{ log.operatorName || (log.operatorId ? `管理员#${log.operatorId}` : '系统') }}</span>
            <span>角色：{{ roleLabel(log.operatorRole) }}</span>
          </div>
        </div>
      </div>
      <el-empty v-else description="暂无开关变更记录" />
    </el-card>
  </div>
</template>

<script setup>
/*
文件速览：
1. 文件职责：加载管理员看板统计、待办列表、快捷入口与账号安全策略，并组织成执行型工作台。
2. 对外入口：页面挂载时 refresh；快捷跳转入口为 goToAdminPage；安全入口为 handleForcePasswordChangeToggle。
3. 关键结构：primaryStatCards、secondaryStatCards、priorityPanel、taskGuides、quickEntries、securityLogs。
4. 阅读建议：先看主次指标与任务引导计算属性，再看 refresh，最后看强制改密开关确认逻辑。
*/
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { OfficeBuilding, Suitcase, UserFilled, WarningFilled } from '@element-plus/icons-vue'
import {
  getAdminJobs,
  getAdminMerchants,
  getAdminSecuritySettingLogs,
  getAdminSecuritySettings,
  getAdminStats,
  updateForcePasswordChangeSetting
} from '@/api/admin'

const router = useRouter()

const stats = ref({
  jobPending: 0,
  merchantPending: 0,
  reportPending: 0,
  todayJobs: 0,
  todayMerchants: 0,
  todayReports: 0
})

const pendingJobs = ref([])
const pendingMerchants = ref([])
const securityLoading = ref(false)
const securitySubmitting = ref(false)
const securitySettings = ref({
  forcePasswordChangeEnabled: false,
  defaultForcePasswordChangeEnabled: false,
  runtimeOverrideActive: false
})
const securityLogs = ref([])

// 总览区数据：把待办总量、今日新增和安全策略浓缩成管理员一眼可扫的结构。
const pendingTaskCount = computed(() => {
  return Number(stats.value.jobPending || 0)
    + Number(stats.value.merchantPending || 0)
    + Number(stats.value.reportPending || 0)
})

const todayTotalCount = computed(() => {
  return Number(stats.value.todayJobs || 0)
    + Number(stats.value.todayMerchants || 0)
    + Number(stats.value.todayReports || 0)
})

const overviewHighlights = computed(() => [
  {
    label: '审核待办',
    value: Number(stats.value.jobPending || 0) + Number(stats.value.merchantPending || 0)
  },
  {
    label: '举报风险',
    value: Number(stats.value.reportPending || 0)
  },
  {
    label: '今日新增',
    value: todayTotalCount.value
  }
])

const statCards = computed(() => [
  {
    key: 'jobPending',
    value: stats.value.jobPending,
    label: '待审核职位',
    badge: '审核队列',
    linkText: '进入职位审核',
    path: '/admin/jobs',
    tone: 'primary'
  },
  {
    key: 'merchantPending',
    value: stats.value.merchantPending,
    label: '待审核商家',
    badge: '商家准入',
    linkText: '进入商家审核',
    path: '/admin/merchants',
    tone: 'success'
  },
  {
    key: 'reportPending',
    value: stats.value.reportPending,
    label: '待处理举报',
    badge: '风险治理',
    linkText: '进入举报处理',
    path: '/admin/reports',
    tone: 'warning'
  },
  {
    key: 'todayJobs',
    value: stats.value.todayJobs,
    label: '今日新增职位',
    badge: '新增流量',
    linkText: '查看职位列表',
    path: '/admin/jobs',
    tone: 'neutral'
  },
  {
    key: 'todayMerchants',
    value: stats.value.todayMerchants,
    label: '今日新增商家',
    badge: '新增入驻',
    linkText: '查看商家列表',
    path: '/admin/merchants',
    tone: 'neutral'
  },
  {
    key: 'todayReports',
    value: stats.value.todayReports,
    label: '今日新增举报',
    badge: '新增风险',
    linkText: '查看举报列表',
    path: '/admin/reports',
    tone: 'danger'
  }
])

const primaryStatCards = computed(() => statCards.value.filter(card => card.key.endsWith('Pending')))
const secondaryStatCards = computed(() => statCards.value.filter(card => card.key.startsWith('today')))

// 右侧聚焦卡：优先把管理员注意力引导到当前最该处理的页面。
const priorityPanel = computed(() => {
  if (Number(stats.value.reportPending) > 0) {
    return {
      title: '举报待办需要优先清理',
      desc: '举报处理会直接影响平台治理风险，建议先核查证据，再完成动作处置。',
      countText: `${stats.value.reportPending} 条待处理`,
      actionText: '进入举报处理',
      path: '/admin/reports',
      tone: 'warning',
      buttonType: 'warning'
    }
  }
  if (Number(stats.value.jobPending) > 0) {
    return {
      title: '职位审核存在积压',
      desc: '优先消化职位审核队列，可以减少新职位上线等待时间。',
      countText: `${stats.value.jobPending} 条待审核`,
      actionText: '进入职位审核',
      path: '/admin/jobs',
      tone: 'primary',
      buttonType: 'primary'
    }
  }
  if (Number(stats.value.merchantPending) > 0) {
    return {
      title: '商家准入仍有待办',
      desc: '商家审核会影响发岗节奏，建议及时完成准入判断。',
      countText: `${stats.value.merchantPending} 家待审核`,
      actionText: '进入商家审核',
      path: '/admin/merchants',
      tone: 'success',
      buttonType: 'success'
    }
  }
  return {
    title: '当前治理事项已清空',
    desc: '暂无高优先级风险，可转入账号风控与安全策略巡检。',
    countText: '运行平稳',
    actionText: '查看账号风控',
    path: '/admin/users',
    tone: 'calm',
    buttonType: 'primary'
  }
})

const taskGuides = computed(() => {
  return [
    {
      step: '01',
      title: stats.value.reportPending > 0 ? '先处理举报高风险事项' : '先巡检风险项状态',
      desc: stats.value.reportPending > 0
        ? `当前还有 ${stats.value.reportPending} 条举报待处理，优先核查证据和对象快照。`
        : '举报事项已清空，可保持日常巡检节奏。'
    },
    {
      step: '02',
      title: '再清理审核积压',
      desc: `职位待审核 ${stats.value.jobPending} 条，商家待审核 ${stats.value.merchantPending} 家。`
    },
    {
      step: '03',
      title: '最后检查账号策略',
      desc: securitySettings.value.runtimeOverrideActive
        ? '当前运行策略已偏离默认配置，建议确认是否仍需保留临时调整。'
        : '当前策略与默认配置一致，保持观察即可。'
    }
  ]
})

const governanceStatusText = computed(() => {
  if (Number(stats.value.reportPending) > 0) return '举报待办偏高'
  if (pendingTaskCount.value > 0) return '审核待办处理中'
  return '当前运行平稳'
})

const securityRuntimeText = computed(() => {
  return securitySettings.value.forcePasswordChangeEnabled ? '强制改密开启' : '强制改密关闭'
})

const securityDefaultText = computed(() => {
  return securitySettings.value.defaultForcePasswordChangeEnabled ? '开启' : '关闭'
})

const quickEntries = [
  {
    key: 'jobs',
    title: '职位审核',
    desc: '处理新增职位与审核积压',
    actionText: '立即处理',
    path: '/admin/jobs',
    icon: Suitcase,
    tone: 'primary'
  },
  {
    key: 'merchants',
    title: '商家审核',
    desc: '完成商家准入判断与回收',
    actionText: '查看商家',
    path: '/admin/merchants',
    icon: OfficeBuilding,
    tone: 'success'
  },
  {
    key: 'reports',
    title: '举报处理',
    desc: '优先清理高风险治理事项',
    actionText: '进入举报',
    path: '/admin/reports',
    icon: WarningFilled,
    tone: 'warning'
  },
  {
    key: 'users',
    title: '账号风控',
    desc: '检查限制、封禁与密码重置记录',
    actionText: '查看风控',
    path: '/admin/users',
    icon: UserFilled,
    tone: 'neutral'
  }
]

const normalizeList = (data) => {
  if (Array.isArray(data)) return data
  return data?.records || []
}

const goToAdminPage = async (path) => {
  if (!path) return
  try {
    await router.push(path)
  } catch (error) {
    console.error('管理员看板跳转失败', error)
  }
}

const handleStatCardNavigate = (card) => {
  goToAdminPage(card?.path)
}

const applySecuritySettings = (data) => {
  securitySettings.value = {
    forcePasswordChangeEnabled: Boolean(data?.forcePasswordChangeEnabled),
    defaultForcePasswordChangeEnabled: Boolean(data?.defaultForcePasswordChangeEnabled),
    runtimeOverrideActive: Boolean(data?.runtimeOverrideActive)
  }
}

const roleLabel = (role) => {
  if (role === 'ADMIN') return '管理员'
  if (role === 'MERCHANT') return '商家'
  if (role === 'APPLICANT') return '求职者'
  if (!role) return '—'
  return role
}

const formatDateTime = (value) => {
  if (!value) return ''
  return String(value).replace('T', ' ')
}

const formatQueueTime = (value) => {
  const text = formatDateTime(value)
  if (!text) return '时间待补充'
  return text.length > 16 ? text.slice(0, 16) : text
}

const fetchSecurityModule = async () => {
  securityLoading.value = true
  try {
    const [settingsRes, logsRes] = await Promise.all([
      getAdminSecuritySettings(),
      getAdminSecuritySettingLogs()
    ])
    if (settingsRes.code === 200 && settingsRes.data) {
      applySecuritySettings(settingsRes.data)
    }
    if (logsRes.code === 200 && Array.isArray(logsRes.data)) {
      securityLogs.value = logsRes.data
    }
  } catch (error) {
    console.error('获取账号安全设置失败', error)
    ElMessage.error('获取账号安全设置失败')
  } finally {
    securityLoading.value = false
  }
}

const refresh = async () => {
  try {
    const [statsRes, jobRes, merchantRes] = await Promise.all([
      getAdminStats(),
      getAdminJobs({ status: 0, current: 1, size: 5 }),
      getAdminMerchants({ status: 0, current: 1, size: 5 })
    ])
    if (statsRes.code === 200 && statsRes.data) {
      stats.value = statsRes.data
    }
    pendingJobs.value = normalizeList(jobRes.data)
    pendingMerchants.value = normalizeList(merchantRes.data)
  } catch (error) {
    console.error('获取管理员看板数据失败', error)
  }
  await fetchSecurityModule()
}

const handleForcePasswordChangeToggle = async () => {
  const nextEnabled = !securitySettings.value.forcePasswordChangeEnabled
  const actionText = nextEnabled ? '开启' : '关闭'
  const impactText = nextEnabled
    ? '开启后，管理员重置过密码且尚未自行修改的账号，会在下次登录后被要求先修改密码。'
    : '关闭后，用户仍可使用管理员下发的临时密码登录，但不会被强制跳转修改密码。'
  try {
    await ElMessageBox.confirm(impactText, `${actionText}强制改密开关`, {
      type: 'warning',
      confirmButtonText: actionText,
      cancelButtonText: '取消'
    })
    securitySubmitting.value = true
    const res = await updateForcePasswordChangeSetting({ enabled: nextEnabled })
    if (res.code !== 200 || !res.data) {
      throw new Error(res.msg || '更新账号安全设置失败')
    }
    applySecuritySettings(res.data)
    const logsRes = await getAdminSecuritySettingLogs()
    if (logsRes.code === 200 && Array.isArray(logsRes.data)) {
      securityLogs.value = logsRes.data
    }
    ElMessage.success(`已${actionText}临时密码登录后强制修改密码`)
    // 已手动按后端快照回写状态，阻止 el-switch 再次本地翻转，避免界面与后端值相反。
    return false
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return false
    }
    console.error('更新账号安全设置失败', error)
    ElMessage.error(error?.message || '更新账号安全设置失败')
    return false
  } finally {
    securitySubmitting.value = false
  }
}

onMounted(() => {
  refresh()
})
</script>

<style scoped>
.admin-dashboard {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.hero-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.75fr) minmax(280px, 0.95fr);
  gap: 18px;
  align-items: start;
}

.overview-card {
  overflow: hidden;
  background:
    radial-gradient(circle at top left, rgba(0, 113, 227, 0.09), transparent 34%),
    linear-gradient(180deg, #fbfdff 0%, #ffffff 62%);
  border-color: rgba(0, 113, 227, 0.12);
}

.overview-card :deep(.el-card__body) {
  padding: 22px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  flex-wrap: wrap;
}

.page-kicker {
  font-size: 11px;
  line-height: 1.4;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: #64748b;
}

.page-title {
  margin: 6px 0 0;
  font-size: 28px;
  line-height: 1.15;
  color: #0f172a;
}

.page-desc {
  margin: 10px 0 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.7;
  max-width: 620px;
}

.page-actions {
  flex-shrink: 0;
}

.overview-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(260px, 0.85fr);
  gap: 16px;
  margin-top: 18px;
}

.overview-main {
  position: relative;
  overflow: hidden;
  border-radius: 22px;
  padding: 22px;
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.86), rgba(248, 250, 252, 0.94)),
    radial-gradient(circle at top right, rgba(14, 165, 233, 0.16), transparent 42%);
  border: 1px solid rgba(148, 163, 184, 0.16);
  min-height: 240px;
}

.overview-kicker {
  font-size: 12px;
  font-weight: 600;
  color: #0369a1;
  letter-spacing: 0.04em;
}

.overview-total {
  margin-top: 14px;
  font-size: clamp(42px, 6vw, 56px);
  line-height: 1;
  font-weight: 700;
  color: #0f172a;
}

.overview-caption {
  margin-top: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
}

.overview-desc {
  margin: 10px 0 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.75;
  max-width: 460px;
}

.overview-meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-top: 20px;
}

.overview-meta-item {
  padding: 12px 14px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.84);
  border: 1px solid rgba(226, 232, 240, 0.88);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.5);
}

.overview-meta-label {
  display: block;
  font-size: 12px;
  color: #64748b;
}

.overview-meta-value {
  display: block;
  margin-top: 6px;
  font-size: 22px;
  line-height: 1.1;
  color: #0f172a;
}

.priority-panel {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 18px;
  min-height: 240px;
  border-radius: 22px;
  padding: 20px;
  border: 1px solid #e2e8f0;
}

.priority-panel--primary {
  background: linear-gradient(160deg, #eff6ff 0%, #ffffff 100%);
  border-color: #bfdbfe;
}

.priority-panel--success {
  background: linear-gradient(160deg, #ecfdf5 0%, #ffffff 100%);
  border-color: #a7f3d0;
}

.priority-panel--warning {
  background: linear-gradient(160deg, #fff7ed 0%, #ffffff 100%);
  border-color: #fed7aa;
}

.priority-panel--calm {
  background: linear-gradient(160deg, #f8fafc 0%, #ffffff 100%);
  border-color: #cbd5e1;
}

.priority-label {
  font-size: 12px;
  color: #64748b;
  letter-spacing: 0.04em;
}

.priority-title {
  margin-top: 10px;
  font-size: 22px;
  line-height: 1.35;
  font-weight: 700;
  color: #0f172a;
}

.priority-desc {
  margin: 12px 0 0;
  font-size: 13px;
  line-height: 1.75;
  color: #475569;
}

.priority-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.priority-count {
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
}

.report-banner {
  margin-top: 16px;
  padding: 16px 18px;
  border-radius: 18px;
  border: 1px solid rgba(245, 158, 11, 0.22);
  background: linear-gradient(90deg, rgba(245, 158, 11, 0.12), rgba(255, 255, 255, 0.94));
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}

.report-banner-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.report-banner-label {
  font-size: 12px;
  font-weight: 600;
  color: #b45309;
}

.report-banner-title {
  color: #7c2d12;
  font-size: 14px;
  line-height: 1.6;
}

.report-banner-desc {
  color: #9a3412;
  font-size: 12px;
  line-height: 1.6;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-top: 18px;
}

.stat-card {
  border-radius: 18px;
  border: 1px solid #e5e7eb;
}

.stat-card :deep(.el-card__body) {
  padding: 16px 18px;
}

.stat-card--interactive {
  cursor: pointer;
  user-select: none;
}

.stat-card--interactive:focus-visible {
  outline: 2px solid rgba(0, 113, 227, 0.35);
  outline-offset: 2px;
}

.stat-card--primary {
  background: linear-gradient(180deg, #f8fbff 0%, #ffffff 100%);
}

.stat-card--success {
  background: linear-gradient(180deg, #f3fff9 0%, #ffffff 100%);
}

.stat-card--warning {
  background: linear-gradient(180deg, #fffaf1 0%, #ffffff 100%);
}

.stat-card--danger {
  background: linear-gradient(180deg, #fff5f5 0%, #ffffff 100%);
}

.stat-card--neutral {
  background: linear-gradient(180deg, #fafcff 0%, #ffffff 100%);
}

.stat-card-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 118px;
}

.stat-card-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;
}

.stat-badge {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(148, 163, 184, 0.18);
  color: #475569;
  font-size: 11px;
  font-weight: 600;
}

.stat-link {
  font-size: 12px;
  line-height: 1.5;
  color: #0071e3;
  font-weight: 600;
  text-align: right;
}

.stat-value {
  font-size: 30px;
  line-height: 1;
  font-weight: 700;
  color: #111827;
}

.stat-label {
  color: #6b7280;
  font-size: 13px;
  line-height: 1.6;
}

.command-card {
  background:
    radial-gradient(circle at top right, rgba(15, 23, 42, 0.05), transparent 36%),
    linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
}

.command-card :deep(.el-card__body) {
  padding: 20px;
}

.command-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.panel-eyebrow {
  font-size: 11px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #94a3b8;
}

.panel-title {
  margin-top: 6px;
  font-weight: 700;
  color: #0f172a;
  font-size: 18px;
  line-height: 1.35;
}

.command-tip {
  display: inline-flex;
  align-items: center;
  padding: 0 10px;
  height: 24px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.05);
  color: #475569;
  font-size: 11px;
  font-weight: 600;
}

.command-grid {
  display: grid;
  gap: 12px;
  margin-top: 18px;
}

.command-entry {
  width: 100%;
  padding: 14px;
  border-radius: 18px;
  border: 1px solid #e5e7eb;
  background: rgba(255, 255, 255, 0.92);
  display: flex;
  align-items: flex-start;
  gap: 12px;
  text-align: left;
  cursor: pointer;
  transition:
    transform var(--ui-motion-base) var(--ui-ease-standard),
    border-color var(--ui-motion-base) var(--ui-ease-standard),
    box-shadow var(--ui-motion-base) var(--ui-ease-standard);
}

.command-entry:hover {
  transform: translateY(-2px);
  border-color: rgba(0, 113, 227, 0.18);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.06);
}

.command-entry:focus-visible {
  outline: 2px solid rgba(0, 113, 227, 0.35);
  outline-offset: 2px;
}

.command-entry-icon {
  width: 42px;
  height: 42px;
  border-radius: 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}

.command-entry--primary .command-entry-icon {
  background: rgba(0, 113, 227, 0.12);
  color: #0071e3;
}

.command-entry--success .command-entry-icon {
  background: rgba(16, 185, 129, 0.12);
  color: #059669;
}

.command-entry--warning .command-entry-icon {
  background: rgba(245, 158, 11, 0.14);
  color: #d97706;
}

.command-entry--neutral .command-entry-icon {
  background: rgba(148, 163, 184, 0.16);
  color: #475569;
}

.command-entry-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  flex: 1;
}

.command-entry-title {
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
}

.command-entry-desc {
  font-size: 12px;
  line-height: 1.65;
  color: #64748b;
}

.command-entry-action {
  font-size: 12px;
  font-weight: 600;
  color: #0071e3;
  white-space: nowrap;
}

.command-footer {
  display: grid;
  gap: 10px;
  margin-top: 18px;
}

.command-summary {
  padding: 14px 16px;
  border-radius: 16px;
  border: 1px solid #e2e8f0;
  background: rgba(248, 250, 252, 0.92);
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.command-summary-label {
  font-size: 12px;
  color: #64748b;
}

.command-summary-value {
  font-size: 15px;
  color: #0f172a;
}

.command-summary-note {
  font-size: 12px;
  color: #94a3b8;
}

.workspace-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(320px, 0.95fr);
  gap: 18px;
  align-items: start;
}

.workspace-main {
  min-width: 0;
}

.queue-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 16px;
}

.panel-card {
  border-radius: 18px;
}

.queue-card :deep(.el-card__body) {
  padding: 18px;
}

.queue-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  flex-wrap: wrap;
}

.queue-header-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.queue-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 16px;
}

.queue-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border-radius: 16px;
  border: 1px solid #e5e7eb;
  background: linear-gradient(180deg, #ffffff 0%, #fbfdff 100%);
}

.queue-main {
  min-width: 0;
}

.queue-title {
  font-size: 15px;
  line-height: 1.45;
  font-weight: 600;
  color: #0f172a;
  word-break: break-word;
}

.queue-sub {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: #64748b;
}

.queue-side {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.queue-time {
  font-size: 12px;
  color: #94a3b8;
  white-space: nowrap;
}

.workspace-side {
  min-width: 0;
  position: sticky;
  top: 12px;
}

.security-card {
  border-radius: 18px;
}

.security-card :deep(.el-card__body) {
  padding: 20px;
}

.security-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.security-subtitle {
  margin-top: 8px;
  color: #6b7280;
  font-size: 13px;
  line-height: 1.7;
}

.security-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 16px;
}

.security-tips {
  margin-top: 16px;
  padding: 14px 16px;
  border-radius: 16px;
  background: linear-gradient(135deg, #fff7ed 0%, #f8fafc 100%);
}

.security-tips p {
  margin: 0;
  color: #4b5563;
  font-size: 13px;
  line-height: 1.8;
}

.security-tips p + p {
  margin-top: 6px;
}

.security-log-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.security-log-hint {
  color: #6b7280;
  font-size: 12px;
}

.security-log-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.security-log-item {
  padding: 14px 16px;
  border: 1px solid #e5e7eb;
  border-radius: 14px;
  background: #ffffff;
}

.security-log-main {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.security-log-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  color: #111827;
  font-weight: 500;
}

.security-log-meta-line {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  color: #6b7280;
  font-size: 12px;
  line-height: 1.7;
}

@media (max-width: 1280px) {
  .hero-grid,
  .workspace-grid {
    grid-template-columns: 1fr;
  }

  .workspace-side {
    position: static;
  }
}

@media (max-width: 900px) {
  .overview-grid {
    grid-template-columns: 1fr;
  }

  .stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .overview-meta {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .overview-card :deep(.el-card__body),
  .command-card :deep(.el-card__body),
  .queue-card :deep(.el-card__body),
  .security-card :deep(.el-card__body) {
    padding: 16px;
  }

  .page-title {
    font-size: 24px;
  }

  .stats-grid {
    grid-template-columns: 1fr;
  }

  .queue-item,
  .priority-footer,
  .security-heading,
  .security-log-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .queue-side {
    width: 100%;
    justify-content: space-between;
  }

  .command-entry {
    flex-wrap: wrap;
  }

  .command-entry-action {
    width: 100%;
  }
}

.focus-card {
  overflow: hidden;
  border-radius: 22px;
  background:
    radial-gradient(circle at top left, rgba(0, 113, 227, 0.08), transparent 30%),
    linear-gradient(180deg, #fbfdff 0%, #ffffff 65%);
  border-color: rgba(0, 113, 227, 0.12);
}

.focus-card :deep(.el-card__body) {
  padding: 24px;
}

.focus-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(260px, 0.92fr) minmax(280px, 0.88fr);
  gap: 16px;
  margin-top: 18px;
  align-items: stretch;
}

.focus-summary {
  border-radius: 22px;
  padding: 22px;
  border: 1px solid rgba(148, 163, 184, 0.16);
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.9), rgba(248, 250, 252, 0.96)),
    radial-gradient(circle at top right, rgba(14, 165, 233, 0.15), transparent 42%);
}

.focus-label {
  font-size: 12px;
  font-weight: 600;
  color: #0369a1;
  letter-spacing: 0.04em;
}

.focus-total {
  margin-top: 12px;
  font-size: clamp(44px, 5vw, 60px);
  line-height: 1;
  font-weight: 700;
  color: #0f172a;
}

.focus-caption {
  margin-top: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
}

.focus-desc {
  margin: 10px 0 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.75;
}

.focus-meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-top: 20px;
}

.focus-meta-item {
  padding: 12px 14px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.88);
  border: 1px solid rgba(226, 232, 240, 0.88);
}

.focus-meta-label {
  display: block;
  font-size: 12px;
  color: #64748b;
}

.focus-meta-value {
  display: block;
  margin-top: 6px;
  font-size: 22px;
  line-height: 1.1;
  color: #0f172a;
}

.focus-priority {
  display: flex;
  flex-direction: column;
  gap: 14px;
  justify-content: space-between;
  padding: 20px;
  border-radius: 22px;
  border: 1px solid #e2e8f0;
}

.focus-priority--primary {
  background: linear-gradient(160deg, #eff6ff 0%, #ffffff 100%);
  border-color: #bfdbfe;
}

.focus-priority--success {
  background: linear-gradient(160deg, #ecfdf5 0%, #ffffff 100%);
  border-color: #a7f3d0;
}

.focus-priority--warning {
  background: linear-gradient(160deg, #fff7ed 0%, #ffffff 100%);
  border-color: #fed7aa;
}

.focus-priority--calm {
  background: linear-gradient(160deg, #f8fafc 0%, #ffffff 100%);
  border-color: #cbd5e1;
}

.focus-priority-label {
  font-size: 12px;
  color: #64748b;
  letter-spacing: 0.04em;
}

.focus-priority-title {
  margin-top: 10px;
  font-size: 22px;
  line-height: 1.35;
  font-weight: 700;
  color: #0f172a;
}

.focus-priority-count {
  margin-top: 10px;
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}

.guide-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.guide-item {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 10px 0;
  border-top: 1px dashed rgba(148, 163, 184, 0.34);
}

.guide-item:first-child {
  border-top: 0;
  padding-top: 0;
}

.guide-step {
  width: 34px;
  height: 34px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(148, 163, 184, 0.24);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  color: #0f172a;
  flex-shrink: 0;
}

.guide-main {
  min-width: 0;
}

.guide-title {
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
}

.guide-desc {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.65;
  color: #64748b;
}

.focus-actions {
  border-radius: 22px;
  padding: 20px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
  border: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.focus-actions-head {
  margin-bottom: 4px;
}

.action-entry {
  width: 100%;
  padding: 14px;
  border-radius: 18px;
  border: 1px solid #e5e7eb;
  background: rgba(255, 255, 255, 0.92);
  display: flex;
  align-items: flex-start;
  gap: 12px;
  text-align: left;
  cursor: pointer;
  transition:
    transform var(--ui-motion-base) var(--ui-ease-standard),
    border-color var(--ui-motion-base) var(--ui-ease-standard),
    box-shadow var(--ui-motion-base) var(--ui-ease-standard);
}

.action-entry:hover {
  transform: translateY(-2px);
  border-color: rgba(0, 113, 227, 0.18);
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.06);
}

.action-entry:focus-visible {
  outline: 2px solid rgba(0, 113, 227, 0.35);
  outline-offset: 2px;
}

.action-entry-icon {
  width: 42px;
  height: 42px;
  border-radius: 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}

.action-entry--primary .action-entry-icon {
  background: rgba(0, 113, 227, 0.12);
  color: #0071e3;
}

.action-entry--success .action-entry-icon {
  background: rgba(16, 185, 129, 0.12);
  color: #059669;
}

.action-entry--warning .action-entry-icon {
  background: rgba(245, 158, 11, 0.14);
  color: #d97706;
}

.action-entry--neutral .action-entry-icon {
  background: rgba(148, 163, 184, 0.16);
  color: #475569;
}

.action-entry-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  flex: 1;
}

.action-entry-title {
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
}

.action-entry-desc {
  font-size: 12px;
  line-height: 1.65;
  color: #64748b;
}

.action-entry-action {
  font-size: 12px;
  font-weight: 600;
  color: #0071e3;
  white-space: nowrap;
}

.metric-section {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.section-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 12px;
  flex-wrap: wrap;
}

.section-title {
  margin-top: 6px;
  font-size: 22px;
  line-height: 1.35;
  font-weight: 700;
  color: #0f172a;
}

.section-side {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.section-side-note {
  font-size: 12px;
  color: #64748b;
}

.metric-primary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.metric-card {
  border-radius: 18px;
  border: 1px solid #e5e7eb;
}

.metric-card :deep(.el-card__body) {
  padding: 18px;
}

.metric-card--interactive {
  cursor: pointer;
  user-select: none;
}

.metric-card--interactive:focus-visible {
  outline: 2px solid rgba(0, 113, 227, 0.35);
  outline-offset: 2px;
}

.metric-card--primary {
  background: linear-gradient(180deg, #f8fbff 0%, #ffffff 100%);
}

.metric-card--success {
  background: linear-gradient(180deg, #f3fff9 0%, #ffffff 100%);
}

.metric-card--warning {
  background: linear-gradient(180deg, #fffaf1 0%, #ffffff 100%);
}

.metric-card-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;
}

.metric-badge {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(148, 163, 184, 0.18);
  color: #475569;
  font-size: 11px;
  font-weight: 600;
}

.metric-link {
  font-size: 12px;
  line-height: 1.5;
  color: #0071e3;
  font-weight: 600;
  text-align: right;
}

.metric-value {
  margin-top: 18px;
  font-size: 32px;
  line-height: 1;
  font-weight: 700;
  color: #111827;
}

.metric-label {
  margin-top: 10px;
  color: #6b7280;
  font-size: 13px;
  line-height: 1.6;
}

.metric-secondary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.secondary-card {
  padding: 14px 16px;
  border-radius: 16px;
  border: 1px solid #e5e7eb;
  background: linear-gradient(180deg, #ffffff 0%, #fafcff 100%);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  text-align: left;
  cursor: pointer;
  transition:
    transform var(--ui-motion-base) var(--ui-ease-standard),
    border-color var(--ui-motion-base) var(--ui-ease-standard),
    box-shadow var(--ui-motion-base) var(--ui-ease-standard);
}

.secondary-card:hover {
  transform: translateY(-1px);
  border-color: rgba(0, 113, 227, 0.18);
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.05);
}

.secondary-card:focus-visible {
  outline: 2px solid rgba(0, 113, 227, 0.35);
  outline-offset: 2px;
}

.secondary-card-label {
  font-size: 13px;
  color: #475569;
}

.secondary-card-value {
  font-size: 22px;
  line-height: 1;
  color: #0f172a;
}

.secondary-card-action {
  font-size: 12px;
  color: #0071e3;
  font-weight: 600;
}

.workspace-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.7fr) minmax(280px, 0.78fr);
  gap: 18px;
  align-items: start;
}

.security-state-card {
  margin-top: 16px;
  padding: 16px;
  border-radius: 16px;
  background: linear-gradient(180deg, #f8fbff 0%, #ffffff 100%);
  border: 1px solid rgba(0, 113, 227, 0.12);
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.security-state-label {
  font-size: 12px;
  color: #64748b;
}

.security-state-value {
  font-size: 18px;
  color: #0f172a;
}

.security-state-note {
  font-size: 12px;
  line-height: 1.65;
  color: #64748b;
}

.security-tips.compact {
  margin-top: 14px;
}

.security-shortcut {
  margin-top: 16px;
}

.log-card {
  border-radius: 18px;
}

.log-card :deep(.el-card__body) {
  padding: 20px;
}

.log-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}

.log-hint {
  font-size: 12px;
  color: #64748b;
}

.log-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 12px;
}

.log-item {
  padding: 14px 16px;
  border: 1px solid #e5e7eb;
  border-radius: 16px;
  background: linear-gradient(180deg, #ffffff 0%, #fafcff 100%);
}

.log-item-top {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.log-item-title {
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
}

.log-item-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 10px;
  font-size: 12px;
  line-height: 1.7;
  color: #64748b;
}

@media (max-width: 1280px) {
  .focus-grid {
    grid-template-columns: 1fr;
  }

  .workspace-grid {
    grid-template-columns: 1fr;
  }

  .workspace-side {
    order: -1;
  }
}

@media (max-width: 900px) {
  .focus-card :deep(.el-card__body),
  .log-card :deep(.el-card__body) {
    padding: 18px;
  }

  .focus-meta {
    grid-template-columns: 1fr;
  }

  .metric-primary-grid,
  .metric-secondary-grid {
    grid-template-columns: 1fr;
  }

  .log-list {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .page-title,
  .section-title {
    font-size: 24px;
  }

  .focus-summary,
  .focus-priority,
  .focus-actions,
  .queue-card :deep(.el-card__body),
  .security-card :deep(.el-card__body) {
    padding: 16px;
  }

  .section-head,
  .log-header,
  .security-heading,
  .queue-item,
  .queue-side {
    flex-direction: column;
    align-items: flex-start;
  }

  .secondary-card,
  .action-entry {
    flex-wrap: wrap;
  }

  .action-entry-action,
  .secondary-card-action {
    width: 100%;
  }
}
</style>
