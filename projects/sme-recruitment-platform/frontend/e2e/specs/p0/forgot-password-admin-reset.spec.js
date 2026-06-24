/*
文件速览：
1. 文件职责：覆盖“忘记密码-管理员重置”方案的前端真实闭环，包括入口、重置、强制改密与日志展示。
2. 对外入口：Playwright P0 用例 `Module 1 - P0 忘记密码管理员重置闭环`。
3. 关键结构：beforeAll 初始化后端上下文、管理员重置密码、管理员切换开关、临时账号登录改密。
4. 阅读建议：先看 beforeAll / afterAll 的环境准备，再看两个测试用例的主流程。
*/
import { expect, request as playwrightRequest, test } from '@playwright/test'
import {
  createBackendContext,
  createTemporaryApplicant,
  disposeBackendContext,
  ensureBackendReady,
  expectApiSuccess,
  loginWithCredentials
} from '../../support/backend-api.js'
import { testAccounts } from '../../support/accounts.js'
import { prepareRolePage, resolveFrontendUrl } from '../../support/role-session.js'
import { expectToast } from '../../support/ui-helpers.js'

test.describe.serial('Module 1 - P0 忘记密码管理员重置闭环', () => {
  let apiContext
  let adminAuth
  let originalForcePasswordChangeEnabled = false

  test.beforeAll(async () => {
    apiContext = await createBackendContext(playwrightRequest)
    await ensureBackendReady(apiContext)
    adminAuth = await loginWithCredentials(apiContext, testAccounts.admin)
    const settingsBody = await expectApiSuccess(apiContext, 'GET', '/admin/security-settings', {
      token: adminAuth.token
    })
    originalForcePasswordChangeEnabled = Boolean(settingsBody.data?.forcePasswordChangeEnabled)
  })

  test.afterAll(async () => {
    try {
      const latestSettings = await expectApiSuccess(apiContext, 'GET', '/admin/security-settings', {
        token: adminAuth.token
      })
      const currentEnabled = Boolean(latestSettings.data?.forcePasswordChangeEnabled)
      if (currentEnabled !== originalForcePasswordChangeEnabled) {
        await expectApiSuccess(apiContext, 'PUT', '/admin/security-settings/password-force-change', {
          token: adminAuth.token,
          data: { enabled: originalForcePasswordChangeEnabled }
        })
      }
    } finally {
      await disposeBackendContext(apiContext)
    }
  })

  test('未登录用户可从登录页进入忘记密码说明页', async ({ page }) => {
    await page.goto('/login')

    await expect(page.getByRole('link', { name: '忘记密码？联系管理员重置' })).toBeVisible()
    await page.getByRole('link', { name: '忘记密码？联系管理员重置' }).click()

    await expect(page).toHaveURL(/\/forgot-password$/)
    await expect(page.getByRole('heading', { name: '忘记密码' })).toBeVisible()
    await expect(page.getByText('本系统采用“管理员协助重置临时密码”的方案')).toBeVisible()
    await expect(page.getByText('联系管理员核验')).toBeVisible()
    await expect(page.getByRole('button', { name: '返回登录' })).toBeVisible()
  })

  test('管理员重置临时密码并开启强制改密后，用户应被收口到统一修改密码页', async ({ browser, request }) => {
    const applicantAccount = await createTemporaryApplicant(apiContext)
    const temporaryPassword = 'Temp1234'
    const finalPassword = 'Final1234'

    const adminContext = await browser.newContext()
    const adminPage = await adminContext.newPage()

    try {
      await prepareRolePage(adminPage, request, 'admin', '/admin/users')
      await adminPage.getByPlaceholder('账号/昵称/手机号/邮箱').fill(applicantAccount.username)
      await adminPage.getByRole('button', { name: '筛选' }).click()

      const userRow = adminPage.locator('.el-table__row').filter({ hasText: applicantAccount.username }).first()
      await expect(userRow).toBeVisible()
      await userRow.getByRole('button', { name: '重置密码' }).click()

      const resetDialog = adminPage.getByRole('dialog', { name: '管理员重置密码' })
      await expect(resetDialog).toBeVisible()
      await resetDialog.getByPlaceholder('请输入6位及以上临时密码').fill(temporaryPassword)
      await resetDialog.getByPlaceholder('请再次输入临时密码').fill(temporaryPassword)
      await resetDialog.getByPlaceholder('例如：用户忘记密码，已线下核验身份').fill('E2E 找回密码闭环测试')
      await resetDialog.getByRole('button', { name: '确认重置' }).click()

      await expectToast(adminPage, `已为账号 ${applicantAccount.username} 重置临时密码`)

      await adminPage.goto(resolveFrontendUrl('/admin/dashboard'))
      await expect(adminPage.getByText('账号安全策略')).toBeVisible()

      await ensureForcePasswordChangeEnabled(adminPage, originalForcePasswordChangeEnabled)
      await expect(adminPage.getByText('最近策略变更记录')).toBeVisible()
      await expect(adminPage.locator('.log-item').first()).toContainText('更新临时密码登录后强制修改密码开关')
    } finally {
      await adminContext.close()
    }

    const applicantContext = await browser.newContext()
    const applicantPage = await applicantContext.newPage()

    try {
      await applicantPage.goto(resolveFrontendUrl('/login'))
      await applicantPage.getByPlaceholder('请输入用户名').fill(applicantAccount.username)
      await applicantPage.getByPlaceholder('请输入密码').fill(temporaryPassword)
      await applicantPage.getByRole('button', { name: '登录' }).click()

      await expect(applicantPage).toHaveURL(/\/account\/password/)
      await expect(applicantPage.getByText('当前账号使用的是管理员重置的临时密码')).toBeVisible()

      await applicantPage.getByPlaceholder('请输入当前密码').fill(temporaryPassword)
      await applicantPage.getByPlaceholder('请输入新密码').fill(finalPassword)
      await applicantPage.getByPlaceholder('请再次输入新密码').fill(finalPassword)
      await applicantPage.getByRole('button', { name: '确认修改' }).click()

      await expectToast(applicantPage, '密码已更新')
      await expect(applicantPage).toHaveURL(/\/applicant\/dashboard$/)
    } finally {
      await applicantContext.close()
    }
  })
})

async function ensureForcePasswordChangeEnabled(page, originalEnabled) {
  const switchRoot = page.locator('.security-card .el-switch').first()
  const switchLocator = page.locator('.security-card [role="switch"]').first()
  const loadingMask = page.locator('.security-card .el-loading-mask')
  await expect(switchRoot).toBeVisible()
  await expect(loadingMask).toBeHidden()

  const readChecked = async () => (await switchLocator.getAttribute('aria-checked')) === 'true'
  const toggleTo = async (desiredEnabled) => {
    const currentEnabled = await readChecked()
    if (currentEnabled === desiredEnabled) {
      return false
    }

    await switchRoot.click()
    const actionText = desiredEnabled ? '开启' : '关闭'
    const dialog = page.getByRole('dialog', { name: `${actionText}强制改密开关` })
    await expect(dialog).toBeVisible()
    await dialog.getByRole('button', { name: actionText }).click()
    await expectToast(page, `已${actionText}临时密码登录后强制修改密码`)
    await expect.poll(readChecked).toBe(desiredEnabled)
    return true
  }

  if (originalEnabled) {
    await toggleTo(false)
  }
  await toggleTo(true)
}
