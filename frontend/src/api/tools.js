import request from '@/api/request'

// v0.3.0 SOCK-3 · XML 扁平化(嵌套 XML → dot.notation 键值对)
export function xmlFlatten(xml, prefix = '') {
  return request.post('/tools/xml-flatten', { xml, prefix })
}
