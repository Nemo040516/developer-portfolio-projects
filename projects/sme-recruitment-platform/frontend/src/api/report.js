import request from '@/utils/request'

// 提交举报
export function submitReport(data) {
  return request({
    url: '/report/submit',
    method: 'post',
    data
  })
}

// 上传举报证据
export function uploadReportEvidence(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: '/report/evidence',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
