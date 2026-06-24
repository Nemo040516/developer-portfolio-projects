<template>
  <div class="user-avatar-container">
    <el-dropdown trigger="click" @command="handleCommand" @visible-change="handleVisibleChange">
      <div :class="['avatar-wrapper', { active: isDropdownVisible }]">
        <el-avatar :size="32" :src="userInfo.avatar || defaultAvatar" class="avatar-shadow" />
        <div class="user-info-meta">
          <span class="username">{{ displayName }}</span>
          <el-tag size="small" :type="roleTagType" effect="light" class="role-tag">
            {{ roleText }}
          </el-tag>
        </div>
        <el-icon class="arrow-icon" :class="{ rotate: isDropdownVisible }">
          <ArrowDown />
        </el-icon>
        <span v-if="showGovernanceBadge" class="avatar-governance-badge">{{ governanceBadgeValue }}</span>
      </div>
      <template #dropdown>
        <el-dropdown-menu class="user-dropdown-menu">
          <div class="dropdown-header">
            <p class="greeting">你好, {{ displayName }}</p>
            <p class="email" v-if="userInfo.email">{{ userInfo.email }}</p>
            <p class="restricted-tip" v-if="isRestrictedMode">{{ restrictedReasonText }}</p>
          </div>
          <template v-if="!isRestrictedMode">
            <el-dropdown-item command="profile" :icon="User">个人中心</el-dropdown-item>
            <el-dropdown-item v-if="role === 'APPLICANT'" command="resume" :icon="Document">我的简历</el-dropdown-item>
            <el-dropdown-item v-if="showGovernanceEntry" command="governance" :icon="Bell" class="governance-menu-entry">
              <span class="dropdown-menu-row">
                <span class="governance-menu-label">平台提醒</span>
                <span v-if="showGovernanceBadge" class="menu-governance-badge">{{ governanceBadgeValue }}</span>
              </span>
            </el-dropdown-item>
            <el-dropdown-item v-if="role === 'MERCHANT'" command="company" :icon="OfficeBuilding">企业资料</el-dropdown-item>
          </template>
          <el-dropdown-item v-else-if="showGovernanceEntry" command="governance" :icon="Bell" class="governance-menu-entry">
            <span class="dropdown-menu-row">
              <span class="governance-menu-label">平台提醒</span>
              <span v-if="showGovernanceBadge" class="menu-governance-badge">{{ governanceBadgeValue }}</span>
            </span>
          </el-dropdown-item>
          <el-dropdown-item divided command="logout" :icon="SwitchButton" class="logout-item">
            退出登录
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</template>

<script setup>
/*
文件速览：
1. 文件职责：展示登录用户头像入口、下拉菜单、提醒红点与退出登录，并在受限账号场景下切换为只读提醒入口。
2. 对外入口：displayName、handleCommand、isRestrictedMode、governancePath、showGovernanceBadge。
3. 关键结构：用户信息拉取、角色菜单、提醒徽标、受限提示文案。
4. 阅读建议：先看提醒入口相关 computed，再看 isRestrictedMode / governancePath 与 handleCommand。
*/
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useGovernanceStore } from '@/stores/governance'
import { ElMessageBox, ElMessage } from 'element-plus'
import { 
  ArrowDown, User, Document, OfficeBuilding, SwitchButton, Bell
} from '@element-plus/icons-vue'
import request from '@/utils/request'

const userStore = useUserStore()
const governanceStore = useGovernanceStore()
const router = useRouter()
const isDropdownVisible = ref(false)
const defaultAvatar = 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'

const userInfo = computed(() => userStore.userInfo || {})
const role = computed(() => userStore.role)
const isRestrictedMode = computed(() => userStore.restrictedMode && ['APPLICANT', 'MERCHANT'].includes(role.value))
const governancePath = computed(() => userStore.resolveRestrictedPath(role.value))
const restrictedReasonText = computed(() => userStore.restrictedReason || '当前账号处于受限状态，仅支持查看平台提醒。')
const showGovernanceEntry = computed(() => role.value === 'APPLICANT' || isRestrictedMode.value)
const governanceBadgeValue = computed(() =>
  governanceStore.pendingTotal > 99 ? '99+' : String(governanceStore.pendingTotal || 0)
)
const showGovernanceBadge = computed(() =>
  role.value === 'APPLICANT' && governanceStore.pendingTotal > 0
)

const displayName = computed(() => {
  const info = userInfo.value
  return info.nickname || info.realName || info.name || info.username || '用户'
})

