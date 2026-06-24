/*
文件速览：
1. 文件职责：验证存在真实投递关系时，聊天页 WebSocket 新消息会实时出现并同步为已读。
2. 测试入口：Playwright P1 用例 `Task 5 补充 - 聊天 WebSocket 实时断言`。
3. 关键结构：beforeAll 准备商家与管理员鉴权、API 建立投递关系、会话打开后发实时消息并轮询服务端状态。
4. 阅读建议：先看投递关系造数，再看首条种子消息建会话，最后看实时消息与已读双断言。
*/
import { expect, request as playwrightRequest, test } from '@playwright/test'
import {
  createApprovedJob,
  createBackendContext,
  createTemporaryApplicant,
  disposeBackendContext,
  ensureBackendReady,
  findChatMessageByContent,
  findChatSessionByPeer,
  getFirstCategoryLeaf,
  getChatMessagesViaApi,
  getChatSessionsViaApi,
  loginWithCredentials,
  sendChatMessageViaApi,
  submitDeliveryViaApi
} from '../../support/backend-api.js'
import { testAccounts } from '../../support/accounts.js'
import { prepareCredentialsPage } from '../../support/role-session.js'
import { waitForChatConversation } from '../../support/ui-helpers.js'

test.describe.serial('Task 5 补充 - 聊天 WebSocket 实时断言', () => {
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

  test('当前会话打开时，WebSocket 新消息应实时出现并同步为已读', async ({ page, request }) => {
    const applicantAccount = await createTemporaryApplicant(apiContext)
    const seedText = `TASK5_CHAT_WS_SEED_${Date.now()}`
    const realtimeText = `TASK5_CHAT_WS_REALTIME_${Date.now()}`

    await createChatReadyDelivery(apiContext, {
      adminToken: adminAuth.token,
      applicantToken: applicantAccount.token,
      categoryId: categoryLeaf.id,
      merchantToken: merchantAuth.token
    })

    // 先创建服务端会话，避免首次进入页面时只有本地临时会话。
    await sendChatMessageViaApi(
      apiContext,
      merchantAuth.token,
      applicantAccount.userId,
      seedText
    )

    const query = new URLSearchParams({
      targetId: String(merchantAuth.userId),
      targetName: merchantCredentials.username
    })

    await prepareCredentialsPage(page, request, applicantAccount, `/chat?${query.toString()}`)
    await waitForChatConversation(page, {
      expectedMessageText: seedText
    })
    await expect(page.locator('.peer-status')).toContainText('实时在线')

    await sendChatMessageViaApi(
      apiContext,
      merchantAuth.token,
      applicantAccount.userId,
      realtimeText
    )

    await expect(page.locator('.message-list')).toContainText(realtimeText)

    await expect
      .poll(async () => {
        const sessions = await getChatSessionsViaApi(apiContext, applicantAccount.token)
        const session = findChatSessionByPeer(sessions, merchantAuth.userId)
        return Number(session?.unreadCount || 0)
      }, {
        message: '当前会话已打开时，服务端未读数应保持为 0'
      })
      .toBe(0)

    await expect
      .poll(async () => {
        const messages = await getChatMessagesViaApi(
          apiContext,
          applicantAccount.token,
          merchantAuth.userId,
          { pageSize: 50 }
        )
        return Number(findChatMessageByContent(messages, realtimeText)?.isRead ?? -1)
      }, {
        message: '实时到达的新消息应被同步标记为已读'
      })
      .toBe(1)
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
