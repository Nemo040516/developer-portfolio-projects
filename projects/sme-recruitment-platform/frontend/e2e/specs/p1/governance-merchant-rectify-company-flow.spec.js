/*
文件速览：
1. 文件职责：覆盖 Module 7 的 MERCHANT_RECTIFY 企业资料整改闭环，验证管理员驳回后，商家可从平台通知进入企业资料页并重新提交审核。
2. 测试入口：Playwright P1 用例 `企业资料整改通知承接闭环`。
3. 关键结构：管理员审核驳回联动、商家平台通知详情、企业资料整改横幅、保存并重新提交审核。
4. 阅读建议：先看管理员驳回造数，再看商家通知详情跳转，最后看企业资料页重新提交审核断言。
*/
import { expect, test } from '@playwright/test'
import {
  auditMerchantViaApi,
  buildUniqueValue,
  createBackendContext,
  createTemporaryMerchant,
  disposeBackendContext,
  ensureBackendReady,
  findMerchantByKeyword,
  loginWithCredentials,
  saveMerchantInfoViaApi
} from '../../support/backend-api.js'
import { testAccounts } from '../../support/accounts.js'
import { prepareCredentialsPage } from '../../support/role-session.js'
import { expectToast } from '../../support/ui-helpers.js'

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
    qualificationUrls: JSON.stringify([
      `https://example.com/qualifications/${encodeURIComponent(companyName)}-1.png`
    ])
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

test.describe.serial('Module 7 - P1 企业资料整改通知承接闭环', () => {
  test('商家应可从 MERCHANT_RECTIFY 通知进入企业资料页并重新提交审核', async ({ browser, request }) => {
    const apiContext = await createBackendContext(request)
    const merchantCompanyName = buildUniqueValue('企业整改临时商家')
    const merchantProfile = buildMerchantProfilePayload(merchantCompanyName, '13800002021')
    const rejectReason = buildUniqueValue('商家资料整改原因')
    const updateMarker = buildUniqueValue('企业资料整改更新')

    let merchantAccount
    let merchantAuth
    let adminAuth
    let merchantRecord
    let browserContext = null

    try {
      await ensureBackendReady(apiContext)
      adminAuth = await loginWithCredentials(apiContext, testAccounts.admin)
      merchantAccount = await createTemporaryMerchant(apiContext)
      merchantAuth = merchantAccount

      await saveMerchantInfoViaApi(apiContext, merchantAccount.token, merchantProfile)

      merchantRecord = await waitForMerchantRecord(apiContext, adminAuth.token, merchantCompanyName)
      await auditMerchantViaApi(apiContext, adminAuth.token, merchantRecord.id, {
        status: 1,
        reason: 'E2E 初始化审核通过'
      })

      await auditMerchantViaApi(apiContext, adminAuth.token, merchantRecord.id, {
        status: 2,
        reason: rejectReason
      })

      browserContext = await browser.newContext()
      const merchantPage = await browserContext.newPage()
      await prepareCredentialsPage(merchantPage, request, {
        username: merchantAccount.username,
        password: merchantAccount.password
      }, '/merchant/governance')

      await expect(merchantPage.getByRole('heading', { name: '平台通知', exact: true })).toBeVisible()

      const targetNoticeCard = merchantPage.locator('.notice-card').filter({
        hasText: rejectReason
      }).first()
      await expect(targetNoticeCard).toBeVisible({ timeout: 15000 })
      await targetNoticeCard.getByRole('button', { name: '查看详情' }).click()

      const detailDrawer = merchantPage.locator('.el-drawer').filter({
        hasText: rejectReason
      }).last()
      await expect(detailDrawer.locator('.drawer-hero__summary').filter({ hasText: rejectReason })).toBeVisible()

      const markReadResponse = merchantPage.waitForResponse((response) => {
        return response.url().includes('/governance/notices/')
          && response.url().includes('/read')
          && response.request().method() === 'PUT'
      })
      await detailDrawer.getByRole('button', { name: '确认已读' }).click()
      const markReadBody = await (await markReadResponse).json()
      expect(markReadBody.code).toBe(200)
      await expectToast(merchantPage, '已确认已读')

      await detailDrawer.getByRole('button', { name: '去修改企业资料' }).click()
      await expect(merchantPage).toHaveURL(/\/merchant\/company(\?|$)/)

      await expect(merchantPage.locator('.rectify-banner')).toBeVisible()
      await expect(merchantPage.locator('.rectify-insight-card')).toContainText(rejectReason)
      await expect(merchantPage.locator('.profile-hero__title')).toContainText('企业资料需要整改')

      const descriptionInput = merchantPage.getByPlaceholder('请介绍公司的主营业务、团队优势和招聘亮点。')
      const originalDescription = await descriptionInput.inputValue()
      await descriptionInput.fill(`${originalDescription}\n${updateMarker}`)

      const updateResponse = merchantPage.waitForResponse((response) => {
        return response.url().includes('/merchant/update')
          && response.request().method() === 'POST'
      })
      await merchantPage.getByRole('button', { name: '保存并重新提交审核' }).click()
      const updateBody = await (await updateResponse).json()
      expect(updateBody.code).toBe(200)
      await expectToast(merchantPage, '企业资料已重新提交审核')

      await expect(merchantPage.locator('.rectify-insight-card')).toContainText('等待平台复核')
      await expect(merchantPage.locator('.rectify-banner')).toContainText('当前无需重复提交，等待管理员复核即可。')
    } finally {
      if (merchantRecord?.id && adminAuth?.token) {
        await auditMerchantViaApi(apiContext, adminAuth.token, merchantRecord.id, {
          status: 1,
          reason: 'E2E 恢复通过'
        }).catch(() => {})
      }
      if (browserContext) {
        await browserContext.close()
      }
      await disposeBackendContext(apiContext)
    }
  })
})
