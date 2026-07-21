/**
 * TEST-1 · pg-testkit（8081 端口）API 封装
 *
 * 关键：这些请求不走 PG 主后端 8080 的 /api 前缀，而是直连 pg-testkit 8081。
 * 通过独立 axios 实例，避免污染主 request.js 的拦截器逻辑。
 */
import axios from 'axios'

const testkitClient = axios.create({
  baseURL: import.meta.env.VITE_TESTKIT_BASE_URL || 'http://localhost:8081',
  timeout: 10000
})

// ============ 样例数据库 ============

export function initDemoDb() {
  return testkitClient.post('/testkit/demo-db/init').then(r => r.data)
}

export function resetDemoDb() {
  return testkitClient.post('/testkit/demo-db/reset').then(r => r.data)
}

export function dropDemoDb() {
  return testkitClient.post('/testkit/demo-db/drop').then(r => r.data)
}

export function getDemoDbStats() {
  return testkitClient.get('/testkit/demo-db/stats').then(r => r.data)
}

// ============ Mock Server 规则 ============

export function listMockRules() {
  return testkitClient.get('/test/mock-server/rules').then(r => r.data)
}

export function addMockRule(rule) {
  return testkitClient.post('/test/mock-server/configure', rule).then(r => r.data)
}

export function replaceMockRules(rules) {
  return testkitClient.post('/test/mock-server/configure-batch', rules).then(r => r.data)
}

export function resetMockServer() {
  return testkitClient.post('/test/mock-server/reset').then(r => r.data)
}

// ============ Mock Server 请求历史 ============

export function listMockRequests(path) {
  const params = path ? { path } : {}
  return testkitClient.get('/test/mock-server/requests', { params }).then(r => r.data)
}
