import request from '@/api/request'

/**
 * FN-11 配置导入/导出 API
 */

/** 全量导出配置 zip */
export function exportConfig() {
  return request.get('/config/export', { responseType: 'blob' })
}

/**
 * 上传 zip 导入配置
 * @param {File} file - 上传的 zip 文件
 * @param {string} strategy - 冲突策略：OVERWRITE | SKIP | ASK
 */
export function importConfig(file, strategy = 'SKIP') {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('strategy', strategy)
  return request.post('/config/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
