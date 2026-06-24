/*
文件速览：
1. 文件职责：封装求职者端看板、投递记录、在线简历与附件简历相关请求。
2. 对外入口：getDashboardStats、getInsightStats、getMyApplications、getMyResume、saveMyResume。
3. 关键结构：统一复用 request 实例，并支持部分接口传入额外请求配置。
4. 阅读建议：先看 saveMyResume 与 getMyApplications 的参数归一化，再看附件简历相关接口。
*/
import request from '@/utils/request'

// 获取求职者看板统计数据 (简历完善度、投递状态统计)
export function getDashboardStats() {
  return request({
    url: '/seeker/dashboard-stats',
    method: 'get'
  })
}

// 获取求职洞察统计 (周报图表)
export function getInsightStats() {
  return request({
    url: '/seeker/insight-stats',
    method: 'get'
  })
}

// 获取我的投递记录
export function getMyApplications(params) {
  const { page, current, ...rest } = params || {}
  const normalized = {
    ...rest,
    current: current ?? page
  }
  return request({
    url: '/seeker/applications',
    method: 'get',
    params: normalized
  })
}

// 获取在线简历
export function getMyResume() {
  return request({
    url: '/seeker/resume',
    method: 'get'
  })
}

// 保存在线简历
export function saveMyResume(data, config = {}) {
  return request({
    url: '/seeker/resume',
    method: 'post',
    data,
    ...config
  })
}

// 获取附件简历
export function getResumeAttachment() {
  return request({
    url: '/seeker/resume-attachment',
    method: 'get'
  })
}

// 上传/替换附件简历
export function uploadResumeAttachment(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: '/seeker/resume-attachment',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// 删除附件简历
export function deleteResumeAttachment() {
  return request({
    url: '/seeker/resume-attachment',
    method: 'delete'
  })
}
