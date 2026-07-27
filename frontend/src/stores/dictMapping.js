import { defineStore } from 'pinia'
import { listDictMappings } from '@/api/dictMapping'

const TTL_MS = 5 * 60 * 1000

export const useDictMappingStore = defineStore('dictMapping', {
  state: () => ({
    all: [],
    groupedData: {},   // { systemCode: { dictKey: { direction: [{source, target}] } } }
    refreshedAt: 0
  }),
  getters: {
    systems: (s) => Object.keys(s.groupedData).sort(),
    dictKeysOf: (s) => (sys) => Object.keys(s.groupedData[sys] || {}).sort()
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
