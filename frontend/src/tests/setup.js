// Element Plus 内部组件（el-menu / el-dropdown 等）依赖 ResizeObserver，
// happy-dom 未内置该 API，需在全局 polyfill 以避免挂载即抛错。
class ResizeObserverMock {
  observe() {}
  unobserve() {}
  disconnect() {}
}
if (typeof globalThis.ResizeObserver === 'undefined') {
  globalThis.ResizeObserver = ResizeObserverMock
}

// happy-dom 缺少 matchMedia，Element Plus 主题切换等间接调用会报错
if (typeof globalThis.matchMedia === 'undefined') {
  globalThis.matchMedia = () => ({
    matches: false,
    media: '',
    addEventListener() {},
    removeEventListener() {},
    addListener() {},
    removeListener() {},
    dispatchEvent() { return false }
  })
}
