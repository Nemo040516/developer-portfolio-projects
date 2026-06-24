import { expect } from '@playwright/test'

/**
 * 断言并等待 Element Plus 消息提示出现。
 */
export async function expectToast(page, text) {
  const toast = page.locator('.el-message, [role="alert"]').filter({ hasText: text }).last()
  await expect(toast).toBeVisible({ timeout: 10000 })
}

/**
 * 选择 Element Plus 下拉项。
 */
export async function selectElementPlusOption(page, triggerLocator, optionText) {
  await triggerLocator.click()
  const option = page.locator('.el-select-dropdown__item:visible').filter({ hasText: optionText }).first()
  await expect(option).toBeVisible()
  await option.click()
}

/**
 * 选择 Element Plus 级联项。
 */
export async function selectCascaderPath(page, triggerLocator, labels) {
  await triggerLocator.click()
  for (const label of labels) {
    const option = page.locator('.el-cascader-node:visible').filter({ hasText: label }).first()
    await expect(option).toBeVisible()
    await option.click()
  }
}

/**
 * 统一通过上传组件内部 input 注入文件，减少 spec 中重复选择器拼装。
 */
export async function uploadByInput(scopeLocator, filePayload) {
  const input = scopeLocator.locator('input[type="file"]').first()
  await input.setInputFiles(filePayload)
}

/**
 * 等待聊天页完成会话激活与首屏消息加载，减少并发回归下的初始化抖动。
 */
export async function waitForChatConversation(page, options = {}) {
  const {
    expectedHeaderText,
    expectedMessageText,
    timeout = 15000
  } = options

  await expect(page.locator('.chat-shell')).toBeVisible({ timeout })

  if (expectedHeaderText) {
    await expect(page.locator('.chat-header')).toContainText(expectedHeaderText, { timeout })
  } else {
    await expect(page.locator('.chat-header')).toBeVisible({ timeout })
  }

  if (expectedMessageText) {
    await expect(page.locator('.message-list')).toContainText(expectedMessageText, { timeout })
  } else {
    await expect(page.locator('.message-list')).toBeVisible({ timeout })
  }
}
