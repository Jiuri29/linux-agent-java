import axios, { AxiosError } from 'axios'
import { ElMessage } from 'element-plus'

export const CSRF_KEY = 'ops_csrf_token'

export function getCsrf(): string {
  return localStorage.getItem(CSRF_KEY) ?? ''
}

export function setCsrf(token: string) {
  if (token) localStorage.setItem(CSRF_KEY, token)
  else localStorage.removeItem(CSRF_KEY)
}

export class ApiError extends Error {
  code: string
  data: unknown
  constructor(code: string, message: string, data?: unknown) {
    super(message)
    this.code = code
    this.data = data
  }
}

const http = axios.create({
  baseURL: '',
  timeout: 60000,
  withCredentials: true,
})

http.interceptors.request.use((config) => {
  const m = (config.method ?? 'get').toLowerCase()
  if (m !== 'get' && m !== 'head' && m !== 'options') {
    config.headers['X-CSRF-Token'] = getCsrf()
  }
  return config
})

let onUnauthorized: (() => void) | null = null
export function setUnauthorizedHandler(fn: () => void) {
  onUnauthorized = fn
}

function extract(resData: unknown): { ok: boolean; code: string; message: string; payload: unknown } {
  if (resData && typeof resData === 'object' && 'ok' in (resData as Record<string, unknown>)) {
    const env = resData as Record<string, unknown>
    return {
      ok: Boolean(env.ok),
      code: String(env.code ?? ''),
      message: String(env.message ?? ''),
      payload: env.data,
    }
  }
  return { ok: true, code: 'ok', message: '', payload: resData }
}

http.interceptors.response.use(
  (response) => {
    const r = extract(response.data)
    if (!r.ok) return Promise.reject(new ApiError(r.code, r.message || r.code, r.payload))
    return r.payload as never
  },
  (error: AxiosError) => {
    const status = error.response?.status
    let body: Record<string, unknown> | undefined
    if (error.response?.data && typeof error.response.data === 'object') {
      body = error.response.data as Record<string, unknown>
    }
    const code = String(body?.code ?? `http_${status ?? 0}`)
    let message = String(body?.message ?? error.message)
    if (status === 401 || code === 'unauthenticated') {
      message = '会话已失效，请重新登录'
      onUnauthorized?.()
    } else if (code === 'csrf_required' || status === 403) {
      message = body?.message ? String(body.message) : '权限不足或 CSRF 校验失败'
    }
    ElMessage.error(message)
    return Promise.reject(new ApiError(code, message, body?.data))
  },
)

export default http

export async function downloadFile(url: string, fallbackName: string) {
  const res = await http.get(url, { responseType: 'blob' })
  const blob = res as unknown as Blob
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = fallbackName
  a.click()
  URL.revokeObjectURL(a.href)
}
