/*
文件速览：
1. 文件职责：覆盖 Module 7 管理员创建治理通知时的高风险二次确认链路，验证对象错配提示、确认框摘要和继续创建后的落库结果。
2. 测试入口：Playwright P1 用例 `管理员创建治理通知高风险二次确认闭环`。
3. 关键结构：两个临时商家账号、一个已审核通过职位、对象一致性检查面板、高风险提交确认框、创建成功后的详情抽屉。
4. 阅读建议：先看临时商家与职位造数，再看创建弹窗中的错配组合，最后看确认框断言与创建成功后的回显。
*/
import { expect, test } from '@playwright/test'
import {
  auditMerchantViaApi,
  buildUniqueValue,
  createApprovedJob,
  createBackendContext,
  createTemporaryMerchant,
  disposeBackendContext,
  ensureBackendReady,
  getFirstCategoryLeaf,
  loginWithCredentials,
  findMerchantByKeyword,
  saveMerchantInfoViaApi
} from '../../support/backend-api.js'
import { testAccounts } from '../../support/accounts.js'
import { prepareRolePage } from '../../support/role-session.js'
import { expectToast, selectElementPlusOption } from '../../support/ui-helpers.js'

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

async function confirmCreateWithRetry(adminPage, createDialog) {
  let lastBody = null

  for (let attempt = 1; attempt <= 2; attempt += 1) {
    const riskBox = adminPage.locator('.governance-create-risk-box').last()
    await expect(riskBox).toBeVisible()

    const createResponsePromise = adminPage.waitForResponse((response) => {
      return response.url().includes('/admin/governance/notices')
        && response.request().method() === 'POST'
    })

    await riskBox.getByRole('button', { name: '仍然创建' }).click()
    const createResponse = await createResponsePromise
    lastBody = await createResponse.json()

    if (createResponse.ok() && lastBody.code === 200) {
      return lastBody
    }

    if (attempt === 2 || lastBody.code !== 500) {
      break
    }

    await expect(createDialog).toBeVisible()
    await createDialog.getByRole('button', { name: '创建通知' }).click()
  }

  throw new Error(`治理通知创建失败，最终业务码：${lastBody?.code ?? 'UNKNOWN'}`)
}

test.describe.serial('Module 7 - P1 管理员创建治理通知高风险二次确认闭环', () => {
  test('管理员应可在高风险错配提示后再次确认并继续创建治理通知', async ({ browser, request }) => {
    const apiContext = await createBackendContext(request)
    const merchantACompanyName = buildUniqueValue('风控企业甲')
    const merchantBCompanyName = buildUniqueValue('风控企业乙')
    const merchantAProfile = buildMerchantProfilePayload(merchantACompanyName, '13800001001')
    const merchantBProfile = buildMerchantProfilePayload(merchantBCompanyName, '13800001002')
    const noticeTitle = buildUniqueValue('高风险确认E2E')

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
        reason: 'E2E 审核通过 - 企业甲'
      })
      await auditMerchantViaApi(apiContext, adminAuth.token, merchantBRecord.id, {
        status: 1,
        reason: 'E2E 审核通过 - 企业乙'
      })

      const firstCategory = await getFirstCategoryLeaf(apiContext)
      approvedJob = await createApprovedJob(apiContext, {
        merchantToken: merchantA.token,
        adminToken: adminAuth.token,
        categoryId: firstCategory.id,
        title: buildUniqueValue('高风险错配职位')
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
      await expect(readonlyPanel).toContainText(merchantACompanyName)
      await expect(readonlyPanel).toContainText(merchantBCompanyName)

      await createDialog.getByRole('button', { name: '创建通知' }).click()

      const riskBox = adminPage.locator('.governance-create-risk-box').last()
      await expect(riskBox).toBeVisible()
      await expect(riskBox).toContainText('当前创建单仍存在高风险对象错配')
      await expect(riskBox).toContainText('高风险项（1）')
      await expect(riskBox).toContainText('关联职位与关联商家疑似不属于同一企业')
      await expect(riskBox).toContainText('当前绑定对象')
      await expect(riskBox).toContainText(merchantB.username)
      await expect(riskBox).toContainText(merchantBCompanyName)
      await expect(riskBox).toContainText(approvedJob.title)

      await confirmCreateWithRetry(adminPage, createDialog)

      await expectToast(adminPage, '治理通知创建成功')

      const detailDrawer = adminPage.locator('.el-drawer').last()
      await expect(detailDrawer.locator('.drawer-hero__title').filter({ hasText: noticeTitle })).toBeVisible()
      await expect(detailDrawer.locator('.drawer-hero__tags').filter({ hasText: '职位整改' })).toBeVisible()

      const targetRow = adminPage.locator('.el-table__body tr').filter({
        hasText: noticeTitle
      }).first()
      await expect(targetRow).toBeVisible({ timeout: 15000 })
    } finally {
      if (adminContext) {
        await adminContext.close()
      }
      await disposeBackendContext(apiContext)
    }
  })
})
