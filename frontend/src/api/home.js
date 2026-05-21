import request from '@/api/request'

export const getOverview = (dimension = 'today') =>
  request.get('/home/overview', { params: { dimension } })
