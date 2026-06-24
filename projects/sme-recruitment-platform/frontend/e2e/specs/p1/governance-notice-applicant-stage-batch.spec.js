/*
文件速览：
1. 文件职责：覆盖 Module 7 求职者提醒中心的跨页阶段快捷处理，验证“跨页确认全部已读”和“导出阶段摘要”可稳定执行。
2. 测试入口：Playwright P1 用例 `求职者跨页阶段处理闭环`。
3. 关键结构：临时求职者账号、批量治理通知造数、先看说明阶段卡、txt 下载断言、跨页已读回落。
4. 阅读建议：先看批量造数，再看阶段卡筛选与下载，最后看跨页已读后的阶段清零断言。
*/
import fs from 'node:fs/promises'
import { expect, test } from '@playwright/test'
import {
  buildLocalIsoDateTime,
  buildUniqueValue,
  createBackendContext,
  createGovernanceNoticeViaApi,
  createTemporaryApplicant,
  disposeBackendContext,
  ensureBackendReady,
  loginWithCredentials
} from '../../support/backend-api.js'
import { testAccounts } from '../../support/accounts.js'
import { prepareCredentialsPage } from '../../support/role-session.js'
import { expectToast } from '../../support/ui-helpers.js'

test.describe.serial('Module 7 - P1 求职者跨页阶段处理闭环', () => {
  test('求职者应可导出先看说明摘要并跨页确认全部已读', async ({ browser, request }) => {
    const apiContext = await createBackendContext(request)
    const noticePrefix = buildUniqueValue('阶段快捷处理E2E')
    const totalNotices = 21

    let adminAuth = null
    let applicantAccount = null
    let browserContext = null

    try {
      await ensureBackendReady(apiContext)
      adminAuth = await loginWithCredentials(apiContext, testAccounts.admin)
      applicantAccount = await createTemporaryApplicant(apiContext)

      for (let index = 0; index < totalNotices; index += 1) {
        const order = String(index + 1).padStart(2, '0')
        const title = `${noticePrefix}_${order}`
        await createGovernanceNoticeViaApi(apiContext, adminAuth.token, {
          targetRole: 'APPLICANT',
          targetUserId: applicantAccount.userId,
          noticeType: 'USER_WARNING',
          severity: 'WARNING',
          sourceModule: 'RISK_CONTROL',
          title,
          summary: `${title} 摘要`,
          detail: `${title} 详细说明`,
          requiredAction: '请先确认已读并了解平台说明。',
          dueTime: buildLocalIsoDateTime(3),
          needAck: 1,
          needReply: 0
        })
      }

      browserContext = await browser.newContext({
        acceptDownloads: true
      })
      const applicantPage = await browserContext.newPage()
      await prepareCredentialsPage(applicantPage, request, {
        username: applicantAccount.username,
        password: applicantAccount.password
      }, '/applicant/notices')

      await expect(applicantPage.getByRole('heading', { name: '平台提醒', exact: true })).toBeVisible()

      const readStageCard = applicantPage.locator('.governance-stage-card').filter({
        hasText: '先读说明'
      }).first()
      await expect(readStageCard).toContainText(String(totalNotices), { timeout: 15000 })
      await readStageCard.locator('.governance-stage-card__label').click()

      const stageBar = applicantPage.locator('.stage-batch-bar')
      await expect(stageBar).toContainText(`当前阶段已载入`)
      const crossPageButton = stageBar.getByRole('button', {
        name: `跨页确认全部已读（${totalNotices}）`
      })
      await expect(crossPageButton).toBeVisible()

      const downloadPromise = applicantPage.waitForEvent('download')
      await stageBar.getByRole('button', { name: '导出阶段摘要' }).click()
      await expectToast(applicantPage, `已导出“先读说明”阶段摘要，共 ${totalNotices} 条提醒`)
      const download = await downloadPromise
      const downloadPath = await download.path()
      expect(downloadPath).toBeTruthy()
      const downloadContent = await fs.readFile(downloadPath, 'utf8')
      expect(downloadContent).toContain('平台提醒阶段摘要')
      expect(downloadContent).toContain('阶段视图：先读说明')
      expect(downloadContent).toContain(`事项总数：${totalNotices}`)
      expect(downloadContent).toContain(`${noticePrefix}_01`)
      expect(downloadContent).toContain(`${noticePrefix}_${String(totalNotices).padStart(2, '0')}`)

      await crossPageButton.click()
      const confirmDialog = applicantPage.locator('.el-message-box').filter({
        hasText: '跨页批量确认'
      }).last()
      await expect(confirmDialog).toBeVisible()
      await confirmDialog.getByRole('button', { name: '确认处理' }).click()

      await expectToast(applicantPage, `已跨页确认 ${totalNotices} 条提醒`)
      await expect(readStageCard).toContainText('0')
      await expect(applicantPage.locator('.stage-batch-bar')).toHaveCount(0)
      await expect(applicantPage.getByRole('button', { name: '待查看 0' })).toBeVisible()
      await expect(applicantPage.getByRole('button', { name: `已完成 ${totalNotices}` })).toBeVisible()
    } finally {
      if (browserContext) {
        await browserContext.close()
      }
      await disposeBackendContext(apiContext)
    }
  })
})
