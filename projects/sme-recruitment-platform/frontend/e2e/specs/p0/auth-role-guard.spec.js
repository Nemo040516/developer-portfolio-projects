import { expect, test } from '@playwright/test'
import { prepareRolePage, resolveFrontendUrl } from '../../support/role-session.js'

test.describe('Task 4 - P0 路由守卫', () => {
  test('未登录访问受保护页面时应跳回登录页', async ({ page }) => {
    await page.goto(resolveFrontendUrl('/merchant/jobs'))

    await expect(page).toHaveURL(/\/login\?redirect=\/merchant\/jobs/)
    await expect(page.getByRole('button', { name: '登录' })).toBeVisible()
  })

  test('求职者已登录时访问登录页应跳转到求职者首页', async ({ page, request }) => {
    await prepareRolePage(page, request, 'applicant', '/login')
    await expect(page).toHaveURL(/\/applicant\/dashboard$/)
  })

  test('商家越权访问管理员页面时应进入 403 页面', async ({ page, request }) => {
    await prepareRolePage(page, request, 'merchant', '/admin/dashboard')

    await expect(page).toHaveURL(/\/403$/)
    await expect(page.getByText('无权限访问')).toBeVisible()
  })
})
