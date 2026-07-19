import request from './request'

/**
 * FN-07 导出接口字段清单 Excel（双 Sheet：请求字段 / 响应字段）
 * @param {number} id 接口 ID
 * @returns {Promise<Blob>}
 */
export function exportFieldSchema(id) {
  return request.get(`/interface/${id}/field-schema/export`, {
    responseType: 'blob'
  })
}
