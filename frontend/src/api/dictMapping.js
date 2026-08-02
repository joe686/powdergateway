import request from '@/api/request'

/**
 * FN-12 · 字典映射管理 API（v0.2.5 CR-004 加 scope 参数）
 *
 * scope 语义:
 *   1 = 接口转换 M1 侧(/transform/dict 菜单)
 *   2 = 可视化接口 M2 侧(/interface/dict 菜单)
 *   3 = 通用共享
 */

export const listDictMappings = (params) => request.get('/dict-mapping/list', { params })
export const listSystems = (scope) => request.get('/dict-mapping/systems', { params: scope != null ? { scope } : {} })
export const getDictMapping = (id) => request.get(`/dict-mapping/${id}`)
export const saveDictMapping = (data) => request.post('/dict-mapping', data)
export const saveDictMappingBatch = (data) => request.post('/dict-mapping/batch', data)
export const updateDictMapping = (id, data) => request.put(`/dict-mapping/${id}`, data)
export const deleteDictMapping = (id) => request.delete(`/dict-mapping/${id}`)
export const importDictMappings = (fd, scope) => request.post('/dict-mapping/import', fd,
  { headers: { 'Content-Type': 'multipart/form-data' }, params: scope != null ? { scope } : {} })
export const exportDictMappings = (params) => request.get('/dict-mapping/export',
  { params, responseType: 'blob' })
export const lookupDictMapping = (params) => request.post('/dict-mapping/lookup', null, { params })
