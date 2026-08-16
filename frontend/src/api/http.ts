import axios from 'axios'

export const http = axios.create({
  baseURL: '/',
  timeout: 12000,
})

http.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.code === 'ECONNABORTED') {
      return Promise.reject(new Error('请求超时：请确认后端已在 8080 端口启动（mvn spring-boot:run）'))
    }
    if (!err.response) {
      return Promise.reject(
        new Error(err.message || '无法连接后端：请确认已启动 Spring Boot（8080）且前端用 npm run dev'),
      )
    }
    const detail = err.response?.data?.detail
    if (typeof detail === 'string' && detail) {
      return Promise.reject(new Error(detail))
    }
    const message = err.response?.data?.message || err.message || '请求失败'
    return Promise.reject(new Error(message))
  },
)

