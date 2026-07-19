import { setActivePinia, createPinia } from 'pinia'
import { mount } from '@vue/test-utils'
import { beforeEach, test, expect, vi } from 'vitest'
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
  saveTemplate: vi.fn().mockResolvedValue(42)
}))
vi.mock('@/api/request', () => ({
  default: { post: vi.fn().mockResolvedValue('{"result":"ok"}') }
}))
vi.mock('vue-router', () => ({
  useRouter: vi.fn(() => ({ push: vi.fn() }))
}))

let store

beforeEach(() => {
  setActivePinia(createPinia())
  localStorage.clear()
  store = useTransformWizardStore()
})

async function mountSteps() {
  const TransformInterfaceSteps = (await import('@/views/convert/TransformInterfaceSteps.vue')).default
  const wrapper = mount(TransformInterfaceSteps, {
    props: { isActive: () => true }
  })
  return wrapper
}

test('Step 5 templateMode=EXISTING 且 savedTemplateId 为空_validateStep 返回错误', async () => {
  const wrapper = await mountSteps()
  store.templateMode = 'EXISTING'
  store.savedTemplateId = null
  const result = wrapper.vm.validateStep('template')
  expect(typeof result).toBe('string')
  expect(result.length).toBeGreaterThan(0)
})

test('Step 5 templateMode=NEW 且 newTemplateDraft.name 非空_validateStep 返回true', async () => {
  const wrapper = await mountSteps()
  store.templateMode = 'NEW'
  store.newTemplateDraft = { name: 'TestTpl', srcFmt: '', targetFmt: '', mappingRules: [], processRules: [] }
  const result = wrapper.vm.validateStep('template')
  expect(result).toBe(true)
})

test('Step 6 未执行过测试_validateStep 返回错误', async () => {
  const wrapper = await mountSteps()
  store.testOutput = ''
  store.testError = ''
  const result = wrapper.vm.validateStep('test')
  expect(typeof result).toBe('string')
})

test('Step 6 执行过_validateStep 返回 true（testOutput 非空）', async () => {
  const wrapper = await mountSteps()
  store.testOutput = '{"result":"ok"}'
  const result = wrapper.vm.validateStep('test')
  expect(result).toBe(true)
})

test('onSubmit 调 store.reset 且执行后存储被清空', async () => {
  const wrapper = await mountSteps()
  store.functionCode = 'TEST_FC'
  store.templateMode = 'EXISTING'
  store.savedTemplateId = 5
  await wrapper.vm.onSubmit()
  // After reset, functionCode should be cleared
  expect(store.functionCode).toBe('')
})
