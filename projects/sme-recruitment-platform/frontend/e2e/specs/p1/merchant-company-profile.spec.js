/*
文件速览：
1. 文件职责：验证商家企业资料页的资料上传、保存与刷新回显主链路。
2. 测试入口：Playwright P1 用例 `Task 5 - P1 商家企业资料维护`。
3. 关键结构：企业资料页表单填写、Logo/营业执照/资质材料上传、保存后刷新回显。
4. 阅读建议：先看页面入口断言，再看上传与表单填写，最后看保存后的回显校验。
*/
import { expect, test } from '@playwright/test'
import { buildJpgFixture, buildPdfFixture } from '../../support/file-fixtures.js'
import { prepareCredentialsPage } from '../../support/role-session.js'
import { expectToast, selectElementPlusOption, uploadByInput } from '../../support/ui-helpers.js'

test.describe.serial('Task 5 - P1 商家企业资料维护', () => {
  test('商家可上传资料并在刷新后看到回显', async ({ page, request }) => {
    const uniqueStamp = Date.now()
    const companyName = `E2E测试企业_${uniqueStamp}`
    const contactName = `测试联系人${String(uniqueStamp).slice(-4)}`
    const description = `Task5 企业资料自动化说明 ${uniqueStamp}`
    const city = '苏州市'
    const district = '工业园区'
    const address = `星湖街${String(uniqueStamp).slice(-3)}号`

    await prepareCredentialsPage(page, request, {
      username: 'boss2',
      password: '12345'
    }, '/merchant/company')
    await expect(page.locator('.profile-hero__eyebrow')).toContainText('企业信息管理')
    await expect(page.locator('.company-form-card__title')).toContainText('企业资料编辑')

    await uploadByInput(page.locator('.avatar-uploader'), buildJpgFixture('task5-company-logo.jpg'))
    await expectToast(page, 'Logo 上传成功')

    const licenseFormItem = page.locator('.el-form-item').filter({ hasText: '营业执照' }).first()
    await uploadByInput(licenseFormItem, buildPdfFixture('task5-license.pdf'))
    await expectToast(page, '营业执照上传成功')

    const qualificationFormItem = page.locator('.el-form-item').filter({ hasText: '资质材料' }).first()
    await uploadByInput(qualificationFormItem, buildPdfFixture('task5-qualification.pdf'))
    await expectToast(page, '资质材料上传成功')

    await page.getByPlaceholder('请输入营业执照上的完整名称').fill(companyName)
    await selectElementPlusOption(
      page,
      page.locator('.el-form-item').filter({ hasText: '所属行业' }).locator('.el-select').first(),
      '互联网'
    )
    await selectElementPlusOption(
      page,
      page.locator('.el-form-item').filter({ hasText: '人员规模' }).locator('.el-select').first(),
      '20-99人'
    )
    await page.locator('.el-radio-button__inner').filter({ hasText: 'A轮' }).click()
    await page.getByPlaceholder('请介绍公司的主营业务、团队优势和招聘亮点。').fill(description)
    await page.getByPlaceholder('负责人姓名').fill(contactName)
    await page.getByPlaceholder('负责人手机号').fill('13912345678')
    await page.getByPlaceholder('统一社会信用代码').fill(`91320594TASK5${String(uniqueStamp).slice(-4)}`)
    await page.getByPlaceholder('营业执照法定代表人').fill('测试法人')
    await page.getByPlaceholder('例如：江苏省').fill('江苏省')
    await page.getByPlaceholder('例如：苏州市').fill(city)
    await page.getByPlaceholder('例如：工业园区').fill(district)
    await page.getByPlaceholder('街道、楼宇、门牌号').fill(address)

    await page.locator('.form-actions .el-button--primary').click()
    const submitToast = page.locator('.el-message, [role="alert"]').last()
    await expect(submitToast).toBeVisible()
    await expect(submitToast).toContainText(/企业资料已(重新提交审核|保存并提交审核)/)

    await page.goto('/merchant/company')
    await expect(page.getByPlaceholder('请输入营业执照上的完整名称')).toHaveValue(companyName)
    await expect(page.getByPlaceholder('负责人姓名')).toHaveValue(contactName)
    await expect(page.getByPlaceholder('请介绍公司的主营业务、团队优势和招聘亮点。')).toHaveValue(description)
    await expect(page.getByPlaceholder('街道、楼宇、门牌号')).toHaveValue(address)
    await expect(page.locator('.profile-hero__title')).toBeVisible()
    await expect(page.locator('.material-chip-list')).toContainText('材料 1')
  })
})
