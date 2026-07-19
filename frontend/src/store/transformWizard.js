import { defineStore } from 'pinia'

const DRAFT_KEY = 'transform_wizard_draft'

function defaultState() {
  return {
    _skipNextPersist: false,
    currentStep: 0,
    // Step 1 · 选择系统
    sourceChannelCode: '',
    sourceChannelName: '',
    targetChannelCode: '',
    targetChannelName: '',
    sourceFormat: 'JSON',
    targetFormat: 'JSON',
    complexity: 'BODY_FIELDS',
    // Step 2 · 功能号
    functionCode: '',
    functionName: '',
    messageCategory: '',
    // Step 3 · 端口
    portAddress: '',
    portMethod: 'POST',
    timeout: 3000,
    retryCount: 3,
    // Step 4 · 路由绑定
    headerConfig: {
      contentType: 'application/json',
      charset: 'UTF-8',
      requestHeaders: {},
      responseHeaders: {}
    },
    savedPortRouteId: null,
    // Step 5 · 模板
    templateMode: 'EXISTING',
    savedTemplateId: null,
    newTemplateDraft: {
      name: '',
      srcFmt: '',
      targetFmt: '',
      mappingRules: [],
      processRules: []
    },
    // Step 6 · 测试
    testInput: '',
    testMode: 'SIMULATE',
    testOutput: '',
    testError: ''
  }
}

export const useTransformWizardStore = defineStore('transformWizard', {
  state: defaultState,
  actions: {
    reset() {
      this._skipNextPersist = true
      this.$patch(defaultState())
      localStorage.removeItem(DRAFT_KEY)
    },

    persist() {
      if (this._skipNextPersist) {
        this._skipNextPersist = false
        return
      }
      try {
        const state = { ...this.$state }
        delete state.testOutput
        delete state.testError
        delete state._skipNextPersist
        localStorage.setItem(DRAFT_KEY, JSON.stringify(state))
      } catch (e) {
        console.warn('[transformWizardStore] persist failed:', e)
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
    }
  }
})
