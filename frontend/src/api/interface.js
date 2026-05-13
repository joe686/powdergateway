import request from '@/api/request'

/**
 * 接口配置 API（M2-3 查询接口配置）
 */

/** 保存接口配置（新建或更新） */
export function saveInterface(data) {
  return request.post('/interface/save', data)
}

/** 预览接口（执行 SQL，返回前10条） */
export function previewInterface(id, params) {
  return request.post(`/interface/${id}/preview`, { params })
}

/** 查询接口配置列表 */
export function listInterfaces(name, page = 1, size = 20) {
  return request.get('/interface/list', { params: { name, page, size } })
}

/** 查询接口配置详情 */
export function getInterface(id) {
  return request.get(`/interface/${id}`)
}

/** 删除接口配置 */
export function deleteInterface(id) {
  return request.delete(`/interface/${id}`)
}

/** 执行 INSERT 接口（M2-4） */
export function executeInterface(id, params) {
  return request.post(`/interface/${id}/execute`, { params })
}

/** 预览待删数据（M2-6） */
export function deletePreview(id, params) {
  return request.post(`/interface/${id}/delete-preview`, { params })
}

/** 发布接口（M2-7） */
export function publishInterface(id) {
  return request.post(`/interface/${id}/publish`)
}

/** 禁用接口（M2-7） */
export function disableInterface(id) {
  return request.post(`/interface/${id}/disable`)
}

/** 绑定/解绑分库分表配置（M2-8），shardConfigId=null 表示解绑 */
export function bindShardConfig(id, shardConfigId) {
  return request.patch(`/interface/${id}/shard-config`, { shardConfigId })
}
