import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import { useEffect, type ReactElement } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { vi } from 'vitest'
import { AuthProvider, useAuth } from '../app/auth'
import type { Role, User } from '../lib/types'

export interface Res { status?: number; body: unknown }
type Handler = Res | ((body: unknown) => Res)

export const ok = (body: unknown): Res => ({ body })
export const fail = (status: number, code: string, message: string, details: string[] = []): Res => ({ status, body: { status, code, message, details } })

/** Routes "METHOD /path?query" (without /api/v1) to canned responses. An unmocked call fails the test loudly. */
export function mockApi(routes: Record<string, Handler>) {
  const calls: { key: string; body: unknown; headers: Record<string, string> }[] = []
  const fn = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input).replace('/api/v1', '')
    const key = `${init?.method ?? 'GET'} ${url}`
    const body = init?.body ? JSON.parse(String(init.body)) : undefined
    calls.push({ key, body, headers: (init?.headers ?? {}) as Record<string, string> })
    const handler = routes[key] ?? routes[key.split('?')[0]]
    if (handler === undefined) throw new Error(`Unmocked API call: ${key}`)
    const res = typeof handler === 'function' ? handler(body) : handler
    return new Response(JSON.stringify(res.body), { status: res.status ?? 200, headers: { 'Content-Type': 'application/json' } })
  })
  vi.stubGlobal('fetch', fn)
  return { fn, calls, count: (prefix: string) => calls.filter((c) => c.key.startsWith(prefix)).length }
}

export const makeUser = (roles: Role[], id = 'u-1', username = 'tester'): User => ({ id, username, displayName: 'Test User', roles })

function SignIn() {
  const { user, login } = useAuth()
  useEffect(() => { if (!user) void login('seed', 'seed') }, [user, login])
  return null
}

/** Renders inside router, query client and AuthProvider. Pass `user: null` to stay signed out. */
export function renderApp(ui: ReactElement, opts: { user?: User | null; routes?: Record<string, Handler>; route?: string; path?: string } = {}) {
  const user = opts.user === undefined ? makeUser(['REQUESTER']) : opts.user
  const api = mockApi({
    ...(user ? { 'POST /auth/login': ok({ accessToken: 't', tokenType: 'Bearer', expiresIn: 900, user }) } : {}),
    ...opts.routes,
  })
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const view = render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={[opts.route ?? '/']}>
        <AuthProvider>
          {user && <SignIn />}
          <Routes>
            <Route path={opts.path ?? '*'} element={ui} />
            {opts.path && <Route path="*" element={<div>home page</div>} />}
            {opts.path !== '/login' && <Route path="/login" element={<div>login page</div>} />}
          </Routes>
        </AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
  return { ...view, api }
}
