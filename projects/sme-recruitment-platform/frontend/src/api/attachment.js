/*
文件速览：
1. 文件职责：集中封装附件简历授权、授权状态查询等前端接口。
2. 对外入口：requestResumeAttachmentPermission、grantResumeAttachmentPermission、getResumeAttachmentPermissionStatus。
3. 关键结构：所有方法均复用统一 request 实例，可按需透传静默提示等请求配置。
4. 阅读建议：先看状态查询接口，再看具体授权申请与授权确认接口。
*/
import request from '@/utils/request'

/**
 * 商家申请查看附件简历
 * @param {object} data - 请求参数（applicantId, expireTime）
 * @param {object} config - 额外请求配置（如静默错误提示）
 */
export function requestResumeAttachmentPermission(data, config = {}) {
  return request({
    url: '/resume-attachment/permission/request',
    method: 'post',
    data,
    ...config
  })
}

/**
 * 求职者授权附件简历
 * @param {object} data - 请求参数（merchantId, expireTime）
 * @param {object} config - 额外请求配置（如静默错误提示）
 */
export function grantResumeAttachmentPermission(data, config = {}) {
  return request({
    url: '/resume-attachment/permission/grant',
    method: 'post',
    data,
    ...config
  })
}

/**
 * 获取附件简历授权状态
 * @param {object} params - 查询参数（applicantId, merchantId）
 * @param {object} config - 额外请求配置（如静默错误提示）
 */
export function getResumeAttachmentPermissionStatus(params, config = {}) {
  return request({
    url: '/resume-attachment/permission/status',
    method: 'get',
    params,
    ...config
  })
}
