import request from '@/utils/request'

// 检查商户当前状态（是否完善信息、审核状态）
export function checkMerchantStatus() {
  return request({
    url: '/merchant/check-status',
    method: 'get'
  })
}

// 获取企业详情
export function getMerchantInfo() {
  return request({
    url: '/merchant/info',
    method: 'get'
  })
}

// 更新企业详情
export function updateMerchantInfo(data) {
  return request({
    url: '/merchant/update',
    method: 'post',
    data
  })
}

// 上传企业 Logo
export function uploadMerchantLogo(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: '/merchant/logo',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// 上传企业资质材料（营业执照/法人身份证/授权书等）
export function uploadMerchantQualification(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: '/merchant/qualification',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// 获取商家工作台统计数据
export function getMerchantDashboardStats(params) {
  return request({
    url: '/merchant/dashboard-stats',
    method: 'get',
    params
  })
}
