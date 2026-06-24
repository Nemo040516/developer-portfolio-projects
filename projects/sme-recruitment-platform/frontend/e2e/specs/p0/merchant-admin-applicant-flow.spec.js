import { expect, request as playwrightRequest, test } from '@playwright/test'
import {
  createBackendContext,
  disposeBackendContext,
  ensureMerchantApproved,
  ensureBackendReady,
  findJobByKeyword,
  getFirstCategoryLeaf,
  loginWithCredentials
} from '../../support/backend-api.js'
import { testAccounts } from '../../support/accounts.js'
import { prepareRolePage, resolveFrontendUrl } from '../../support/role-session.js'
import { expectToast, selectCascaderPath } from '../../support/ui-helpers.js'

test.describe.serial('Task 4 - P0 商家发布到求职者投递主流程', () => {
  let apiContext
  let categoryLeaf

  test.beforeAll(async () => {
    apiContext = await createBackendContext(playwrightRequest)
    await ensureBackendReady(apiContext)
    categoryLeaf = await getFirstCategoryLeaf(apiContext)
  })

  test.afterAll(async () => {
    await disposeBackendContext(apiContext)
  })

  test('商家发布职位后，管理员审核通过，求职者可浏览并投递', async ({ browser, request }) => {
    const uniqueTitle = `E2E_UI_JOB_${Date.now()}`
    const merchantAuth = await loginWithCredentials(apiContext, testAccounts.merchant)
    const adminAuth = await loginWithCredentials(apiContext, testAccounts.admin)
    await ensureMerchantApproved(apiContext, merchantAuth.token, adminAuth.token)

    const merchantContext = await browser.newContext()
    const merchantPage = await merchantContext.newPage()
    await prepareRolePage(merchantPage, request, 'merchant', '/merchant/jobs')

    await merchantPage.getByRole('button', { name: '新建职位' }).click()

    const drawer = merchantPage.locator('.el-drawer')
    await expect(drawer).toContainText('发布新职位')
    await drawer.getByPlaceholder('例如：高级Java后端开发工程师').fill(uniqueTitle)
    await selectCascaderPath(
      merchantPage,
      drawer.locator('.el-cascader').first(),
      categoryLeaf.labels
    )
    await drawer.locator('.salary-input input').nth(0).fill('12')
    await drawer.locator('.salary-input input').nth(1).fill('18')
    await drawer.getByPlaceholder('例如：苏州').fill('苏州')
    await drawer.getByPlaceholder('例如：虎丘区').fill('工业园区')
    await drawer.getByPlaceholder('输入福利关键词后按回车').fill('E2E')
    await drawer.getByPlaceholder('输入福利关键词后按回车').press('Enter')
    await drawer.getByPlaceholder('请按段落输入岗位职责与任职要求，换行将按段落保存。')
      .fill(`${uniqueTitle} 职位描述\n熟悉自动化测试协作`)
    await drawer.getByRole('button', { name: '立即发布' }).click()

    await expectToast(merchantPage, '发布成功')
    await expect(merchantPage.locator('.job-card').filter({ hasText: uniqueTitle }).first()).toBeVisible()
    await merchantContext.close()

    const createdJob = await findJobByKeyword(apiContext, adminAuth.token, uniqueTitle)
    expect(createdJob, '商家发布后未能通过管理员接口定位到职位').toBeTruthy()

    const adminContext = await browser.newContext()
    const adminPage = await adminContext.newPage()
    await prepareRolePage(adminPage, request, 'admin', '/admin/jobs')

    await adminPage.getByPlaceholder('职位/企业关键词').fill(uniqueTitle)
    await adminPage.getByRole('button', { name: '筛选' }).click()

    const auditRow = adminPage.locator('.el-table__row').filter({ hasText: uniqueTitle }).first()
    await expect(auditRow).toBeVisible()
    await auditRow.getByRole('button', { name: '查看' }).click()

    const auditDrawer = adminPage.locator('.el-drawer')
    await expect(auditDrawer).toContainText(uniqueTitle)
    await auditDrawer.getByRole('button', { name: '通过' }).click()
    await adminPage.getByRole('button', { name: '确认通过' }).click()

    await expectToast(adminPage, '已通过审核')
    await adminContext.close()

    const applicantContext = await browser.newContext()
    const applicantPage = await applicantContext.newPage()
    await prepareRolePage(applicantPage, request, 'applicant', '/jobs')

    await applicantPage.getByPlaceholder('搜索职位、公司或技术关键字...').fill(uniqueTitle)
    await applicantPage.getByRole('button', { name: '搜索' }).click()

    const jobCard = applicantPage.locator('.job-hall-card').filter({ hasText: uniqueTitle }).first()
    await expect(jobCard).toBeVisible()

    await applicantPage.goto(resolveFrontendUrl(`/job/detail/${createdJob.id}`))
    const detailPage = applicantPage

    await expect(detailPage).toHaveURL(new RegExp(`/job/detail/${createdJob.id}$`))
    await expect(detailPage.getByRole('heading', { name: uniqueTitle })).toBeVisible()

    await detailPage.getByRole('button', { name: '投递简历' }).click()
    await expectToast(detailPage, '简历投递成功')
    await expect(detailPage.getByRole('button', { name: '已投递' })).toBeDisabled()

    await applicantPage.goto(resolveFrontendUrl('/applicant/applications'))
    const applicationCard = applicantPage.locator('.apple-app-card').filter({ hasText: uniqueTitle }).first()
    await expect(applicationCard).toBeVisible()
    await expect(applicationCard).toContainText('已投递')

    await applicantContext.close()
  })
})
