import { expect, request as playwrightRequest, test } from '@playwright/test'
import {
  createBackendContext,
  createTemporaryApplicant,
  disposeBackendContext,
  ensureBackendReady
} from '../../support/backend-api.js'
import { prepareCredentialsPage } from '../../support/role-session.js'
import { expectToast } from '../../support/ui-helpers.js'

test.describe.serial('Task 5 - P1 求职者个人中心扩展链路', () => {
  let apiContext

  test.beforeAll(async () => {
    apiContext = await createBackendContext(playwrightRequest)
    await ensureBackendReady(apiContext)
  })

  test.afterAll(async () => {
    await disposeBackendContext(apiContext)
  })

  test('临时求职者可保存资料、更新隐私并提交认证', async ({ page, request }) => {
    const tempApplicant = await createTemporaryApplicant(apiContext)
    const uniqueStamp = Date.now()
    const nickname = `Task5用户${String(uniqueStamp).slice(-4)}`
    const phone = '13812345678'
    const email = `task5_${uniqueStamp}@example.com`

    await prepareCredentialsPage(page, request, tempApplicant, '/applicant/profile')
    await page.waitForLoadState('networkidle')
    await expect(page.getByRole('button', { name: '保存账号资料' })).toBeVisible()
    await expect(page.getByRole('button', { name: '保存隐私设置' })).toBeVisible()
    await expect(page.getByRole('button', { name: '立即认证' })).toBeVisible()

    const accountCard = page.locator('.left-column .profile-card').first()
    await accountCard.getByRole('textbox', { name: '昵称' }).fill(nickname)
    await accountCard.getByRole('textbox', { name: '手机号' }).fill(phone)
    await accountCard.getByRole('textbox', { name: '邮箱' }).fill(email)
    await page.getByRole('button', { name: '保存账号资料' }).click()
    await expectToast(page, '账号资料已保存')
    await expect(accountCard.getByRole('textbox', { name: '昵称' })).toHaveValue(nickname)

    await page.locator('.privacy-options .el-radio').filter({ hasText: '仅授权企业' }).first().click()
    await page.getByRole('button', { name: '保存隐私设置' }).click()
    await expectToast(page, '隐私设置已保存')
    await expect(page.locator('.hero-badges')).toContainText('隐私：仅授权企业')

    await page.getByRole('button', { name: '立即认证' }).click()
    await page.getByLabel('真实姓名').fill('任务五测试用户')
    await page.getByLabel('证件号码').fill(`DEMO-CERT-${String(uniqueStamp).slice(-6)}`)
    await page.getByLabel('备注说明').fill('Task5 自动化认证提交')
    await page.getByRole('button', { name: '提交认证' }).click()
    await expectToast(page, '认证资料已提交，等待审核')
    await expect(page.locator('.verify-section')).toContainText('审核中')
    await expect(page.locator('.login-empty, .login-list')).toBeVisible()
  })
})
