import { expect, request as playwrightRequest, test } from '@playwright/test'
import {
  createBackendContext,
  createTemporaryApplicant,
  disposeBackendContext,
  ensureBackendReady,
  findUserByKeyword,
  loginWithCredentials
} from '../../support/backend-api.js'
import { testAccounts } from '../../support/accounts.js'
import { prepareRolePage, resolveFrontendUrl } from '../../support/role-session.js'
import { expectToast } from '../../support/ui-helpers.js'

test.describe.serial('Task 4 - P0 管理员封禁闭环', () => {
  let apiContext
  let adminAuth

  test.beforeAll(async () => {
    apiContext = await createBackendContext(playwrightRequest)
    await ensureBackendReady(apiContext)
    adminAuth = await loginWithCredentials(apiContext, testAccounts.admin)
  })

  test.afterAll(async () => {
    await disposeBackendContext(apiContext)
  })

  test('管理员封禁临时测试账号后，该账号应无法再次登录', async ({ browser, request }) => {
    const applicantAccount = await createTemporaryApplicant(apiContext)
    const targetUser = await findUserByKeyword(apiContext, adminAuth.token, applicantAccount.username)
    expect(targetUser, '未能通过管理员接口定位到临时测试账号').toBeTruthy()

    const adminContext = await browser.newContext()
    const adminPage = await adminContext.newPage()
    await prepareRolePage(adminPage, request, 'admin', '/admin/users')

    await adminPage.getByPlaceholder('账号/昵称/手机号/邮箱').fill(applicantAccount.username)
    await adminPage.getByRole('button', { name: '筛选' }).click()

    const userRow = adminPage.locator('.el-table__row').filter({ hasText: applicantAccount.username }).first()
    await expect(userRow).toBeVisible()
    await userRow.getByRole('button', { name: '封禁' }).click()

    const banDialog = adminPage.getByRole('dialog', { name: '账号封禁/拉黑' })
    await expect(banDialog).toBeVisible()
    await banDialog.getByPlaceholder('请输入封禁原因').fill('E2E 封禁校验')
    await banDialog.getByRole('button', { name: '确认' }).click()

    await expectToast(adminPage, '账号状态已更新')
    await expect(adminPage.locator('.el-table__row').filter({ hasText: applicantAccount.username }).first()).toContainText('封禁/拉黑')
    await adminContext.close()

    const loginContext = await browser.newContext()
    const loginPage = await loginContext.newPage()
    await loginPage.goto(resolveFrontendUrl('/login'))
    await loginPage.getByPlaceholder('请输入用户名').fill(applicantAccount.username)
    await loginPage.getByPlaceholder('请输入密码').fill(applicantAccount.password)
    await loginPage.getByRole('button', { name: '登录' }).click()

    await expect(loginPage).toHaveURL(/\/login$/)
    await expect(loginPage.locator('.el-message').filter({ hasText: '账号已被封禁' }).last()).toBeVisible()
    await loginContext.close()
  })
})
