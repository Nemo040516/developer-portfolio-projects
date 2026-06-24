/*
文件速览：
1. 文件职责：集中维护商家端 / 求职者端治理通知摘要，用于导航红点、工作台提醒卡和治理阶段分组。
2. 对外入口：useGovernanceStore，提供 fetchSummary、resetSummary、pendingTotal、highPriorityNotices、workbenchBuckets 等状态。
3. 关键结构：counts 统计、noticeDecorators、priorityScore 排序、阶段分桶与角色隔离。
4. 阅读建议：先看 decorateNotice / sortNotices，再看 buildPriorityNotices 与 buildWorkbenchBuckets。
*/
import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { getMyGovernanceNotices } from '@/api/governance'
import {
  buildGovernancePriorityNotices,
  buildGovernanceWorkbenchBuckets,
  normalizeGovernanceTotal
} from '@/utils/governanceWorkbench'

const DEFAULT_COUNTS = () => ({
  total: 0,
  unread: 0,
  pendingAction: 0,
  pendingReview: 0,
  finished: 0
})

const DEFAULT_BUCKETS = () => ({
  read: [],
  action: [],
  review: []
})

/**
 * @description 统一治理通知摘要状态，避免布局、工作台和通知页重复拉取统计数据。
 */
export const useGovernanceStore = defineStore('governance', () => {
  const role = ref('')
  const loading = ref(false)
  const counts = ref(DEFAULT_COUNTS())
  const highPriorityNotices = ref([])
  const workbenchBuckets = ref(DEFAULT_BUCKETS())
  const lastFetchAt = ref(0)

  const pendingTotal = computed(() =>
    Number(counts.value.unread || 0)
    + Number(counts.value.pendingAction || 0)
    + Number(counts.value.pendingReview || 0)
  )

  const hasAttention = computed(() => pendingTotal.value > 0)

  const primaryNotice = computed(() => highPriorityNotices.value[0] || null)

  const resetSummary = () => {
    counts.value = DEFAULT_COUNTS()
    highPriorityNotices.value = []
    workbenchBuckets.value = DEFAULT_BUCKETS()
    lastFetchAt.value = 0
  }

  /**
   * @description 拉取治理通知摘要。当前只对商家端和求职者端启用。
   */
  const fetchSummary = async (targetRole, options = {}) => {
    const safeRole = String(targetRole || '').toUpperCase()
    if (!['MERCHANT', 'APPLICANT'].includes(safeRole)) {
      resetSummary()
      role.value = safeRole
      return
    }

    const now = Date.now()
    const cacheMs = Number(options.cacheMs || 0)
    if (!options.force && role.value === safeRole && cacheMs > 0 && now - lastFetchAt.value < cacheMs) {
      return
    }

    role.value = safeRole
    loading.value = true
    try {
      const listSize = Number(options.listSize || 18)
      const [allRes, unreadRes, actionRes, reviewRes, finishedRes] = await Promise.all([
        getMyGovernanceNotices({ current: 1, size: listSize }),
        getMyGovernanceNotices({ current: 1, size: 1, status: 'PENDING_READ' }),
        getMyGovernanceNotices({ current: 1, size: 1, status: 'PENDING_ACTION' }),
        getMyGovernanceNotices({ current: 1, size: 1, status: 'PENDING_REVIEW' }),
        getMyGovernanceNotices({ current: 1, size: 1, status: 'FINISHED' })
      ])

      const allData = allRes?.data || {}
      counts.value = {
        total: normalizeGovernanceTotal(allData),
        unread: normalizeGovernanceTotal(unreadRes?.data || {}),
        pendingAction: normalizeGovernanceTotal(actionRes?.data || {}),
        pendingReview: normalizeGovernanceTotal(reviewRes?.data || {}),
        finished: normalizeGovernanceTotal(finishedRes?.data || {})
      }
      highPriorityNotices.value = buildGovernancePriorityNotices(allData)
      workbenchBuckets.value = buildGovernanceWorkbenchBuckets(allData)
      lastFetchAt.value = Date.now()
    } catch (error) {
      resetSummary()
      throw error
    } finally {
      loading.value = false
    }
  }

  return {
    role,
    loading,
    counts,
    highPriorityNotices,
    workbenchBuckets,
    pendingTotal,
    hasAttention,
    primaryNotice,
    fetchSummary,
    resetSummary
  }
})
