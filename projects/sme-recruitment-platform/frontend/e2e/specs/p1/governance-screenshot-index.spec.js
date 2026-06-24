/*
文件速览：
1. 文件职责：采集 Module 7 平台治理通知与整改单的关键页面截图，供本地测试复核使用。
2. 测试入口：Playwright 用例 `治理模块关键页面截图采集`。
3. 关键结构：临时商家/求职者造数、管理员/商家/求职者页面打开、截图目录写入。
4. 阅读建议：先看截图目录与命名，再看造数流程，最后看各角色页面截图步骤。
*/
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { expect, test } from '@playwright/test'
import {
  auditMerchantViaApi,
  buildLocalIsoDateTime,
  buildUniqueValue,
  createBackendContext,
  createGovernanceNoticeViaApi,
  createTemporaryApplicant,
  createTemporaryMerchant,
  disposeBackendContext,
  ensureBackendReady,
  expectApiSuccess,
  findMerchantByKeyword,
  loginWithCredentials,
  saveMerchantInfoViaApi
} from '../../support/backend-api.js'
import { prepareCredentialsPage, prepareRolePage } from '../../support/role-session.js'

const currentDir = path.dirname(fileURLToPath(import.meta.url))
const screenshotDir = path.resolve(currentDir, '../../../../test-results/governance-screenshots')

function ensureScreenshotDir() {
  fs.mkdirSync(screenshotDir, { recursive: true })
}

async function capturePage(page, fileName) {
  ensureScreenshotDir()
  await page.screenshot({
    path: path.join(screenshotDir, fileName),
    fullPage: true
  })
}

function buildMerchantProfilePayload(companyName, phone) {
  return {
    companyName,
    contactName: `${companyName}联系人`,
    contactPhone: phone,
    creditCode: buildUniqueValue('CREDIT').slice(0, 18).toUpperCase(),
    legalPerson: `${companyName}法人`,
    industry: '互联网',
    scale: '20-99人',
    financing: '未融资',
    description: `${companyName} 企业简介`,
    province: '江苏省',
    city: '苏州',
    district: '工业园区',
    address: '星湖街 88 号',
    licenseUrl: `https://example.com/licenses/${encodeURIComponent(companyName)}.png`,
    qualificationUrls: '[]'
  }
}

async function waitForMerchantRecord(apiContext, adminToken, keyword) {
  for (let attempt = 0; attempt < 6; attempt += 1) {
    const merchant = await findMerchantByKeyword(apiContext, adminToken, keyword)
    if (merchant) {
      return merchant
    }
    await new Promise((resolve) => setTimeout(resolve, 200))
  }
  throw new Error(`未能通过企业名 ${keyword} 定位商家记录`)
}

