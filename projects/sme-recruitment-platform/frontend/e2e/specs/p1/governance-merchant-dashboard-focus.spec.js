/*
文件速览：
1. 文件职责：覆盖 Module 7 商家工作台的治理聚焦区，验证企业资料整改主聚焦卡、三段治理队列与跳转联动。
2. 测试入口：Playwright P1 用例 `商家工作台治理聚焦闭环`。
3. 关键结构：临时商家账号、企业资料审批、三类治理状态造数、工作台治理横幅、企业资料整改主卡、阶段队列跳转。
4. 阅读建议：先看临时商家与企业资料造数，再看三类治理状态准备，最后看工作台主聚焦卡与阶段队列断言。
*/
import { expect, test } from '@playwright/test'
import {
  auditMerchantViaApi,
  buildLocalIsoDateTime,
  buildUniqueValue,
  createBackendContext,
  createGovernanceNoticeViaApi,
  createTemporaryMerchant,
  disposeBackendContext,
  ensureBackendReady,
  expectApiSuccess,
  findMerchantByKeyword,
  loginWithCredentials,
  saveMerchantInfoViaApi
} from '../../support/backend-api.js'
import { testAccounts } from '../../support/accounts.js'
import { prepareCredentialsPage } from '../../support/role-session.js'

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

async function ensureDashboardGovernanceReady(page, totalCount) {
  const sidebarGovernanceCount = page.locator('.merchant-menu-count').filter({
    hasText: String(totalCount)
  }).first()

  await expect(sidebarGovernanceCount).toBeVisible({ timeout: 15000 })

  const heroTitle = page.locator('.governance-spotlight__title')
  if ((await heroTitle.textContent())?.includes('平台通知当前已清空')) {
    await page.reload()
    await expect(sidebarGovernanceCount).toBeVisible({ timeout: 15000 })
  }
}

