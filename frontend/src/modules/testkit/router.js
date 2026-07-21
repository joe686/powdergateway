/**
 * TEST-1 · testkit 模块路由数组
 * 由 src/router/index.js 条件加载（TESTER 角色可见）
 */

export default [
  {
    path: 'testkit/demo-db',
    name: 'TestkitDemoDb',
    component: () => import('./views/DemoDbManage.vue'),
    meta: { title: '样例数据库管理' }
  },
  {
    path: 'testkit/mock-rules',
    name: 'TestkitMockRules',
    component: () => import('./views/MockServerRules.vue'),
    meta: { title: 'Mock 后端规则' }
  },
  {
    path: 'testkit/mock-history',
    name: 'TestkitMockHistory',
    component: () => import('./views/MockServerHistory.vue'),
    meta: { title: 'Mock 请求历史' }
  }
]
