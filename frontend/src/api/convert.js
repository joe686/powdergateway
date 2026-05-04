import request from '@/api/request'

/**
 * 报文格式转换 API (M1-1)
 */

/** 格式转换：srcFormat → targetFormat */
export function convertFormat(data) {
  return request.post('/format-convert/convert', data)
}

/** 解析报文为字段 Map */
export function parseMessage(data) {
  return request.post('/format-convert/parse', data)
}

/**
 * M1-6 报文转换调用接口
 * 串联全流程：读模板 → 格式转换 → 字段映射 → 字段加工 → 返回结果
 *
 * @param {Object} data - { templateId?, message, srcFormat }
 * @returns {{ result, targetFormat, costMs }}
 */
export function convertMessage(data) {
  return request.post('/convert', data)
}
