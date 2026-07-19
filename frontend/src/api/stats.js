import request from '@/api/request'

export const getStatsSummary = (dimension) =>
  request.get('/stats/summary', { params: { dimension } })

export const getAlerts = (page, pageSize) =>
  request.get('/stats/alerts', { params: { page, pageSize } })

export const updateAlertConfig = (data) =>
  request.put('/stats/alert-config', data)

/** FN-10 导出性能统计列表 Excel */
export const exportPerfStatList = (params) =>
  request.get('/stats/list/export', { params, responseType: 'blob' })
