import request from '@/api/request'

/**
 * 检查功能号是否已存在（UX-D）
 * @param {string} functionCode 功能号
 * @returns {Promise<boolean>} true = 已存在
 */
export async function checkFunctionCodeExists(functionCode) {
  try {
    const res = await request.get('/port-route/list', {
      params: { page: 1, size: 1, functionCode }
    })
    return res && res.total > 0
  } catch {
    return false
  }
}
