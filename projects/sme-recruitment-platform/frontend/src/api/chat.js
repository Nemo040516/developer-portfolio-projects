import request from '@/utils/request'

// 获取会话列表
export const getChatSessionList = () => {
  return request.get('/chat/session/list')
}

// 获取消息列表（分页）
export const getChatMessageList = (params) => {
  return request.get('/chat/message/list', { params })
}

// 标记会话消息为已读
export const markChatRead = (data) => {
  return request.put('/chat/message/read', data)
}

// 更新会话岗位信息
export const updateChatSessionJob = (data) => {
  return request.put('/chat/session/job', data)
}

// REST 兜底发送消息
export const sendChatMessage = (data) => {
  return request.post('/chat/message/send', data)
}
