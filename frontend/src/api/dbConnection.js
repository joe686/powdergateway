import request from '@/api/request'

export function listConnections() {
  return request.get('/db/list')
}

export function saveConnection(data) {
  return request.post('/db/save', data)
}

export function deleteConnection(id) {
  return request.delete(`/db/${id}`)
}

export function testConnection(id) {
  return request.post(`/db/${id}/test`)
}

/** FN-10 导出数据源列表 Excel（密码列脱敏） */
export function exportDbList(keyword) {
  return request.get('/db/list/export', {
    params: { keyword },
    responseType: 'blob'
  })
}
