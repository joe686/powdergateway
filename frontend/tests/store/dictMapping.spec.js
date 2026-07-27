import { setActivePinia, createPinia } from 'pinia'
import { beforeEach, describe, test, expect, vi } from 'vitest'

vi.mock('@/api/dictMapping', () => ({
  listDictMappings: vi.fn()
}))
import { listDictMappings } from '@/api/dictMapping'
import { useDictMappingStore } from '@/stores/dictMapping'

beforeEach(() => {
  setActivePinia(createPinia())
  listDictMappings.mockReset()
})

describe('useDictMappingStore', () => {
  test('ensureLoaded 首次调用会拉数据并 group', async () => {
    listDictMappings.mockResolvedValue({ data: [
      { systemCode: 'CIF', dictKey: 'GENDER', direction: 1, sourceValue: 'M', targetValue: '1', status: 1 },
      { systemCode: 'CIF', dictKey: 'GENDER', direction: 1, sourceValue: 'F', targetValue: '0', status: 1 },
      { systemCode: 'CORE', dictKey: 'STATUS', direction: 2, sourceValue: '1', targetValue: 'ACTIVE', status: 1 }
    ]})
    const s = useDictMappingStore()
    await s.ensureLoaded()
    expect(s.systems).toEqual(['CIF', 'CORE'])
    expect(s.dictKeysOf('CIF')).toEqual(['GENDER'])
  })

  test('status=0 条目不进 groupedData', async () => {
    listDictMappings.mockResolvedValue({ data: [
      { systemCode: 'X', dictKey: 'K', direction: 1, sourceValue: 'a', targetValue: 'b', status: 0 }
    ]})
    const s = useDictMappingStore()
    await s.ensureLoaded()
    expect(s.systems).toEqual([])
  })

  test('invalidate 后 ensureLoaded 会重新拉', async () => {
    listDictMappings.mockResolvedValue({ data: [{ systemCode: 'A', dictKey: 'K', direction: 1, sourceValue: 'x', targetValue: 'y', status: 1 }] })
    const s = useDictMappingStore()
    await s.ensureLoaded()
    listDictMappings.mockClear()
    await s.ensureLoaded()  // TTL 内不重复拉
    expect(listDictMappings).not.toHaveBeenCalled()
    s.invalidate()
    await s.ensureLoaded()
    expect(listDictMappings).toHaveBeenCalledTimes(1)
  })
})
