import { useEffect, useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../app/auth'
import { Button } from '../components/ui'
import type { Role } from '../lib/types'

const links: { to: string; label: string; roles?: Role[]; end?: boolean }[] = [
  { to: '/', label: 'Dashboard', end: true },
  { to: '/inventory', label: 'Inventory' },
  { to: '/requests', label: 'My requests', end: true },
  { to: '/requests/new', label: 'New request', roles: ['REQUESTER', 'ADMIN'] },
  { to: '/approvals', label: 'Approval queue', roles: ['REVIEWER', 'ADMIN'], end: true },
  { to: '/approvals/history', label: 'Approval history', roles: ['REVIEWER', 'ADMIN'] },
  { to: '/audit', label: 'Audit trail', roles: ['REVIEWER', 'ADMIN'] },
  { to: '/admin/inventory', label: 'Manage inventory', roles: ['ADMIN'] },
]

function useTheme() {
  const [dark, setDark] = useState(() => {
    try {
      const saved = localStorage.getItem('theme')
      if (saved) return saved === 'dark'
    } catch { /* storage unavailable */ }
    return window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false
  })
  useEffect(() => {
    document.documentElement.classList.toggle('dark', dark)
    try { localStorage.setItem('theme', dark ? 'dark' : 'light') } catch { /* ignore */ }
  }, [dark])
  return [dark, () => setDark((d) => !d)] as const
}

export function AppLayout() {
  const { user, logout, has } = useAuth()
  const [dark, toggle] = useTheme()
  const [open, setOpen] = useState(false)
  const visible = links.filter((l) => !l.roles || has(...l.roles))

  return (
    <div className="min-h-screen lg:flex">
      <a href="#main" className="sr-only focus:not-sr-only focus:absolute focus:left-2 focus:top-2 focus:z-50 focus:rounded focus:bg-white focus:px-3 focus:py-2 focus:text-slate-900">Skip to content</a>
      <header className="flex items-center justify-between border-b border-slate-200 bg-white px-4 py-3 lg:hidden dark:border-slate-800 dark:bg-slate-900">
        <span className="font-semibold">Rackwise</span>
        <Button variant="secondary" aria-expanded={open} aria-controls="sidebar" onClick={() => setOpen((o) => !o)}>Menu</Button>
      </header>
      <aside id="sidebar" className={`${open ? 'block' : 'hidden'} w-full shrink-0 border-r border-slate-200 bg-white lg:flex lg:min-h-screen lg:w-60 lg:flex-col dark:border-slate-800 dark:bg-slate-900`}>
        <div className="hidden px-5 py-5 text-lg font-semibold tracking-tight lg:block">Rackwise</div>
        <nav aria-label="Main" className="flex-1 space-y-0.5 px-3 pb-3">
          {visible.map((l) => (
            <NavLink key={l.to} to={l.to} end={l.end} onClick={() => setOpen(false)}
              className={({ isActive }) => `block rounded-lg px-3 py-2 text-sm ${isActive ? 'bg-blue-50 font-medium text-blue-800 dark:bg-blue-950 dark:text-blue-200' : 'text-slate-700 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800'}`}>
              {l.label}
            </NavLink>
          ))}
        </nav>
        <div className="space-y-3 border-t border-slate-200 p-4 text-sm dark:border-slate-800">
          <div>
            <p className="font-medium">{user?.displayName}</p>
            <p className="text-xs text-slate-500 dark:text-slate-400">{user?.roles.join(', ')}</p>
          </div>
          <div className="flex gap-2">
            <Button variant="secondary" onClick={toggle} aria-pressed={dark}>{dark ? 'Light mode' : 'Dark mode'}</Button>
            <Button variant="secondary" onClick={logout}>Sign out</Button>
          </div>
        </div>
      </aside>
      <main id="main" tabIndex={-1} className="mx-auto w-full max-w-6xl flex-1 px-4 py-6 lg:px-8 lg:py-8">
        <Outlet />
      </main>
    </div>
  )
}
