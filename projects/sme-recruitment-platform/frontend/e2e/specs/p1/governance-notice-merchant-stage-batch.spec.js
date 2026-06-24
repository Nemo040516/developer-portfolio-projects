/*
文件速览：
1. 文件职责：覆盖 Module 7 商家平台通知中心的跨页阶段快捷处理，验证“跨页确认全部已读”和“导出阶段摘要”可稳定执行。
2. 测试入口：Playwright P1 用例 `商家跨页阶段处理闭环`。
3. 关键结构：临时商家账号、批量治理通知造数、先看说明阶段卡、txt 下载断言、跨页已读回落。
4. 阅读建议：先看临时商家造数，再看阶段卡筛选与下载，最后看跨页已读后的阶段清零断言。
*/
import fs from 'node:fs/promises'
import { expect, test } from '@playwright/test'
import {
  buildLocalIsoDateTime,
  buildUniqueValue,
  createBackendContext,
  createGovernanceNoticeViaApi,
  createTemporaryMerchant,
  disposeBackendContext,
  ensureBackendReady,
  loginWithCredentials
} from '../../support/backend-api.js'
import { testAccounts } from '../../support/accounts.js'
import { prepareCredentialsPage } from '../../support/role-session.js'
import { expectToast } from '../../support/ui-helpers.js'

test.describe.serial('Module 7 - P1 商家跨页阶段处理闭环', () => {
  test('商家应可导出先看说明摘要并跨页确认全部已读', async ({ browser, request }) => {
    const apiContext = await createBackendContext(request)
    const noticePrefix = buildUniqueValue('商家阶段快捷处理E2E')
    const totalNotices = 11

    let adminAuth = null
    let merchantAccount = null
    let browserContext = null

    try {
      await ensureBackendReady(apiContext)
      adminAuth = await loginWithCredentials(apiContext, testAccounts.admin)
      merchantAccount = await createTemporaryMerchant(apiContext)

      for (let index = 0; index < totalNotices; index += 1) {
        const order = String(index + 1).padStart(2, '0')
        const title = `${noticePrefix}_${order}`
        await createGovernanceNoticeViaApi(apiContext, adminAuth.token, {
          targetRole: 'MERCHANT',
          targetUserId: merchantAccount.userId,
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
      const merchantPage = await browserContext.newPage()
      await prepareCredentialsPage(merchantPage, request, {
        username: merchantAccount.username,
        password: merchantAccount.password
      }, '/merchant/governance')

      await expect(merchantPage.getByRole('heading', { name: '平台通知', exact: true })).toBeVisible()

      const readStageCard = merchantPage.locator('.governance-stage-card').filter({
        hasText: '先看说明'
      }).first()
      await expect(readStageCard).toContainText(String(totalNotices), { timeout: 15000 })
      await readStageCard.locator('.governance-stage-card__label').click()

      const stageBar = merchantPage.locator('.stage-batch-bar')
      await expect(stageBar).toContainText('当前阶段已载入')

      const downloadPromise = merchantPage.waitForEvent('download')
      await stageBar.getByRole('button', { name: '导出阶段摘要' }).click()
      await expectToast(merchantPage, `已导出“先看说明”阶段摘要，共 ${totalNotices} 条事项`)
      const download = await downloadPromise
      const downloadPath = await download.path()
      expect(downloadPath).toBeTruthy()
      const downloadContent = await fs.readFile(downloadPath, 'utf8')
      expect(downloadContent).toContain('平台通知阶段摘要')
      expect(downloadContent).toContain('阶段视图：先看说明')
      expect(downloadContent).toContain(`事项总数：${totalNotices}`)
      expect(downloadContent).toContain(`${noticePrefix}_01`)
      expect(downloadContent).toContain(`${noticePrefix}_${String(totalNotices).padStart(2, '0')}`)

      await stageBar.getByRole('button', {
        name: `跨页确认全部已读（${totalNotices}）`
      }).click()
      const confirmDialog = merchantPage.locator('.el-message-box').filter({
        hasText: '跨页批量确认'
      }).last()
      await expect(confirmDialog).toBeVisible()
      await confirmDialog.getByRole('button', { name: '确认处理' }).click()

      await expectToast(merchantPage, `已跨页确认 ${totalNotices} 条通知`)
      await expect(readStageCard).toContainText('0')
      await expect(merchantPage.locator('.stage-batch-bar')).toHaveCount(0)
      await expect(merchantPage.getByRole('button', { name: '待查看 0' })).toBeVisible()
      await expect(merchantPage.getByRole('button', { name: `已完成 ${totalNotices}` })).toBeVisible()
    } finally {
      if (browserContext) {
        await browserContext.close()
      }
      await disposeBackendContext(apiContext)
    }
  })
})
