import request from '@/api/request'

/**
 * 查询所有渠道配置列表
 * @returns {Promise<Array>}
 */
export function listChannels() {
  return request.get('/channel/list')
}

/**
 * 按 id 查询渠道配置
 * @param {number} id
 */
export function getChannelById(id) {
  return request.get(`/channel/${id}`)
}

/**
 * 新增或更新渠道配置
 * @param {object} data - { id?, channelCode, channelName, identifyField, templateId }
 * @returns {Promise<number>} 渠道配置 id
 */
export function saveChannel(data) {
  return request.post('/channel/save', data)
}

/**
 * 删除渠道配置（逻辑删除）
 * @param {number} id
 */
export function deleteChannel(id) {
  return request.delete(`/channel/${id}`)
}

/**
 * 运行时渠道自动路由匹配
 * @param {string} message - 源报文字符串
 * @param {string} format  - 报文格式（JSON / XML / CSV / FORM_DATA）
 * @returns {Promise<number|null>} 匹配的 templateId，未命中返回 null
 */
export function matchChannel(message, format) {
  return request.post('/channel/match', null, { params: { message, format } })
}

/** FN-10 导出渠道配置列表 Excel */
export function exportChannelList(keyword) {
  return request.get('/channel/list/export', {
    params: { keyword },
    responseType: 'blob'
  })
}
