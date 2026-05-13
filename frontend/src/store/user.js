import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref(JSON.parse(localStorage.getItem('userInfo') || 'null'))
  const allowedMenus = ref(JSON.parse(localStorage.getItem('allowedMenus') || '[]'))

  const isLoggedIn = computed(() => !!token.value)
  const username = computed(() => userInfo.value && userInfo.value.username ? userInfo.value.username : '')
  const role = computed(() => userInfo.value && userInfo.value.role ? userInfo.value.role : '')

  function setToken(newToken) {
    token.value = newToken
    localStorage.setItem('token', newToken)
  }

  function setUserInfo(info) {
    userInfo.value = info
    localStorage.setItem('userInfo', JSON.stringify(info))
  }

  function setAllowedMenus(menus) {
    allowedMenus.value = menus || []
    localStorage.setItem('allowedMenus', JSON.stringify(allowedMenus.value))
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    allowedMenus.value = []
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
    localStorage.removeItem('allowedMenus')
  }

  return {
    token,
    userInfo,
    allowedMenus,
    isLoggedIn,
    username,
    role,
    setToken,
    setUserInfo,
    setAllowedMenus,
    logout
  }
})
