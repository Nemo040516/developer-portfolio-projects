/*
文件速览：
1. 文件职责：维护全局登录态、角色信息、用户信息与强制改密状态。
2. 对外入口：useUserStore。
3. 关键结构：token、role、userInfo、restrictedMode、passwordChangeRequired、passwordChangeRedirect。
4. 阅读建议：先看 state 初始化，再看 restrictedMode 与 passwordChangeRequired 两组状态控制，最后看 logout。
*/
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { logout as logoutApi } from '@/api/auth'

// 角色规范化：兼容旧角色 HR/STUDENT
const normalizeRole = (value) => {
  if (!value) return ''
  const upper = String(value).trim().toUpperCase()
  if (upper === 'HR') return 'MERCHANT'
  if (upper === 'STUDENT') return 'APPLICANT'
  return upper
}

const normalizeUserInfo = (info) => {
  if (!info) return {}
  const normalized = { ...info }
  if (normalized.role) {
    normalized.role = normalizeRole(normalized.role)
  }
  return normalized
}

export const useUserStore = defineStore('user', () => {
  // State
  // 使用 sessionStorage：关闭浏览器后需重新登录
  const token = ref(sessionStorage.getItem('token') || '')
  const role = ref(normalizeRole(sessionStorage.getItem('role') || ''))
  const userInfo = ref(normalizeUserInfo(JSON.parse(sessionStorage.getItem('userInfo') || '{}')))
  const restrictedMode = ref(sessionStorage.getItem('restrictedMode') === '1')
  const restrictedReason = ref(sessionStorage.getItem('restrictedReason') || '')
  const passwordChangeRequired = ref(sessionStorage.getItem('passwordChangeRequired') === '1')
  const passwordChangeRedirect = ref(sessionStorage.getItem('passwordChangeRedirect') || '')

  // 同步修复旧缓存角色，避免历史角色导致前端鉴权失败
  if (role.value && role.value !== sessionStorage.getItem('role')) {
    sessionStorage.setItem('role', role.value)
  }
  if (userInfo.value?.role && userInfo.value.role !== JSON.parse(sessionStorage.getItem('userInfo') || '{}')?.role) {
    sessionStorage.setItem('userInfo', JSON.stringify(userInfo.value))
  }

  // Actions
  const setToken = (newToken) => {
    token.value = newToken
    sessionStorage.setItem('token', newToken)
  }

  const setRole = (newRole) => {
    const normalized = normalizeRole(newRole)
    role.value = normalized
    sessionStorage.setItem('role', normalized)
  }

  const setUserInfo = (info) => {
    const normalized = normalizeUserInfo(info)
    userInfo.value = normalized
    sessionStorage.setItem('userInfo', JSON.stringify(normalized))
    if (normalized.role) {
        setRole(normalized.role)
    }
  }

  const setRestrictedMode = (enabled, reason = '') => {
    restrictedMode.value = !!enabled
    restrictedReason.value = enabled ? String(reason || '') : ''
    sessionStorage.setItem('restrictedMode', enabled ? '1' : '0')
    if (enabled && reason) {
      sessionStorage.setItem('restrictedReason', String(reason))
    } else {
      sessionStorage.removeItem('restrictedReason')
    }
  }

  const clearRestrictedMode = () => {
    restrictedMode.value = false
    restrictedReason.value = ''
    sessionStorage.removeItem('restrictedMode')
    sessionStorage.removeItem('restrictedReason')
  }

  const setPasswordChangeRequired = (required, redirect = '') => {
    passwordChangeRequired.value = !!required
    passwordChangeRedirect.value = redirect || ''
    sessionStorage.setItem('passwordChangeRequired', required ? '1' : '0')
    if (redirect) {
      sessionStorage.setItem('passwordChangeRedirect', redirect)
    } else {
      sessionStorage.removeItem('passwordChangeRedirect')
    }
  }

  const clearPasswordChangeRequired = () => {
    passwordChangeRequired.value = false
    passwordChangeRedirect.value = ''
    sessionStorage.removeItem('passwordChangeRequired')
    sessionStorage.removeItem('passwordChangeRedirect')
  }

  const resolveHomePath = (targetRole = role.value) => {
    const normalized = normalizeRole(targetRole)
    if (restrictedMode.value) {
      const restrictedPath = resolveRestrictedPath(normalized)
      if (restrictedPath) {
        return restrictedPath
      }
    }
    if (normalized === 'MERCHANT') return '/merchant/dashboard'
    if (normalized === 'APPLICANT') return '/applicant/dashboard'
    if (normalized === 'ADMIN') return '/admin/dashboard'
    return '/'
  }

  const resolveRestrictedPath = (targetRole = role.value) => {
    const normalized = normalizeRole(targetRole)
    if (normalized === 'MERCHANT') return '/merchant/governance'
    if (normalized === 'APPLICANT') return '/applicant/notices'
    return '/login'
  }

  const logout = async () => {
    try {
      // 1. 调用后端注销接口 (可选)
      await logoutApi()
    } catch (error) {
      console.warn('后端注销接口调用失败，继续执行前端清理', error)
    } finally {
      // 2. 清除前端状态
      token.value = ''
      role.value = ''
      userInfo.value = {}
      restrictedMode.value = false
      restrictedReason.value = ''
      passwordChangeRequired.value = false
      passwordChangeRedirect.value = ''
      
      // 3. 清除存储
      sessionStorage.removeItem('token')
      sessionStorage.removeItem('role')
      sessionStorage.removeItem('userInfo')
      sessionStorage.removeItem('restrictedMode')
      sessionStorage.removeItem('restrictedReason')
      sessionStorage.removeItem('passwordChangeRequired')
      sessionStorage.removeItem('passwordChangeRedirect')

      // 兼容清理旧缓存（避免历史版本遗留）
      localStorage.removeItem('token')
      localStorage.removeItem('role')
      localStorage.removeItem('userInfo')
    }
  }

  return {
    token,
    role,
    userInfo,
    restrictedMode,
    restrictedReason,
    passwordChangeRequired,
    passwordChangeRedirect,
    setToken,
    setRole,
    setUserInfo,
    setRestrictedMode,
    clearRestrictedMode,
    setPasswordChangeRequired,
    clearPasswordChangeRequired,
    resolveHomePath,
    resolveRestrictedPath,
    logout
  }
})
