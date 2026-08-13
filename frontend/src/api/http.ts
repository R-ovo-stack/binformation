import axios from 'axios'

export const http = axios.create({
  baseURL: '/',
  timeout: 15000,
})

http.interceptors.response.use(
  (res) => res,
  (err) => {
    const detail = err.response?.data?.detail
    if (typeof detail === 'string' && detail) {
      return Promise.reject(new Error(detail))
    }
    const message = err.response?.data?.message || err.message || '请求失败'
    return Promise.reject(new Error(message))
  },
)

