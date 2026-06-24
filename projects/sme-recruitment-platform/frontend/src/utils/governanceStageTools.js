/*
文件速览：
1. 文件职责：为治理通知中心提供跨页阶段批量处理辅助能力，负责分页拉平查询与文本摘要下载。
2. 对外入口：fetchAllGovernanceStageNotices、downloadGovernanceStageText。
3. 关键结构：分页聚合、去重归并、纯前端文本文件下载。
4. 阅读建议：先看 fetchAllGovernanceStageNotices 的跨页查询，再看 downloadGovernanceStageText 的导出逻辑。
*/

const dedupeNoticeRecords = (records) => {
  const map = new Map()
  records.forEach((item) => {
    const id = Number(item?.id)
    if (!Number.isFinite(id) || id <= 0) return
    map.set(id, item)
  })
  return Array.from(map.values())
}

/**
 * @description 拉平治理通知当前筛选条件下的全部分页结果，供跨页批量处理与摘要导出复用。
 */
export async function fetchAllGovernanceStageNotices({
  fetchPage,
  baseParams = {},
  normalizeList,
  pageSize = 100,
  maxPages = 30
}) {
  const firstRes = await fetchPage({
    ...baseParams,
    current: 1,
    size: pageSize
  })

  if (firstRes?.code !== 200) {
    throw new Error(firstRes?.msg || '获取治理通知失败')
  }

  const firstPageList = normalizeList(firstRes.data)
  const total = Number(firstRes.data?.total || firstPageList.length || 0)
  const totalPages = Math.max(Math.ceil(total / pageSize), 1)
  const finalPage = Math.min(totalPages, maxPages)
  const allRecords = [...firstPageList]

  for (let current = 2; current <= finalPage; current += 1) {
    const res = await fetchPage({
      ...baseParams,
      current,
      size: pageSize
    })
    if (res?.code !== 200) {
      throw new Error(res?.msg || '获取治理通知失败')
    }
    allRecords.push(...normalizeList(res.data))
  }

  return {
    total,
    records: dedupeNoticeRecords(allRecords),
    truncated: totalPages > maxPages
  }
}

/**
 * @description 将治理摘要导出为 txt 文件，便于管理员或用户离线留存。
 */
export function downloadGovernanceStageText(filename, content) {
  const blob = new Blob([content], { type: 'text/plain;charset=utf-8' })
  const url = window.URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  document.body.appendChild(anchor)
  anchor.click()
  document.body.removeChild(anchor)
  window.URL.revokeObjectURL(url)
}
