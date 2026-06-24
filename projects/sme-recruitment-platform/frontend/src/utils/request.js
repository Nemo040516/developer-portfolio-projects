/*
文件速览：
1. 文件职责：封装全局 axios 实例，统一处理鉴权头、业务响应与错误提示。
2. 对外入口：默认导出 service，请求模块统一复用。
3. 关键结构：request interceptor、response interceptor、401 失效跳转、受限账号的只读提醒模式重定向。
4. 阅读建议：先看响应拦截器里的 401/403 分支，再看自定义静默提示开关的处理。
*/
import axios from 'axios'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'

const resolveResponseMessage = (payload, fallback = 'Error') => {
    if (!payload || typeof payload !== 'object') {
        return fallback
    }
    return payload.message || payload.msg || fallback
}

const handleUnauthorizedRedirect = async (message) => {
    const userStore = useUserStore()
    await userStore.logout()
    if (window.location.pathname !== '/login') {
        const currentFullPath = window.location.pathname + window.location.search + window.location.hash
        const loginPath = `/login?redirect=${encodeURIComponent(currentFullPath)}`
        window.location.href = loginPath
        ElMessage.error(message || '登录已过期，请重新登录')
    }
}

// 1. 创建 axios 实例
const service = axios.create({
    baseURL: import.meta.env.VITE_APP_BASE_URL || 'http://localhost:8080',
    timeout: 10000 
})

// 2. 请求拦截器 (Request Interceptor)
service.interceptors.request.use(
    config => {
        // 从 Pinia Store 中获取 token
        const userStore = useUserStore()
        const token = userStore.token

        if (token) {
            // 统一使用 Bearer 方案，同时兼容已带前缀的旧格式
            config.headers['Authorization'] = token.startsWith('Bearer ')
              ? token
              : `Bearer ${token}`
        }
        return config
    },
    error => {
        return Promise.reject(error)
    }
)

// 3. 响应拦截器 (Response Interceptor)
service.interceptors.response.use(
    async response => {
        const res = response.data
        const shouldSilentBusinessError = response.config?.skipBusinessErrorMessage === true
        // 如果 code 不是 200，则认为是错误
        if (res.code !== 200) {
            // 忽略特定的业务提示，避免页面一加载就弹窗
            // 例如：如果是 "未登录" 或 "Token无效" 且当前页面允许游客访问，可以静默处理
            if (res.code === 401) {
                const unauthorizedMessage = resolveResponseMessage(res, '登录已过期，请重新登录')
                if (!shouldSilentBusinessError) {
                    await handleUnauthorizedRedirect(unauthorizedMessage)
                }
                const unauthorizedError = new Error(unauthorizedMessage)
                unauthorizedError.code = 401
                unauthorizedError.responseData = res
                unauthorizedError.isBusinessUnauthorized = true
                return Promise.reject(unauthorizedError)
            }

            const businessError = new Error(resolveResponseMessage(res, 'Error'))
            businessError.code = res.code
            businessError.responseData = res

            if (!shouldSilentBusinessError) {
                ElMessage({
                    message: resolveResponseMessage(res, 'Error'),
                    type: 'error',
                    duration: 5 * 1000
                })
            }
            return Promise.reject(businessError)
        } else {
            return res
        }
    },
    error => {
        console.error('Request Error:', error) // 打印错误信息
        const shouldSilentHttpError = error.config?.skipHttpErrorMessage === true
        const responseData = error.response?.data || {}
        const restrictedPayload = responseData?.data || {}

        if (error.response && error.response.status === 403 && restrictedPayload?.restrictedMode) {
            const userStore = useUserStore()
            const restrictedReason = restrictedPayload?.restrictedReason || resolveResponseMessage(responseData, '当前账号处于受限状态，仅可查看平台提醒')
            userStore.setRestrictedMode(true, restrictedReason)
            const restrictedPath = userStore.resolveRestrictedPath(userStore.role)
            if (restrictedPath && window.location.pathname !== restrictedPath) {
                window.location.href = restrictedPath
            }
            if (!shouldSilentHttpError) {
                ElMessage.warning(restrictedReason)
            }
        } else if (error.response && error.response.status === 401) {
            // 401 Unauthorized: Token 无效或过期
            if (!shouldSilentHttpError) {
                void handleUnauthorizedRedirect(resolveResponseMessage(responseData, '登录已过期，请重新登录'))
            }
        } else {
            if (!shouldSilentHttpError) {
                ElMessage({
                    message: error.message || resolveResponseMessage(responseData, '请求失败'),
                    type: 'error',
                    duration: 5 * 1000
                })
            }
        }
        return Promise.reject(error)
    }
)

export default service
