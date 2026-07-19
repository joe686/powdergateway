import { setActivePinia, createPinia } from 'pinia'
import { mount } from '@vue/test-utils'
import { beforeEach, test, expect, vi } from 'vitest'
import { defineComponent, ref } from 'vue'
import { useTransformWizardStore } from '@/store/transformWizard'

// Mock API modules
vi.mock('@/api/channel', () => ({
  listChannels: vi.fn().mockResolvedValue([]),
  saveChannel: vi.fn().mockResolvedValue(1)
}))
vi.mock('@/api/portRoute', () => ({
  savePortRoute: vi.fn().mockResolvedValue(99)
}))
vi.mock('@/api/functionCode', () => ({
  checkFunctionCodeExists: vi.fn().mockResolvedValue(false)
}))
vi.mock('@/api/template', () => ({
  listTemplates: vi.fn().mockResolvedValue({ records: [], total: 0 }),
  saveTemplate: vi.fn().mockResolvedValue(1)
}))
vi.mock('@/api/request', () => ({
  default: { post: vi.fn().mockResolvedValue('{}') }
}))

let store

beforeEach(() => {
  setActivePinia(createPinia())
  localStorage.clear()
  store = useTransformWizardStore()
})

// Helper: mount component and get expose
async function mountSteps() {
  const TransformInterfaceSteps = (await import('@/views/convert/TransformInterfaceSteps.vue')).default
  const wrapper = mount(TransformInterfaceSteps, {
    props: { isActive: () => true }
  })
  return wrapper
}

test('validateStep(system) 来源/目标渠道均非空且格式非空_返回true', async () => {
  const wrapper = await mountSteps()
  store.sourceChannelCode = 'SRC'
  store.targetChannelCode = 'TGT'
  store.sourceFormat = 'JSON'
  store.targetFormat = 'JSON'
  const result = wrapper.vm.validateStep('system')
  expect(result).toBe(true)
})

test('validateStep(system) 缺来源渠道_返回字符串提示', async () => {
  const wrapper = await mountSteps()
  store.sourceChannelCode = ''
  store.targetChannelCode = 'TGT'
  const result = wrapper.vm.validateStep('system')
  expect(typeof result).toBe('string')
  expect(result.length).toBeGreaterThan(0)
})

test('validateStep(function) functionCode 5-64位英数下划线_返回true', async () => {
  const wrapper = await mountSteps()
  store.functionCode = 'VALID_FC_01'
  const result = wrapper.vm.validateStep('function')
  expect(result).toBe(true)
})

test('validateStep(function) functionCode 含中文_返回错误字符串', async () => {
  const wrapper = await mountSteps()
  store.functionCode = '含中文功能号'
  const result = wrapper.vm.validateStep('function')
  expect(typeof result).toBe('string')
})

test('validateStep(port) portAddress 非 http/https 前缀_返回错误', async () => {
  const wrapper = await mountSteps()
  store.portAddress = 'ftp://bad-address'
  store.portMethod = 'POST'
  store.timeout = 3000
  store.retryCount = 3
  const result = wrapper.vm.validateStep('port')
  expect(typeof result).toBe('string')
})

test('validateStep(route) headerConfig 结构合法_返回true', async () => {
  const wrapper = await mountSteps()
  store.headerConfig = { contentType: 'application/json', charset: 'UTF-8', requestHeaders: {}, responseHeaders: {} }
  const result = wrapper.vm.validateStep('route')
  expect(result).toBe(true)
})

test('buildPortRoutePayload 包含 functionCode/functionName/headerConfig 各字段', async () => {
  const wrapper = await mountSteps()
  store.sourceChannelCode = 'SRC'
  store.portAddress = 'http://localhost:9999/mock'
  store.portMethod = 'POST'
  store.timeout = 3000
  store.retryCount = 3
  store.functionCode = 'TEST_FC'
  store.functionName = '测试功能'
  store.headerConfig = { contentType: 'application/json', charset: 'UTF-8', requestHeaders: {}, responseHeaders: {} }
  const payload = wrapper.vm.buildPortRoutePayload()
  expect(payload.functionCode).toBe('TEST_FC')
  expect(payload.functionName).toBe('测试功能')
  expect(payload.headerConfig).toBeTruthy()
})
