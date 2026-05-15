import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/store/user'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/LoginView.vue'),
      meta: { requiresAuth: false }
    },
    {
      path: '/',
      component: () => import('@/components/layout/MainLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        {
          path: '',
          redirect: '/dashboard'
        },
        {
          path: 'dashboard',
          name: 'Dashboard',
          component: () => import('@/views/DashboardView.vue'),
          meta: { title: '系统概览' }
        },
        // 接口转换配置（模块一）
        {
          path: 'convert/format',
          name: 'FormatConvert',
          component: () => import('@/views/convert/FormatConvert.vue'),
          meta: { title: '报文格式转换' }
        },
        {
          path: 'convert/field-mapping',
          name: 'FieldMapping',
          component: () => import('@/views/convert/FieldMapping.vue'),
          meta: { title: '字段映射配置' }
        },
        {
          path: 'convert/field-process',
          name: 'FieldProcess',
          component: () => import('@/views/convert/FieldProcess.vue'),
          meta: { title: '字段加工配置' }
        },
        {
          path: 'convert/channel',
          name: 'ChannelTemplate',
          component: () => import('@/views/convert/ChannelConfig.vue'),
          meta: { title: '渠道模板管理' }
        },
        {
          path: 'convert/port-route',
          name: 'PortRoute',
          component: () => import('@/views/convert/PortRoute.vue'),
          meta: { title: '端口分发路由' }
        },
        {
          path: 'convert/template',
          name: 'ConvertTemplate',
          component: () => import('@/views/convert/TemplateList.vue'),
          meta: { title: '转换模板管理' }
        },
        // 可视化接口开发（模块二）
        {
          path: 'interface/db',
          name: 'DbConnection',
          component: () => import('@/views/db/ConnectionList.vue'),
          meta: { title: '数据库连接管理' }
        },
        {
          path: 'interface/table',
          name: 'TableStructure',
          component: () => import('@/views/db/TableStructure.vue'),
          meta: { title: '表结构管理' }
        },
        {
          path: 'interface/wizard',
          name: 'InterfaceWizard',
          component: () => import('@/views/interface/InterfaceWizard.vue'),
          meta: { title: '接口配置向导' }
        },
        {
          path: 'interface/dev',
          name: 'InterfaceDev',
          component: () => import('@/views/interface/QueryConfig.vue'),
          meta: { title: '查询接口配置' }
        },
        {
          path: 'interface/insert',
          name: 'InterfaceInsert',
          component: () => import('@/views/interface/InsertConfig.vue'),
          meta: { title: '插入接口配置' }
        },
        {
          path: 'interface/update',
          name: 'InterfaceUpdate',
          component: () => import('@/views/interface/UpdateConfig.vue'),
          meta: { title: '修改接口配置' }
        },
        {
          path: 'interface/delete',
          name: 'InterfaceDelete',
          component: () => import('@/views/interface/DeleteConfig.vue'),
          meta: { title: '删除接口配置' }
        },
        {
          path: 'interface/list',
          name: 'InterfaceList',
          component: () => import('@/views/interface/InterfaceList.vue'),
          meta: { title: '接口管理' }
        },
        {
          path: 'interface/shard',
          name: 'ShardConfig',
          component: () => import('@/views/interface/ShardConfig.vue'),
          meta: { title: '分库分表配置' }
        },
        {
          path: 'interface/formula',
          name: 'FieldFormula',
          component: () => import('@/views/placeholder/PlaceholderView.vue'),
          meta: { title: '字段公式管理' }
        },
        {
          path: 'interface/cache',
          name: 'CacheQuery',
          component: () => import('@/views/cache/CacheList.vue'),
          meta: { title: '缓存查询管理' }
        },
        // 系统管理
        {
          path: 'system/log',
          name: 'LogManage',
          component: () => import('@/views/system/LogList.vue'),
          meta: { title: '日志管理' }
        },
        {
          path: 'system/stats',
          name: 'PerfStats',
          component: () => import('@/views/system/Stats.vue'),
          meta: { title: '性能统计' }
        },
        {
          path: 'system/user',
          name: 'UserManage',
          component: () => import('@/views/system/UserList.vue'),
          meta: { title: '用户权限管理' }
        },
        {
          path: 'system/config',
          name: 'SysConfig',
          component: () => import('@/views/system/SystemConfig.vue'),
          meta: { title: '系统配置' }
        },
        // 辅助工具
        {
          path: 'tools/debug',
          name: 'MessageDebug',
          component: () => import('@/views/placeholder/PlaceholderView.vue'),
          meta: { title: '报文调试' }
        },
        {
          path: 'tools/swagger',
          name: 'SwaggerDoc',
          component: () => import('@/views/placeholder/PlaceholderView.vue'),
          meta: { title: '接口文档' }
        }
      ]
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/'
    }
  ]
})

// 路由守卫：未登录跳转 /login，已登录不允许再访问 /login
router.beforeEach((to, from, next) => {
  const userStore = useUserStore()
  if (to.meta.requiresAuth !== false && !userStore.isLoggedIn) {
    next('/login')
  } else if (to.path === '/login' && userStore.isLoggedIn) {
    next('/')
  } else {
    var menus = userStore.allowedMenus
    if (menus.length > 0
        && to.meta.requiresAuth !== false
        && to.path !== '/dashboard'
        && !menus.includes(to.path)) {
      next('/dashboard')
    } else {
      next()
    }
  }
})

export default router
