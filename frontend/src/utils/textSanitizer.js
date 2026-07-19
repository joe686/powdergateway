// UX-F · 中文乱码检测与占位兜底
// 场景：某些历史数据被 MySQL 服务端字符集配置错误双重编码，
//       无法在前端修复，只能显示占位符避免视觉污染

// 触发规则：UTF-8 replacement char (U+FFFD) 或连续 3+ 个问号
const GARBLED_PATTERN = /[�]|\?{3,}|锟斤拷/

/**
 * 检测字符串是否为乱码
 * @param {string} s
 * @returns {boolean}
 */
export function isGarbled(s) {
  if (!s || typeof s !== 'string') return false
  return GARBLED_PATTERN.test(s)
}

/**
 * 若为乱码返回占位符，否则原样返回
 * @param {string} s
 * @param {string} placeholder
 * @returns {string}
 */
export function sanitize(s, placeholder = '（编码异常）') {
  return isGarbled(s) ? placeholder : s
}
