import { expect, request as playwrightRequest, test } from '@playwright/test'
import {
  createApprovedJob,
  createBackendContext,
  createTemporaryApplicant,
  disposeBackendContext,
  ensureBackendReady,
  getFirstCategoryLeaf,
  loginWithCredentials,
  uploadResumeAttachmentViaApi,
  submitDeliveryViaApi
} from '../../support/backend-api.js'
import { buildPdfFixture } from '../../support/file-fixtures.js'
import { testAccounts } from '../../support/accounts.js'
import { prepareCredentialsPage } from '../../support/role-session.js'
import { expectToast, waitForChatConversation } from '../../support/ui-helpers.js'

test.describe.serial('Task 5 - P1 附件授权与聊天扩展链路', () => {
  let apiContext
  let categoryLeaf
  let merchantAuth
  let adminAuth
  const merchantCredentials = {
    username: 'boss3',
    password: '12345'
  }

  test.beforeAll(async () => {
    apiContext = await createBackendContext(playwrightRequest)
    await ensureBackendReady(apiContext)
    categoryLeaf = await getFirstCategoryLeaf(apiContext)
    merchantAuth = await loginWithCredentials(apiContext, merchantCredentials)
    adminAuth = await loginWithCredentials(apiContext, testAccounts.admin)
  })

  test.afterAll(async () => {
    await disposeBackendContext(apiContext)
  })

  test('商家申请附件后，求职者可授权并发送附件简历', async ({ browser, request }) => {
    const tempApplicant = await createTemporaryApplicant(apiContext)
    const expectedAttachmentName = `${tempApplicant.username}的简历.pdf`
    await uploadResumeAttachmentViaApi(
      apiContext,
      tempApplicant.token,
      buildPdfFixture('task5-attachment-resume.pdf')
    )

    const approvedJob = await createApprovedJob(apiContext, {
      merchantToken: merchantAuth.token,
      adminToken: adminAuth.token,
      categoryId: categoryLeaf.id,
      title: `E2E_CHAT_JOB_${Date.now()}`
    })

    await submitDeliveryViaApi(apiContext, tempApplicant.token, approvedJob.id)

    const merchantContext = await browser.newContext()
    const merchantPage = await merchantContext.newPage()
    const merchantQuery = new URLSearchParams({
      targetId: String(tempApplicant.userId),
      targetName: tempApplicant.username,
      jobId: String(approvedJob.id),
      jobTitle: approvedJob.title,
      autoRequest: '1'
    })

    await prepareCredentialsPage(
      merchantPage,
      request,
      merchantCredentials,
      `/merchant/chat?${merchantQuery.toString()}`
    )

    await waitForChatConversation(merchantPage, {
      expectedHeaderText: approvedJob.title
    })
    await expect(merchantPage.locator('.message-list')).toContainText('附件简历申请')
    await expect(merchantPage.locator('.request-btn')).toContainText('申请中')

    const applicantContext = await browser.newContext()
    const applicantPage = await applicantContext.newPage()
    const applicantQuery = new URLSearchParams({
      targetId: String(merchantAuth.userId),
      targetName: merchantCredentials.username,
      jobId: String(approvedJob.id),
      jobTitle: approvedJob.title
    })

    await prepareCredentialsPage(
      applicantPage,
      request,
      tempApplicant,
      `/chat?${applicantQuery.toString()}`
    )

    await waitForChatConversation(applicantPage, {
      expectedMessageText: '附件简历申请'
    })
    await applicantPage.getByRole('button', { name: '同意授权' }).click()
    await expectToast(applicantPage, '已授权')
    await expect(applicantPage.locator('.message-list')).toContainText('已授权')

    await applicantPage.getByRole('button', { name: '发送附件简历' }).click()
    await expect(applicantPage.locator('.message-list')).toContainText('附件简历')
    await expect(applicantPage.locator('.message-list')).toContainText(expectedAttachmentName)

    await merchantPage.reload()
    await expect(merchantPage.locator('.request-btn')).toContainText('已授权')
    await expect(merchantPage.locator('.message-list')).toContainText('附件简历')
    await expect(merchantPage.locator('.message-list')).toContainText(expectedAttachmentName)
    await expect(merchantPage.getByRole('button', { name: '在线查看' }).first()).toBeVisible()

    await applicantContext.close()
    await merchantContext.close()
  })
})
