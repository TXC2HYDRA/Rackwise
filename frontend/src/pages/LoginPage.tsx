import { useState, type FormEvent } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../app/auth'
import { ApiError } from '../lib/api'
import { Button, Field, Notice, inputClass } from '../components/ui'

export function LoginPage() {
  const { user, login, sessionExpired } = useAuth()
  const navigate = useNavigate()
  const from = (useLocation().state as { from?: string } | null)?.from ?? '/'
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState<{ username?: string; password?: string }>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  if (user) return <Navigate to={from} replace />

  async function submit(e: FormEvent) {
    e.preventDefault()
    const next: typeof errors = {}
    if (!username.trim()) next.username = 'Enter your username'
    if (!password) next.password = 'Enter your password'
    setErrors(next)
    setFormError(null)
    if (Object.keys(next).length) return
    setBusy(true)
    try {
      await login(username.trim(), password)
      navigate(from, { replace: true })
    } catch (err) {
      setFormError(err instanceof ApiError && err.status === 401 ? 'Invalid username or password.' : err instanceof ApiError ? err.message : 'Sign-in failed.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="flex min-h-screen items-center justify-center px-4">
      <div className="w-full max-w-sm">
        <h1 className="text-center text-2xl font-semibold tracking-tight">Rackwise</h1>
        <p className="mt-1 text-center text-sm text-slate-500">Infrastructure capacity planning and approval</p>
        <form onSubmit={submit} noValidate className="mt-8 space-y-4 rounded-xl border border-slate-200 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900">
          {sessionExpired && <Notice kind="info">Your session ended. Please sign in again.</Notice>}
          {formError && <Notice kind="error">{formError}</Notice>}
          <Field label="Username" htmlFor="username" error={errors.username}>
            <input id="username" className={inputClass} autoComplete="username" value={username} onChange={(e) => setUsername(e.target.value)}
              aria-invalid={!!errors.username} aria-describedby={errors.username ? 'username-error' : undefined} />
          </Field>
          <Field label="Password" htmlFor="password" error={errors.password}>
            <input id="password" type="password" className={inputClass} autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)}
              aria-invalid={!!errors.password} aria-describedby={errors.password ? 'password-error' : undefined} />
          </Field>
          <Button type="submit" loading={busy} className="w-full">Sign in</Button>
        </form>
        <p className="mt-4 text-center text-xs text-slate-500">Simulated infrastructure. Independent portfolio project.</p>
      </div>
    </main>
  )
}
