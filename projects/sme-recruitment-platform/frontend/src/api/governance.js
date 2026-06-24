/*
文件速览：
1. 文件职责：封装商家 / 求职者侧治理通知相关接口，覆盖列表、详情、已读与动作提交。
2. 对外入口：getMyGovernanceNotices、getMyGovernanceNoticeDetail、markGovernanceNoticeRead、submitGovernanceNoticeAction。
3. 关键结构：统一的 `/governance/notices/*` 接口封装、分页参数兼容与阶段筛选透传。
4. 阅读建议：先看列表和详情，再看已读与动作提交两个写操作接口。
*/
import request from '@/utils/request'

// 获取我的治理通知列表
export function getMyGovernanceNotices(params) {
  const { page, current, ...rest } = params || {}
  const normalized = {
    ...rest,
    current: current ?? page
  }
  return request({
    url: '/governance/notices/my',
    method: 'get',
    params: normalized
  })
}

// 获取我的治理通知详情
export function getMyGovernanceNoticeDetail(id) {
  return request({
    url: `/governance/notices/my/${id}`,
    method: 'get'
  })
}

// 标记治理通知已读
export function markGovernanceNoticeRead(id) {
  return request({
    url: `/governance/notices/${id}/read`,
    method: 'put'
  })
}

// 提交治理通知动作
export function submitGovernanceNoticeAction(id, data) {
  return request({
    url: `/governance/notices/${id}/actions`,
    method: 'post',
    data
  })
}
