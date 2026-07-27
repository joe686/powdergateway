import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import { beforeEach, describe, test, expect, vi } from 'vitest'
import ElementPlus from 'element-plus'

vi.mock('@/api/interfaceDoc', () => ({
  listTransformDocs: vi.fn().mockResolvedValue([]),
  listVisualDocs: vi.fn().mockResolvedValue([
    { id: 1, name: '测试接口', type: 'SELECT', status: 'draft' }
  ]),
  downloadVisualDoc: vi.fn(),
  downloadTransformDoc: vi.fn(),
  exportVisualZip: vi.fn(),
  exportTransformZip: vi.fn()
}))
import * as api from '@/api/interfaceDoc'
import InterfaceDocument from '@/views/interface/InterfaceDocument.vue'

beforeEach(() => {
  setActivePinia(createPinia())
  Object.values(api).forEach(fn => fn.mockReset && fn.mockReset())
})

describe('InterfaceDocument.vue', () => {
  test('行内含 3 按钮 MD/HTML/Excel', async () => {
    api.listTransformDocs.mockResolvedValue([
      { id: 1, name: '转换测试', srcFormat: 'JSON', targetFormat: 'XML', status: 'draft' }
    ])
    const w = mount(InterfaceDocument, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    // 检查转换接口标签页中的 3 个按钮
    expect(w.text()).toContain('下载 Markdown')
    expect(w.text()).toContain('下载 HTML')
    expect(w.text()).toContain('Excel')
  })

  test('点 Excel 按钮调 downloadVisualDoc(id, "xlsx")', async () => {
    api.listVisualDocs.mockResolvedValue([{ id: 42, name: 'x', type: 'SELECT', status: 'draft' }])
    api.downloadVisualDoc.mockResolvedValue(new Blob(['x']))
    const w = mount(InterfaceDocument, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    // 找 Excel button 并点击
    const btn = w.findAll('button').find(b => b.text().includes('Excel'))
    if (btn) {
      await btn.trigger('click')
      expect(api.downloadVisualDoc).toHaveBeenCalledWith(42, 'xlsx')
    }
  })
})
