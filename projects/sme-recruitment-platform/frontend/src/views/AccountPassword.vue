<!--
文件速览：
1. 文件职责：提供全角色统一的密码修改页，并承接“强制改密”跳转。
2. 页面入口：路由 `/account/password`。
3. 关键结构：passwordForm、submitPasswordChange、resolveTargetPath。
4. 阅读建议：先看顶部提示区，再看 submitPasswordChange 与跳转收口逻辑。
-->
<template>
  <div class="password-page">
    <div class="password-glow"></div>
    <el-card class="password-card">
      <template #header>
        <div class="password-header">
          <h2>修改密码</h2>
          <p>为保障账号安全，请使用当前密码完成更新</p>
        </div>
      </template>

      <el-alert
        v-if="userStore.passwordChangeRequired"
        type="warning"
        show-icon
        :closable="false"
        class="password-alert"
        title="当前账号使用的是管理员重置的临时密码，请先完成修改后再继续使用系统。"
      />

      <el-form :model="passwordForm" label-width="92px" class="password-form">
        <el-form-item label="当前密码">
          <el-input v-model="passwordForm.oldPassword" type="password" show-password placeholder="请输入当前密码" />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="请输入新密码" />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input v-model="passwordForm.confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
        </el-form-item>
      </el-form>

      <div class="password-tip">
        建议使用长度不少于 6 位、且不与临时密码相同的新密码。修改成功后会自动返回你原本要访问的页面。
      </div>

      <div class="password-actions">
        <el-button v-if="!userStore.passwordChangeRequired" @click="goBack">返回</el-button>
        <el-button type="primary" :loading="submitting" @click="submitPasswordChange">确认修改</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
/*
文件速览：
1. 文件职责：统一处理当前密码校验、新密码提交与强制改密完成后的跳转。
2. 对外入口：submitPasswordChange、goBack。
3. 关键结构：resolveTargetPath 用于恢复原目标页面，resetForm 用于清理状态。
4. 阅读建议：优先查看 submitPasswordChange，再看 resolveTargetPath。
*/
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { changePassword } from '@/api/user'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const submitting = ref(false)

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const getSafeRedirect = (value) => {
  const target = String(value || '')
  if (!target.startsWith('/')) return ''
  if (target.startsWith('//')) return ''
  if (target.startsWith('/login') || target.startsWith('/register') || target.startsWith('/forgot-password')) return ''
  if (target.startsWith('/account/password')) return ''
  return target
}

const resolveTargetPath = () => {
  const routeRedirect = Array.isArray(route.query.redirect) ? route.query.redirect[0] : route.query.redirect
  const safeRouteRedirect = getSafeRedirect(routeRedirect)
  if (safeRouteRedirect) return safeRouteRedirect
  const safeStoreRedirect = getSafeRedirect(userStore.passwordChangeRedirect)
  if (safeStoreRedirect) return safeStoreRedirect
  return userStore.resolveHomePath()
}

const resetForm = () => {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
}

const goBack = () => {
  router.back()
}

const submitPasswordChange = async () => {
  if (!passwordForm.oldPassword || !passwordForm.newPassword) {
    ElMessage.warning('请填写完整密码信息')
    return
  }
  if (passwordForm.newPassword.length < 6) {
    ElMessage.warning('新密码至少 6 位')
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.warning('两次密码输入不一致')
    return
  }

  submitting.value = true
  try {
    await changePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword
    })
    userStore.clearPasswordChangeRequired()
    resetForm()
    ElMessage.success('密码已更新')
    router.replace(resolveTargetPath())
  } catch (error) {
    // 统一由请求拦截器提示
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.password-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 24px;
  background:
    radial-gradient(circle at 18% 18%, rgba(14, 165, 233, 0.14), transparent 40%),
    radial-gradient(circle at 82% 6%, rgba(34, 197, 94, 0.1), transparent 34%),
    linear-gradient(180deg, #f8fafc 0%, var(--ui-bg) 100%);
}

.password-glow {
  position: absolute;
  width: min(72vw, 700px);
  height: min(72vw, 700px);
  border-radius: 999px;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.72) 0%, rgba(255, 255, 255, 0) 70%);
  pointer-events: none;
}

.password-card {
  width: min(460px, 94vw);
  border-radius: 24px;
  border: 1px solid var(--ui-border);
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(16px);
  box-shadow: var(--ui-shadow-lg);
}

.password-header {
  text-align: center;
}

.password-header h2 {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  color: #0f172a;
}

.password-header p {
  margin-top: 8px;
  font-size: 13px;
  color: #64748b;
}

.password-alert {
  margin-bottom: 18px;
  border-radius: 14px;
}

.password-form :deep(.el-input__wrapper) {
  min-height: 42px;
  border-radius: 12px;
}

.password-tip {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.7;
  color: #64748b;
}

.password-actions {
  margin-top: 18px;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
