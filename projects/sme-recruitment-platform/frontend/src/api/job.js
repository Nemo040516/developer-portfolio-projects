import request from '@/utils/request'

// 获取职位分类列表 (树形结构)
export function getCategoryList() {
  return request({
    url: '/category/list',
    method: 'get'
  })
}

// 发布职位
export function addJob(data) {
  return request({
    url: '/jobs',
    method: 'post',
    data
  })
}

// 更新职位
export function updateJob(data) {
  return request({
    url: '/jobs',
    method: 'put',
    data
  })
}

/**
 * 更新职位状态
 * @param {Number} id 职位ID
 * @param {Number} status 状态 (1=上架, 0=下架)
 */
export function updateJobStatus(id, status) {
  return request({
    // ✅ URL 必须完全匹配后端的 @RequestMapping + @PutMapping
    // 最终生成的 URL 例如: /jobs/status/101/0
    url: `/jobs/status/${id}/${status}`, 
    method: 'put'
  })
}

// 商家端：提交复审
export function resubmitJobAudit(id) {
  return request({
    url: `/jobs/resubmit/${id}`,
    method: 'put'
  })
}

// 获取商家职位列表
export function getMerchantJobs(params) {
  const { page, current, ...rest } = params || {}
  const normalized = {
    ...rest,
    current: current ?? page
  }
  return request({
    url: '/jobs/merchant',
    method: 'get',
    params: normalized
  })
}

// ================== 公开接口 (学生端/首页) ==================

/**
 * 职位搜索接口 (支持分页和多条件筛选)
 * @param {Object} params { keyword, location, exp, edu, current, size }
 */
export function searchJobs(params) {
  const { page, current, ...rest } = params || {}
  const normalized = {
    ...rest,
    current: current ?? page
  }
  return request({
    url: '/jobs/search',
    method: 'get',
    params: normalized
  })
}

/**
 * 获取公开职位详情
 * @param {Number} id 职位ID
 */
export function getPublicJobDetail(id) {
  return request({
    url: '/jobs/public/' + id,
    method: 'get'
  })
}
