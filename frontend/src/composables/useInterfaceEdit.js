import { getInterface } from '@/api/interface'
import { ElMessage } from 'element-plus'

/**
 * v0.3.1 FB-054 · 接口编辑回填 composable。
 *
 * 供 InterfaceWizard 和 v0.2.7 分类型 4 页(Task 5)复用 · 从后端拉接口配置反解到 wizard store。
 *
 * @param {Object} wizardStore - useWizardStore() 返回的 pinia store 实例
 * @returns {{ loadForEdit: (id: number|string) => Promise<Object|null>, isEditMode: (route) => boolean }}
 */
export function useInterfaceEdit(wizardStore) {
  const isEditMode = (route) => !!(route && route.query && route.query.id)

  const loadForEdit = async (id) => {
    if (!id) return null
    try {
      const cfg = await getInterface(id)
      const patch = {
        savedId: cfg.id,
        interfaceName: cfg.name || '',
        functionId: cfg.functionId || '',
        interfaceType: cfg.type || 'SELECT',
        dbConnectionId: cfg.dbConnectionId || null,
        shardConfigId: cfg.shardConfigId || null,
        logEnabled: cfg.logEnabled === 1 || cfg.logEnabled === true,
      }
      // configJson 反解到 store 对应字段
      if (cfg.configJson) {
        try {
          const j = JSON.parse(cfg.configJson)
          if (j.mainTable) patch.mainTable = j.mainTable
          if (j.joinConfigs) patch.joinConfigs = j.joinConfigs
          if (j.tables) patch.tables = j.tables
          if (j.fields) {
            // SELECT 使用 selectedColumns · INSERT/UPDATE 使用 fieldTables
            if (cfg.type === 'SELECT') {
              patch.selectedColumns = j.fields
            } else {
              patch.fieldTables = j.fieldTables || j.fields
            }
          }
          if (j.fieldTables) patch.fieldTables = j.fieldTables
          if (j.conditions) patch.conditions = j.conditions
          if (j.processRules) patch.processRules = j.processRules
        } catch (e) {
          console.warn('[useInterfaceEdit] configJson 解析失败:', e)
        }
      }
      wizardStore.$patch(patch)
      return cfg
    } catch (e) {
      ElMessage.error('加载接口配置失败:' + (e?.message || e))
      return null
    }
  }

  return { loadForEdit, isEditMode }
}