test.describe.serial('Module 7 - P1 商家工作台治理聚焦闭环', () => {
  test('商家工作台应展示企业资料整改主聚焦卡、三段治理队列并支持跳转', async ({ browser, request }) => {
    const apiContext = await createBackendContext(request)
    const merchantCompanyName = buildUniqueValue('工作台企业整改商家')
    const merchantProfile = buildMerchantProfilePayload(merchantCompanyName, '13800002001')
    const readTitle = buildUniqueValue('商家工作台待查看E2E')
    const actionTitle = buildUniqueValue('商家工作台待处理E2E')
    const reviewTitle = buildUniqueValue('商家工作台待复核E2E')
    const reviewFixContent = `${reviewTitle} 已按要求补齐资料`

    let adminAuth = null
    let merchantAccount = null
    let browserContext = null
    let readNoticeId = null
    let actionNoticeId = null
    let reviewNoticeId = null

    try {
      await ensureBackendReady(apiContext)
      adminAuth = await loginWithCredentials(apiContext, testAccounts.admin)
      merchantAccount = await createTemporaryMerchant(apiContext)

      await saveMerchantInfoViaApi(apiContext, merchantAccount.token, merchantProfile)

      const merchantRecord = await waitForMerchantRecord(apiContext, adminAuth.token, merchantCompanyName)
      await auditMerchantViaApi(apiContext, adminAuth.token, merchantRecord.id, {
        status: 1,
        reason: 'E2E 审核通过 - 商家工作台治理聚焦'
      })

      readNoticeId = await createGovernanceNoticeViaApi(apiContext, adminAuth.token, {
        targetRole: 'MERCHANT',
        targetUserId: merchantAccount.userId,
        noticeType: 'JOB_RECTIFY',
        severity: 'WARNING',
        sourceModule: 'JOB_AUDIT',
        title: readTitle,
        summary: `${readTitle} 摘要`,
        detail: `${readTitle} 详细说明`,
        requiredAction: '请先查看平台说明，再安排后续整改。',
        dueTime: buildLocalIsoDateTime(3),
        needAck: 1,
        needReply: 0
      })

      actionNoticeId = await createGovernanceNoticeViaApi(apiContext, adminAuth.token, {
        targetRole: 'MERCHANT',
        targetUserId: merchantAccount.userId,
        noticeType: 'MERCHANT_RECTIFY',
        severity: 'HIGH',
        sourceModule: 'MERCHANT_AUDIT',
        title: actionTitle,
        summary: `${actionTitle} 摘要`,
        detail: `${actionTitle} 详细说明`,
        requiredAction: '请优先修正企业资料并重新提交审核。',
        dueTime: buildLocalIsoDateTime(3),
        needAck: 1,
        needReply: 1
      })

      reviewNoticeId = await createGovernanceNoticeViaApi(apiContext, adminAuth.token, {
        targetRole: 'MERCHANT',
        targetUserId: merchantAccount.userId,
        noticeType: 'JOB_RECTIFY',
        severity: 'WARNING',
        sourceModule: 'JOB_AUDIT',
        title: reviewTitle,
        summary: `${reviewTitle} 摘要`,
        detail: `${reviewTitle} 详细说明`,
        requiredAction: '请确认已读后提交复核说明。',
        dueTime: buildLocalIsoDateTime(3),
        needAck: 1,
        needReply: 1
      })

      await expectApiSuccess(apiContext, 'PUT', `/governance/notices/${actionNoticeId}/read`, {
        token: merchantAccount.token
      })
      await expectApiSuccess(apiContext, 'PUT', `/governance/notices/${reviewNoticeId}/read`, {
        token: merchantAccount.token
      })
      await expectApiSuccess(apiContext, 'POST', `/governance/notices/${reviewNoticeId}/actions`, {
        token: merchantAccount.token,
        data: {
          actionType: 'SUBMIT_FIX',
          content: reviewFixContent
        }
      })

      const allNoticeBody = await expectApiSuccess(apiContext, 'GET', '/governance/notices/my', {
        token: merchantAccount.token,
        params: {
          current: 1,
          size: 10
        }
      })
      const readNoticeBody = await expectApiSuccess(apiContext, 'GET', '/governance/notices/my', {
        token: merchantAccount.token,
        params: {
          current: 1,
          size: 10,
          status: 'PENDING_READ'
        }
      })
      const actionNoticeBody = await expectApiSuccess(apiContext, 'GET', '/governance/notices/my', {
        token: merchantAccount.token,
        params: {
          current: 1,
          size: 10,
          status: 'PENDING_ACTION'
        }
      })
      const reviewNoticeBody = await expectApiSuccess(apiContext, 'GET', '/governance/notices/my', {
        token: merchantAccount.token,
        params: {
          current: 1,
          size: 10,
          status: 'PENDING_REVIEW'
        }
      })

      expect(Number(allNoticeBody.data?.total || 0)).toBeGreaterThanOrEqual(3)
      expect(Number(readNoticeBody.data?.total || 0)).toBe(1)
      expect(Number(actionNoticeBody.data?.total || 0)).toBe(1)
      expect(Number(reviewNoticeBody.data?.total || 0)).toBe(1)

      browserContext = await browser.newContext()
      const merchantPage = await browserContext.newPage()
      await prepareCredentialsPage(merchantPage, request, {
        username: merchantAccount.username,
        password: merchantAccount.password
      }, '/merchant/governance')
      await expect(merchantPage.getByRole('heading', { name: '平台通知', exact: true })).toBeVisible()

      await merchantPage.goto('/merchant/dashboard')
      await ensureDashboardGovernanceReady(merchantPage, 3)
      await expect(merchantPage.locator('.governance-spotlight__title')).toContainText('企业资料整改')
      await expect(merchantPage.locator('.governance-stat-pill').filter({ hasText: '待查看 1' })).toBeVisible()
      await expect(merchantPage.locator('.governance-stat-pill').filter({ hasText: '待处理 1' })).toBeVisible()
      await expect(merchantPage.locator('.governance-stat-pill').filter({ hasText: '待复核 1' })).toBeVisible()

      const focusCard = merchantPage.locator('.governance-focus-card')
      await expect(focusCard.locator('.governance-focus-card__title')).toHaveText(actionTitle)
      await expect(focusCard.locator('.governance-focus-card__stage')).toContainText('等待你修正企业资料')
      await expect(focusCard.locator('.governance-focus-card__fact-value').first()).toContainText('进入企业信息管理页修改资料')

      const readLane = merchantPage.locator('.governance-lane-card').filter({ hasText: '先看说明' }).first()
      const actionLane = merchantPage.locator('.governance-lane-card').filter({ hasText: '先处理整改' }).first()
      const reviewLane = merchantPage.locator('.governance-lane-card').filter({ hasText: '等待复核' }).first()

      await expect(readLane).toContainText('1')
      await expect(readLane).toContainText(readTitle)
      await expect(actionLane).toContainText('1')
      await expect(actionLane).toContainText('当前最优先的整改动作已在左侧主聚焦卡展示')
      await expect(reviewLane).toContainText('1')
      await expect(reviewLane).toContainText(reviewTitle)

      await focusCard.getByRole('button', { name: '去修改企业资料' }).click()
      await expect(merchantPage).toHaveURL(new RegExp(`/merchant/company\\?noticeId=${actionNoticeId}`))

      await merchantPage.goto('/merchant/dashboard')
      await expect(merchantPage.locator('.governance-focus-card__title')).toHaveText(actionTitle)

      await readLane.locator('.governance-lane-entry').filter({ hasText: readTitle }).first().click()
      await expect(merchantPage).toHaveURL(/\/merchant\/governance$/)
      await expect(merchantPage.locator('.el-drawer').filter({ hasText: readTitle }).last()).toBeVisible()

      await merchantPage.goto('/merchant/dashboard')
      await expect(merchantPage.locator('.governance-focus-card__title')).toHaveText(actionTitle)

      await reviewLane.locator('.governance-lane-entry').filter({ hasText: reviewTitle }).first().click()
      await expect(merchantPage).toHaveURL(/\/merchant\/governance$/)
      await expect(merchantPage.locator('.el-drawer').filter({ hasText: reviewTitle }).last()).toBeVisible()
    } finally {
      if (browserContext) {
        await browserContext.close()
      }
      await disposeBackendContext(apiContext)
    }
  })
})
