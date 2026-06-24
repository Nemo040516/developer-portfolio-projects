<!--
文件速览：
1. 文件职责：提供未登录用户的统一注册入口，并完成基础身份角色注册。
2. 页面入口：路由 `/register`。
3. 关键结构：registerForm、registerRules、handleRegister、角色选择。
4. 阅读建议：先看 handleRegister，再看表单校验和样式层次。
-->
<template>
  <div class="auth-page">
    <div class="auth-glow"></div>
    <el-card class="auth-card register-card">
      <template #header>
        <div class="auth-header">
          <h2>创建账号</h2>
          <p>开始你的求职流程，连接更多机会</p>
        </div>
      </template>
      <el-form
        ref="registerFormRef"
        :model="registerForm"
        :rules="registerRules"
        label-width="80px"
        class="auth-form"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="registerForm.username"
            placeholder="请输入用户名"
            prefix-icon="User"
          />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="registerForm.password"
            type="password"
            placeholder="请输入密码"
            prefix-icon="Lock"
            show-password
          />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="registerForm.confirmPassword"
            type="password"
            placeholder="请再次输入密码"
            prefix-icon="Lock"
            show-password
          />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="registerForm.role" placeholder="请选择角色">
            <el-option label="求职者" value="APPLICANT" />
            <el-option label="商家" value="MERCHANT" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            :loading="loading"
            class="auth-submit-btn"
            @click="handleRegister"
          >
            注册
          </el-button>
          <div class="auth-link">
            已有账号？<router-link to="/login">去登录</router-link>
          </div>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const router = useRouter()
const registerFormRef = ref(null)
const loading = ref(false)

const registerForm = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  role: ''
})

const validatePass2 = (rule, value, callback) => {
  if (value === '') {
    callback(new Error('请再次输入密码'))
  } else if (value !== registerForm.password) {
    callback(new Error('两次输入密码不一致!'))
  } else {
    callback()
  }
}

const registerRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 3, message: '密码长度不能小于3位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, validator: validatePass2, trigger: 'blur' }
  ],
  role: [
    { required: true, message: '请选择角色', trigger: 'change' }
  ]
}

const handleRegister = async () => {
  if (!registerFormRef.value) return

  await registerFormRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        // 调用后端接口
        const res = await request.post('/auth/register', {
            username: registerForm.username,
            password: registerForm.password,
            role: registerForm.role // 确保这里是 APPLICANT 或 MERCHANT
        })

        // 假设后端返回 code === 200 代表成功
        // 注意：request.js 响应拦截器已经处理了 response.data，所以这里 res 就是后端返回的数据对象
        if(res.code === 200) {
            ElMessage.success('注册成功，请登录')
            router.push('/login')
        } else {
            ElMessage.error(res.message || '注册失败')
        }
      } catch (error) {
        console.error(error)
        // 错误提示通常在 request.js 拦截器或这里处理，这里简单打印
        // 如果 request.js 没有统一处理错误提示，可以在这里加
        // ElMessage.error(error.message || '注册失败')
      } finally {
        loading.value = false
      }
    }
  })
}
</script>

<style scoped>
/* 注册页与登录页保持同一 Apple 风格入口语言 */
.auth-page {
  position: relative;
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background:
    radial-gradient(circle at 82% 12%, rgba(0, 113, 227, 0.14), transparent 40%),
    radial-gradient(circle at 18% 88%, rgba(96, 165, 250, 0.12), transparent 36%),
    linear-gradient(180deg, #f8fbff 0%, #f3f7fd 46%, var(--ui-bg) 100%);
  padding: 24px;
}

.auth-glow {
  position: absolute;
  width: min(74vw, 720px);
  height: min(74vw, 720px);
  border-radius: 999px;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.76) 0%, rgba(235, 244, 255, 0.18) 44%, rgba(255, 255, 255, 0) 72%);
  filter: blur(4px);
  pointer-events: none;
}

.auth-card {
  width: min(470px, 94vw);
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
  padding: 10px 4px 4px;
}

.auth-form :deep(.el-form-item__label) {
  color: var(--ui-muted-strong);
  font-weight: 600;
}

.auth-form :deep(.el-input__wrapper),
.auth-form :deep(.el-select__wrapper) {
  border-radius: var(--ui-radius-md);
  min-height: 42px;
  box-shadow: 0 0 0 1px rgba(191, 219, 254, 0.72) inset;
  background: rgba(248, 251, 255, 0.92);
}

.auth-form :deep(.el-input__wrapper:hover),
.auth-form :deep(.el-select__wrapper:hover) {
  box-shadow: 0 0 0 1px rgba(147, 197, 253, 0.9) inset;
}

.auth-form :deep(.el-input__wrapper.is-focus),
.auth-form :deep(.el-select__wrapper.is-focused) {
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
</style>
