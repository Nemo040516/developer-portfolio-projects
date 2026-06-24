/*
文件速览：
1. 文件职责：集中定义前端路由、游客页入口与登录态守卫。
2. 对外入口：createRouter 返回的路由实例。
3. 关键结构：游客页路由、三类角色布局路由、beforeEach 权限守卫。
4. 阅读建议：先看游客路由，再看各角色子路由，最后看底部导航守卫。
*/
import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/Login.vue'),
      meta: { guest: true } // 标记为游客页面
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('../views/Register.vue'),
      meta: { guest: true }
    },
    {
      path: '/forgot-password',
      name: 'forgot-password',
      component: () => import('../views/ForgotPassword.vue'),
      meta: { guest: true }
    },
    {
      path: '/account/password',
      name: 'account-password',
      component: () => import('../views/AccountPassword.vue'),
      meta: { requiresAuth: true, roles: ['ADMIN', 'MERCHANT', 'APPLICANT'], allowDuringPasswordChange: true }
    },
    {
      path: '/',
      redirect: '/login'
    },
    // 商家工作台 (嵌套路由)
    {
        path: '/merchant',
        component: () => import('../layout/MerchantLayout.vue'),
        meta: { requiresAuth: true, roles: ['MERCHANT', 'ADMIN'] },
        children: [
          {
            path: '', // 默认跳转到 dashboard
            redirect: '/merchant/dashboard'
          },
          {
            path: 'dashboard',
            name: 'merchant-dashboard',
            component: () => import('../views/merchant/Dashboard.vue')
          },
          {
            path: 'jobs',
            name: 'merchant-jobs',
            component: () => import('../views/merchant/JobManage.vue')
          },
          {
            path: 'company',
            name: 'merchant-company',
            component: () => import('../views/merchant/CompanyProfile.vue')
          },
          {
            path: 'resumes',
            name: 'merchant-resumes',
            component: () => import('../views/merchant/CandidateManage.vue')
          },
          {
            path: 'governance',
            name: 'merchant-governance',
            component: () => import('../views/merchant/GovernanceCenter.vue'),
            meta: { requiresAuth: true, roles: ['MERCHANT', 'ADMIN'], allowRestricted: true }
          },
          {
            path: 'talent',
            name: 'merchant-talent',
            component: () => import('../views/merchant/TalentPool.vue')
          },
          {
            path: 'interviews',
            name: 'merchant-interviews',
            component: () => import('../views/merchant/InterviewSchedule.vue')
          },
          {
            path: 'chat',
            name: 'merchant-chat',
            component: () => import('../views/chat/ChatPage.vue'),
            meta: { requiresAuth: true, roles: ['MERCHANT'] }
          },
          {
            path: 'candidates',
            redirect: '/merchant/resumes'
          }
        ]
    },
    // 求职者/公共区域 (使用 MainLayout)
    {
        path: '/',
        component: () => import('../layout/MainLayout.vue'),
        children: [
            {
                path: 'applicant/dashboard',
                name: 'applicant-dashboard',
                component: () => import('../views/applicant/Dashboard.vue'),
                meta: { requiresAuth: true, roles: ['APPLICANT'] }
            },
            {
                path: 'jobs',
                name: 'jobs',
                component: () => import('../views/job/JobHall.vue'),
                meta: { requiresAuth: true, roles: ['APPLICANT', 'ADMIN'] }
            },
            {
                path: 'job/detail/:id',
                name: 'job-detail',
                component: () => import('../views/job/JobDetail.vue'),
                meta: { requiresAuth: true, roles: ['APPLICANT', 'ADMIN'] }
            },
            {
                path: 'applicant/resume',
                name: 'applicant-resume',
                component: () => import('../views/applicant/Resume.vue'),
                meta: { requiresAuth: true, roles: ['APPLICANT'] }
            },
            {
                path: 'applicant/profile',
                name: 'applicant-profile',
                component: () => import('../views/applicant/Profile.vue'),
                meta: { requiresAuth: true, roles: ['APPLICANT'] }
            },
            {
                path: 'applicant/notices',
                name: 'applicant-notices',
                component: () => import('../views/applicant/GovernanceCenter.vue'),
                meta: { requiresAuth: true, roles: ['APPLICANT'], allowRestricted: true }
            },
            {
                path: 'applicant/applications',
                name: 'applicant-applications',
                component: () => import('../views/applicant/Applications.vue'),
                meta: { requiresAuth: true, roles: ['APPLICANT'] }
            },
            {
                path: 'applicant/interviews',
                name: 'applicant-interviews',
                component: () => import('../views/applicant/InterviewList.vue'),
                meta: { requiresAuth: true, roles: ['APPLICANT'] }
            },
            {
                path: 'chat',
                name: 'applicant-chat',
                component: () => import('../views/chat/ChatPage.vue'),
                meta: { requiresAuth: true, roles: ['APPLICANT'] }
            },
            {
                path: 'applicant/delivery',
                name: 'applicant-delivery',
                redirect: '/applicant/applications',
                meta: { requiresAuth: true, roles: ['APPLICANT'] }
            }
        ]
    },
    // 管理员后台
    {
        path: '/admin',
        component: () => import('../layout/AdminLayout.vue'),
        children: [
          {
            path: '',
            redirect: '/admin/dashboard'
          },
          {
            path: 'login',
            redirect: '/login'
          },
          {
            path: 'dashboard',
            name: 'admin-dashboard',
            component: () => import('../views/admin/Dashboard.vue'),
            meta: { requiresAuth: true, roles: ['ADMIN'] }
          },
          {
            path: 'jobs',
            name: 'admin-jobs',
            component: () => import('../views/admin/JobAudit.vue'),
            meta: { requiresAuth: true, roles: ['ADMIN'] }
          },
          {
            path: 'merchants',
            name: 'admin-merchants',
            component: () => import('../views/admin/MerchantAudit.vue'),
            meta: { requiresAuth: true, roles: ['ADMIN'] }
          },
          {
            path: 'reports',
            name: 'admin-reports',
            component: () => import('../views/admin/Reports.vue'),
            meta: { requiresAuth: true, roles: ['ADMIN'] }
          },
          {
            path: 'governance',
            name: 'admin-governance',
            component: () => import('../views/admin/GovernanceNotices.vue'),
            meta: { requiresAuth: true, roles: ['ADMIN'] }
          },
          {
            path: 'users',
            name: 'admin-users',
            component: () => import('../views/admin/UserBan.vue'),
            meta: { requiresAuth: true, roles: ['ADMIN'] }
          }
        ]
    },
    // 403 无权限页面
    {
        path: '/403',
        name: 'forbidden',
        component: () => import('../views/Forbidden.vue')
    }
  ]
})