test.describe.serial('Module 7 - 治理模块关键页面截图采集', () => {
  test('应生成管理员、商家、求职者关键治理页面截图', async ({ browser, request }) => {
    const apiContext = await createBackendContext(request)
    const merchantCompanyName = buildUniqueValue('截图商家')
    const merchantProfile = buildMerchantProfilePayload(merchantCompanyName, '13800003001')
    const applicantNoticeTitle = buildUniqueValue('截图求职者提醒')
    const merchantReadTitle = buildUniqueValue('截图商家待查看')
    const merchantActionTitle = buildUniqueValue('截图商家待处理')
    const merchantReviewTitle = buildUniqueValue('截图商家待复核')

    let adminAuth = null
    let merchantAccount = null
    let applicantAccount = null
    let browserContext = null

    try {
      await ensureBackendReady(apiContext)
      adminAuth = await loginWithCredentials(apiContext, {
        username: 'admin1',
        password: '12345'
      })
      merchantAccount = await createTemporaryMerchant(apiContext)
      applicantAccount = await createTemporaryApplicant(apiContext)

      await saveMerchantInfoViaApi(apiContext, merchantAccount.token, merchantProfile)
      const merchantRecord = await waitForMerchantRecord(apiContext, adminAuth.token, merchantCompanyName)
      await auditMerchantViaApi(apiContext, adminAuth.token, merchantRecord.id, {
        status: 1,
        reason: '截图采集前置审核通过'
      })

      const applicantNoticeId = await createGovernanceNoticeViaApi(apiContext, adminAuth.token, {
        targetRole: 'APPLICANT',
        targetUserId: applicantAccount.userId,
        noticeType: 'USER_WARNING',
        severity: 'WARNING',
        sourceModule: 'RISK_CONTROL',
        title: applicantNoticeTitle,
        summary: `${applicantNoticeTitle} 摘要`,
        detail: `${applicantNoticeTitle} 详细说明`,
        requiredAction: '请阅读平台提醒，并按要求规范后续行为。',
        dueTime: buildLocalIsoDateTime(3),
        needAck: 1,
        needReply: 1
      })

      const merchantReadId = await createGovernanceNoticeViaApi(apiContext, adminAuth.token, {
        targetRole: 'MERCHANT',
        targetUserId: merchantAccount.userId,
        noticeType: 'JOB_RECTIFY',
        severity: 'WARNING',
        sourceModule: 'JOB_AUDIT',
        title: merchantReadTitle,
        summary: `${merchantReadTitle} 摘要`,
        detail: `${merchantReadTitle} 详细说明`,
        requiredAction: '请先查看平台说明，再决定后续整改动作。',
        dueTime: buildLocalIsoDateTime(3),
        needAck: 1,
        needReply: 0
      })

      const merchantActionId = await createGovernanceNoticeViaApi(apiContext, adminAuth.token, {
        targetRole: 'MERCHANT',
        targetUserId: merchantAccount.userId,
        noticeType: 'MERCHANT_RECTIFY',
        severity: 'HIGH',
        sourceModule: 'MERCHANT_AUDIT',
        title: merchantActionTitle,
        summary: `${merchantActionTitle} 摘要`,
        detail: `${merchantActionTitle} 详细说明`,
        requiredAction: '请优先修正企业资料并重新提交审核。',
        dueTime: buildLocalIsoDateTime(3),
        needAck: 1,
        needReply: 1
      })

      const merchantReviewId = await createGovernanceNoticeViaApi(apiContext, adminAuth.token, {
        targetRole: 'MERCHANT',
        targetUserId: merchantAccount.userId,
        noticeType: 'JOB_RECTIFY',
        severity: 'WARNING',
        sourceModule: 'JOB_AUDIT',
        title: merchantReviewTitle,
        summary: `${merchantReviewTitle} 摘要`,
        detail: `${merchantReviewTitle} 详细说明`,
        requiredAction: '请确认已读后提交复核说明。',
        dueTime: buildLocalIsoDateTime(3),
        needAck: 1,
        needReply: 1
      })

      await expectApiSuccess(apiContext, 'PUT', `/governance/notices/${merchantActionId}/read`, {
        token: merchantAccount.token
      })
      await expectApiSuccess(apiContext, 'PUT', `/governance/notices/${merchantReviewId}/read`, {
        token: merchantAccount.token
      })
      await expectApiSuccess(apiContext, 'POST', `/governance/notices/${merchantReviewId}/actions`, {
        token: merchantAccount.token,
        data: {
          actionType: 'SUBMIT_FIX',
          content: `${merchantReviewTitle} 已按要求提交复核说明`
        }
      })

      browserContext = await browser.newContext()

      const adminPage = await browserContext.newPage()
      await prepareRolePage(adminPage, request, 'admin', '/admin/governance')
      await expect(adminPage.getByRole('heading', { name: '治理通知', exact: true })).toBeVisible()
      await capturePage(adminPage, 'admin-governance-list.png')
      await adminPage.getByRole('button', { name: '创建通知' }).click()
      await expect(adminPage.getByRole('dialog', { name: '创建治理通知' })).toBeVisible()
      await capturePage(adminPage, 'admin-governance-create-dialog.png')
      await adminPage.keyboard.press('Escape')

      const merchantDashboardPage = await browserContext.newPage()
      await prepareCredentialsPage(merchantDashboardPage, request, {
        username: merchantAccount.username,
        password: merchantAccount.password
      }, '/merchant/dashboard')
      await expect(merchantDashboardPage.locator('.governance-focus-card__title')).toHaveText(merchantActionTitle)
      await capturePage(merchantDashboardPage, 'merchant-dashboard-governance-focus.png')

      const merchantGovernancePage = await browserContext.newPage()
      await prepareCredentialsPage(merchantGovernancePage, request, {
        username: merchantAccount.username,
        password: merchantAccount.password
      }, `/merchant/governance?noticeId=${merchantReadId}`)
      await expect(merchantGovernancePage.getByRole('heading', { name: '平台通知', exact: true })).toBeVisible()
      await expect(merchantGovernancePage.locator('.el-drawer').filter({ hasText: merchantReadTitle }).last()).toBeVisible()
      await capturePage(merchantGovernancePage, 'merchant-governance-center.png')

      const applicantGovernancePage = await browserContext.newPage()
      await prepareCredentialsPage(applicantGovernancePage, request, {
        username: applicantAccount.username,
        password: applicantAccount.password
      }, `/applicant/notices?noticeId=${applicantNoticeId}`)
      await expect(applicantGovernancePage.getByRole('heading', { name: '平台提醒', exact: true })).toBeVisible()
      await expect(applicantGovernancePage.locator('.el-drawer').filter({ hasText: applicantNoticeTitle }).last()).toBeVisible()
      await capturePage(applicantGovernancePage, 'applicant-governance-center.png')
    } finally {
      if (browserContext) {
        await browserContext.close()
      }
      await disposeBackendContext(apiContext)
    }
  })
})
