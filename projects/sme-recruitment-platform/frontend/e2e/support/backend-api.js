/*
文件速览：
1. 文件职责：为 Playwright E2E 提供后端造数、登录、查询与清理辅助，减少 spec 里重复拼接接口请求。
2. 对外入口：登录 helper、临时账号创建、商家资料写入、职位与举报造数、治理通知创建、账号封禁更新等函数。
3. 关键结构：统一请求封装、角色登录、业务查询、治理场景专用 helper。
4. 阅读建议：先看 apiFetch / expectApiSuccess，再看账号登录与临时账号 helper，随后看商家资料、职位、治理通知、封禁等业务辅助函数。
*/
import { expect } from '@playwright/test'

const backendBaseURL = process.env.PLAYWRIGHT_API_BASE_URL
  || process.env.VITE_APP_BASE_URL
  || 'http://127.0.0.1:8080'

/**
 * 统一计算后端基地址，便于本地与后续 CI 复用。
 */
export function getBackendBaseURL() {
  return backendBaseURL
}

/**
 * 构建唯一测试数据前缀，尽量降低本地脏数据相互影响。
 */
export function buildUniqueValue(prefix) {
  const stamp = new Date().toISOString().replace(/[-:.TZ]/g, '').slice(0, 14)
  const suffix = Math.random().toString(36).slice(2, 6)
  return `${prefix}_${stamp}_${suffix}`
}

/**
 * 生成后端 LocalDateTime 可直接反序列化的本地 ISO 时间文本。
 */
export function buildLocalIsoDateTime(daysLater = 3) {
  const target = new Date()
  target.setDate(target.getDate() + daysLater)
  const pad = (value) => String(value).padStart(2, '0')
  return `${target.getFullYear()}-${pad(target.getMonth() + 1)}-${pad(target.getDate())}T${pad(target.getHours())}:${pad(target.getMinutes())}:${pad(target.getSeconds())}`
}

/**
 * 为 Playwright 创建面向后端的请求上下文。
 */
export async function createBackendContext(playwrightRequest) {
  if (playwrightRequest && typeof playwrightRequest.newContext === 'function') {
    const apiContext = await playwrightRequest.newContext({
      baseURL: backendBaseURL,
      extraHTTPHeaders: {
        Accept: 'application/json'
      }
    })
    apiContext.__codexOwned = true
    return apiContext
  }

  return playwrightRequest
}

/**
 * 只释放当前 helper 自己创建的请求上下文，避免误关 Playwright fixture。
 */
export async function disposeBackendContext(apiContext) {
  if (apiContext?.__codexOwned && typeof apiContext.dispose === 'function') {
    await apiContext.dispose()
  }
}

function buildUrl(pathname, params = {}) {
  const url = new URL(pathname, backendBaseURL)
  Object.entries(params || {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      url.searchParams.set(key, String(value))
    }
  })
  return url.toString()
}

function buildHeaders(token, extraHeaders = {}) {
  const headers = { ...extraHeaders }
  if (token) {
    headers.Authorization = token.startsWith('Bearer ')
      ? token
      : `Bearer ${token}`
  }
  return headers
}

function extractRecords(body) {
  if (Array.isArray(body?.data)) return body.data
  if (Array.isArray(body?.data?.records)) return body.data.records
  return []
}

/**
 * 统一封装后端请求，避免各个 spec 重复拼 URL 和鉴权头。
 */
export async function apiFetch(apiContext, method, pathname, options = {}) {
  const {
    token,
    data,
    multipart,
    params,
    headers,
    failOnStatusCode = false
  } = options

  const requestOptions = {
    method,
    headers: buildHeaders(token, headers),
    failOnStatusCode
  }

  if (multipart) {
    requestOptions.multipart = multipart
  } else if (data !== undefined) {
    requestOptions.data = data
  }

  const response = await apiContext.fetch(buildUrl(pathname, params), requestOptions)

  let body
  try {
    body = await response.json()
  } catch (error) {
    body = await response.text()
  }

  return { response, body }
}

/**
 * 统一提取业务层 data，便于脚本只关心接口返回值。
 */
export function extractBusinessData(body) {
  return body?.data
}

/**
 * 对成功接口做统一断言，便于测试代码保持简洁。
 */
