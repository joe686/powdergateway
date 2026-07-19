import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'
import SideMenu from '@/components/layout/SideMenu.vue'
import { useUserStore } from '@/store/user'

const ADMIN_MENUS = [
  '/dashboard',
  '/convert/template', '/convert/channel',
  '/convert/field-mapping', '/convert/field-process',
  '/convert/port-route', '/convert/format',
  '/interface/db', '/interface/table', '/interface/wizard', '/interface/dev',
  '/interface/insert', '/interface/update', '/interface/delete',
  '/interface/list', '/interface/shard', '/interface/formula', '/interface/cache',
  '/system/log', '/system/stats', '/system/user', '/system/config',
  '/tools/debug', '/tools/swagger'
]
const USER_MENUS = [
  '/dashboard',
  '/convert/template', '/convert/channel',
  '/convert/field-mapping', '/convert/field-process',
  '/convert/port-route', '/convert/format',
  '/interface/db', '/interface/table', '/interface/wizard', '/interface/dev',
  '/interface/insert', '/interface/update',
  '/interface/list', '/interface/formula', '/interface/cache',
  '/system/log', '/system/stats',
  '/tools/debug', '/tools/swagger'
]
const READONLY_MENUS = [
  '/dashboard',
  '/interface/list', '/interface/cache',
  '/tools/debug', '/tools/swagger'
]

function makeRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/:pathMatch(.*)*', component: { template: '<div/>' } }]
  })
}

async function mountWith(allowedMenus) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const store = useUserStore()
  store.setAllowedMenus(allowedMenus)
  const router = makeRouter()
  router.push('/dashboard')
  await router.isReady()
  return mount(SideMenu, {
    global: { plugins: [pinia, router, ElementPlus] }
  })
}

describe('UX-B SideMenu 结构与顺序', () => {
  beforeEach(() => localStorage.clear())

  it('admin 角色：接口转换分组含 3 个小节标题（基础配置/转换规则/发布测试）', async () => {
    const wrapper = await mountWith(ADMIN_MENUS)
    const groupTitles = wrapper.findAll('.el-menu-item-group__title').map(el => el.text().trim())
    expect(groupTitles).toContain('基础配置')
    expect(groupTitles).toContain('转换规则')
    expect(groupTitles).toContain('发布测试')
  })

  it('admin 角色：接口转换分组第一个菜单项是「转换模板管理」，最后是「报文格式转换」', async () => {
    const wrapper = await mountWith(ADMIN_MENUS)
    const convertItems = wrapper.findAll('[data-ux-b-convert-path]')
    const paths = convertItems.map(el => el.attributes('data-ux-b-convert-path'))
    expect(paths[0]).toBe('/convert/template')
    expect(paths[paths.length - 1]).toBe('/convert/format')
  })

  it('admin 角色：基础配置小节包含 [/convert/template, /convert/channel]，顺序固定', async () => {
    const wrapper = await mountWith(ADMIN_MENUS)
    const g = wrapper.find('[data-ux-b-group="base"]')
    expect(g.exists()).toBe(true)
    const paths = g.findAll('[data-ux-b-convert-path]').map(e => e.attributes('data-ux-b-convert-path'))
    expect(paths).toEqual(['/convert/template', '/convert/channel'])
  })

  it('admin 角色：转换规则小节包含 [/convert/field-mapping, /convert/field-process]，顺序固定', async () => {
    const wrapper = await mountWith(ADMIN_MENUS)
    const g = wrapper.find('[data-ux-b-group="rules"]')
    expect(g.exists()).toBe(true)
    const paths = g.findAll('[data-ux-b-convert-path]').map(e => e.attributes('data-ux-b-convert-path'))
    expect(paths).toEqual(['/convert/field-mapping', '/convert/field-process'])
  })

  it('admin 角色：发布测试小节包含 [/convert/port-route, /convert/format]，顺序固定', async () => {
    const wrapper = await mountWith(ADMIN_MENUS)
    const g = wrapper.find('[data-ux-b-group="publish"]')
    expect(g.exists()).toBe(true)
    const paths = g.findAll('[data-ux-b-convert-path]').map(e => e.attributes('data-ux-b-convert-path'))
    expect(paths).toEqual(['/convert/port-route', '/convert/format'])
  })

  it('readonly 角色：整个接口转换分组不渲染', async () => {
    const wrapper = await mountWith(READONLY_MENUS)
    expect(wrapper.find('[data-ux-b-submenu="convert"]').exists()).toBe(false)
  })

  it('user 角色：接口转换分组渲染，3 个小节全在', async () => {
    const wrapper = await mountWith(USER_MENUS)
    expect(wrapper.find('[data-ux-b-submenu="convert"]').exists()).toBe(true)
    expect(wrapper.find('[data-ux-b-group="base"]').exists()).toBe(true)
    expect(wrapper.find('[data-ux-b-group="rules"]').exists()).toBe(true)
    expect(wrapper.find('[data-ux-b-group="publish"]').exists()).toBe(true)
  })
})
