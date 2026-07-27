import { describe, test, expect, vi, beforeEach } from 'vitest'

vi.mock('@/api/request', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn()
  }
}))

import request from '@/api/request'
import * as api from '@/api/dictMapping'

beforeEach(() => {
  Object.values(request).forEach(fn => fn.mockReset && fn.mockReset())
})

describe('dictMapping API', () => {
  test('listDictMappings 调 GET /dict-mapping/list', async () => {
    request.get.mockResolvedValue({ data: [] })
    await api.listDictMappings({ systemCode: 'CIF' })
    expect(request.get).toHaveBeenCalledWith('/dict-mapping/list', { params: { systemCode: 'CIF' } })
  })

  test('saveDictMapping 调 POST /dict-mapping', async () => {
    request.post.mockResolvedValue({ data: [1] })
    await api.saveDictMapping({ systemCode: 'CIF', direction: 1, bidirectional: false })
    expect(request.post).toHaveBeenCalledWith('/dict-mapping',
      { systemCode: 'CIF', direction: 1, bidirectional: false })
  })

  test('deleteDictMapping 调 DELETE /dict-mapping/{id}', async () => {
    request.delete.mockResolvedValue({})
    await api.deleteDictMapping(42)
    expect(request.delete).toHaveBeenCalledWith('/dict-mapping/42')
  })
})
