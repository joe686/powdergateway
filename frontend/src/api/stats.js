import request from '@/api/request'

export const getStatsSummary = (dimension) =>
  request.get('/stats/summary', { params: { dimension } })

export const getAlerts = (page, pageSize) =>
  request.get('/stats/alerts', { params: { page, pageSize } })

export const updateAlertConfig = (data) =>
  request.put('/stats/alert-config', data)
