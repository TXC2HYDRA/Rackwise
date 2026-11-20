import { useQueryClient } from '@tanstack/react-query'
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { api, setToken, setUnauthorizedHandler } from '../lib/api'
import type { LoginResponse, Role, User } from '../lib/types'

interface AuthState {
  user: User | null
  sessionExpired: boolean
  login: (username: string, password: string) => Promise<void>
  logout: () => void
  has: (...roles: Role[]) => boolean
}

const Ctx = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [sessionExpired, setSessionExpired] = useState(false)
  const timer = useRef<number | undefined>(undefined)
  const qc = useQueryClient()

  const clear = useCallback((expired: boolean) => {
    window.clearTimeout(timer.current)
    setToken(null)
    setUser(null)
    setSessionExpired(expired)
    qc.clear()
  }, [qc])

  useEffect(() => {
    setUnauthorizedHandler(() => clear(true))
    return () => setUnauthorizedHandler(null)
  }, [clear])

  const login = useCallback(async (username: string, password: string) => {
    const r = await api<LoginResponse>('/auth/login', { method: 'POST', body: { username, password } })
    setToken(r.accessToken)
    setUser(r.user)
    setSessionExpired(false)
    window.clearTimeout(timer.current)
    timer.current = window.setTimeout(() => clear(true), r.expiresIn * 1000)
  }, [clear])

  const value = useMemo<AuthState>(() => ({
    user,
    sessionExpired,
    login,
    logout: () => clear(false),
    has: (...roles) => !!user && roles.some((r) => user.roles.includes(r)),
  }), [user, sessionExpired, login, clear])

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}

export function useAuth(): AuthState {
  const v = useContext(Ctx)
  if (!v) throw new Error('useAuth outside AuthProvider')
  return v
}
