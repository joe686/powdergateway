import request from '@/api/request'

export const cacheApi = {
  list: () =>
    request.get('/cache/list'),

  updateConfig: (interfaceId, data) =>
    request.put(`/cache/${interfaceId}/config`, data),

  evict: (interfaceId) =>
    request.delete(`/cache/${interfaceId}`),

  refresh: (interfaceId, params = {}) =>
    request.post(`/cache/${interfaceId}/refresh`, params),

  evictAll: () =>
    request.delete('/cache/all'),

  stats: (interfaceId) =>
    request.get(`/cache/${interfaceId}/stats`)
}