// 挂载时尝试获取最新用户信息，补全登录时缺失的字段
onMounted(async () => {
  if (userStore.token && !userInfo.value.nickname && !isRestrictedMode.value) {
    try {
      const res = await request.get('/user/me')
      if (res.code === 200 && res.data) {
        userStore.setUserInfo(res.data)
      }
    } catch (e) {
      console.warn('获取用户信息失败', e)
    }
  }
})

const roleText = computed(() => {
  const dict = { 'APPLICANT': '求职者', 'MERCHANT': '招聘方', 'ADMIN': '管理员' }
  return dict[role.value] || '用户'
})

const roleTagType = computed(() => {
  const dict = { 'APPLICANT': 'success', 'MERCHANT': 'primary', 'ADMIN': 'danger' }
  return dict[role.value] || 'info'
})

const handleVisibleChange = (visible) => {
  isDropdownVisible.value = visible
}

const handleCommand = async (command) => {
  switch (command) {
    case 'governance':
      router.push(governancePath.value)
      break
    case 'profile':
      const path = role.value === 'APPLICANT' ? '/applicant/profile' : 
                   role.value === 'MERCHANT' ? '/merchant/company' : '/admin'
      router.push(path)
      break
    case 'resume':
      router.push('/applicant/resume')
      break
    case 'company':
      router.push('/merchant/company')
      break
    case 'logout':
      try {
        await ElMessageBox.confirm('确定要退出当前账号吗？', '提示', {
          confirmButtonText: '确定退出',
          cancelButtonText: '取消',
          type: 'warning',
          roundButton: true
        })
        await userStore.logout()
        router.push('/login')
        ElMessage.success('已安全退出')
      } catch (e) {}
      break
  }
}
</script>

<style scoped>
.user-avatar-container {
  display: inline-block;
}

.avatar-wrapper {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 12px;
  position: relative;
  border-radius: var(--ui-radius-full);
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  border: 1px solid transparent;
}

.avatar-wrapper:hover, .avatar-wrapper.active {
  background: var(--ui-surface);
  box-shadow: var(--ui-shadow-sm);
  border-color: var(--ui-border);
}

.avatar-shadow {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.user-info-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
}

.username {
  font-size: 14px;
  font-weight: 600;
  color: var(--ui-text);
  line-height: 1.2;
}

.role-tag {
  font-size: 10px;
  height: 16px;
  padding: 0 6px;
  line-height: 14px;
}

.arrow-icon {
  font-size: 12px;
  color: var(--ui-muted);
  transition: transform 0.3s;
}

.arrow-icon.rotate {
  transform: rotate(180deg);
}

.avatar-governance-badge {
  position: absolute;
  top: -6px;
  right: -4px;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  border: 2px solid #ffffff;
  background: linear-gradient(180deg, #ff6b6b 0%, #e03131 100%);
  box-shadow: 0 8px 18px rgba(224, 49, 49, 0.28);
  color: #ffffff;
  font-size: 11px;
  font-weight: 700;
  line-height: 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}

/* 下拉菜单样式定制 */
.user-dropdown-menu {
  padding: 8px !important;
  border-radius: var(--ui-radius-lg) !important;
  border: 1px solid var(--ui-border) !important;
  min-width: 180px !important;
}

.dropdown-header {
  padding: 12px 16px;
  border-bottom: 1px solid var(--ui-border);
  margin-bottom: 8px;
}

.greeting {
  margin: 0;
  font-weight: 600;
  font-size: 14px;
  color: var(--ui-text);
}

.email {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--ui-muted);
}

.restricted-tip {
  margin: 8px 0 0;
  font-size: 12px;
  line-height: 1.5;
  color: #9a3412;
}

.governance-menu-entry {
  padding-right: 12px !important;
}

.dropdown-menu-row {
  width: 100%;
  display: inline-flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.governance-menu-label {
  min-width: 0;
}

.menu-governance-badge {
  flex-shrink: 0;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  border-radius: 999px;
  background: linear-gradient(180deg, #ff6b6b 0%, #e03131 100%);
  color: #ffffff;
  font-size: 11px;
  font-weight: 700;
  line-height: 20px;
  text-align: center;
  box-shadow: 0 8px 18px rgba(224, 49, 49, 0.2);
}

.logout-item {
  color: var(--ui-danger) !important;
}

.logout-item:hover {
  background-color: rgba(239, 64, 64, 0.08) !important;
}
</style>
