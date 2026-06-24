/*
文件速览：
1. 文件职责：沉淀治理通知在工作台场景下的排序、优先级和阶段分组规则，供 store 与页面共用。
2. 对外入口：normalizeGovernanceRecords、normalizeGovernanceTotal、buildGovernancePriorityNotices、buildGovernanceWorkbenchBuckets。
3. 关键结构：逾期判断、优先级打分、统一排序、先看/先处理/待复核分桶。
4. 阅读建议：先看 decorateGovernanceNotice 的打分规则，再看 buildGovernancePriorityNotices 与 buildGovernanceWorkbenchBuckets。
*/

/**
 * @description 统一兼容分页 records 与直接数组两种治理列表结构。
 */
export function normalizeGovernanceRecords(data) {
  if (Array.isArray(data)) return data
  return Array.isArray(data?.records) ? data.records : []
}

/**
 * @description 统一读取治理列表总数，分页接口优先读 total，兜底回退 records 长度。
 */
export function normalizeGovernanceTotal(data) {
  const total = Number(data?.total)
  if (Number.isFinite(total)) {
    return total
  }
  return normalizeGovernanceRecords(data).length
}

/**
 * @description 判断治理事项是否已经超过时限，已关闭态默认不再计入逾期。
 */
export function isGovernanceNoticeOverdue(item) {
  if (!item?.dueTime) return false
  if (['FINISHED', 'CLOSED', 'EXPIRED'].includes(item.status)) return false
  return new Date(item.dueTime).getTime() < Date.now()
}

/**
 * @description 为治理事项补齐工作台排序和展示所需的派生字段。
 */
export function decorateGovernanceNotice(item) {
  let score = 0
  if (isGovernanceNoticeOverdue(item)) score += 400
  if (item?.severity === 'HIGH') score += 240
  if (item?.status === 'PENDING_ACTION') score += 180
  if (item?.status === 'REJECTED') score += 170
  if (item?.status === 'EXPIRED') score += 165
  if (item?.status === 'PENDING_READ') score += 140
  if (item?.status === 'PENDING_REVIEW') score += 120
  if (item?.severity === 'WARNING') score += 80

  const dueTimeValue = item?.dueTime ? new Date(item.dueTime).getTime() : Number.MAX_SAFE_INTEGER
  const latestTimeValue = new Date(item?.latestActionTime || item?.createTime || 0).getTime()

  return {
    ...item,
    isOverdue: isGovernanceNoticeOverdue(item),
    priorityScore: score,
    dueTimeValue: Number.isFinite(dueTimeValue) ? dueTimeValue : Number.MAX_SAFE_INTEGER,
    latestTimeValue: Number.isFinite(latestTimeValue) ? latestTimeValue : 0
  }
}

/**
 * @description 所有治理事项统一排序：先看优先级，再看时限，最后看最近动作时间。
 */
export function sortGovernanceNotices(records) {
  return normalizeGovernanceRecords(records)
    .map((item) => decorateGovernanceNotice(item))
    .sort((a, b) => {
      if (b.priorityScore !== a.priorityScore) {
        return b.priorityScore - a.priorityScore
      }
      if (a.dueTimeValue !== b.dueTimeValue) {
        return a.dueTimeValue - b.dueTimeValue
      }
      return b.latestTimeValue - a.latestTimeValue
    })
}

/**
 * @description 生成工作台主聚焦区使用的高优先级事项列表。
 */
export function buildGovernancePriorityNotices(records, limit = 4) {
  return sortGovernanceNotices(records).slice(0, limit)
}

/**
 * @description 将治理事项拆成“先看 / 先处理 / 等复核”三段工作台队列。
 */
export function buildGovernanceWorkbenchBuckets(records, limit = 3) {
  const sorted = sortGovernanceNotices(records)
  return {
    read: sorted
      .filter((item) => item.status === 'PENDING_READ')
      .slice(0, limit),
    action: sorted
      .filter((item) => ['PENDING_ACTION', 'REJECTED', 'EXPIRED'].includes(item.status))
      .slice(0, limit),
    review: sorted
      .filter((item) => item.status === 'PENDING_REVIEW')
      .slice(0, limit)
  }
}
