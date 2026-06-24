<!--
文件速览：
1. 文件职责：提供统一登录入口，并根据角色完成登录后跳转。
2. 页面入口：路由 `/login`。
3. 关键结构：loginForm、handleLogin、redirect 安全校验、受限账号只读提醒模式、忘记密码入口。
4. 阅读建议：先看 handleLogin，再看 redirect 与 restrictedMode 分支，最后看模板中的入口链接。
-->
<template>
  <div class="auth-page">
    <div class="auth-glow"></div>
    <el-card class="auth-card login-card">
      <template #header>
        <div class="auth-header">
          <h2>欢迎回来</h2>
          <p>登录后继续你的求职与沟通</p>
        </div>
      </template>
      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        label-width="0px"
        class="auth-form"
      >
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="请输入用户名"
            prefix-icon="User"
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="请输入密码"
            prefix-icon="Lock"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            :loading="loading"
            class="auth-submit-btn"
            @click="handleLogin"
          >
            登录
          </el-button>
          <div class="auth-link">
            没有账号？<router-link to="/register">去注册</router-link>
          </div>
          <div class="auth-sub-link">
            <router-link to="/forgot-password">忘记密码？联系管理员重置</router-link>
          </div>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const loginFormRef = ref(null)
const loading = ref(false)

const loginForm = reactive({
  username: '',
  password: ''
})

const loginRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 3, message: '密码长度不能小于3位', trigger: 'blur' }
  ]
}

const handleLogin = async () => {
  if (!loginFormRef.value) return

  await loginFormRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        const res = await request.post('/auth/login', {
            username: loginForm.username,
            password: loginForm.password
        })

        if (res.code === 200) {
            const { token, role, username, restrictedMode, restrictedReason } = res.data

            userStore.setToken(token)
            userStore.setRole(role)
            userStore.setUserInfo({ username, role })
            userStore.setRestrictedMode(restrictedMode, restrictedReason)

            if (restrictedMode) {
                userStore.clearPasswordChangeRequired()
                ElMessage.warning(restrictedReason || '当前账号处于受限状态，仅可查看平台提醒')
                router.replace(userStore.resolveRestrictedPath(role))
                return
            }

            ElMessage.success('登录成功')

            // --- 核心跳转逻辑（支持 redirect） ---
            const redirect = getSafeRedirect()
            const defaultHomePath = userStore.resolveHomePath(role)
            const nextPath = (redirect && isRedirectAllowed(redirect, role)) ? redirect : defaultHomePath

            if (res.data?.forceChangePassword) {
                userStore.setPasswordChangeRequired(true, nextPath)
                router.replace({
                  name: 'account-password',
                  query: { redirect: nextPath }
                })
                return
            }

            userStore.clearPasswordChangeRequired()
            router.replace(nextPath)
        } else {
            ElMessage.error(res.message || '登录失败')
        }

      } catch (error) {
        console.error(error)
      } finally {
        loading.value = false
      }
    }
  })
}

// 解析并校验 redirect，避免跳出站点或跳回登录页
const getSafeRedirect = () => {
  const raw = route.query.redirect
  if (!raw) return ''
  const value = Array.isArray(raw) ? raw[0] : raw
  const target = String(value || '')
  if (!target.startsWith('/')) return ''
  if (target.startsWith('//')) return ''
  if (target.startsWith('/login') || target.startsWith('/register')) return ''
  return target
}

// 简单权限匹配，避免登录后直达无权限页面
const isRedirectAllowed = (path, role) => {
  if (!path || !role) return false
  if (path.startsWith('/admin')) return role === 'ADMIN'
  if (path.startsWith('/merchant')) return role === 'MERCHANT' || role === 'ADMIN'
  if (path.startsWith('/applicant')) return role === 'APPLICANT'
  if (path.startsWith('/jobs') || path.startsWith('/job/detail')) return role === 'APPLICANT' || role === 'ADMIN'
  if (path.startsWith('/chat')) return role === 'APPLICANT'
  return true
}
</script>

<style scoped>
/* 登录页采用 Apple 风格入口视觉，与主站统一 */
.auth-page {
  position: relative;
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background:
    radial-gradient(circle at 20% 18%, rgba(0, 113, 227, 0.14), transparent 40%),
    radial-gradient(circle at 82% 4%, rgba(96, 165, 250, 0.12), transparent 34%),
    linear-gradient(180deg, #f8fbff 0%, #f3f7fd 46%, var(--ui-bg) 100%);
  padding: 24px;
}

.auth-glow {
  position: absolute;
  width: min(70vw, 680px);
  height: min(70vw, 680px);
  border-radius: 999px;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.76) 0%, rgba(235, 244, 255, 0.18) 44%, rgba(255, 255, 255, 0) 72%);
  filter: blur(4px);
  pointer-events: none;
}

.auth-card {
  width: min(420px, 92vw);
  border-radius: 24px;
  border: 1px solid rgba(191, 219, 254, 0.72);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.94) 0%, rgba(247, 250, 255, 0.9) 100%);
  backdrop-filter: blur(16px);
  box-shadow:
    0 28px 72px rgba(15, 23, 42, 0.11),
    0 10px 28px rgba(0, 113, 227, 0.08);
}

.auth-header {
  text-align: center;
}

.auth-header h2 {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  color: #1d1d1f;
}

.auth-header p {
  margin-top: 8px;
  font-size: 13px;
  color: #5b6b82;
}

.auth-form {
  padding: 12px 4px 4px;
}

.auth-form :deep(.el-input__wrapper) {
  border-radius: var(--ui-radius-md);
  min-height: 42px;
  box-shadow: 0 0 0 1px rgba(191, 219, 254, 0.72) inset;
  background: rgba(248, 251, 255, 0.92);
}

.auth-form :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px rgba(147, 197, 253, 0.9) inset;
}

.auth-form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--ui-accent) inset, 0 0 0 4px var(--ui-accent-light) !important;
}

.auth-submit-btn {
  width: 100%;
  height: 44px;
  border-radius: var(--ui-radius-md);
  font-size: 15px;
  font-weight: 600;
}

.auth-link {
  margin-top: 12px;
  text-align: center;
  width: 100%;
  font-size: 14px;
  color: #6e6e73;
}

.auth-link a {
  color: #0071e3;
  text-decoration: none;
  font-weight: 600;
}

.auth-link a:hover {
  color: #005bb5;
}

.auth-sub-link {
  margin-top: 10px;
  text-align: center;
  width: 100%;
  font-size: 13px;
}

.auth-sub-link a {
  color: #4c6a92;
  text-decoration: none;
  font-weight: 600;
}

.auth-sub-link a:hover {
  color: #005bb5;
}
</style>
