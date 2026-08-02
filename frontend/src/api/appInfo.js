import request from '@/api/request'

// v0.3.1 CR-003 · 获取程序版本信息(免登)
export function getAppInfo() {
  return request.get('/app-info')
}
