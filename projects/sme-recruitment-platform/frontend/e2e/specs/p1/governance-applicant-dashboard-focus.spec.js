/*
文件速览：
1. 文件职责：覆盖 Module 7 求职者工作台的治理聚焦横幅，验证主事项、三段分组和跳转联动。
2. 测试入口：Playwright P1 用例 `求职者工作台治理聚焦闭环`。
3. 关键结构：临时求职者账号、三类治理状态造数、工作台治理横幅、主事项查看按钮、阶段条目跳转。
4. 阅读建议：先看三类通知状态的 API 准备，再看工作台横幅断言，最后看跳转到平台提醒页的联动。
*/
import { expect, test } from '@playwright/test'
import {
  buildLocalIsoDateTime,
  buildUniqueValue,
  createBackendContext,
  createGovernanceNoticeViaApi,
  createTemporaryApplicant,
  disposeBackendContext,
  ensureBackendReady,
  expectApiSuccess,
  loginWithCredentials
} from '../../support/backend-api.js'
import { testAccounts } from '../../support/accounts.js'
import { prepareCredentialsPage } from '../../support/role-session.js'

test.describe.serial('Module 7 - P1 求职者工作台治理聚焦闭环', () => {
  test('求职者工作台应展示主事项、三段分组并支持跳转到平台提醒', async ({ browser, request }) => {
    const apiContext = await createBackendContext(request)
    const readTitle = buildUniqueValue('工作台待查看E2E')
    const actionTitle = buildUniqueValue('工作台待处理E2E')
    const reviewTitle = buildUniqueValue('工作台待复核E2E')
    const reviewAppealContent = `${reviewTitle} 申诉说明`

    let adminAuth = null
    let applicantAccount = null
    let readNoticeId = null
    let actionNoticeId = null
    let reviewNoticeId = null
    let browserContext = null

    try {
      await ensureBackendReady(apiContext)
      adminAuth = await loginWithCredentials(apiContext, testAccounts.admin)
      applicantAccount = await createTemporaryApplicant(apiContext)

      readNoticeId = await createGovernanceNoticeViaApi(apiContext, adminAuth.token, {
        targetRole: 'APPLICANT',
        targetUserId: applicantAccount.userId,
        noticeType: 'USER_WARNING',
        severity: 'WARNING',
        sourceModule: 'RISK_CONTROL',
        title: readTitle,
        summary: `${readTitle} 摘要`,
        detail: `${readTitle} 详细说明`,
        requiredAction: '请先确认已读并了解平台说明。',
        dueTime: buildLocalIsoDateTime(3),
        needAck: 1,
        needReply: 0
      })

      actionNoticeId = await createGovernanceNoticeViaApi(apiContext, adminAuth.token, {
        targetRole: 'APPLICANT',
        targetUserId: applicantAccount.userId,
        noticeType: 'USER_WARNING',
        severity: 'HIGH',
        sourceModule: 'RISK_CONTROL',
        title: actionTitle,
        summary: `${actionTitle} 摘要`,
        detail: `${actionTitle} 详细说明`,
        requiredAction: '请确认已读后补充说明本次情况。',
        dueTime: buildLocalIsoDateTime(3),
        needAck: 1,
        needReply: 1
      })

      reviewNoticeId = await createGovernanceNoticeViaApi(apiContext, adminAuth.token, {
        targetRole: 'APPLICANT',
        targetUserId: applicantAccount.userId,
        noticeType: 'USER_WARNING',
        severity: 'WARNING',
        sourceModule: 'RISK_CONTROL',
        title: reviewTitle,
        summary: `${reviewTitle} 摘要`,
        detail: `${reviewTitle} 详细说明`,
        requiredAction: '请确认已读后提交申诉或补充说明。',
        dueTime: buildLocalIsoDateTime(3),
        needAck: 1,
        needReply: 1
      })

      await expectApiSuccess(apiContext, 'PUT', `/governance/notices/${actionNoticeId}/read`, {
        token: applicantAccount.token
      })
      await expectApiSuccess(apiContext, 'PUT', `/governance/notices/${reviewNoticeId}/read`, {
        token: applicantAccount.token
      })
      await expectApiSuccess(apiContext, 'POST', `/governance/notices/${reviewNoticeId}/actions`, {
        token: applicantAccount.token,
        data: {
          actionType: 'APPEAL',
          content: reviewAppealContent
        }
      })

      browserContext = await browser.newContext()
      const applicantPage = await browserContext.newPage()
      await prepareCredentialsPage(applicantPage, request, {
        username: applicantAccount.username,
        password: applicantAccount.password
      }, '/applicant/dashboard')

      await expect(applicantPage.getByRole('heading', { name: /当前有 3 项平台提醒需要你优先查看/ })).toBeVisible()
      await expect(applicantPage.locator('.governance-banner__pill').filter({ hasText: '待查看 1' })).toBeVisible()
      await expect(applicantPage.locator('.governance-banner__pill').filter({ hasText: '待处理 1' })).toBeVisible()
      await expect(applicantPage.locator('.governance-banner__pill').filter({ hasText: '待复核 1' })).toBeVisible()

      const primaryCard = applicantPage.locator('.governance-focus-card')
      await expect(primaryCard.locator('.governance-focus-card__title')).toHaveText(actionTitle)
      await expect(primaryCard.locator('.governance-focus-card__meta')).toContainText('待处理')

      const readStageCard = applicantPage.locator('.governance-stage-card').filter({ hasText: '先读说明' }).first()
      const actionStageCard = applicantPage.locator('.governance-stage-card').filter({ hasText: '需要你说明' }).first()
      const reviewStageCard = applicantPage.locator('.governance-stage-card').filter({ hasText: '平台处理中' }).first()

      await expect(readStageCard).toContainText('1')
      await expect(readStageCard).toContainText(readTitle)
      await expect(actionStageCard).toContainText('1')
      await expect(reviewStageCard).toContainText('1')
      await expect(reviewStageCard).toContainText(reviewTitle)

      await primaryCard.getByRole('button', { name: '查看' }).click()
      await expect(applicantPage).toHaveURL(/\/applicant\/notices$/)
      await expect(applicantPage.locator('.el-drawer').filter({ hasText: actionTitle }).last()).toBeVisible()

      await applicantPage.goto('/applicant/dashboard')
      await expect(primaryCard.locator('.governance-focus-card__title')).toHaveText(actionTitle)

      await reviewStageCard.locator('.governance-stage-entry').filter({ hasText: reviewTitle }).first().click()
      await expect(applicantPage).toHaveURL(/\/applicant\/notices$/)
      await expect(applicantPage.locator('.el-drawer').filter({ hasText: reviewTitle }).last()).toBeVisible()
    } finally {
      if (browserContext) {
        await browserContext.close()
      }
      await disposeBackendContext(apiContext)
    }
  })
})
