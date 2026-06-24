/*
文件速览：
1. 文件职责：封装管理员后台所有前端 API 请求。
2. 对外入口：职位审核、商家审核、举报处理、治理通知、账号管理、密码重置与安全设置函数。
3. 关键结构：列表查询接口、操作型接口、治理通知接口、账号类接口、安全策略接口、安全日志接口。
4. 阅读建议：按 jobs / merchants / reports / governance / users / security 六段阅读，账号与安全相关放在文件末尾。
*/
import request from '@/utils/request'

// 管理员后台接口封装

// 管理员看板统计
export function getAdminStats() {
  return request({
    url: '/admin/stats',
    method: 'get'
  })
}

// 职位审核列表
export function getAdminJobs(params) {
  const { page, current, ...rest } = params || {}
  const normalized = {
    ...rest,
    current: current ?? page
  }
  return request({
    url: '/admin/jobs',
    method: 'get',
    params: normalized
  })
}

// 职位审核统计
export function getJobAuditCounts() {
  return request({
    url: '/admin/jobs/counts',
    method: 'get'
  })
}

// 审核职位
export function auditJob(id, data) {
  return request({
    url: `/admin/jobs/${id}/audit`,
    method: 'put',
    data
  })
}

// 批量审核职位
export function auditJobBatch(data) {
  return request({
    url: '/admin/jobs/batch/audit',
    method: 'put',
    data
  })
}

// 获取职位审核操作记录
export function getJobAuditLogs(id) {
  return request({
    url: `/admin/jobs/${id}/logs`,
    method: 'get'
  })
}

// 撤回职位审核
export function revokeJobAudit(id, data) {
  return request({
    url: `/admin/jobs/${id}/revoke`,
    method: 'put',
    data
  })
}

// 商家审核列表
export function getAdminMerchants(params) {
  const { page, current, ...rest } = params || {}
  const normalized = {
    ...rest,
    current: current ?? page
  }
  return request({
    url: '/admin/merchants',
    method: 'get',
    params: normalized
  })
}

// 审核商家
export function auditMerchant(id, data) {
  return request({
    url: `/admin/merchants/${id}/audit`,
    method: 'put',
    data
  })
}

// 批量审核商家
export function auditMerchantBatch(data) {
  return request({
    url: '/admin/merchants/batch/audit',
    method: 'put',
    data
  })
}

// 获取商家审核操作记录
export function getMerchantAuditLogs(id) {
  return request({
    url: `/admin/merchants/${id}/logs`,
    method: 'get'
  })
}

// 更新商家发布状态
export function updateMerchantStatus(id, data) {
  return request({
    url: `/admin/merchants/${id}/status`,
    method: 'put',
    data
  })
}

// 举报列表
export function getAdminReports(params) {
  const { page, current, ...rest } = params || {}
  const normalized = {
    ...rest,
    current: current ?? page
  }
  return request({
    url: '/admin/reports',
    method: 'get',
    params: normalized
  })
}

// 处理举报
export function handleReport(id, data) {
  return request({
    url: `/admin/reports/${id}/handle`,
    method: 'put',
    data
  })
}

// 批量处理举报
export function handleReportBatch(data) {
  return request({
    url: '/admin/reports/batch/handle',
    method: 'put',
    data
  })
}

// 获取举报操作记录
export function getReportLogs(id) {
  return request({
    url: `/admin/reports/${id}/logs`,
    method: 'get'
  })
}

// 获取治理通知列表
export function getAdminGovernanceNotices(params) {
  const { page, current, ...rest } = params || {}
  const normalized = {
    ...rest,
    current: current ?? page
  }
  return request({
    url: '/admin/governance/notices',
    method: 'get',
    params: normalized
  })
}

// 获取治理通知详情
export function getAdminGovernanceNoticeDetail(id) {
  return request({
    url: `/admin/governance/notices/${id}`,
    method: 'get'
  })
}

// 创建治理通知
export function createAdminGovernanceNotice(data) {
  return request({
    url: '/admin/governance/notices',
    method: 'post',
    data
  })
}

// 复核治理通知
export function reviewAdminGovernanceNotice(id, data) {
  return request({
    url: `/admin/governance/notices/${id}/review`,
    method: 'post',
    data
  })
}

// 账号列表（封禁/黑名单管理）
export function getAdminUsers(params) {
  const { page, current, ...rest } = params || {}
  const normalized = {
    ...rest,
    current: current ?? page
  }
  return request({
    url: '/admin/users',
    method: 'get',
    params: normalized
  })
}

// 更新账号封禁状态
export function updateUserBan(id, data) {
  return request({
    url: `/admin/users/${id}/ban`,
    method: 'put',
    data
  })
}

// 管理员重置账号密码
export function resetUserPassword(id, data) {
  return request({
    url: `/admin/users/${id}/password/reset`,
    method: 'put',
    data
  })
}

// 获取管理员可见的账号安全设置
export function getAdminSecuritySettings() {
  return request({
    url: '/admin/security-settings',
    method: 'get'
  })
}

// 更新“临时密码登录后强制修改密码”开关
export function updateForcePasswordChangeSetting(data) {
  return request({
    url: '/admin/security-settings/password-force-change',
    method: 'put',
    data
  })
}

// 获取账号安全设置最近变更记录
export function getAdminSecuritySettingLogs() {
  return request({
    url: '/admin/security-settings/logs',
    method: 'get'
  })
}
