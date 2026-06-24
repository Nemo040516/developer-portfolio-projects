/*
文件速览：
1. 文件职责：验证存在真实投递关系时，聊天 WebSocket 断开后能够自动重连并继续接收消息。
2. 测试入口：Playwright P1 用例 `Task 5 补充 - 聊天 WebSocket 重连与恢复`。
3. 关键结构：beforeAll 准备商家与管理员鉴权、API 建立投递关系、页面内强制断链后继续发送消息。
4. 阅读建议：先看前置投递关系与种子消息，再看浏览器侧断链注入，最后看重连与恢复消息断言。
*/
import { expect, request as playwrightRequest, test } from '@playwright/test'
import {
  createApprovedJob,
  createBackendContext,
  createTemporaryApplicant,
  disposeBackendContext,
  ensureBackendReady,
  getFirstCategoryLeaf,
  loginWithCredentials,
  sendChatMessageViaApi,
  submitDeliveryViaApi
} from '../../support/backend-api.js'
import { testAccounts } from '../../support/accounts.js'
import { prepareCredentialsPage } from '../../support/role-session.js'
import { waitForChatConversation } from '../../support/ui-helpers.js'

test.describe.serial('Task 5 补充 - 聊天 WebSocket 重连与恢复', () => {
  let apiContext
  let adminAuth
  let categoryLeaf
  let merchantAuth
  const merchantCredentials = {
    username: 'boss3',
    password: '12345'
  }

  test.beforeAll(async () => {
    apiContext = await createBackendContext(playwrightRequest)
    await ensureBackendReady(apiContext)
    merchantAuth = await loginWithCredentials(apiContext, merchantCredentials)
    adminAuth = await loginWithCredentials(apiContext, testAccounts.admin)
    categoryLeaf = await getFirstCategoryLeaf(apiContext)
  })

  test.afterAll(async () => {
    await disposeBackendContext(apiContext)
  })

  test('连接临时断开后，应自动重连并继续实时接收消息', async ({ page, request }) => {
    const applicantAccount = await createTemporaryApplicant(apiContext)
    const seedText = `TASK5_CHAT_RECONNECT_SEED_${Date.now()}`
    const recoveryText = `TASK5_CHAT_RECONNECT_RECOVERY_${Date.now()}`

    await createChatReadyDelivery(apiContext, {
      adminToken: adminAuth.token,
      applicantToken: applicantAccount.token,
      categoryId: categoryLeaf.id,
      merchantToken: merchantAuth.token
    })

    // 先创建服务端会话，避免初次进入时只有本地临时会话。
    await sendChatMessageViaApi(
      apiContext,
      merchantAuth.token,
      applicantAccount.userId,
      seedText
    )

    await page.addInitScript(() => {
      window.__PLAYWRIGHT_E2E__ = true
    })

    const query = new URLSearchParams({
      targetId: String(merchantAuth.userId),
      targetName: merchantCredentials.username
    })

    await prepareCredentialsPage(page, request, applicantAccount, `/chat?${query.toString()}`)
    await waitForChatConversation(page, {
      expectedMessageText: seedText
    })
    await expect(page.locator('.peer-status')).toContainText('实时在线')

    await expect
      .poll(async () => page.evaluate(() => typeof window.__chatE2E?.forceCloseWebSocket === 'function'))
      .toBeTruthy()

    await page.evaluate(() => {
      window.__chatE2E.forceCloseWebSocket()
    })

    await expect(page.locator('.peer-status')).toContainText('连接已断开')
    await expect(page.locator('.peer-status')).toContainText('实时在线', { timeout: 15000 })

    await sendChatMessageViaApi(
      apiContext,
      merchantAuth.token,
      applicantAccount.userId,
      recoveryText
    )

    await expect(page.locator('.message-list')).toContainText(recoveryText)
  })
})

async function createChatReadyDelivery(apiContext, options) {
  const approvedJob = await createApprovedJob(apiContext, {
    merchantToken: options.merchantToken,
    adminToken: options.adminToken,
    categoryId: options.categoryId
  })
  await submitDeliveryViaApi(apiContext, options.applicantToken, approvedJob.id)
  return approvedJob
}
