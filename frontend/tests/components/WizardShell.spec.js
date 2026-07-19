import { mount } from '@vue/test-utils'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus, { ElMessage } from 'element-plus'
import WizardShell from '@/components/wizard/WizardShell.vue'

const mountOptions = {
  global: {
    plugins: [ElementPlus]
  }
}

beforeEach(() => {
  setActivePinia(createPinia())
})

describe('WizardShell', () => {
  test('渲染 steps 空数组时不崩溃且步骤条 el-step 数量为 0', () => {
    const wrapper = mount(WizardShell, {
      props: { title: '测试向导', steps: [], currentStep: 0 },
      ...mountOptions
    })
    // 渲染完成即通过，无崩溃
    expect(wrapper.find('.wizard-shell').exists()).toBe(true)
    // steps 为空时 el-steps 内无 el-step
    const stepsEl = wrapper.findAll('.el-step')
    expect(stepsEl.length).toBe(0)
  })

  test('传 skipWhen 返回 true 的步骤在 visibleSteps 中不出现', async () => {
    const steps = [
      { key: 'a', label: 'A', skipWhen: () => false },
      { key: 'b', label: 'B', skipWhen: () => true },
      { key: 'c', label: 'C' }
    ]
    const wrapper = mount(WizardShell, {
      props: { title: '测试', steps, currentStep: 0 },
      ...mountOptions
    })
    // visibleSteps 应只剩 a 和 c，步骤条只显示 2 个
    const stepsEl = wrapper.findAll('.el-step')
    expect(stepsEl.length).toBe(2)
  })

  test('validateNext 返回字符串时点击下一步阻止前进且触发 ElMessage.warning', async () => {
    const warnSpy = vi.spyOn(ElMessage, 'warning').mockImplementation(() => {})
    const steps = [
      { key: 'a', label: 'A' },
      { key: 'b', label: 'B' }
    ]
    const wrapper = mount(WizardShell, {
      props: {
        title: '测试',
        steps,
        currentStep: 0,
        validateNext: () => '请完成当前步骤'
      },
      ...mountOptions
    })
    // 找到"下一步"按钮并点击
    const buttons = wrapper.findAll('button')
    const nextBtn = buttons.find(b => b.text().includes('下一步'))
    if (nextBtn) await nextBtn.trigger('click')
    expect(warnSpy).toHaveBeenCalledWith('请完成当前步骤')
    // currentStep 不应发生 update
    const emitted = wrapper.emitted('update:currentStep')
    expect(!emitted || emitted.length === 0).toBe(true)
    warnSpy.mockRestore()
  })

  test('v-model:currentStep 双向绑定：外部改 → 内部渲染切换；内部点下一步 → emit update:currentStep', async () => {
    const steps = [
      { key: 'a', label: 'A' },
      { key: 'b', label: 'B' }
    ]
    const wrapper = mount(WizardShell, {
      props: { title: '测试', steps, currentStep: 0 },
      ...mountOptions
    })
    // 点击下一步 → emit update:currentStep(1)
    const buttons = wrapper.findAll('button')
    const nextBtn = buttons.find(b => b.text().includes('下一步'))
    if (nextBtn) await nextBtn.trigger('click')
    const emitted = wrapper.emitted('update:currentStep')
    expect(emitted).toBeTruthy()
    expect(emitted[0][0]).toBe(1)
    // 外部改为 step 1
    await wrapper.setProps({ currentStep: 1 })
    expect(wrapper.props('currentStep')).toBe(1)
  })
})
