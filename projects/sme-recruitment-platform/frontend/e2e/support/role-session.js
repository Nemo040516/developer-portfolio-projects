import { sessionStatePaths, testAccounts } from './accounts.js'
import { applySessionState, saveSessionState } from './session-auth.js'
import {
  createBackendContext,
  disposeBackendContext,
  loginWithCredentials,
  toSessionAuthState
} from './backend-api.js'

const frontendHost = process.env.PLAYWRIGHT_HOST || '127.0.0.1'
const frontendPort = Number(process.env.PLAYWRIGHT_PORT || 4173)
const frontendBaseURL = `http://${frontendHost}:${frontendPort}`

/**
 * 统一拼接前端地址，避免手动创建的新页面丢失 baseURL。
 */
export function resolveFrontendUrl(targetPath = '/') {
  return new URL(targetPath, `${frontendBaseURL}/`).toString()
}

/**
 * 刷新固定角色的 session-state 文件，并返回可直接注入页面的登录态。
 */
export async function refreshRoleSessionState(playwrightRequest, roleKey) {
  const account = testAccounts[roleKey]
  if (!account) {
    throw new Error(`未找到角色账号配置：${roleKey}`)
  }

  const apiContext = await createBackendContext(playwrightRequest)
  try {
    const authResult = await loginWithCredentials(apiContext, account)
    const authState = toSessionAuthState(authResult)
    await saveSessionState(sessionStatePaths[roleKey], authState)
    return authState
  } finally {
    await disposeBackendContext(apiContext)
  }
}

/**
 * 为任意账号构建临时登录态，可用于新注册测试账号。
 */
export async function buildSessionStateForCredentials(playwrightRequest, credentials) {
  const apiContext = await createBackendContext(playwrightRequest)
  try {
    const authResult = await loginWithCredentials(apiContext, credentials)
    return toSessionAuthState(authResult)
  } finally {
    await disposeBackendContext(apiContext)
  }
}

/**
 * 以指定角色打开页面。
 */
export async function prepareRolePage(page, playwrightRequest, roleKey, targetPath) {
  const authState = await refreshRoleSessionState(playwrightRequest, roleKey)
  await applySessionState(page, authState)
  await page.goto(resolveFrontendUrl(targetPath))
  return authState
}

/**
 * 以任意临时账号打开页面。
 */
export async function prepareCredentialsPage(page, playwrightRequest, credentials, targetPath) {
  const authState = await buildSessionStateForCredentials(playwrightRequest, credentials)
  await applySessionState(page, authState)
  await page.goto(resolveFrontendUrl(targetPath))
  return authState
}
