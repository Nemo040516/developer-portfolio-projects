/*
文件速览：
1. 文件职责：覆盖 Module 7 管理员创建治理通知时的高风险确认“返回检查”分支，验证不会误发治理通知。
2. 测试入口：Playwright P1 用例 `管理员创建治理通知高风险返回检查闭环`。
3. 关键结构：两个临时商家账号、一个已审核通过职位、高风险确认框、返回检查分支、请求计数与列表未创建断言。
4. 阅读建议：先看临时商家与职位造数，再看高风险错配组合，最后看“返回检查”后的保留态与未创建断言。
*/
import { expect, test } from '@playwright/test'
import {
  apiFetch,
  auditMerchantViaApi,
  buildUniqueValue,
  createApprovedJob,
  createBackendContext,
  createTemporaryMerchant,
  disposeBackendContext,
  ensureBackendReady,
  findMerchantByKeyword,
  getFirstCategoryLeaf,
  loginWithCredentials,
  saveMerchantInfoViaApi
} from '../../support/backend-api.js'
import { testAccounts } from '../../support/accounts.js'
import { prepareRolePage } from '../../support/role-session.js'
import { selectElementPlusOption } from '../../support/ui-helpers.js'

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
    address: '星湖街 66 号',
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

test.describe.serial('Module 7 - P1 管理员创建治理通知高风险返回检查闭环', () => {
  test('管理员点击返回检查后应保留当前填写内容且不创建治理通知', async ({ browser, request }) => {
    const apiContext = await createBackendContext(request)
    const merchantACompanyName = buildUniqueValue('返回检查企业甲')
    const merchantBCompanyName = buildUniqueValue('返回检查企业乙')
    const merchantAProfile = buildMerchantProfilePayload(merchantACompanyName, '13800002001')
    const merchantBProfile = buildMerchantProfilePayload(merchantBCompanyName, '13800002002')
    const noticeTitle = buildUniqueValue('返回检查E2E')

    let adminAuth = null
    let merchantA = null
    let merchantB = null
    let approvedJob = null
    let adminContext = null

    try {
      await ensureBackendReady(apiContext)
      adminAuth = await loginWithCredentials(apiContext, testAccounts.admin)

      merchantA = await createTemporaryMerchant(apiContext)
      merchantB = await createTemporaryMerchant(apiContext)

      await saveMerchantInfoViaApi(apiContext, merchantA.token, merchantAProfile)
      await saveMerchantInfoViaApi(apiContext, merchantB.token, merchantBProfile)

      const merchantARecord = await waitForMerchantRecord(apiContext, adminAuth.token, merchantACompanyName)
      const merchantBRecord = await waitForMerchantRecord(apiContext, adminAuth.token, merchantBCompanyName)

      await auditMerchantViaApi(apiContext, adminAuth.token, merchantARecord.id, {
        status: 1,
        reason: 'E2E 审核通过 - 返回检查企业甲'
      })
      await auditMerchantViaApi(apiContext, adminAuth.token, merchantBRecord.id, {
        status: 1,
        reason: 'E2E 审核通过 - 返回检查企业乙'
      })

      const firstCategory = await getFirstCategoryLeaf(apiContext)
      approvedJob = await createApprovedJob(apiContext, {
        merchantToken: merchantA.token,
        adminToken: adminAuth.token,
        categoryId: firstCategory.id,
        title: buildUniqueValue('返回检查错配职位')
      })

      adminContext = await browser.newContext()
      const adminPage = await adminContext.newPage()
      await prepareRolePage(adminPage, request, 'admin', '/admin/governance')

      await expect(adminPage.getByRole('heading', { name: '治理通知' })).toBeVisible()
      await adminPage.getByRole('button', { name: '创建通知' }).first().click()

      const createDialog = adminPage.getByRole('dialog', { name: '创建治理通知' })
      await expect(createDialog).toBeVisible()

      await createDialog.locator('.template-chip').filter({ hasText: '职位整改' }).click()
      await createDialog.getByPlaceholder('搜索商家账号 / 昵称 / 手机 / 邮箱').fill(merchantB.username)
      const targetUserOption = createDialog.locator('.inline-remote-picker__option').filter({
        hasText: merchantB.username
      }).first()
      await expect(targetUserOption).toBeVisible({ timeout: 15000 })
      await targetUserOption.click()

      await selectElementPlusOption(
        adminPage,
        createDialog.locator('.create-form-item--span-2 .el-select').nth(0),
        approvedJob.title
      )
      await selectElementPlusOption(
        adminPage,
        createDialog.locator('.create-form-item--span-2 .el-select').nth(1),
        merchantBCompanyName
      )

      await createDialog.getByPlaceholder('请输入通知标题').fill(noticeTitle)

      const readonlyPanel = createDialog.locator('.readonly-check-panel')
      await expect(readonlyPanel).toContainText('高风险 1')
      await expect(readonlyPanel).toContainText('关联职位与关联商家疑似不属于同一企业')

      let createRequestCount = 0
      adminPage.on('request', (requestItem) => {
        if (requestItem.method() === 'POST' && requestItem.url().includes('/admin/governance/notices')) {
          createRequestCount += 1
        }
      })

      await createDialog.getByRole('button', { name: '创建通知' }).click()

      const riskBox = adminPage.locator('.governance-create-risk-box').last()
      await expect(riskBox).toBeVisible()
      await riskBox.getByRole('button', { name: '返回检查' }).click()

      await expect(riskBox).toBeHidden()
      await expect(createDialog).toBeVisible()
      await expect(createDialog.getByPlaceholder('请输入通知标题')).toHaveValue(noticeTitle)
      await expect(readonlyPanel).toContainText('高风险 1')
      await expect(createDialog.locator('.relation-summary-card').filter({ hasText: merchantB.username }).first()).toBeVisible()

      await adminPage.waitForTimeout(300)
      expect(createRequestCount).toBe(0)

      const targetRow = adminPage.locator('.el-table__body tr').filter({
        hasText: noticeTitle
      })
      await expect(targetRow).toHaveCount(0)

      const apiResult = await apiFetch(apiContext, 'GET', '/admin/governance/notices', {
        token: adminAuth.token,
        params: {
          current: 1,
          size: 20,
          targetRole: 'MERCHANT',
          noticeType: 'JOB_RECTIFY',
          sourceModule: 'JOB_AUDIT'
        }
      })
      expect(apiResult.response.ok()).toBeTruthy()
      expect(apiResult.body?.code).toBe(200)
      const records = Array.isArray(apiResult.body?.data?.records) ? apiResult.body.data.records : []
      const matched = records.find((item) => item.title === noticeTitle)
      expect(matched).toBeFalsy()
    } finally {
      if (adminContext) {
        await adminContext.close()
      }
      await disposeBackendContext(apiContext)
    }
  })
})
