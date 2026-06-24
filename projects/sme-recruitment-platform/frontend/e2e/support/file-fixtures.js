/**
 * 构建测试用 PDF 文件夹具。
 * 后端当前只校验扩展名和大小，因此这里使用轻量内容即可。
 */
export function buildPdfFixture(name = 'resume-sample.pdf') {
  return {
    name,
    mimeType: 'application/pdf',
    buffer: Buffer.from('%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF\n', 'utf-8')
  }
}

/**
 * 构建测试用 PNG 图片夹具。
 */
export function buildPngFixture(name = 'sample-image.png') {
  return {
    name,
    mimeType: 'image/png',
    buffer: Buffer.from('fake png content for playwright upload', 'utf-8')
  }
}

/**
 * 构建测试用 JPG 图片夹具。
 */
export function buildJpgFixture(name = 'sample-image.jpg') {
  return {
    name,
    mimeType: 'image/jpeg',
    buffer: Buffer.from('fake jpg content for playwright upload', 'utf-8')
  }
}
