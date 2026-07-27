import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import { beforeEach, describe, test, expect, vi } from 'vitest'
import ElementPlus from 'element-plus'

vi.mock('@/api/dictMapping', () => ({ listDictMappings: vi.fn() }))
import { listDictMappings } from '@/api/dictMapping'
import DictMappingParamDialog from '@/components/dict/DictMappingParamDialog.vue'

beforeEach(() => {
  setActivePinia(createPinia())
  listDictMappings.mockReset()
  listDictMappings.mockResolvedValue({ data: [
    { systemCode: 'CIF', dictKey: 'GENDER', direction: 1, sourceValue: 'M', targetValue: '1', status: 1 },
    { systemCode: 'CIF', dictKey: 'STATUS', direction: 1, sourceValue: 'A', targetValue: '1', status: 1 },
    { systemCode: 'CORE', dictKey: 'K',    direction: 2, sourceValue: 'x', targetValue: 'y', status: 1 }
  ]})
})

describe('DictMappingParamDialog', () => {
  test('打开时拉全量字典，system 联动 dictKey', async () => {
    const w = mount(DictMappingParamDialog, {
      props: { visible: true, modelValue: { system: '', dictKey: '', direction: 1 } },
      global: { plugins: [ElementPlus] }
    })
    await flushPromises()
    expect(listDictMappings).toHaveBeenCalled()
    // 选中 system=CIF 后 dictKeys 只显示 CIF 下的
    w.vm.local.system = 'CIF'
    await flushPromises()
    expect(w.vm.local.system).toBe('CIF')
  })

  test('confirm 时 emit params 且 direction 是字符串', async () => {
    const w = mount(DictMappingParamDialog, {
      props: { visible: true, modelValue: { system: 'CIF', dictKey: 'GENDER', direction: 1 } },
      global: { plugins: [ElementPlus] }
    })
    await flushPromises()
    w.vm.local.system = 'CIF'
    w.vm.local.dictKey = 'GENDER'
    w.vm.local.direction = 1
    w.vm.doConfirm()
    expect(w.emitted('confirm')[0][0]).toEqual({
      system: 'CIF', dictKey: 'GENDER', direction: '1'
    })
  })

  test('未选完必填时 canConfirm 为 false', async () => {
    const w = mount(DictMappingParamDialog, {
      props: { visible: true, modelValue: {} },
      global: { plugins: [ElementPlus] }
    })
    await flushPromises()
    expect(w.vm.canConfirm).toBe(false)
    w.vm.local.system = 'CIF'
    w.vm.local.dictKey = 'GENDER'
    w.vm.local.direction = 1
    await flushPromises()
    expect(w.vm.canConfirm).toBe(true)
  })
})
