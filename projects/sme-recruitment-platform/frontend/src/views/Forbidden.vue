<template>
  <div class="forbidden-page">
    <div class="forbidden-card">
      <div class="forbidden-code">403</div>
      <div class="forbidden-title">无权限访问</div>
      <div class="forbidden-desc">
        你没有访问该页面的权限，请联系管理员或切换账号后重试。
      </div>
      <div class="forbidden-actions">
        <el-button type="primary" @click="goHome">返回首页</el-button>
        <el-button @click="goBack">返回上一页</el-button>
        <el-button plain @click="goLogin">去登录</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const goBack = () => {
  router.back()
}

const goLogin = () => {
  router.push('/login')
}

const goHome = () => {
  const role = (userStore.role || '').toUpperCase()
  if (role === 'MERCHANT') {
    router.push('/merchant/dashboard')
    return
  }
  if (role === 'APPLICANT') {
    router.push('/applicant/dashboard')
    return
  }
  if (role === 'ADMIN') {
    router.push('/admin/dashboard')
    return
  }
  router.push('/login')
}
</script>

<style scoped>
.forbidden-page {
  min-height: calc(100vh - 120px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: radial-gradient(circle at 20% 20%, rgba(59, 130, 246, 0.18), transparent 50%),
    radial-gradient(circle at 80% 0%, rgba(14, 116, 144, 0.16), transparent 45%),
    #f8fafc;
}

.forbidden-card {
  max-width: 520px;
  width: 100%;
  background: #ffffff;
  border-radius: 18px;
  padding: 36px 32px;
  border: 1px solid rgba(148, 163, 184, 0.25);
  box-shadow: 0 16px 30px rgba(15, 23, 42, 0.12);
  text-align: center;
}

.forbidden-code {
  font-size: 64px;
  font-weight: 700;
  color: #1d4ed8;
  letter-spacing: 4px;
}

.forbidden-title {
  font-size: 20px;
  font-weight: 600;
  margin-top: 8px;
  color: #0f172a;
}

.forbidden-desc {
  margin-top: 12px;
  font-size: 14px;
  color: #64748b;
  line-height: 1.6;
}

.forbidden-actions {
  margin-top: 20px;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  justify-content: center;
}
</style>
