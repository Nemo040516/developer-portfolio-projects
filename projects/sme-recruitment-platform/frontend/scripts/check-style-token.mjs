import { readdirSync, readFileSync, statSync } from 'node:fs'
import path from 'node:path'
import process from 'node:process'

// P1 样式约束：新增页面禁止引入历史色常量（渐变声明除外）
const FORBIDDEN_COLORS = [
  '#3b82f6',
  '#409eff',
  '#1890ff',
  '#1677ff'
]

const ROOT_DIR = path.resolve(process.cwd(), 'src', 'views')
const VUE_EXT = '.vue'

/**
 * @description 递归收集目录下的 .vue 文件。
 * @param {string} dir
 * @returns {string[]}
 */
function collectVueFiles(dir) {
  const entries = readdirSync(dir)
  const files = []
  for (const entry of entries) {
    const fullPath = path.join(dir, entry)
    const stats = statSync(fullPath)
    if (stats.isDirectory()) {
      files.push(...collectVueFiles(fullPath))
      continue
    }
    if (stats.isFile() && fullPath.endsWith(VUE_EXT)) {
      files.push(fullPath)
    }
  }
  return files
}

/**
 * @description 判断某行是否是渐变声明，渐变中允许保留个性化色值。
 * @param {string} lineText
 * @returns {boolean}
 */
function isGradientLine(lineText) {
  return /gradient\s*\(/i.test(lineText)
}

const violations = []
const files = collectVueFiles(ROOT_DIR)

for (const filePath of files) {
  const content = readFileSync(filePath, 'utf8')
  const lines = content.split(/\r?\n/)
  lines.forEach((line, index) => {
    const lowerLine = line.toLowerCase()
    if (isGradientLine(lowerLine)) return
    for (const color of FORBIDDEN_COLORS) {
      if (lowerLine.includes(color)) {
        violations.push({
          file: path.relative(process.cwd(), filePath).replace(/\\/g, '/'),
          line: index + 1,
          color
        })
      }
    }
  })
}

if (violations.length > 0) {
  console.error('发现不符合样式约束的历史色常量：')
  for (const item of violations) {
    console.error(`- ${item.file}:${item.line} 使用了 ${item.color}`)
  }
  console.error('请改为使用 --ui-* 设计变量；若确需保留，请放入渐变声明并在评审中说明。')
  process.exit(1)
}

console.log(`样式约束检查通过，共扫描 ${files.length} 个视图文件。`)
