import request from '@/api/request'

/** REG-1 · 注册中心配置 API */

export function listRegistries() {
  return request.get('/registry/list')
}

export function saveRegistry(data) {
  return request.post('/registry/save', data)
}

export function deleteRegistry(id) {
  return request.delete(`/registry/${id}`)
}

/** 测试指定注册中心连通性 */
export function testRegistryConnection(id) {
  return request.post(`/registry/${id}/test`)
}

/** 跨所有已启用注册中心聚合发现指定服务名的实例 */
export function discoverServicePreview(serviceName) {
  return request.get('/registry/discover-preview', { params: { serviceName } })
}

/** 手动触发本机重新注册（SystemConfig「重新注册」按钮） */
export function reregisterSelf() {
  return request.post('/registry/reregister-self')
}
