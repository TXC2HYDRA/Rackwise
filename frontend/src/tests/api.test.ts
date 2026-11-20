import { describe, expect, it, vi } from 'vitest'
import { ApiError, api, qs, setToken, setUnauthorizedHandler } from '../lib/api'
import { fail, mockApi, ok } from './helpers'

describe('api client', () => {
  it('sends the bearer token and JSON body', async () => {
    const m = mockApi({ 'POST /x': ok({ fine: true }) })
    setToken('abc')
    await api('/x', { method: 'POST', body: { a: 1 } })
    const init = m.fn.mock.calls[0][1] as RequestInit
    expect((init.headers as Record<string, string>).Authorization).toBe('Bearer abc')
    expect(init.body).toBe('{"a":1}')
  })

  it('turns error bodies into ApiError with field errors', async () => {
    mockApi({ 'GET /x': fail(400, 'VALIDATION_FAILED', 'bad', ['serverCount: must be at least 1', 'justification: must not be blank']) })
    const e = (await api('/x').catch((x: unknown) => x as ApiError)) as ApiError
    expect(e).toBeInstanceOf(ApiError)
    expect(e.status).toBe(400)
    expect(e.fieldErrors()).toEqual({ serverCount: 'must be at least 1', justification: 'must not be blank' })
  })

  it('maps network failures to a friendly error', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('failed')))
    const e = (await api('/x').catch((x: unknown) => x as ApiError)) as ApiError
    expect(e.code).toBe('NETWORK')
    expect(e.message).toMatch(/cannot reach the server/i)
  })

  it('signals unauthorized only when a token was in use (not for a failed login)', async () => {
    const handler = vi.fn()
    setUnauthorizedHandler(handler)
    mockApi({ 'GET /x': fail(401, 'UNAUTHENTICATED', 'no') })
    await api('/x').catch(() => undefined)
    expect(handler).not.toHaveBeenCalled()
    setToken('t')
    await api('/x').catch(() => undefined)
    expect(handler).toHaveBeenCalledOnce()
  })

  it('builds query strings without empty values', () => {
    expect(qs({ a: 1, b: '', c: undefined, d: 'x y' })).toBe('?a=1&d=x+y')
    expect(qs({})).toBe('')
  })
})
