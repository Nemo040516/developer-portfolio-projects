import { expect, request as playwrightRequest, test } from '@playwright/test'
import {
  createApprovedJob,
  createBackendContext,
  disposeBackendContext,
  ensureBackendReady,
  findReportByReason,
  getFirstCategoryLeaf,
  loginWithCredentials
} from '../../support/backend-api.js'
import { testAccounts } from '../../support/accounts.js'
import { prepareRolePage } from '../../support/role-session.js'
import { expectToast, selectElementPlusOption } from '../../support/ui-helpers.js'

test.describe.serial('Task 4 - P0 举报处理闭环', () => {
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

  test('求职者提交职位举报后，管理员应可在后台处理', async ({ browser, request }) => {
    const approvedJob = await createApprovedJob(apiContext, {
      merchantToken: merchantAuth.token,
      adminToken: adminAuth.token,
      categoryId: categoryLeaf.id,
      title: `E2E_REPORT_JOB_${Date.now()}`
    })
    const reasonMarker = `E2E_REPORT_REASON_${Date.now()}`

    const applicantContext = await browser.newContext()
    const applicantPage = await applicantContext.newPage()
    await prepareRolePage(applicantPage, request, 'applicant', `/job/detail/${approvedJob.id}`)

    await expect(applicantPage.getByRole('heading', { name: approvedJob.title })).toBeVisible({ timeout: 15000 })
    await applicantPage.getByRole('button', { name: '举报职位' }).click()
    await applicantPage.getByRole('textbox', { name: '详细说明' }).fill(reasonMarker)
    await applicantPage.getByRole('button', { name: '提交举报' }).click()
    await applicantPage.getByRole('button', { name: '确认提交' }).click()

    await expectToast(applicantPage, '举报已提交')
    await applicantContext.close()

    const report = await findReportByReason(apiContext, adminAuth.token, {
      type: 'JOB',
      status: 0,
      reasonKeyword: reasonMarker
    })
    expect(report, '未能通过管理员接口定位到新提交举报').toBeTruthy()

    const adminContext = await browser.newContext()
    const adminPage = await adminContext.newPage()
    await prepareRolePage(adminPage, request, 'admin', '/admin/reports')

    await selectElementPlusOption(
      adminPage,
      adminPage.locator('.filter-row .el-select').nth(0),
      '职位'
    )
    await adminPage.getByRole('button', { name: '筛选' }).click()

    const reportRow = adminPage.locator('.el-table__row').filter({ hasText: reasonMarker }).first()
    await expect(reportRow).toBeVisible()
    await reportRow.getByRole('button', { name: '处理' }).click()

    const handleDialog = adminPage.getByRole('dialog', { name: '处理举报' })
    await expect(handleDialog).toBeVisible()
    await handleDialog.getByPlaceholder('处理说明（可选）').fill(`已核实 ${reasonMarker}`)
    await handleDialog.getByRole('button', { name: '确认处理' }).click()

    await expectToast(adminPage, '举报已处理')
    await expect(adminPage.locator('.el-table__row').filter({ hasText: reasonMarker }).first()).toContainText('已处理')
    await adminContext.close()
  })
})
