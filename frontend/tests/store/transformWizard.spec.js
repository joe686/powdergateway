import { setActivePinia, createPinia } from 'pinia'
import { beforeEach, test, expect } from 'vitest'
import { useTransformWizardStore } from '@/store/transformWizard'

beforeEach(() => {
  setActivePinia(createPinia())
  localStorage.clear()
})

test('reset 后 state 回默认且 localStorage 被清除', () => {
  const s = useTransformWizardStore()
  s.functionCode = 'X'
  s.persist()
  expect(localStorage.getItem('transform_wizard_draft')).toBeTruthy()
  s.reset()
  expect(s.functionCode).toBe('')
  expect(localStorage.getItem('transform_wizard_draft')).toBeNull()
})

test('persist 后 loadDraft 状态一致（排除运行时字段）', () => {
  const s = useTransformWizardStore()
  s.functionCode = 'FC_01'
  s.testOutput = 'shouldNotPersist'
  s.persist()
  // 模拟页面刷新：重置 state 但保留 localStorage 中的草稿
  s.$patch({ functionCode: '', testOutput: '' })
  s.loadDraft()
  expect(s.functionCode).toBe('FC_01')
  expect(s.testOutput).toBe('')  // 被排除
})
