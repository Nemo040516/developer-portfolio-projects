/*
文件速览：
1. 文件职责：覆盖 Module 7 的治理通知浏览器主链路，验证管理员创建通知后，求职者可在平台提醒中查看、已读并提交申诉。
2. 测试入口：Playwright P1 用例 `治理通知创建与求职者处理闭环`。
3. 关键结构：临时求职者账号、管理员创建弹窗、自定义目标用户搜索面板、求职者提醒详情与申诉动作。
4. 阅读建议：先看临时账号造数，再看管理员创建步骤，最后看求职者侧已读与申诉断言。
*/
import { expect, test } from '@playwright/test'
import {
  buildUniqueValue,
  createBackendContext,
  createTemporaryApplicant,
  disposeBackendContext,
  ensureBackendReady
} from '../../support/backend-api.js'
import { prepareCredentialsPage, prepareRolePage } from '../../support/role-session.js'
import { expectToast } from '../../support/ui-helpers.js'

test.describe.serial('Module 7 - P1 治理通知创建与求职者处理闭环', () => {
  test('管理员创建用户警告后，求职者应可查看、已读并提交申诉', async ({ browser, request }) => {
    const apiContext = await createBackendContext(request)
    const noticeTitle = buildUniqueValue('治理通知E2E')
    const noticeSummary = `${noticeTitle} 摘要`
    const noticeDetail = `${noticeTitle} 详细说明`
    const appealContent = `${noticeTitle} 申诉说明`

    let applicantAccount = null
    let adminContext = null
    let applicantContext = null

    try {
      await ensureBackendReady(apiContext)
      applicantAccount = await createTemporaryApplicant(apiContext)

      adminContext = await browser.newContext()
      const adminPage = await adminContext.newPage()
      await prepareRolePage(adminPage, request, 'admin', '/admin/governance')

      await expect(adminPage.getByRole('heading', { name: '治理通知' })).toBeVisible()
      await adminPage.getByRole('button', { name: '创建通知' }).first().click()
      await expect(adminPage.getByRole('dialog', { name: '创建治理通知' })).toBeVisible()

      await adminPage.locator('.template-chip').filter({ hasText: '用户警告' }).click()

      const targetUserInput = adminPage.getByPlaceholder(/搜索求职者账号/)
      await targetUserInput.fill(applicantAccount.username)
      const targetUserOption = adminPage.locator('.inline-remote-picker__option').filter({
        hasText: applicantAccount.username
      }).first()
      await expect(targetUserOption).toBeVisible({ timeout: 10000 })
      await targetUserOption.click()

      await expect(adminPage.locator('.relation-summary-card').filter({
        hasText: applicantAccount.username
      })).toBeVisible()

      await adminPage.getByPlaceholder('请输入通知标题').fill(noticeTitle)
      await adminPage.getByPlaceholder('建议用一句话概括本次处理要求').fill(noticeSummary)
      await adminPage.getByPlaceholder('请写明触发原因、问题点和背景说明').fill(noticeDetail)
      await adminPage.getByPlaceholder('请写明用户下一步要完成的动作').fill('请先阅读平台说明，如有异议可提交申诉。')

      const createNoticeResponse = adminPage.waitForResponse((response) => {
        return response.url().includes('/admin/governance/notices')
          && response.request().method() === 'POST'
      })
      await adminPage.getByRole('button', { name: '创建通知' }).last().click()
      const createNoticeBody = await (await createNoticeResponse).json()
      expect(createNoticeBody.code).toBe(200)
      await expect(adminPage.locator('.drawer-hero__title').filter({ hasText: noticeTitle })).toBeVisible({ timeout: 10000 })

      await adminContext.close()
      adminContext = null

      applicantContext = await browser.newContext()
      const applicantPage = await applicantContext.newPage()
      await prepareCredentialsPage(applicantPage, request, {
        username: applicantAccount.username,
        password: applicantAccount.password
      }, '/applicant/notices')

      await expect(applicantPage.getByRole('heading', { name: '平台提醒', exact: true })).toBeVisible()

      const targetNoticeCard = applicantPage.locator('.notice-item').filter({
        hasText: noticeTitle
      }).first()
      await expect(targetNoticeCard).toBeVisible({ timeout: 15000 })
      await targetNoticeCard.getByRole('button', { name: '查看详情' }).click()

      const detailDrawer = applicantPage.locator('.el-drawer').filter({
        hasText: noticeTitle
      }).last()
      await expect(detailDrawer.locator('.drawer-hero__title').filter({ hasText: noticeTitle })).toBeVisible()
      const markReadResponse = applicantPage.waitForResponse((response) => {
        return response.url().includes('/governance/notices/')
          && response.url().includes('/read')
          && response.request().method() === 'PUT'
      })
      await detailDrawer.getByRole('button', { name: '确认已读' }).click()
      const markReadBody = await (await markReadResponse).json()
      expect(markReadBody.code).toBe(200)
      await expectToast(applicantPage, '已确认已读')

      await detailDrawer.getByRole('button', { name: '提交申诉' }).click()
      const actionDialog = applicantPage.getByRole('dialog', { name: '提交申诉' })
      await expect(actionDialog).toBeVisible()
      await actionDialog.getByRole('textbox').fill(appealContent)
      const submitAppealResponse = applicantPage.waitForResponse((response) => {
        return response.url().includes('/governance/notices/')
          && response.url().includes('/actions')
          && response.request().method() === 'POST'
      })
      await actionDialog.getByRole('button', { name: '确认提交申诉' }).click()
      const submitAppealBody = await (await submitAppealResponse).json()
      expect(submitAppealBody.code).toBe(200)
      await expectToast(applicantPage, '申诉已提交')

      await expect(detailDrawer.locator('.timeline-card__content').filter({
        hasText: appealContent
      }).first()).toBeVisible()
    } finally {
      if (adminContext) {
        await adminContext.close()
      }
      if (applicantContext) {
        await applicantContext.close()
      }
      await disposeBackendContext(apiContext)
    }
  })
})
