import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus, { ElMessageBox } from 'element-plus'
import TopBar from '@/components/layout/TopBar.vue'
import { useUserStore } from '@/store/user'

// mock 后端登出接口，避免真实网络
vi.mock('@/api/auth', () => ({
  logout: vi.fn().mockResolvedValue(null)
}))

const ADMIN_MENUS = [
  '/dashboard', '/system/user', '/system/log', '/tools/debug'
]
const READONLY_MENUS = [
  '/dashboard', '/interface/list', '/tools/debug'
]

function makeRouter() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div/>' } },
      { path: '/login', component: { template: '<div/>' } },
      { path: '/system/user', meta: { title: '用户权限管理' }, component: { template: '<div/>' } },
      { path: '/:pathMatch(.*)*', component: { template: '<div/>' } }
    ]
  })
  return router
}

async function mountWith(allowedMenus) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const store = useUserStore()
  store.setAllowedMenus(allowedMenus)
  store.setUserInfo({ username: 'tester', role: 'admin' })
  const router = makeRouter()
  router.push('/')
  await router.isReady()
  const wrapper = mount(TopBar, {
    props: { collapsed: false },
    global: { plugins: [pinia, router, ElementPlus] }
  })
  return { wrapper, router }
}

describe('UX-B TopBar 个人信息入口', () => {
  beforeEach(() => localStorage.clear())

  it('admin 角色：点击「个人信息」跳转到 /system/user', async () => {
    const { wrapper, router } = await mountWith(ADMIN_MENUS)
    const pushSpy = vi.spyOn(router, 'push')
    // 直接调用暴露方法，避开 el-dropdown 触发 DOM 层依赖
    await wrapper.vm.handleCommand('profile')
    await flushPromises()
    expect(pushSpy).toHaveBeenCalledWith('/system/user')
  })

  it('admin 角色：canProfile 为 true，下拉菜单渲染个人信息项', async () => {
    const { wrapper } = await mountWith(ADMIN_MENUS)
    expect(wrapper.vm.canProfile).toBe(true)
  })

  it('readonly 角色：canProfile 为 false，下拉菜单不渲染个人信息项', async () => {
    const { wrapper } = await mountWith(READONLY_MENUS)
    expect(wrapper.vm.canProfile).toBe(false)
  })

  it('退出登录逻辑保持：confirm 通过后调用 userStore.logout 并跳 /login', async () => {
    const { wrapper, router } = await mountWith(ADMIN_MENUS)
    const store = useUserStore()
    const logoutSpy = vi.spyOn(store, 'logout')
    const pushSpy = vi.spyOn(router, 'push')
    // stub confirm 直接 resolve
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValueOnce('confirm')
    await wrapper.vm.handleCommand('logout')
    await flushPromises()
    expect(logoutSpy).toHaveBeenCalled()
    expect(pushSpy).toHaveBeenCalledWith('/login')
  })
})
