import { ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'

/**
 * 草稿保存 composable（UX-D Task 1）
 * 契约与 wizard.js 五方法保持一致：
 * persist / hasDraft / loadDraft / reset / _skipNextPersist
 *
 * @param {object} store - Pinia store 实例（需实现 persist/hasDraft/loadDraft/reset）
 * @returns {{ draftSaved: Ref<boolean>, promptRestoreDraft: () => Promise<boolean> }}
 */
export function useDraft(store) {
  const draftSaved = ref(false)

  watch(
    () => store.$state,
    () => {
      store.persist()
      draftSaved.value = true
    },
    { deep: true }
  )

  async function promptRestoreDraft(message = '发现未完成的向导配置，是否恢复？') {
    if (!store.hasDraft()) return false
    try {
      await ElMessageBox.confirm(message, '恢复草稿', {
        confirmButtonText: '恢复',
        cancelButtonText: '重新开始',
        type: 'info'
      })
      store.loadDraft()
      return true
    } catch {
      store.reset()
      return false
    }
  }

  return { draftSaved, promptRestoreDraft }
}
