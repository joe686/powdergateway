import request from '@/api/request'

export function getTableStructure(dbId) {
  return request.get(`/db/${dbId}/tables`)
}

export function refreshTableCache(dbId) {
  return request.delete(`/db/${dbId}/tables/cache`)
}

export function exportTableExcel(dbId) {
  return request.get(`/db/${dbId}/tables/export`, { responseType: 'blob' })
}
