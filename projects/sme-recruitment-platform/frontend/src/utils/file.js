/*
文件速览：
1. 文件职责：统一处理文件地址补全、敏感附件鉴权透传、文件类型判断与 Blob 下载。
2. 对外入口：formatFileUrl、getFileExtension、getFilePreviewType、canPreviewFile、getFileName、downloadFileByUrl。
3. 关键结构：敏感 uploads 目录识别、query token 兼容预览、带 Authorization 的下载请求。
4. 阅读建议：先看 formatFileUrl，再看 downloadFileByUrl。
*/
const baseUrl = import.meta.env.VITE_APP_BASE_URL || 'http://localhost:8080'
const imageExtSet = new Set(['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'svg'])
const wordExtSet = new Set(['doc', 'docx'])
const sensitiveUploadPrefixes = [
  '/uploads/resumes/',
  '/uploads/reports/',
  '/uploads/license/',
  '/uploads/licenses/',
  '/uploads/qualification/',
  '/uploads/qualifications/'
]

const getStoredToken = () => sessionStorage.getItem('token') || ''

const shouldAppendAuthQuery = (url) => {
  const normalized = String(url || '').trim()
  return sensitiveUploadPrefixes.some(prefix => normalized.includes(prefix))
}

const withTokenQuery = (url) => {
  const token = getStoredToken()
  if (!token) return url
  const target = new URL(url, baseUrl)
  if (!target.searchParams.get('token')) {
    target.searchParams.set('token', token)
  }
  return target.toString()
}

/**
 * @description 将相对路径补全为可访问的绝对地址
 * @param {string} url - 文件路径（支持 http/data/相对路径）
 * @returns {string}
 */
export function formatFileUrl(url) {
  if (!url) return ''
  if (url.startsWith('data:')) return url
  if (url.startsWith('http')) {
    return shouldAppendAuthQuery(url) ? withTokenQuery(url) : url
  }
  const normalizedBase = baseUrl.endsWith('/') ? baseUrl.slice(0, -1) : baseUrl
  const fullUrl = `${normalizedBase}${url}`
  return shouldAppendAuthQuery(url) ? withTokenQuery(fullUrl) : fullUrl
}

/**
 * @description 提取文件后缀（自动忽略 query/hash）
 * @param {string} input - 文件名或文件地址
 * @returns {string}
 */
export function getFileExtension(input) {
  const raw = String(input || '')
  if (!raw) return ''
  const withoutHash = raw.split('#')[0]
  const withoutQuery = withoutHash.split('?')[0]
  const lastSegment = withoutQuery.split('/').pop() || ''
  const dotIndex = lastSegment.lastIndexOf('.')
  if (dotIndex === -1) return ''
  return lastSegment.slice(dotIndex + 1).toLowerCase()
}

/**
 * @description 获取文件展示类型（用于在线预览判断）
 * @param {string} input - 文件名或文件地址
 * @returns {'pdf' | 'image' | 'word' | 'other'}
 */
export function getFilePreviewType(input) {
  const ext = getFileExtension(input)
  if (ext === 'pdf') return 'pdf'
  if (imageExtSet.has(ext)) return 'image'
  if (wordExtSet.has(ext)) return 'word'
  return 'other'
}

/**
 * @description 判断是否支持在线预览（当前支持 PDF/图片）
 * @param {string} input - 文件名或文件地址
 * @returns {boolean}
 */
export function canPreviewFile(input) {
  const type = getFilePreviewType(input)
  return type === 'pdf' || type === 'image'
}

/**
 * @description 从文件地址中推断文件名
 * @param {string} input - 文件名或文件地址
 * @returns {string}
 */
export function getFileName(input) {
  const raw = String(input || '').trim()
  if (!raw) return '附件文件'
  const withoutHash = raw.split('#')[0]
  const withoutQuery = withoutHash.split('?')[0]
  const lastSegment = withoutQuery.split('/').pop() || withoutQuery
  try {
    return decodeURIComponent(lastSegment) || '附件文件'
  } catch (error) {
    return lastSegment || '附件文件'
  }
}

/**
 * @description 通过 Blob 方式触发下载，确保“下载”行为独立于“在线查看”
 * @param {string} url - 文件地址（相对/绝对）
 * @param {string} [fileName] - 下载文件名（可选）
 * @returns {Promise<void>}
 */
export async function downloadFileByUrl(url, fileName = '') {
  const fullUrl = formatFileUrl(url)
  if (!fullUrl) {
    throw new Error('文件地址为空')
  }
  const token = getStoredToken()
  const response = await fetch(fullUrl, {
    headers: token ? { Authorization: token.startsWith('Bearer ') ? token : `Bearer ${token}` } : {}
  })
  if (!response.ok) {
    throw new Error(`下载失败(${response.status})`)
  }
  const blob = await response.blob()
  const objectUrl = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = objectUrl
  anchor.download = fileName || getFileName(fullUrl)
  document.body.appendChild(anchor)
  anchor.click()
  document.body.removeChild(anchor)
  setTimeout(() => URL.revokeObjectURL(objectUrl), 1200)
}
