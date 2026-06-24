/**
 * @description 通用格式化工具：统一时间、数字、单位展示，避免页面各写各的导致样式与文案不一致。
 */

/**
 * @description 统一解析时间输入，兼容后端常见字符串（如 2026-02-16T10:20:00）。
 * @param {string|number|Date|null|undefined} value
 * @returns {Date|null}
 */
function toDate(value) {
  if (!value) return null
  if (value instanceof Date) return Number.isNaN(value.getTime()) ? null : value
  const text = String(value).trim()
  if (!text) return null
  const normalized = text.replace(/-/g, '/').replace('T', ' ')
  const parsed = new Date(normalized)
  return Number.isNaN(parsed.getTime()) ? null : parsed
}

/**
 * @description 将数字转为两位补零字符串。
 * @param {number} num
 * @returns {string}
 */
function pad2(num) {
  return String(num).padStart(2, '0')
}

/**
 * @description 格式化为中文长日期（示例：2026年2月16日 星期一）。
 * @param {string|number|Date|null|undefined} value
 * @returns {string}
 */
export function formatDateLongCN(value) {
  const date = toDate(value)
  if (!date) return ''
  return date.toLocaleDateString('zh-CN', {
    weekday: 'long',
    year: 'numeric',
    month: 'long',
    day: 'numeric'
  })
}

/**
 * @description 格式化为时分（示例：09:30）。
 * @param {string|number|Date|null|undefined} value
 * @returns {string}
 */
export function formatTimeHM(value) {
  const date = toDate(value)
  if (!date) return ''
  return `${pad2(date.getHours())}:${pad2(date.getMinutes())}`
}

/**
 * @description 格式化为月/日 + 时分（示例：2/16 09:30）。
 * @param {string|number|Date|null|undefined} value
 * @param {string} fallback
 * @returns {string}
 */
export function formatMonthDayTime(value, fallback = '') {
  const date = toDate(value)
  if (!date) return fallback
  return `${date.getMonth() + 1}/${date.getDate()} ${formatTimeHM(date)}`
}

/**
 * @description 格式化为完整日期时间（示例：2026-02-16 09:30）。
 * @param {string|number|Date|null|undefined} value
 * @param {string} fallback
 * @returns {string}
 */
export function formatDateTime(value, fallback = '-') {
  const date = toDate(value)
  if (!date) return fallback
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())} ${formatTimeHM(date)}`
}

/**
 * @description 格式化为日期（示例：2026-02-16）。
 * @param {string|number|Date|null|undefined} value
 * @param {string} fallback
 * @returns {string}
 */
export function formatDateYMD(value, fallback = '') {
  const date = toDate(value)
  if (!date) return fallback
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`
}

/**
 * @description 格式化薪资区间（单位 K），用于职位卡片等统一展示。
 * @param {string|number|null|undefined} min
 * @param {string|number|null|undefined} max
 * @param {string} fallback
 * @returns {string}
 */
export function formatSalaryK(min, max, fallback = '面议') {
  const minNum = Number(min)
  const maxNum = Number(max)
  if (!Number.isFinite(minNum) || !Number.isFinite(maxNum) || minNum <= 0 || maxNum <= 0) {
    return fallback
  }
  return `${minNum}-${maxNum}K`
}

/**
 * @description 格式化百分比，默认保留 0 位小数。
 * @param {string|number|null|undefined} value
 * @param {number} fractionDigits
 * @returns {string}
 */
export function formatPercent(value, fractionDigits = 0) {
  const num = Number(value)
  if (!Number.isFinite(num)) return '0%'
  return `${num.toFixed(fractionDigits)}%`
}
