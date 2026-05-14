import request from '@/api/request'

export const getAllConfig = () => request.get('/config/all')
export const updateConfig = (data) => request.put('/config', data)
