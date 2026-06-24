import path from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = path.dirname(fileURLToPath(import.meta.url))
const sessionStateDir = path.resolve(currentDir, '../session-state')

/**
 * Task 1 先冻结三类角色测试账号口径。
 * 后续若账号有变化，只在这里统一维护，避免脚本分散硬编码。
 */
export const testAccounts = {
  applicant: {
    key: 'applicant',
    username: process.env.E2E_APPLICANT_USERNAME || 'app1',
    password: process.env.E2E_APPLICANT_PASSWORD || '12345',
    expectedRole: 'APPLICANT'
  },
  merchant: {
    key: 'merchant',
    username: process.env.E2E_MERCHANT_USERNAME || 'boss1',
    password: process.env.E2E_MERCHANT_PASSWORD || '12345',
    expectedRole: 'MERCHANT'
  },
  admin: {
    key: 'admin',
    username: process.env.E2E_ADMIN_USERNAME || 'admin1',
    password: process.env.E2E_ADMIN_PASSWORD || '12345',
    expectedRole: 'ADMIN'
  }
}

export const sessionStatePaths = {
  applicant: path.join(sessionStateDir, 'applicant.json'),
  merchant: path.join(sessionStateDir, 'merchant.json'),
  admin: path.join(sessionStateDir, 'admin.json')
}
