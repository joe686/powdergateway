import { defineStore } from 'pinia'
import { listDictMappings } from '@/api/dictMapping'

const TTL_MS = 5 * 60 * 1000

/**
 * v0.2.5 CR-004: store 加载所有 scope 数据(不预过滤) · 各消费方按 scope 动态过滤
 * scope 语义: 1=M1侧 2=M2侧 3=共享
 * scope 过滤规则: scope=1 显示 (scope=1 或 scope=3) · scope=2 显示 (scope=2 或 scope=3) · scope=3 显示 scope=3
 */
export const useDictMappingStore = defineStore('dictMapping', {
  state: () => ({
    all: [],
    groupedData: {},   // { systemCode: { dictKey: { direction: [{source, target}] } } } · 全量(所有 scope)
    refreshedAt: 0
  }),
  getters: {
    systems: (s) => Object.keys(s.groupedData).sort(),
    dictKeysOf: (s) => (sys) => Object.keys(s.groupedData[sys] || {}).sort(),

    /** v0.2.5 CR-004: 按 scope 过滤后的分组视图 */
    groupedForScope: (s) => (scope) => scope == null ? s.groupedData : groupByFn(filterByScope(s.all, scope)),
    systemsForScope: (s) => (scope) => {
      if (scope == null) return Object.keys(s.groupedData).sort()
      return Object.keys(groupByFn(filterByScope(s.all, scope))).sort()
    },
    dictKeysForScope: (s) => (scope, sys) => {
      if (scope == null) return Object.keys(s.groupedData[sys] || {}).sort()
      const g = groupByFn(filterByScope(s.all, scope))
      return Object.keys(g[sys] || {}).sort()
    }
  },
  actions: {
    async ensureLoaded(force = false) {
      if (!force && Date.now() - this.refreshedAt < TTL_MS && this.all.length > 0) return
      const res = await listDictMappings()
      this.all = res?.data || res || []
      this.groupedData = groupByFn(this.all)
      this.refreshedAt = Date.now()
    },
    invalidate() { this.refreshedAt = 0 }
  }
})

function groupByFn(rows) {
  const g = {}
  for (const r of rows) {
    if (r.status !== 1) continue   // 只装启用条目
    g[r.systemCode] ??= {}
    g[r.systemCode][r.dictKey] ??= {}
    g[r.systemCode][r.dictKey][r.direction] ??= []
    g[r.systemCode][r.dictKey][r.direction].push({ source: r.sourceValue, target: r.targetValue })
  }
  return g
}

function filterByScope(rows, scope) {
  if (scope == null) return rows
  // scope=1 → 显示 scope=1 + scope=3 · scope=2 → 显示 scope=2 + scope=3 · scope=3 → 只显示 scope=3
  return rows.filter(r => {
    const rs = r.scope == null ? 3 : r.scope
    if (scope === 3) return rs === 3
    return rs === scope || rs === 3
  })
}
