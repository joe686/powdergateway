import request from '@/api/request'

/**
 * FN-12 · 字典映射管理 API
 */

export const listDictMappings = (params) => request.get('/dict-mapping/list', { params })
export const listSystems = () => request.get('/dict-mapping/systems')
export const getDictMapping = (id) => request.get(`/dict-mapping/${id}`)
export const saveDictMapping = (data) => request.post('/dict-mapping', data)
export const updateDictMapping = (id, data) => request.put(`/dict-mapping/${id}`, data)
export const deleteDictMapping = (id) => request.delete(`/dict-mapping/${id}`)
export const importDictMappings = (fd) => request.post('/dict-mapping/import', fd,
  { headers: { 'Content-Type': 'multipart/form-data' } })
export const exportDictMappings = (params) => request.get('/dict-mapping/export',
  { params, responseType: 'blob' })
export const lookupDictMapping = (params) => request.post('/dict-mapping/lookup', null, { params })
