import request from '@/api/request'

/**
 * 保存/更新转换模板（含映射规则）
 * @param {object} data - { id?, name, srcFormat, targetFormat, mappingRules[] }
 * @returns {Promise<number>} 新记录 id
 */
export function saveTemplate(data) {
  return request.post('/template/save', data)
}

/**
 * 分页查询模板列表（M1-5）
 * @param {object} params - { page, size, keyword?, latestOnly? }
 * @returns {Promise<{records, total, pages, current}>}
 */
export function listTemplates(params) {
  return request.get('/template/list', { params })
}

/**
 * 按 ID 查询模板
 * @param {number} id
 */
export function getTemplateById(id) {
  return request.get(`/template/${id}`)
}

/**
 * 逻辑删除模板（M1-5）
 * @param {number} id
 */
export function deleteTemplate(id) {
  return request.delete(`/template/${id}`)
}

/**
 * 复制模板（M1-5）：name 加 _copy 后缀
 * @param {number} id - 源模板 id
 * @returns {Promise<number>} 新模板 id
 */
export function copyTemplate(id) {
  return request.post(`/template/${id}/copy`)
}

/**
 * 映射预览
 * @param {number} id - 模板 id
 * @param {object} data - { message, format }
 * @returns {Promise<object>} 映射后的字段 Map
 */
export function previewMapping(id, data) {
  return request.post(`/template/${id}/preview`, data)
}

/**
 * 按功能号查询模板列表（UX-D）
 * @param {string} functionCode 功能号
 * @returns {Promise<{records, total}>}
 */
export function listTemplatesByFunctionCode(functionCode) {
  return request.get('/template/list', { params: { page: 1, size: 100, functionCode } })
}

/** FN-10 导出转换模板列表 Excel */
export function exportTemplateList(keyword) {
  return request.get('/template/list/export', {
    params: { keyword },
    responseType: 'blob'
  })
}
