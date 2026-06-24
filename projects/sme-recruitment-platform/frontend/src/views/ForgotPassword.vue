<!--
文件速览：
1. 文件职责：为未登录用户展示“管理员协助重置密码”的忘记密码说明页。
2. 页面入口：路由 `/forgot-password`。
3. 关键结构：处理步骤说明、身份核验提示、返回登录按钮。
4. 阅读建议：先看 template 中的流程说明，再看 script 的跳转逻辑和样式结构。
-->
<template>
  <div class="auth-page forgot-page">
    <div class="auth-glow"></div>
    <el-card class="auth-card forgot-card">
      <template #header>
        <div class="auth-header">
          <h2>忘记密码</h2>
          <p>本系统采用“管理员协助重置临时密码”的方案</p>
        </div>
      </template>

      <div class="guide-section">
        <div class="section-title">处理流程</div>
        <div class="step-list">
          <div class="step-item">
            <span class="step-index">1</span>
            <div class="step-body">
              <div class="step-title">准备身份信息</div>
              <div class="step-desc">请准备账号名，以及你注册时填写的手机号、邮箱或其他可核验信息。</div>
            </div>
          </div>
          <div class="step-item">
            <span class="step-index">2</span>
            <div class="step-body">
              <div class="step-title">联系管理员核验</div>
              <div class="step-desc">管理员会在后台账号管理页为你设置一个临时密码。</div>
            </div>
          </div>
          <div class="step-item">
            <span class="step-index">3</span>
            <div class="step-body">
              <div class="step-title">登录后立即修改</div>
              <div class="step-desc">收到临时密码后，请尽快登录，并按系统提示进入统一修改密码页，更新为你的正式密码。</div>
            </div>
          </div>
        </div>
      </div>

      <div class="guide-section">
        <div class="section-title">建议提供给管理员的信息</div>
        <div class="info-chip-list">
          <span class="info-chip">账号名</span>
          <span class="info-chip">用户角色</span>
          <span class="info-chip">绑定手机号</span>
          <span class="info-chip">绑定邮箱</span>
          <span class="info-chip">最近一次使用时间</span>
        </div>
      </div>

      <div class="notice-card">
        如果你仍然持有可登录设备，建议优先在已登录状态下直接使用系统内的“修改密码”能力，这样更快也更安全。
      </div>

      <div class="action-row">
        <el-button plain @click="goLogin">返回登录</el-button>
        <el-button type="primary" @click="goLogin">我已联系管理员</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
/*
文件速览：
1. 文件职责：提供忘记密码说明页的最小交互。
2. 对外入口：goLogin。
3. 关键结构：仅负责页面返回登录，不依赖后端接口。
4. 阅读建议：从 goLogin 开始看即可。
*/
import { useRouter } from 'vue-router'

const router = useRouter()

const goLogin = () => {
  router.push('/login')
}
</script>

<style scoped>
.auth-page {
  position: relative;
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background:
    radial-gradient(circle at 18% 18%, rgba(0, 113, 227, 0.14), transparent 42%),
    radial-gradient(circle at 82% 8%, rgba(96, 165, 250, 0.12), transparent 34%),
    linear-gradient(180deg, #f8fbff 0%, #f3f7fd 46%, var(--ui-bg) 100%);
  padding: 24px;
}

.auth-glow {
  position: absolute;
  width: min(72vw, 720px);
  height: min(72vw, 720px);
  border-radius: 999px;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.78) 0%, rgba(235, 244, 255, 0.2) 46%, rgba(255, 255, 255, 0) 72%);
  filter: blur(4px);
  pointer-events: none;
}

.auth-card {
  width: min(560px, 94vw);
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
  color: #0f172a;
}

.auth-header p {
  margin-top: 8px;
  font-size: 13px;
  color: #5b6b82;
}

.guide-section {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-bottom: 20px;
}

.section-title {
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
}

.step-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.step-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 16px;
  border-radius: 18px;
  background: linear-gradient(180deg, rgba(250, 252, 255, 0.98) 0%, rgba(241, 247, 255, 0.96) 100%);
  border: 1px solid rgba(191, 219, 254, 0.72);
}

.step-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 999px;
  background: #0071e3;
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  flex-shrink: 0;
}

.step-body {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.step-title {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
}

.step-desc {
  font-size: 13px;
  line-height: 1.7;
  color: #475569;
}

.info-chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.info-chip {
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(0, 113, 227, 0.08);
  border: 1px solid rgba(0, 113, 227, 0.16);
  color: #005bb5;
  font-size: 13px;
  font-weight: 600;
}

.notice-card {
  margin-bottom: 20px;
  padding: 14px 16px;
  border-radius: 18px;
  background: linear-gradient(180deg, rgba(244, 248, 255, 0.96) 0%, rgba(235, 243, 255, 0.9) 100%);
  border: 1px solid rgba(191, 219, 254, 0.72);
  color: #4c6a92;
  font-size: 13px;
  line-height: 1.7;
}

.action-row {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  flex-wrap: wrap;
}

@media (max-width: 640px) {
  .action-row {
    justify-content: stretch;
  }

  .action-row :deep(.el-button) {
    flex: 1;
  }
}
</style>