export async function expectApiSuccess(apiContext, method, pathname, options = {}) {
  const result = await apiFetch(apiContext, method, pathname, options)
  expect(result.response.ok(), `${method} ${pathname} 返回了非 2xx 响应`).toBeTruthy()
  expect(result.body?.code, `${method} ${pathname} 返回了非 200 业务码`).toBe(200)
  return result.body
}

/**
 * 在进入正式 E2E 前，先确认后端已可访问。
 */
export async function ensureBackendReady(apiContext) {
  const body = await expectApiSuccess(apiContext, 'GET', '/category/list')
  expect(Array.isArray(body.data)).toBeTruthy()
  return body.data
}

/**
 * 使用指定账号登录，并返回后续 API 造数需要的鉴权信息。
 */
export async function loginWithCredentials(apiContext, credentials) {
  const body = await expectApiSuccess(apiContext, 'POST', '/auth/login', {
    data: {
      username: credentials.username,
      password: credentials.password
    }
  })

  expect(body.data?.token).toBeTruthy()
  expect(body.data?.role).toBeTruthy()

  return {
    username: credentials.username,
    password: credentials.password,
    token: body.data.token,
    role: body.data.role,
    userId: body.data.userId
  }
}

/**
 * 转换为前端 sessionStorage 结构，供页面注入直接复用。
 */
export function toSessionAuthState(authResult) {
  return {
    token: authResult.token,
    role: authResult.role,
    userInfo: {
      username: authResult.username,
      role: authResult.role
    }
  }
}

/**
 * 创建临时求职者账号，避免封禁等高风险场景污染公共账号。
 */
export async function createTemporaryApplicant(apiContext) {
  const account = {
    username: buildUniqueValue('e2e_app').toLowerCase(),
    password: '12345',
    role: 'APPLICANT'
  }

  await expectApiSuccess(apiContext, 'POST', '/auth/register', {
    data: account
  })

  const authResult = await loginWithCredentials(apiContext, account)
  return {
    ...account,
    ...authResult
  }
}

/**
 * 创建临时商家账号，便于治理通知等页面回归时隔离公共商家数据。
 */
export async function createTemporaryMerchant(apiContext) {
  const account = {
    username: buildUniqueValue('e2e_mer').toLowerCase(),
    password: '12345',
    role: 'MERCHANT'
  }

  await expectApiSuccess(apiContext, 'POST', '/auth/register', {
    data: account
  })

  const authResult = await loginWithCredentials(apiContext, account)
  return {
    ...account,
    ...authResult
  }
}

/**
 * 通过 API 写入商家企业资料，便于后续审核、发岗和治理错配场景造数。
 */
export async function saveMerchantInfoViaApi(apiContext, merchantToken, payload) {
  const body = await expectApiSuccess(apiContext, 'POST', '/merchant/update', {
    token: merchantToken,
    data: payload
  })
  return extractBusinessData(body)
}

/**
 * 通过管理员接口手动创建治理通知，便于 UI 用例聚焦通知处理链路。
 */
export async function createGovernanceNoticeViaApi(apiContext, adminToken, payload) {
  const retryTimes = 3
  const retryDelayMs = 150

  for (let attempt = 1; attempt <= retryTimes; attempt += 1) {
    const result = await apiFetch(apiContext, 'POST', '/admin/governance/notices', {
      token: adminToken,
      data: payload
    })

    if (result.response.ok() && result.body?.code === 200) {
      return extractBusinessData(result.body)
    }

    const canRetry = attempt < retryTimes
      && (result.response.status() >= 500 || result.body?.code === 500)

    if (canRetry) {
      await new Promise((resolve) => setTimeout(resolve, retryDelayMs))
      continue
    }

    expect(result.response.ok(), 'POST /admin/governance/notices 返回了非 2xx 响应').toBeTruthy()
    expect(result.body?.code, 'POST /admin/governance/notices 返回了非 200 业务码').toBe(200)
    return extractBusinessData(result.body)
  }
}

/**
 * 读取商家当前企业资料，便于治理用例绑定企业档案或恢复审核状态。
 */
export async function getMerchantInfoViaApi(apiContext, merchantToken) {
  const body = await expectApiSuccess(apiContext, 'GET', '/merchant/info', {
    token: merchantToken
  })
  return extractBusinessData(body)
}

/**
 * 通过管理员接口审核商家资料，既可触发整改通知，也可用于测试后恢复审核通过。
 */
