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

// ============================================================
// FN-11 Task 5 · Excel / Markdown 导入导出（新增端点）
// ============================================================

/**
 * 按 ID 列表导出 Excel
 * @param {number[]} ids
 * @param {'interface'|'template'} type
 */
export function exportExcel(ids, type = 'interface') {
  return request.post('/config/export/excel', { ids, type }, { responseType: 'blob' })
}

export function exportMarkdown(ids, type = 'interface') {
  return request.post('/config/export/markdown', { ids, type }, { responseType: 'blob' })
}

/**
 * 上传多个 Excel 文件导入（同类型：全接口 or 全模板；按文件名前缀 TEMPLATE_ 判断）
 * @param {File[]} files
 * @param {string} strategy - OVERWRITE | SKIP | ASK
 */
export function importExcel(files, strategy = 'SKIP') {
  const formData = new FormData()
  files.forEach(f => formData.append('files', f))
  formData.append('strategy', strategy)
  return request.post('/config/import/excel', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/** 预览多个 Excel 文件的导入结果（不落库） */
export function previewExcelImport(files) {
  const formData = new FormData()
  files.forEach(f => formData.append('files', f))
  return request.post('/config/import/preview', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
