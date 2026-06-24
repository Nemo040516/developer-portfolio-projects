/*
文件速览：
1. 文件职责：覆盖求职者投递反馈回显相关的 P0 端到端主链路。
2. 对外入口：Playwright `delivery-feedback-flow.spec.js` 用例集。
3. 关键结构：已初筛状态回显、面试邀约进度弹窗回显。
4. 阅读建议：先看 beforeAll 造数准备，再看两个核心业务场景。
*/
import { expect, request as playwrightRequest, test } from '@playwright/test'
import {
  buildLocalIsoDateTime,
  createApprovedJob,
  createBackendContext,
  createTemporaryApplicant,
  disposeBackendContext,
  ensureBackendReady,
  expectApiSuccess,
  getFirstCategoryLeaf,
  loginWithCredentials,
  submitDeliveryViaApi
} from '../../support/backend-api.js'
import { testAccounts } from '../../support/accounts.js'
import { prepareCredentialsPage, prepareRolePage } from '../../support/role-session.js'
import { expectToast, selectElementPlusOption } from '../../support/ui-helpers.js'

test.describe.serial('Task 4 - P0 商家反馈回显', () => {
  let apiContext
  let merchantAuth
  let adminAuth
  let categoryLeaf

  test.beforeAll(async () => {
    apiContext = await createBackendContext(playwrightRequest)
    await ensureBackendReady(apiContext)
    categoryLeaf = await getFirstCategoryLeaf(apiContext)
    merchantAuth = await loginWithCredentials(apiContext, testAccounts.merchant)
    adminAuth = await loginWithCredentials(apiContext, testAccounts.admin)
  })

  test.afterAll(async () => {
    await disposeBackendContext(apiContext)
  })

  test('商家标记已初筛后，求职者端应同步回显状态', async ({ browser, request }) => {
    const applicantAccount = await createTemporaryApplicant(apiContext)
    const approvedJob = await createApprovedJob(apiContext, {
      merchantToken: merchantAuth.token,
      adminToken: adminAuth.token,
      categoryId: categoryLeaf.id,
      title: `E2E_FEEDBACK_JOB_${Date.now()}`
    })

    await submitDeliveryViaApi(apiContext, applicantAccount.token, approvedJob.id)

    const merchantContext = await browser.newContext()
    const merchantPage = await merchantContext.newPage()
    await prepareRolePage(merchantPage, request, 'merchant', '/merchant/resumes')

    await selectElementPlusOption(
      merchantPage,
      merchantPage.locator('.filter-control').first(),
      approvedJob.title
    )

    const candidateRow = merchantPage.locator('.el-table__row').filter({ hasText: approvedJob.title }).first()
    await expect(candidateRow).toBeVisible()
    await candidateRow.getByRole('button', { name: '标记已初筛' }).click()

    await expectToast(merchantPage, '状态更新成功')
    await merchantContext.close()

    const applicantContext = await browser.newContext()
    const applicantPage = await applicantContext.newPage()
    await prepareCredentialsPage(applicantPage, request, applicantAccount, '/applicant/applications')

    await applicantPage.locator('.filter-pill').filter({ hasText: '已初筛' }).click()
    const applicationCard = applicantPage.locator('.apple-app-card').filter({ hasText: approvedJob.title }).first()

    await expect(applicationCard).toBeVisible()
    await expect(applicationCard).toContainText('已初筛')
    await applicantContext.close()
  })

  test('商家发起面试邀约后，投递记录页应展示真实面试进度', async ({ browser, request }) => {
    const applicantAccount = await createTemporaryApplicant(apiContext)
    const approvedJob = await createApprovedJob(apiContext, {
      merchantToken: merchantAuth.token,
      adminToken: adminAuth.token,
      categoryId: categoryLeaf.id,
      title: `E2E_INTERVIEW_PROGRESS_JOB_${Date.now()}`
    })

    await submitDeliveryViaApi(apiContext, applicantAccount.token, approvedJob.id)

    const applicationsBody = await expectApiSuccess(apiContext, 'GET', '/seeker/applications', {
      token: applicantAccount.token,
      params: {
        current: 1,
        size: 10,
        keyword: approvedJob.title
      }
    })
    const delivery = (applicationsBody.data?.records || []).find((item) => Number(item.jobId) === Number(approvedJob.id))
    expect(delivery, '未找到对应投递记录，无法继续构造面试邀约场景').toBeTruthy()

    const interviewLocation = '腾讯会议 123-456-789'
    await expectApiSuccess(apiContext, 'PUT', '/delivery/status', {
      token: merchantAuth.token,
      data: {
        id: delivery.id,
        status: 2,
        feedback: '请按时参加技术面试',
        interviewTime: buildLocalIsoDateTime(2),
        interviewLocation,
        interviewMethod: '线上面试',
        interviewRemark: '请提前 10 分钟进入会议'
      }
    })

    const applicantContext = await browser.newContext()
    const applicantPage = await applicantContext.newPage()
    await prepareCredentialsPage(applicantPage, request, applicantAccount, '/applicant/applications')

    await applicantPage.locator('.filter-pill').filter({ hasText: '面试邀约' }).click()
    const applicationCard = applicantPage.locator('.apple-app-card').filter({ hasText: approvedJob.title }).first()
    await expect(applicationCard).toBeVisible()
    await expect(applicationCard).toContainText('收到面试安排')

    await applicationCard.locator('.apple-feedback-row').click()
    const dialog = applicantPage.locator('.el-dialog').filter({ hasText: '面试进度' })
    await expect(dialog).toBeVisible()
    await expect(dialog).toContainText('第 1 轮')
    await expect(dialog).toContainText('待确认')
    await expect(dialog).toContainText('线上面试')
    await expect(dialog).toContainText(interviewLocation)

    await dialog.getByRole('button', { name: '确认面试' }).click()
    await expectToast(applicantPage, '面试状态已更新')
    await expect(dialog).toContainText('已确认')

    await applicantContext.close()
  })
})
