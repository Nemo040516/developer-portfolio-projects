// 候选人库相关接口
import request from '@/utils/request'

/**
 * @description 获取商家候选人库列表（分页）
 * @param {object} params - 查询参数（current, size, keyword, expectJob, city）
 * @returns {Promise}
 */
export function getTalentPoolList(params) {
  return request({
    url: '/merchant/talent/list',
    method: 'get',
    params
  })
}

/**
 * @description 获取候选人详情（在线简历）
 * @param {number} userId - 求职者用户ID
 * @returns {Promise}
 */
export function getTalentDetail(userId) {
  return request({
    url: `/merchant/talent/detail/${userId}`,
    method: 'get'
  })
}
