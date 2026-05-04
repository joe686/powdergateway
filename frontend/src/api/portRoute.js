import request from '@/api/request'

/**
 * 分页查询端口路由列表
 * @param {object} params - { page, size, channelCode? }
 * @returns {Promise<{records, total, pages, current}>}
 */
export function listPortRoutes(params) {
  return request.get('/port-route/list', { params })
}

/**
 * 新增或更新端口路由
 * @param {object} data - { id?, channelCode, portAddress, portMethod, timeout, retryCount, requestTemplateId?, responseTemplateId? }
 * @returns {Promise<number>} 路由 id
 */
export function savePortRoute(data) {
  return request.post('/port-route/save', data)
}

/**
 * 删除端口路由（逻辑删除）
 * @param {number} id
 */
export function deletePortRoute(id) {
  return request.delete(`/port-route/${id}`)
}

/**
 * 测试目标端口连通性（HTTP GET 探活）
 * @param {number} id - 路由 id
 * @returns {Promise<{success: boolean, httpStatus: number, message: string}>}
 */
export function testPortConnectivity(id) {
  return request.post(`/port-route/${id}/test`)
}
