import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

const request = axios.create({
  baseURL: '/api',
  timeout: 10000
})

// 请求拦截器：自动携带 token
request.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers['satoken'] = token
    }
    return config
  },
  error => Promise.reject(error)
)

// 响应拦截器：统一处理错误
request.interceptors.response.use(
  async response => {
    // Blob 响应（下载/导出）绕过 Result<T> 解包 —— #8/#9 修复：
    // 之前直接返回 response.data.data → 对 Blob 而言 undefined，导致导出报表/导出 zip 报错
    if (response.config?.responseType === 'blob' || response.data instanceof Blob) {
      // 后端在下载接口上报错时，仍可能以 application/json 返回错误体，此时需解码识别
      const blob = response.data
      if (blob && blob.type === 'application/json') {
        try {
          const text = await blob.text()
          const json = JSON.parse(text)
          if (json && json.code !== undefined && json.code !== 200) {
            ElMessage.error(json.message || '下载失败')
            return Promise.reject(new Error(json.message || '下载失败'))
          }
        } catch { /* 非 JSON，按二进制返回 */ }
      }
      return blob
    }
    const res = response.data
    // 后端统一响应体 Result<T>，code !== 200 视为业务错误
    if (res.code !== undefined && res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res.data
  },
  error => {
    const status = error.response?.status
    if (status === 401) {
      ElMessage.error('登录已过期，请重新登录')
      localStorage.removeItem('token')
      router.push('/login')
    } else if (status === 500) {
      ElMessage.error('服务器内部错误，请联系管理员')
    } else {
      ElMessage.error(error.message || '网络异常')
    }
    return Promise.reject(error)
  }
)

export default request
