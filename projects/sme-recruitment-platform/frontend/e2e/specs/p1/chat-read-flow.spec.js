/*
文件速览：
1. 文件职责：验证商家与求职者存在真实投递关系时，聊天未读消息会在进入会话后自动清零。
2. 测试入口：Playwright P1 用例 `Task 5 - P1 聊天已读基础断言`。
3. 关键结构：beforeAll 登录商家与管理员、API 造职位与投递关系、聊天会话进入后轮询未读数。
4. 阅读建议：先看 beforeAll 的鉴权准备，再看测试体里的“投递关系造数 -> 发消息 -> 进入聊天页 -> 已读断言”主链路。
*/
import { expect, request as playwrightRequest, test } from '@playwright/test'
import {
  createApprovedJob,
  createBackendContext,
  createTemporaryApplicant,
  disposeBackendContext,
  ensureBackendReady,
  findChatSessionByPeer,
  getFirstCategoryLeaf,
  getChatSessionsViaApi,
  loginWithCredentials,
  sendChatMessageViaApi,
  submitDeliveryViaApi
} from '../../support/backend-api.js'
import { testAccounts } from '../../support/accounts.js'
import { prepareCredentialsPage } from '../../support/role-session.js'
import { waitForChatConversation } from '../../support/ui-helpers.js'

test.describe.serial('Task 5 - P1 聊天已读基础断言', () => {
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

  test('收到未读消息后，进入聊天页应自动标记为已读', async ({ page, request }) => {
    const applicantAccount = await createTemporaryApplicant(apiContext)
    const messageText = `TASK5_CHAT_READ_${Date.now()}`

    await createChatReadyDelivery(apiContext, {
      adminToken: adminAuth.token,
      applicantToken: applicantAccount.token,
      categoryId: categoryLeaf.id,
      merchantToken: merchantAuth.token
    })

    await sendChatMessageViaApi(
      apiContext,
      merchantAuth.token,
      applicantAccount.userId,
      messageText
    )

    const beforeSessions = await getChatSessionsViaApi(apiContext, applicantAccount.token)
    const beforeTargetSession = findChatSessionByPeer(beforeSessions, merchantAuth.userId)
    expect(Number(beforeTargetSession?.unreadCount || 0)).toBeGreaterThan(0)

    const query = new URLSearchParams({
      targetId: String(merchantAuth.userId),
      targetName: merchantCredentials.username
    })

    await prepareCredentialsPage(page, request, applicantAccount, `/chat?${query.toString()}`)
    await waitForChatConversation(page, {
      expectedMessageText: messageText
    })

    await expect
      .poll(async () => {
        const sessions = await getChatSessionsViaApi(apiContext, applicantAccount.token)
        const session = findChatSessionByPeer(sessions, merchantAuth.userId)
        return Number(session?.unreadCount || 0)
      }, {
        message: '进入聊天页后未读数应被清零'
      })
      .toBe(0)
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
