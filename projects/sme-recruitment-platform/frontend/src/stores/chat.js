import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useChatStore = defineStore('chat', () => {
  // 未读消息总数（用于导航栏红点）
  const unreadCount = ref(0)

  const setUnreadCount = (count) => {
    unreadCount.value = Number(count || 0)
  }

  return {
    unreadCount,
    setUnreadCount
  }
})