export async function auditMerchantViaApi(apiContext, adminToken, merchantId, payload) {
  const body = await expectApiSuccess(apiContext, 'PUT', `/admin/merchants/${merchantId}/audit`, {
    token: adminToken,
    data: payload
  })
  return extractBusinessData(body)
}

/**
 * 通过管理员接口更新账号封禁状态，触发 BAN_NOTICE 联动。
 */
export async function updateUserBanViaApi(apiContext, adminToken, userId, payload) {
  const body = await expectApiSuccess(apiContext, 'PUT', `/admin/users/${userId}/ban`, {
    token: adminToken,
    data: payload
  })
  return extractBusinessData(body)
}

/**
 * 读取一个可用的职位分类叶子节点，便于后续商家发岗与 API 造数。
 */
export async function getFirstCategoryLeaf(apiContext) {
  const body = await expectApiSuccess(apiContext, 'GET', '/category/list')
  const tree = Array.isArray(body.data) ? body.data : []
  expect(tree.length, '职位分类不能为空').toBeGreaterThan(0)

  const parent = tree[0]
  const firstChild = Array.isArray(parent.children) && parent.children.length > 0
    ? parent.children[0]
    : null

  return {
    id: firstChild?.id ?? parent.id,
    parentLabel: parent.categoryName,
    childLabel: firstChild?.categoryName ?? '',
    labels: firstChild?.categoryName
      ? [parent.categoryName, firstChild.categoryName]
      : [parent.categoryName]
  }
}

/**
 * 通过管理员视角定位刚创建的职位。
 */
export async function findJobByKeyword(apiContext, adminToken, keyword) {
  const body = await expectApiSuccess(apiContext, 'GET', '/admin/jobs', {
    token: adminToken,
    params: {
      current: 1,
      size: 20,
      keyword
    }
  })

  const records = extractRecords(body)
  return records.find((item) => item.title === keyword)
    || records.find((item) => String(item.title || '').includes(keyword))
    || null
}

/**
 * 通过管理员视角定位商家审核记录。
 */
export async function findMerchantByKeyword(apiContext, adminToken, keyword) {
  const body = await expectApiSuccess(apiContext, 'GET', '/admin/merchants', {
    token: adminToken,
    params: {
      current: 1,
      size: 20,
      keyword
    }
  })

  const records = extractRecords(body)
  return records.find((item) => item.companyName === keyword)
    || records.find((item) => String(item.companyName || '').includes(keyword))
    || null
}

/**
 * 通过管理员视角定位用户。
 */
export async function findUserByKeyword(apiContext, adminToken, keyword) {
  const body = await expectApiSuccess(apiContext, 'GET', '/admin/users', {
    token: adminToken,
    params: {
      current: 1,
      size: 20,
      keyword
    }
  })

  const records = extractRecords(body)
  return records.find((item) => item.username === keyword)
    || records.find((item) => String(item.username || '').includes(keyword))
    || null
}

/**
 * 通过管理员视角定位刚提交的举报。
 */
export async function findReportByReason(apiContext, adminToken, options = {}) {
  const {
    type,
    status = 0,
    reasonKeyword
  } = options

  const body = await expectApiSuccess(apiContext, 'GET', '/admin/reports', {
    token: adminToken,
    params: {
      current: 1,
      size: 20,
      type,
      status
    }
  })

  const records = extractRecords(body)
  return records.find((item) => String(item.reason || '').includes(reasonKeyword))
    || null
}

/**
 * 通过 API 创建一个待审核职位。
 */
export async function createJobViaApi(apiContext, merchantToken, payload) {
  await expectApiSuccess(apiContext, 'POST', '/jobs', {
    token: merchantToken,
    data: payload
  })
}

/**
 * 当商家因资料修改进入待审核或限制发布时，自动恢复到可发岗状态。
 */
export async function ensureMerchantApproved(apiContext, merchantToken, adminToken) {
  const merchantBody = await expectApiSuccess(apiContext, 'GET', '/merchant/info', {
    token: merchantToken
  })
  const merchantInfo = merchantBody.data || {}
  const companyName = merchantInfo.companyName

  expect(companyName, '当前商家缺少企业名称，无法自动恢复审核状态').toBeTruthy()

  const merchantRecord = await findMerchantByKeyword(apiContext, adminToken, companyName)
  expect(merchantRecord, `未能通过企业名 ${companyName} 找到商家审核记录`).toBeTruthy()

  if (Number(merchantRecord.auditStatus) !== 1) {
    await expectApiSuccess(apiContext, 'PUT', `/admin/merchants/${merchantRecord.id}/audit`, {
      token: adminToken,
      data: {
        status: 1,
        reason: 'E2E 自动恢复通过'
      }
    })
  }

  if (Number(merchantRecord.publishStatus) !== 1) {
    await expectApiSuccess(apiContext, 'PUT', `/admin/merchants/${merchantRecord.id}/status`, {
      token: adminToken,
      data: {
        status: 1,
        reason: 'E2E 自动解除限制'
      }
    })
  }
}

