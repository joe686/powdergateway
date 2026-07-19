import { describe, test, expect } from 'vitest'

// 占位测试：强制加载新结构，等 Task 3 pg-testkit 冒烟才是真回归
describe('InterfaceWizard 迁移占位', () => {
  test('InterfaceWizard 模块可被导入', async () => {
    const mod = await import('@/views/interface/InterfaceWizard.vue')
    expect(mod.default).toBeTruthy()
  })

  test('SelectInterfaceSteps 模块可被导入', async () => {
    const mod = await import('@/views/interface/SelectInterfaceSteps.vue')
    expect(mod.default).toBeTruthy()
  })
})
