export class ApiError extends Error {
  status: number
  code: string
  details: string[]
  constructor(status: number, code: string, message: string, details: string[] = []) {
    super(message)
    this.status = status
    this.code = code
    this.details = details
  }
  /** Server-side field errors arrive as "field: message". */
  fieldErrors(): Record<string, string> {
    const out: Record<string, string> = {}
    for (const d of this.details) {
      const i = d.indexOf(': ')
      if (i > 0) out[d.slice(0, i)] = d.slice(i + 2)
    }
    return out
  }
}

// The access token lives in memory only: never in localStorage/sessionStorage.
let token: string | null = null
let onUnauthorized: (() => void) | null = null
export const setToken = (t: string | null) => { token = t }
export const setUnauthorizedHandler = (fn: (() => void) | null) => { onUnauthorized = fn }

interface Options { method?: string; body?: unknown; headers?: Record<string, string> }

export async function api<T>(path: string, opts: Options = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json', ...opts.headers }
  if (token) headers.Authorization = `Bearer ${token}`
  if (opts.body !== undefined) headers['Content-Type'] = 'application/json'
  let res: Response
  try {
    res = await fetch(`/api/v1${path}`, {
      method: opts.method ?? 'GET',
      headers,
      body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
    })
  } catch {
    throw new ApiError(0, 'NETWORK', 'Cannot reach the server. Check your connection and try again.')
  }
  const text = await res.text()
  const data = text ? safeJson(text) : null
  if (!res.ok) {
    if (res.status === 401 && token) onUnauthorized?.()
    const b = (data ?? {}) as { code?: string; message?: string; details?: string[] }
    throw new ApiError(res.status, b.code ?? `HTTP_${res.status}`, b.message ?? res.statusText, b.details ?? [])
  }
  return data as T
}

function safeJson(text: string): unknown {
  try { return JSON.parse(text) } catch { return null }
}

export const qs = (params: Record<string, string | number | undefined | null>) => {
  const p = new URLSearchParams()
  for (const [k, v] of Object.entries(params)) if (v !== undefined && v !== null && v !== '') p.set(k, String(v))
  const s = p.toString()
  return s ? `?${s}` : ''
}