/**
 * 通过 API 创建并审核通过职位，供反馈/举报等场景复用。
 */
export async function createApprovedJob(apiContext, options) {
  const {
    merchantToken,
    adminToken,
    categoryId,
    title = buildUniqueValue('E2E_JOB'),
    workLocation = '苏州',
    district = '工业园区',
    minSalary = 12,
    maxSalary = 18,
    headcount = 1,
    experience = '1-3年',
    degree = '本科'
  } = options

  const payload = {
    title,
    categoryId,
    minSalary,
    maxSalary,
    headcount,
    workLocation,
    district,
    experience,
    degree,
    tags: ['E2E', 'Playwright'],
    description: `<p>${title} 职位描述</p>`,
    requirement: `${title} 任职要求`
  }

  await ensureMerchantApproved(apiContext, merchantToken, adminToken)
  await createJobViaApi(apiContext, merchantToken, payload)

  const job = await findJobByKeyword(apiContext, adminToken, title)
  expect(job, `未能通过关键词 ${title} 定位到新建职位`).toBeTruthy()

  await expectApiSuccess(apiContext, 'PUT', `/admin/jobs/${job.id}/audit`, {
    token: adminToken,
    data: {
      status: 1,
      reason: '通过'
    }
  })

  return {
    ...job,
    title
  }
}

/**
 * 通过 API 提交投递，便于后续从商家端验证反馈回显。
 */
export async function submitDeliveryViaApi(apiContext, applicantToken, jobId) {
  await expectApiSuccess(apiContext, 'POST', '/delivery/submit', {
    token: applicantToken,
    data: {
      jobId
    }
  })
}

/**
 * 通过 REST 兜底接口发送聊天消息，便于构造未读场景。
 */
export async function sendChatMessageViaApi(apiContext, senderToken, toUserId, content) {
  const body = await expectApiSuccess(apiContext, 'POST', '/chat/message/send', {
    token: senderToken,
    data: {
      toUserId,
      content
    }
  })
  return extractBusinessData(body)
}

/**
 * 获取当前账号的会话列表。
 */
export async function getChatSessionsViaApi(apiContext, token) {
  const body = await expectApiSuccess(apiContext, 'GET', '/chat/session/list', {
    token
  })
  const data = extractBusinessData(body)
  return Array.isArray(data) ? data : []
}

/**
 * 读取与指定对端用户的消息列表，便于断言实时消息的已读状态。
 */
export async function getChatMessagesViaApi(apiContext, token, peerId, options = {}) {
  const {
    pageNum = 1,
    pageSize = 20
  } = options

  const body = await expectApiSuccess(apiContext, 'GET', '/chat/message/list', {
    token,
    params: {
      peerId,
      pageNum,
      pageSize
    }
  })

  return extractRecords(body)
}

/**
 * 按对端用户 ID 定位会话，便于断言未读数变化。
 */
export function findChatSessionByPeer(sessions, peerId) {
  return (sessions || []).find((item) => Number(item.peerId) === Number(peerId)) || null
}

/**
 * 按消息内容定位目标消息，便于断言实时消息是否已同步为已读。
 */
export function findChatMessageByContent(messages, content) {
  return (messages || []).find((item) => String(item.content || '') === String(content)) || null
}

/**
 * 通过 API 上传附件简历，避免在业务场景里重复走上传前置步骤。
 */
export async function uploadResumeAttachmentViaApi(apiContext, applicantToken, filePayload) {
  const body = await expectApiSuccess(apiContext, 'POST', '/seeker/resume-attachment', {
    token: applicantToken,
    multipart: {
      file: filePayload
    }
  })

  expect(body.data?.fileUrl, '附件简历上传后缺少 fileUrl').toBeTruthy()
  expect(body.data?.fileName, '附件简历上传后缺少 fileName').toBeTruthy()
  return body.data
}