// 全局路由守卫 (门卫)
router.beforeEach((to, from, next) => {
  const userStore = useUserStore()
  const token = userStore.token
  const userRole = userStore.role
  const restrictedPath = userStore.resolveRestrictedPath(userRole)

  // 1. 防未登录：如果目标页面需要登录
  if (to.meta.requiresAuth) {
    if (!token) {
      // 没有 Token，强制踢回登录页
      ElMessage.warning('请先登录')
      return next({ name: 'login', query: { redirect: to.fullPath } })
    }

    // 2. 防越权：有 Token，但角色不符
    const requiredRoles = to.meta.roles
    if (requiredRoles && !requiredRoles.includes(userRole)) {
      // 角色不匹配，踢回 403 页面
      ElMessage.error('您没有权限访问此页面')
      return next({ name: 'forbidden' })
    }

    if (userStore.restrictedMode && !to.meta.allowRestricted) {
      if (restrictedPath && to.path !== restrictedPath) {
        return next(restrictedPath)
      }
    }

    if (userStore.passwordChangeRequired && !to.meta.allowDuringPasswordChange) {
      const redirect = userStore.passwordChangeRedirect || to.fullPath
      return next({ name: 'account-password', query: { redirect } })
    }

    // 权限通过
    next()
  } 
  // 3. 如果已登录用户访问游客页面 (如登录页)
  else if (to.meta.guest && token) {
      if (userStore.restrictedMode) {
        return next(restrictedPath || userStore.resolveHomePath(userRole))
      }
      if (userStore.passwordChangeRequired) {
          return next({
            name: 'account-password',
            query: { redirect: userStore.passwordChangeRedirect || userStore.resolveHomePath(userRole) }
          })
      }
      // 自动重定向到其角色对应的首页
      const homePath = userStore.resolveHomePath(userRole)
      if (homePath && homePath !== '/') {
          return next(homePath)
      }
      
      next()
  }
  else {
    // 其他情况 (访问游客页面且未登录) 直接放行
    next()
  }
})

export default router
