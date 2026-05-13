import request from '@/api/request'

export function listLogs(params) {
  return request.get('/log/list', { params })
}

export function listHistoryLogs(params) {
  return request.get('/log/history/list', { params })
}

export function listAuditLogs(params) {
  return request.get('/log/audit/list', { params })
}

export function exportLogs(params) {
  return request.get('/log/export', {
    params,
    responseType: 'blob'
  }).then(blob => {
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'sys_log.xlsx'
    a.click()
    URL.revokeObjectURL(url)
  })
}
