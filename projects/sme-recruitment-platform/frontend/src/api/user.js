import request from '@/utils/request'

// 获取当前用户账号信息
export function getUserProfile() {
  return request({
    url: '/user/info',
    method: 'get'
  })
}

// 更新账号基础资料
export function updateUserProfile(data) {
  return request({
    url: '/user/profile',
    method: 'put',
    data
  })
}

// 上传账号头像
export function uploadUserAvatar(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: '/user/avatar',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// 获取账号隐私设置
export function getPrivacySettings() {
  return request({
    url: '/user/privacy',
    method: 'get'
  })
}

// 更新账号隐私设置
export function updatePrivacySettings(data) {
  return request({
    url: '/user/privacy',
    method: 'put',
    data
  })
}

// 修改密码
export function changePassword(data) {
  return request({
    url: '/user/password',
    method: 'post',
    data
  })
}

// 获取最近登录记录
export function getLoginLogs(params) {
  return request({
    url: '/user/login-logs',
    method: 'get',
    params
  })
}

// 获取认证状态
export function getVerifyStatus() {
  return request({
    url: '/user/verify',
    method: 'get'
  })
}

// 提交认证信息
export function submitVerify(data) {
  return request({
    url: '/user/verify',
    method: 'post',
    data
  })
}
