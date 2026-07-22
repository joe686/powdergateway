/**
 * FB-037 · 认证状态清理回归护栏
 * 覆盖未登录/token 失效两种场景下的 store 与 localStorage 一致性
 */
import { setActivePinia, createPinia } from 'pinia'
import { beforeEach, describe, test, expect } from 'vitest'
import { useUserStore } from '@/store/user'

beforeEach(() => {
  setActivePinia(createPinia())
  localStorage.clear()
})

describe('useUserStore · isLoggedIn 判定', () => {
  test('全新会话（localStorage 空）isLoggedIn 应为 false', () => {
    const s = useUserStore()
    expect(s.isLoggedIn).toBe(false)
    expect(s.token).toBe('')
  })

  test('localStorage 有残留 token 时 store 初始化后 isLoggedIn 为 true', () => {
    localStorage.setItem('token', 'stale-token-abc')
    setActivePinia(createPinia())
    const s = useUserStore()
    expect(s.isLoggedIn).toBe(true)
  })
})

describe('useUserStore.logout() · 完整清理', () => {
  test('logout 必须同时清 token / userInfo / allowedMenus 三个 key（否则残留会误导路由守卫）', () => {
    const s = useUserStore()
    s.setToken('t1')
    s.setUserInfo({ id: 1, username: 'admin', role: 'admin' })
    s.setAllowedMenus(['/dashboard', '/convert/wizard'])

    // 前置断言：三项都写入
    expect(localStorage.getItem('token')).toBe('t1')
    expect(localStorage.getItem('userInfo')).toBeTruthy()
    expect(localStorage.getItem('allowedMenus')).toBeTruthy()

    s.logout()

    // 断言：三项都清除
    expect(s.token).toBe('')
    expect(s.userInfo).toBeNull()
    expect(s.allowedMenus).toEqual([])
    expect(localStorage.getItem('token')).toBeNull()
    expect(localStorage.getItem('userInfo')).toBeNull()
    expect(localStorage.getItem('allowedMenus')).toBeNull()
    expect(s.isLoggedIn).toBe(false)
  })
})
