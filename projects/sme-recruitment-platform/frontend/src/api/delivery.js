/*
文件速览：
1. 文件职责：封装投递相关接口，请求求职者投递、商家投递列表、投递状态更新与投递状态查询。
2. 对外入口：submitDelivery、getMerchantDeliveryList、updateDeliveryStatus、getDeliveryStatus。
3. 关键结构：按业务动作划分的请求函数；统一复用 request 实例。
4. 阅读建议：先看函数名与接口路径映射，再看注释中的参数约定。
*/
// 导入封装好的axios实例，用于发送HTTP请求
import request from '@/utils/request'

/**
 * @description 投递简历
 * @param {object} data - 请求数据，应包含 jobId
 * @property {number} data.jobId - 职位ID
 * @returns {Promise} - 返回一个Promise对象，成功时包含响应数据
 */
export function submitDelivery(data) {
  // 向后端 /delivery/submit 地址发送 POST 请求
  return request({
    url: '/delivery/submit',
    method: 'post',
    data // 请求体中包含 { jobId: ... }
  })
}

/**
 * @description 商家端获取候选人投递列表
 * @param {object} params - 查询参数（current, size, jobId, status, degree）
 * @returns {Promise}
 */
export function getMerchantDeliveryList(params) {
  return request({
    url: '/delivery/merchant/list',
    method: 'get',
    params
  })
}

/**
 * @description 商家端更新投递状态
 * @param {object} data - 更新参数（id, status, feedback, interviewTime, interviewLocation, interviewMethod, interviewRemark）
 * @returns {Promise}
 */
export function updateDeliveryStatus(data) {
  return request({
    url: '/delivery/status',
    method: 'put',
    data
  })
}

/**
 * @description 获取当前用户对某职位的投递状态
 * @param {number} jobId - 职位ID
 * @returns {Promise}
 */
export function getDeliveryStatus(jobId) {
  return request({
    url: `/delivery/status/${jobId}`,
    method: 'get'
  })
}
