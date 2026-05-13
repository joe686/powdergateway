import request from '@/api/request'

export function listShardConfigs(name, page = 1, size = 100) {
  return request.get('/shard/list', { params: { name, page, size } })
}

export function saveShardConfig(data) {
  return request.post('/shard/save', data)
}

export function deleteShardConfig(id) {
  return request.delete(`/shard/${id}`)
}

export function previewShardRoute(id, params) {
  return request.post(`/shard/${id}/preview`, { params })
}
