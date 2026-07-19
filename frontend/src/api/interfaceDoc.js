import request from '@/api/request'

/**
 * FN-09 接口文档 API
 */

/** 获取转换接口摘要列表 */
export function listTransformDocs() {
  return request.get('/doc/transform/list')
}

/** 获取可视化接口摘要列表 */
export function listVisualDocs() {
  return request.get('/doc/visual/list')
}

/** 下载单份转换接口文档（md 或 html） */
export function downloadTransformDoc(id, format = 'md') {
  return request.get(`/doc/transform/${id}`, {
    params: { format },
    responseType: 'blob'
  })
}

/** 下载单份可视化接口文档（md 或 html） */
export function downloadVisualDoc(id, format = 'md') {
  return request.get(`/doc/visual/${id}`, {
    params: { format },
    responseType: 'blob'
  })
}

/** 全量导出转换接口文档 zip */
export function exportTransformZip() {
  return request.get('/doc/transform/export', { responseType: 'blob' })
}

/** 全量导出可视化接口文档 zip */
export function exportVisualZip() {
  return request.get('/doc/visual/export', { responseType: 'blob' })
}
