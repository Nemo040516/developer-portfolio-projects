/*
文件速览：
1. 文件职责：提供通用分页列表拉平能力，把分页接口聚合成完整记录数组。
2. 对外入口：fetchAllPagedRecords。
3. 关键结构：normalizePagedList、dedupePagedRecords、分页循环与截断保护。
4. 阅读建议：先看主函数入参，再看 totalPages / maxPages 的处理方式。
*/

const normalizePagedList = (normalizeList, data) => {
  if (typeof normalizeList === 'function') {
    return normalizeList(data)
  }
  if (Array.isArray(data)) {
    return data
  }
  return Array.isArray(data?.records) ? data.records : []
}

const dedupePagedRecords = (records, getRecordId) => {
  if (typeof getRecordId !== 'function') {
    return records
  }
  const map = new Map()
  records.forEach((item) => {
    const recordId = getRecordId(item)
    if (recordId === null || recordId === undefined || recordId === '') {
      return
    }
    map.set(recordId, item)
  })
  return Array.from(map.values())
}

/**
 * @description 拉平普通分页接口，返回完整 records 与是否触发截断保护。
 */
export async function fetchAllPagedRecords({
  fetchPage,
  baseParams = {},
  normalizeList,
  getRecordId = (item) => item?.id,
  pageSize = 100,
  maxPages = 50,
  errorMessage = '获取列表失败'
}) {
  const firstRes = await fetchPage({
    ...baseParams,
    current: 1,
    size: pageSize
  })

  if (firstRes?.code !== 200) {
    throw new Error(firstRes?.msg || errorMessage)
  }

  const firstPageList = normalizePagedList(normalizeList, firstRes.data)
  const totalValue = Number(firstRes.data?.total)
  const hasTotal = Number.isFinite(totalValue) && totalValue >= 0
  const totalPages = hasTotal ? Math.max(Math.ceil(totalValue / pageSize), 1) : maxPages
  const finalPage = Math.min(totalPages, maxPages)
  const allRecords = [...firstPageList]

  for (let current = 2; current <= finalPage; current += 1) {
    const res = await fetchPage({
      ...baseParams,
      current,
      size: pageSize
    })
    if (res?.code !== 200) {
      throw new Error(res?.msg || errorMessage)
    }
    const pageList = normalizePagedList(normalizeList, res.data)
    allRecords.push(...pageList)
    if (!hasTotal && pageList.length < pageSize) {
      break
    }
  }

  return {
    total: hasTotal ? totalValue : allRecords.length,
    records: dedupePagedRecords(allRecords, getRecordId),
    truncated: hasTotal ? totalPages > maxPages : firstPageList.length >= pageSize && allRecords.length >= pageSize * maxPages
  }
}
