// 面试安排相关接口
import request from '@/utils/request'

/**
 * 获取投递对应的面试安排列表（多轮）
 * @param {number} deliveryId - 投递记录ID
 */
export function getInterviewList(deliveryId) {
  return request({
    url: '/interview/list',
    method: 'get',
    params: { deliveryId }
  })
}

/**
 * 更新面试状态（确认/拒绝/取消/完成）
 * @param {object} data - 参数（id, status）
 */
export function updateInterviewStatus(data) {
  return request({
    url: '/interview/status',
    method: 'put',
    data
  })
}
