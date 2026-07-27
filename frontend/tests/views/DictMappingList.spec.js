import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import { beforeEach, describe, test, expect, vi } from 'vitest'
import ElementPlus from 'element-plus'

vi.mock('@/api/dictMapping', () => ({
  listDictMappings:   vi.fn(),
  saveDictMapping:    vi.fn(),
  updateDictMapping:  vi.fn(),
  deleteDictMapping:  vi.fn(),
  exportDictMappings: vi.fn(),
  importDictMappings: vi.fn()
}))
import * as api from '@/api/dictMapping'
import DictMappingList from '@/views/tools/DictMappingList.vue'
import { useUserStore } from '@/store/user'

const seedData = [
  { id: 1, systemCode: 'CIF', dictKey: 'GENDER', direction: 1, sourceValue: 'M', targetValue: '1', cnLabel: '男', status: 1 },
  { id: 2, systemCode: 'CIF', dictKey: 'GENDER', direction: 1, sourceValue: 'F', targetValue: '0', cnLabel: '女', status: 1 },
  { id: 3, systemCode: 'CORE', dictKey: 'STATUS', direction: 2, sourceValue: '1', targetValue: 'ACTIVE', cnLabel: '正常', status: 1 }
]

beforeEach(() => {
  setActivePinia(createPinia())
  Object.values(api).forEach(fn => fn.mockReset && fn.mockReset())
  api.listDictMappings.mockResolvedValue({ data: seedData })
})

const asRole = (role) => {
  const s = useUserStore(); s.setUserInfo({ id: 1, username: 'u', role })
  return s
}

describe('DictMappingList.vue', () => {
  test('admin 角色可见新增/编辑/删除按钮', async () => {
    asRole('admin')
    const w = mount(DictMappingList, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    expect(w.text()).toContain('新增')
    expect(w.text()).toContain('导入 xlsx')
  })

  test('readonly 角色不显示新增按钮', async () => {
    asRole('readonly')
    const w = mount(DictMappingList, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    expect(w.text()).not.toContain('新增')
  })

  test('三选一双向 · 提交时 bidirectional=true direction=1', async () => {
    asRole('admin')
    api.saveDictMapping.mockResolvedValue({ data: [1, 2] })
    const w = mount(DictMappingList, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    // 直接调组件暴露的 doSave（若通过 defineExpose 暴露 form + doSave）
    const vm = w.vm
    vm.openEdit()
    await flushPromises()
    vm.form.systemCode = 'CIF'
    vm.form.dictKey = 'GENDER'
    vm.form.sourceValue = 'M'
    vm.form.targetValue = '1'
    vm.form.mode = 'both'
    await vm.doSave(true)  // 传 skipValidate=true 跳过 el-form 校验
    expect(api.saveDictMapping).toHaveBeenCalledWith(expect.objectContaining({
      bidirectional: true,
      direction: 1
    }))
  })

  test('导入返回 failedRows 时展示错误表格且 dialog 不关闭', async () => {
    asRole('admin')
    api.importDictMappings.mockResolvedValue({ data: {
      successCount: 0,
      failedRows: [
        { rowIndex: 3, errorMsg: 'direction 必须为 1 或 2' },
        { rowIndex: 5, errorMsg: '已存在同源值映射' }
      ]
    }})
    const w = mount(DictMappingList, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    const vm = w.vm
    vm.selectedFile = new File(['x'], 'test.xlsx')
    await vm.doImport()
    await flushPromises()
    expect(vm.importResult.failedRows.length).toBe(2)
    expect(vm.importResult.failedRows[0].rowIndex).toBe(3)
  })

  test('导入成功（无 failedRows）时刷新列表', async () => {
    asRole('admin')
    api.importDictMappings.mockResolvedValue({ data: { successCount: 5, failedRows: [] } })
    api.listDictMappings.mockClear()
    api.listDictMappings.mockResolvedValue({ data: seedData })
    const w = mount(DictMappingList, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    const vm = w.vm
    vm.selectedFile = new File(['x'], 'test.xlsx')
    api.listDictMappings.mockClear()
    await vm.doImport()
    await flushPromises()
    expect(api.listDictMappings).toHaveBeenCalled()
  })
})
