/*
文件速览：
1. 文件职责：覆盖 Module 7 的管理员治理复核主链路，验证事项进入待复核后，管理员可在治理通知页查看详情并完成关闭。
2. 测试入口：Playwright P1 用例 `管理员复核用户警告申诉闭环`。
3. 关键结构：临时求职者账号、API 造数进入待复核、管理员治理通知筛选、详情抽屉、关闭事项确认。
4. 阅读建议：先看通知与申诉的 API 造数，再看管理员筛选定位，最后看关闭事项后的状态与时间线断言。
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
import { prepareRolePage } from '../../support/role-session.js'
import { expectToast, selectElementPlusOption } from '../../support/ui-helpers.js'

test.describe.serial('Module 7 - P1 管理员复核用户警告申诉闭环', () => {
  test('管理员应可查看待复核申诉并关闭治理事项', async ({ browser, request }) => {
    const apiContext = await createBackendContext(request)
    const noticeTitle = buildUniqueValue('治理复核E2E')
    const noticeSummary = `${noticeTitle} 摘要`
    const noticeDetail = `${noticeTitle} 详细说明`
    const appealContent = `${noticeTitle} 申诉说明`
    const reviewComment = `${noticeTitle} 管理员关闭说明`

    let adminAuth = null
    let applicantAccount = null
    let noticeId = null
    let adminContext = null

    try {
      await ensureBackendReady(apiContext)
      adminAuth = await loginWithCredentials(apiContext, testAccounts.admin)
      applicantAccount = await createTemporaryApplicant(apiContext)

      noticeId = await createGovernanceNoticeViaApi(apiContext, adminAuth.token, {
        targetRole: 'APPLICANT',
        targetUserId: applicantAccount.userId,
        noticeType: 'USER_WARNING',
        severity: 'WARNING',
        sourceModule: 'RISK_CONTROL',
        title: noticeTitle,
        summary: noticeSummary,
        detail: noticeDetail,
        requiredAction: '请先确认已读，如有异议请提交申诉说明。',
        dueTime: buildLocalIsoDateTime(3),
        needAck: 1,
        needReply: 1
      })

      expect(noticeId).toBeTruthy()

      await expectApiSuccess(apiContext, 'PUT', `/governance/notices/${noticeId}/read`, {
        token: applicantAccount.token
      })
      await expectApiSuccess(apiContext, 'POST', `/governance/notices/${noticeId}/actions`, {
        token: applicantAccount.token,
        data: {
          actionType: 'APPEAL',
          content: appealContent
        }
      })

      adminContext = await browser.newContext()
      const adminPage = await adminContext.newPage()
      await prepareRolePage(adminPage, request, 'admin', '/admin/governance')

      await expect(adminPage.getByRole('heading', { name: '治理通知' })).toBeVisible()

      const filterSelects = adminPage.locator('.filter-card .el-select')
      await selectElementPlusOption(adminPage, filterSelects.nth(0), '求职者')
      await selectElementPlusOption(adminPage, filterSelects.nth(1), '用户警告')
      await selectElementPlusOption(adminPage, filterSelects.nth(2), '待复核')
      await selectElementPlusOption(adminPage, filterSelects.nth(3), '风险控制')
      await adminPage.getByRole('button', { name: '筛选' }).click()

      const clicked = await openNoticeDetailByTitle(adminPage, noticeTitle)
      expect(clicked, `未能在治理通知表格里点击标题为 ${noticeTitle} 的“查看”按钮`).toBeTruthy()

      const detailDrawer = adminPage.locator('.el-drawer').last()
      await expect(detailDrawer.locator('.drawer-body')).toBeVisible({ timeout: 15000 })
      await expect(detailDrawer).toContainText(noticeTitle)
      await expect(detailDrawer).toContainText(appealContent)

      await detailDrawer.getByRole('button', { name: '关闭事项' }).click()

      const reviewDialog = adminPage.getByRole('dialog', { name: /关闭治理事项/ })
      await expect(reviewDialog).toBeVisible()
      await reviewDialog.getByRole('textbox').fill(reviewComment)
      await reviewDialog.getByRole('button', { name: '确认关闭' }).click()

      const confirmDialog = adminPage.locator('.el-message-box').filter({
        hasText: '确认关闭当前治理事项吗'
      }).last()
      await expect(confirmDialog).toBeVisible()

      const reviewResponsePromise = adminPage.waitForResponse((response) => {
        return response.url().includes(`/admin/governance/notices/${noticeId}/review`)
          && response.request().method() === 'POST'
      })
      await confirmDialog.getByRole('button', { name: '确认' }).click()
      const reviewBody = await (await reviewResponsePromise).json()
      expect(reviewBody.code).toBe(200)
      await expectToast(adminPage, '治理事项处理成功')

      await expect(detailDrawer.locator('.drawer-hero__tags').filter({ hasText: '已关闭' })).toBeVisible()
      await expect(detailDrawer.locator('.timeline-content').filter({ hasText: reviewComment }).first()).toBeVisible()
      await expect(adminPage.locator('.el-table__body tr').filter({ hasText: noticeTitle })).toHaveCount(0)
    } finally {
      if (adminContext) {
        await adminContext.close()
      }
      await disposeBackendContext(apiContext)
    }
  })
})

async function openNoticeDetailByTitle(page, noticeTitle) {
  await expect
    .poll(async () => {
      return page.evaluate((expectedTitle) => {
        const rows = Array.from(document.querySelectorAll('.el-table__body-wrapper tbody tr'))
        return rows.some((row) => row.textContent?.includes(expectedTitle))
      }, noticeTitle)
    }, {
      message: `等待治理通知表格出现标题为 ${noticeTitle} 的行`
    })
    .toBeTruthy()

  return page.evaluate((expectedTitle) => {
    const bodyRows = Array.from(document.querySelectorAll('.el-table__body-wrapper tbody tr'))
    const rowIndex = bodyRows.findIndex((row) => row.textContent?.includes(expectedTitle))
    if (rowIndex < 0) {
      return false
    }

    const directButton = bodyRows[rowIndex]?.querySelector('button')
    if (directButton) {
      directButton.click()
      return true
    }

    const fixedRows = Array.from(document.querySelectorAll('.el-table__fixed-right-wrapper tbody tr, .el-table__fixed-right tbody tr'))
    const fixedButton = fixedRows[rowIndex]?.querySelector('button')
    if (fixedButton) {
      fixedButton.click()
      return true
    }

    return false
  }, noticeTitle)
}
