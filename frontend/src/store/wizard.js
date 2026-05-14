import { defineStore } from 'pinia'

const DRAFT_KEY = 'wizard_draft'

function defaultState() {
  return {
    currentStep: 0,
    interfaceType: '',

    // 步骤②
    interfaceName: '',
    dbConnectionId: null,
    tableColumns: {},       // { tableName: [{ name, type, isPrimary, nullable }] }

    // 步骤③ SELECT
    mainTable: { name: '', alias: '' },
    joinConfigs: [],        // [{ rightTableName, rightAlias, type, leftCol, rightCol }]

    // 步骤③ INSERT/UPDATE/DELETE
    tables: [],             // [{ tableName }]，INSERT 最多3张

    // 步骤④ SELECT
    selectedColumns: [],    // [{ tableAlias, name, type, selected, alias }]

    // 步骤④ INSERT/UPDATE
    fieldTables: [],        // [{ tableName, fields: [{ column, columnType, sourceType, paramKey, constValue, expression }] }]

    // 步骤⑤
    shardConfigId: null,

    // 步骤⑥
    processRules: [],

    // 步骤⑦
    conditions: [],

    // 步骤⑧
    logEnabled: true,

    // 步骤⑨
    previewParams: {},
    previewResult: [],

    // 步骤⑩
    savedId: null,
  }
}

export const useWizardStore = defineStore('wizard', {
  state: defaultState,
  actions: {
    reset() {
      this.$patch(defaultState())
      localStorage.removeItem(DRAFT_KEY)
    },
    persist() {
      try {
        const { previewResult, tableColumns, ...rest } = this.$state
        localStorage.setItem(DRAFT_KEY, JSON.stringify(rest))
      } catch (e) {
        console.warn('[wizardStore] persist failed:', e)
      }
    },
    hasDraft() {
      return !!localStorage.getItem(DRAFT_KEY)
    },
    loadDraft() {
      try {
        const raw = localStorage.getItem(DRAFT_KEY)
        if (!raw) return false
        this.$patch(JSON.parse(raw))
        return true
      } catch {
        return false
      }
    },
  },
})
