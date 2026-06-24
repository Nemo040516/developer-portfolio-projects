import fs from 'node:fs/promises'
import path from 'node:path'
import { expect } from '@playwright/test'
import { testAccounts } from './accounts.js'

/**
 * 通过接口登录获取最小登录态载荷。
 * 当前项目把登录态保存在 sessionStorage，因此这里返回自定义 JSON，
 * 后续通过 page.addInitScript 注入，而不是依赖 Playwright 原生 storageState。
 */
export async function loginByApi(request, account) {
  const response = await request.post('/auth/login', {
    data: {
      username: account.username,
      password: account.password
    }
  })

  expect(response.ok()).toBeTruthy()
  const body = await response.json()

  expect(body.code).toBe(200)
  expect(body.data?.token).toBeTruthy()

  return {
    token: body.data.token,
    role: body.data.role,
    userInfo: {
      username: body.data.username,
      role: body.data.role
    }
  }
}

/**
 * 将登录态写入自定义 session-state 文件，供后续多角色 E2E 复用。
 */
export async function saveSessionState(outputPath, authState) {
  await fs.mkdir(path.dirname(outputPath), { recursive: true })
  await fs.writeFile(outputPath, JSON.stringify(authState, null, 2), 'utf8')
}

/**
 * 从自定义 session-state 文件恢复登录态。
 */
export async function readSessionState(inputPath) {
  const content = await fs.readFile(inputPath, 'utf8')
  return JSON.parse(content)
}

/**
 * 在页面初始化阶段写入 sessionStorage。
 * 必须在目标业务页加载前执行，避免路由守卫先触发未登录逻辑。
 */
export async function applySessionState(page, authState) {
  await page.addInitScript((state) => {
    sessionStorage.setItem('token', state.token || '')
    sessionStorage.setItem('role', state.role || '')
    sessionStorage.setItem('userInfo', JSON.stringify(state.userInfo || {}))
  }, authState)
}

/**
 * Task 1 阶段保留一个 UI 登录兜底 helper，便于后续个别场景直接复用。
 */
export async function loginFromUi(page, account) {
  await page.goto('/login')
  await page.getByPlaceholder('请输入用户名').fill(account.username)
  await page.getByPlaceholder('请输入密码').fill(account.password)
  await page.getByRole('button', { name: '登录' }).click()
}

export function getAccountByRole(roleKey) {
  return testAccounts[roleKey]
}
