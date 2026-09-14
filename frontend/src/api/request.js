import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/api',
  // AI 接口调用大模型可能比较慢，超时放宽到 3 分钟
  timeout: 180000
})

function toLogin() {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  if (!window.location.hash.startsWith('#/login')) {
    window.location.hash = '#/login'
  }
}

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const payload = response.data
    if (payload && typeof payload.code === 'number') {
      if (payload.code === 200) {
        return payload.data
      }
      if (payload.code === 401) {
        toLogin()
        return Promise.reject(new Error(payload.message))
      }
      ElMessage.error(payload.message || '请求失败')
      return Promise.reject(new Error(payload.message))
    }
    return payload
  },
  (error) => {
    const status = error.response?.status
    if (status === 401) {
      ElMessage.warning('登录已过期，请重新登录')
      toLogin()
    } else {
      ElMessage.error(error.response?.data?.message || error.message || '网络异常，请稍后重试')
    }
    return Promise.reject(error)
  }
)

export default request
