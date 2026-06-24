/*
文件速览：
1. 文件职责：覆盖求职者与商家工作台的基础可达性，并验证候选人库核心浏览动作。
2. 测试入口：Playwright P1 用例 `Task 5 - P1 看板与人才库 smoke`。
3. 关键结构：求职者工作台可见性、商家工作台可见性、候选人库在线简历弹窗与聊天跳转。
4. 阅读建议：先看两个工作台页面基础断言，再看候选人库中“查看在线简历 -> 发起沟通”的轻量链路。
*/
import { expect, test } from '@playwright/test'
import { prepareCredentialsPage, prepareRolePage, resolveFrontendUrl } from '../../support/role-session.js'

test.describe.serial('Task 5 - P1 看板与人才库 smoke', () => {
  test('求职者工作台应展示核心卡片并可跳转职位大厅', async ({ page, request }) => {
    await prepareRolePage(page, request, 'applicant', '/applicant/dashboard')

    await expect(page.locator('.dashboard-header')).toBeVisible()
    await expect(page.locator('.interview-highlight')).toBeVisible()
    await expect(page.locator('.chat-highlight')).toBeVisible()
    await expect(page.locator('.insight-card')).toBeVisible()
    await expect(page.locator('.recommend-section')).toBeVisible()
    await expect(page.locator('.job-mini-card').first()).toBeVisible()

    await page.getByRole('button', { name: /探索新职位/ }).click()
    await expect(page).toHaveURL(/\/jobs$/)
  })

  test('商家工作台与候选人库页面应可打开并完成基础浏览', async ({ page, request }) => {
    const merchantCredentials = {
      username: 'boss4',
      password: '12345'
    }

    await prepareCredentialsPage(page, request, merchantCredentials, '/merchant/dashboard')

    await expect(page.locator('.quick-action-card')).toBeVisible()
    await expect(page.locator('.quick-action-grid')).toBeVisible()
    await expect(page.locator('.task-center-card')).toBeVisible()
    await expect(page.locator('.warning-list')).toBeVisible()

    await page.goto(resolveFrontendUrl('/merchant/talent'))

    await expect(page.locator('.toolbar-title').filter({ hasText: '候选人库' })).toBeVisible()
    const firstCard = page.locator('.talent-card').first()
    await expect(firstCard).toBeVisible()

    await firstCard.getByRole('button', { name: '查看在线简历' }).click()
    const resumeDialog = page.getByRole('dialog', { name: '在线简历' })
    await expect(resumeDialog).toBeVisible()
    await expect(resumeDialog.getByText('技能标签')).toBeVisible()
    await page.keyboard.press('Escape')
    await expect(resumeDialog).toBeHidden()

    await firstCard.getByRole('button', { name: /^(先沟通|打招呼|继续沟通)$/ }).click()
    await expect(page).toHaveURL(/\/merchant\/chat\?/)
    await expect(page.locator('.chat-shell')).toBeVisible()
  })
})
