/*
文件速览：
1. 文件职责：覆盖 Module 7 商家治理通知主链路，验证商家可在平台通知中查看职位整改、确认已读并提交复核说明。
2. 测试入口：Playwright P1 用例 `职位整改通知处理闭环`。
3. 关键结构：管理员 API 造唯一职位整改通知、商家平台通知页、详情抽屉、复核说明提交。
4. 阅读建议：先看 API 造数，再看商家通知详情里的已读与提交说明动作。
*/
import { expect, test } from '@playwright/test'
import {
  buildLocalIsoDateTime,
  buildUniqueValue,
  createApprovedJob,
  createBackendContext,
  createGovernanceNoticeViaApi,
  disposeBackendContext,
  ensureBackendReady,
  getFirstCategoryLeaf,
  loginWithCredentials
} from '../../support/backend-api.js'
import { testAccounts } from '../../support/accounts.js'
import { prepareRolePage } from '../../support/role-session.js'
import { expectToast } from '../../support/ui-helpers.js'

test.describe.serial('Module 7 - P1 商家职位整改通知处理闭环', () => {
  test('商家应可查看职位整改通知、确认已读并提交复核说明', async ({ browser, request }) => {
    const apiContext = await createBackendContext(request)
    const noticeTitle = buildUniqueValue('商家整改通知E2E')
    const fixContent = `${noticeTitle} 复核说明`

    let merchantAuth
    let adminAuth
    let browserContext = null

    try {
      await ensureBackendReady(apiContext)
      const categoryLeaf = await getFirstCategoryLeaf(apiContext)
      merchantAuth = await loginWithCredentials(apiContext, testAccounts.merchant)
      adminAuth = await loginWithCredentials(apiContext, testAccounts.admin)

      const approvedJob = await createApprovedJob(apiContext, {
        merchantToken: merchantAuth.token,
        adminToken: adminAuth.token,
        categoryId: categoryLeaf.id,
        title: buildUniqueValue('治理整改职位')
      })

      await createGovernanceNoticeViaApi(apiContext, adminAuth.token, {
        targetRole: 'MERCHANT',
        targetUserId: merchantAuth.userId,
        noticeType: 'JOB_RECTIFY',
        severity: 'WARNING',
        sourceModule: 'JOB_AUDIT',
        sourceId: approvedJob.id,
        relatedJobId: approvedJob.id,
        title: noticeTitle,
        summary: `${noticeTitle} 摘要`,
        detail: `${noticeTitle} 详细说明`,
        requiredAction: '请修改职位后提交复核说明。',
        dueTime: buildLocalIsoDateTime(3),
        needAck: 1,
        needReply: 1
      })

      browserContext = await browser.newContext()
      const merchantPage = await browserContext.newPage()
      await prepareRolePage(merchantPage, request, 'merchant', '/merchant/governance')

      await expect(merchantPage.getByRole('heading', { name: '平台通知', exact: true })).toBeVisible()

      const targetNoticeCard = merchantPage.locator('.notice-card').filter({
        hasText: noticeTitle
      }).first()
      await expect(targetNoticeCard).toBeVisible({ timeout: 15000 })
      await targetNoticeCard.getByRole('button', { name: '查看详情' }).click()

      const detailDrawer = merchantPage.locator('.el-drawer').filter({
        hasText: noticeTitle
      }).last()
      await expect(detailDrawer.locator('.drawer-hero__title').filter({ hasText: noticeTitle })).toBeVisible()

      const markReadResponse = merchantPage.waitForResponse((response) => {
        return response.url().includes('/governance/notices/')
          && response.url().includes('/read')
          && response.request().method() === 'PUT'
      })
      await detailDrawer.getByRole('button', { name: '确认已读' }).click()
      const markReadBody = await (await markReadResponse).json()
      expect(markReadBody.code).toBe(200)
      await expectToast(merchantPage, '已确认已读')

      await expect(detailDrawer.getByRole('button', { name: '提交复核说明' })).toBeVisible()
      await detailDrawer.getByRole('button', { name: '提交复核说明' }).click()

      const submitDialog = merchantPage.getByRole('dialog', { name: '提交复核说明' })
      await expect(submitDialog).toBeVisible()
      await submitDialog.getByRole('textbox').fill(fixContent)

      const submitFixResponse = merchantPage.waitForResponse((response) => {
        return response.url().includes('/governance/notices/')
          && response.url().includes('/actions')
          && response.request().method() === 'POST'
      })
      await submitDialog.getByRole('button', { name: '确认提交' }).click()
      const submitFixBody = await (await submitFixResponse).json()
      expect(submitFixBody.code).toBe(200)
      await expectToast(merchantPage, '已提交复核说明')

      await expect(detailDrawer.locator('.timeline-card__content').filter({
        hasText: fixContent
      }).first()).toBeVisible()
    } finally {
      if (browserContext) {
        await browserContext.close()
      }
      await disposeBackendContext(apiContext)
    }
  })
})
