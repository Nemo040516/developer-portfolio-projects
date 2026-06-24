import { expect, test } from '@playwright/test'

test.describe('登录页 smoke', () => {
  test('应展示基础登录元素并可跳转到注册页', async ({ page }) => {
    await page.goto('/login')

    await expect(page.getByRole('heading', { name: '欢迎回来' })).toBeVisible()
    await expect(page.getByPlaceholder('请输入用户名')).toBeVisible()
    await expect(page.getByPlaceholder('请输入密码')).toBeVisible()
    await expect(page.getByRole('button', { name: '登录' })).toBeVisible()

    await page.getByRole('link', { name: '去注册' }).click()

    await expect(page).toHaveURL(/\/register$/)
    await expect(page.getByRole('heading', { name: '创建账号' })).toBeVisible()
  })
})
