import request from '@/api/request'

// 分页列表；params: { scene, keyword, pageNo, pageSize }
export const listFormulas = (params) =>
  request.get('/field-formula/list', { params })

// 详情；返回 FieldFormulaDto | null
export const getFormula = (id) =>
  request.get(`/field-formula/${id}`)

// 新增或更新；data: FormulaSaveRequest
export const saveFormula = (data) =>
  request.post('/field-formula/save', data)

// 复制
export const duplicateFormula = (id) =>
  request.post(`/field-formula/${id}/duplicate`)

// 软删除（仅 admin）
export const deleteFormula = (id) =>
  request.delete(`/field-formula/${id}`)

// 独立校验；data: FormulaValidateRequest
export const validateFormula = (data) =>
  request.post('/field-formula/validate', data)
