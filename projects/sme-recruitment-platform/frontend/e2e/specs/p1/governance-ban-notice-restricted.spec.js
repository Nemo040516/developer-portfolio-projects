/*
文件速览：
1. 文件职责：覆盖 Module 7 的 BAN_NOTICE 受限模式链路，验证用户被封禁后会进入平台提醒，并可提交封禁申诉。
2. 测试入口：Playwright P1 用例 `封禁通知受限登录与申诉闭环`。
3. 关键结构：临时求职者账号、管理员 API 封禁、登录页 restrictedMode 分支、平台提醒页申诉动作。
4. 阅读建议：先看管理员封禁造数，再看真实登录与路由限制，最后看 BAN_NOTICE 详情中的申诉提交。
*/
import { expect, test } from '@playwright/test'
import {
  buildLocalIsoDateTime,
  buildUniqueValue,
  createBackendContext,
  createTemporaryApplicant,
  disposeBackendContext,
  ensureBackendReady,
  loginWithCredentials,
  updateUserBanViaApi
} from '../../support/backend-api.js'
import { testAccounts } from '../../support/accounts.js'
import { expectToast } from '../../support/ui-helpers.js'

test.describe.serial('Module 7 - P1 BAN_NOTICE 受限登录与申诉闭环', () => {
  test('被封禁求职者应进入受限模式并可在平台提醒中提交申诉', async ({ browser, request }) => {
    const apiContext = await createBackendContext(request)
    const banReason = buildUniqueValue('封禁说明E2E')
    const appealContent = `${banReason} 申诉内容`

    let adminAuth
    let applicantAccount
    let browserContext = null

    try {
      await ensureBackendReady(apiContext)
      adminAuth = await loginWithCredentials(apiContext, testAccounts.admin)
      applicantAccount = await createTemporaryApplicant(apiContext)

      await updateUserBanViaApi(apiContext, adminAuth.token, applicantAccount.userId, {
        banStatus: 1,
        banReason,
        banUntil: buildLocalIsoDateTime(2)
      })

      browserContext = await browser.newContext()
      const page = await browserContext.newPage()
      await page.goto('/login')

      const loginResponsePromise = page.waitForResponse((response) => {
        return response.url().includes('/auth/login')
          && response.request().method() === 'POST'
      })

      await page.getByPlaceholder('请输入用户名').fill(applicantAccount.username)
      await page.getByPlaceholder('请输入密码').fill(applicantAccount.password)
      await page.getByRole('button', { name: '登录' }).click()

      const loginBody = await (await loginResponsePromise).json()
      expect(loginBody.code).toBe(200)
      expect(loginBody.data?.restrictedMode).toBeTruthy()

      await expect(page).toHaveURL(/\/applicant\/notices$/)
      await expect(page.locator('.notice-restricted-alert')).toContainText('当前账号处于受限状态')

      await page.goto('/jobs')
      await expect(page).toHaveURL(/\/applicant\/notices$/)

      const targetNoticeCard = page.locator('.notice-item').filter({
        hasText: banReason
      }).first()
      await expect(targetNoticeCard).toBeVisible({ timeout: 15000 })
      await targetNoticeCard.getByRole('button', { name: '查看详情' }).click()

      const detailDrawer = page.locator('.el-drawer').filter({
        hasText: banReason
      }).last()
      await expect(detailDrawer.locator('.drawer-hero__summary').filter({ hasText: banReason })).toBeVisible()
      await expect(detailDrawer.getByRole('button', { name: '提交申诉' })).toBeVisible()
      await expect(detailDrawer.getByRole('button', { name: '确认已读' })).toHaveCount(0)

      await detailDrawer.getByRole('button', { name: '提交申诉' }).click()
      const actionDialog = page.getByRole('dialog', { name: '提交封禁申诉' })
      await expect(actionDialog).toBeVisible()
      await actionDialog.getByRole('textbox').fill(appealContent)

      const appealResponse = page.waitForResponse((response) => {
        return response.url().includes('/governance/notices/')
          && response.url().includes('/actions')
          && response.request().method() === 'POST'
      })
      await actionDialog.getByRole('button', { name: '确认提交申诉' }).click()
      const appealBody = await (await appealResponse).json()
      expect(appealBody.code).toBe(200)
      await expectToast(page, '申诉已提交')

      await expect(detailDrawer.locator('.timeline-card__content').filter({
        hasText: appealContent
      }).first()).toBeVisible()
    } finally {
      if (browserContext) {
        await browserContext.close()
      }
      await disposeBackendContext(apiContext)
    }
  })
})
