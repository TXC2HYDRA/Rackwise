import { useEffect, useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../app/auth'
import { Icon, type IconName } from '../components/icons'
import { Button } from '../components/ui'
import type { Role } from '../lib/types'

const links: { to: string; label: string; icon: IconName; roles?: Role[]; end?: boolean }[] = [
  { to: '/', label: 'Dashboard', icon: 'dashboard', end: true },
  { to: '/inventory', label: 'Inventory', icon: 'inventory' },
  { to: '/requests', label: 'My requests', icon: 'requests', end: true },
  { to: '/requests/new', label: 'New request', icon: 'plus', roles: ['REQUESTER', 'ADMIN'] },
  { to: '/approvals', label: 'Approval queue', icon: 'queue', roles: ['REVIEWER', 'ADMIN'], end: true },
  { to: '/approvals/history', label: 'Approval history', icon: 'history', roles: ['REVIEWER', 'ADMIN'] },
  { to: '/audit', label: 'Audit trail', icon: 'audit', roles: ['REVIEWER', 'ADMIN'] },
  { to: '/admin/inventory', label: 'Manage inventory', icon: 'admin', roles: ['ADMIN'] },
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

function Brand() {
  return (
    <span className="flex items-center gap-2.5 text-lg font-semibold tracking-tight">
      <svg aria-hidden="true" viewBox="0 0 32 32" className="h-7 w-7"><rect width="32" height="32" rx="7" fill="#2563eb" /><path d="M8 11h16M8 16h16M8 21h16" stroke="#fff" strokeWidth="2.4" strokeLinecap="round" /><circle cx="22" cy="11" r="1.6" fill="#93c5fd" /><circle cx="22" cy="16" r="1.6" fill="#93c5fd" /></svg>
      Rackwise
    </span>
  )
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
        <Brand />
        <Button variant="secondary" aria-expanded={open} aria-controls="sidebar" onClick={() => setOpen((o) => !o)}>Menu</Button>
      </header>
      <aside id="sidebar" className={`${open ? 'block' : 'hidden'} w-full shrink-0 border-r lg:sticky lg:top-0 lg:h-screen border-slate-200 bg-white lg:flex lg:min-h-screen lg:w-60 lg:flex-col dark:border-slate-800 dark:bg-slate-900`}>
        <div className="hidden px-5 py-5 lg:block"><Brand /></div>
        <nav aria-label="Main" className="flex-1 space-y-0.5 px-3 pb-3">
          {visible.map((l) => (
            <NavLink key={l.to} to={l.to} end={l.end} onClick={() => setOpen(false)}
              className={({ isActive }) => `flex items-center gap-3 rounded-lg px-3 py-2 text-sm transition-colors ${isActive ? 'bg-blue-50 font-medium text-blue-800 shadow-[inset_3px_0_0_0_#2563eb] dark:bg-blue-950 dark:text-blue-200' : 'text-slate-700 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800'}`}>
              <Icon name={l.icon} className="h-[18px] w-[18px] shrink-0" />
              {l.label}
            </NavLink>
          ))}
        </nav>
        <div className="space-y-3 border-t border-slate-200 p-4 text-sm dark:border-slate-800">
          <div className="flex items-center gap-3">
            <span aria-hidden="true" className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-blue-100 text-xs font-semibold text-blue-800 dark:bg-blue-950 dark:text-blue-200">
              {(user?.displayName ?? '?').split(' ').map((w) => w[0]).slice(0, 2).join('').toUpperCase()}
            </span>
            <div className="min-w-0">
              <p className="truncate font-medium">{user?.displayName}</p>
              <p className="text-xs text-slate-500 dark:text-slate-400">{user?.roles.join(', ')}</p>
            </div>
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
