import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'

describe('UX-B 前端测试栈冒烟', () => {
  it('happy-dom 环境可挂载最小组件', () => {
    const Hello = defineComponent({ setup: () => () => h('span', { class: 'hi' }, '你好') })
    const wrapper = mount(Hello)
    expect(wrapper.find('.hi').text()).toBe('你好')
  })

  it('happy-dom 提供 localStorage', () => {
    localStorage.setItem('k', 'v')
    expect(localStorage.getItem('k')).toBe('v')
    localStorage.removeItem('k')
  })
})
