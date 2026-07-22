/**
 * FB-037 · request.js 401 拦截完整性
 * 场景：token 存在但服务端已失效，任意请求返 401 时应完整清 store + 跳 /login
 * 修复前 bug：只清 localStorage.token，未清 userInfo/allowedMenus，pinia store 内存态残留
 */
import { setActivePinia, createPinia } from 'pinia'
import { beforeEach, describe, test, expect, vi } from 'vitest'
import { useUserStore } from '@/store/user'

// mock element-plus ElMessage 避免真实弹窗
vi.mock('element-plus', () => ({
  ElMessage: {
    error: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
    info: vi.fn()
  }
}))

// mock router，捕获跳转调用
const routerPushMock = vi.fn()
vi.mock('@/router', () => ({
  default: {
    push: (...args) => {
      routerPushMock(...args)
      return Promise.resolve()
    }
  }
}))

beforeEach(() => {
  setActivePinia(createPinia())
  localStorage.clear()
  routerPushMock.mockClear()
})

describe('request.js · 401 拦截应完整登出', () => {
  test('业务码 401（HTTP 200 + body code=401，Sa-Token 场景）应走完整登出', async () => {
    const s = useUserStore()
    s.setToken('stale-token')
    s.setUserInfo({ id: 1, username: 'admin', role: 'admin' })
    s.setAllowedMenus(['/dashboard'])

    const { default: request } = await import('@/api/request')
    const handlers = request.interceptors.response.handlers
    const successHandler = handlers[handlers.length - 1].fulfilled

    // 模拟后端返回 HTTP 200 + Result{code:401, message:'未登录'}
    const fakeResponse = {
      config: {},
      data: { code: 401, message: '未登录或登录已过期，请重新登录', data: null }
    }
    await successHandler(fakeResponse).catch(() => {})

    expect(s.token).toBe('')
    expect(s.userInfo).toBeNull()
    expect(s.allowedMenus).toEqual([])
    expect(localStorage.getItem('token')).toBeNull()
    expect(localStorage.getItem('userInfo')).toBeNull()
    expect(localStorage.getItem('allowedMenus')).toBeNull()
    expect(routerPushMock).toHaveBeenCalledWith('/login')
  })

  test('HTTP 401（非 Sa-Token 场景，如网关）也走完整登出', async () => {
    const s = useUserStore()
    s.setToken('stale-token')
    s.setUserInfo({ id: 1, username: 'admin', role: 'admin' })
    s.setAllowedMenus(['/dashboard'])

    // 动态引入以确保 mock 已生效
    const { default: request } = await import('@/api/request')
    const interceptors = request.interceptors.response.handlers
    const errorHandler = interceptors[interceptors.length - 1].rejected

    const error401 = { response: { status: 401 }, message: 'Unauthorized' }
    await errorHandler(error401).catch(() => {})

    expect(s.token).toBe('')
    expect(s.userInfo).toBeNull()
    expect(s.allowedMenus).toEqual([])
    expect(localStorage.getItem('token')).toBeNull()
    expect(localStorage.getItem('userInfo')).toBeNull()
    expect(localStorage.getItem('allowedMenus')).toBeNull()
    expect(routerPushMock).toHaveBeenCalledWith('/login')
  })
})
