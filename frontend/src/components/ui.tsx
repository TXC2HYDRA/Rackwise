import type { ButtonHTMLAttributes, ReactNode } from 'react'
import { ApiError } from '../lib/api'
import type { Feasibility, PlanStatus, RequestStatus, ServerStatus } from '../lib/types'

export function Card({ title, actions, children, className = '' }: { title?: string; actions?: ReactNode; children: ReactNode; className?: string }) {
  return (
    <section className={`rounded-xl border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-900 ${className}`}>
      {(title || actions) && (
        <header className="flex items-center justify-between gap-3 border-b border-slate-200 px-5 py-3 dark:border-slate-800">
          {title && <h2 className="text-sm font-semibold tracking-tight">{title}</h2>}
          {actions}
        </header>
      )}
      <div className="p-5">{children}</div>
    </section>
  )
}

type Variant = 'primary' | 'secondary' | 'danger'
const variants: Record<Variant, string> = {
  primary: 'bg-blue-600 text-white hover:bg-blue-700 disabled:hover:bg-blue-600',
  secondary: 'border border-slate-300 bg-white text-slate-800 hover:bg-slate-100 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100 dark:hover:bg-slate-800',
  danger: 'bg-red-600 text-white hover:bg-red-700 disabled:hover:bg-red-600',
}

export function Button({ variant = 'primary', loading, children, className = '', ...rest }:
  ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant; loading?: boolean }) {
  return (
    <button
      {...rest}
      disabled={rest.disabled || loading}
      aria-busy={loading || undefined}
      className={`inline-flex items-center justify-center gap-2 rounded-lg px-3.5 py-2 text-sm font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-60 ${variants[variant]} ${className}`}
    >
      {loading && <Spinner small />}
      {children}
    </button>
  )
}

export function Spinner({ small }: { small?: boolean }) {
  return <span aria-hidden className={`inline-block animate-spin rounded-full border-2 border-current border-t-transparent ${small ? 'size-3.5' : 'size-5'}`} />
}

export function Loading({ label = 'Loading…' }: { label?: string }) {
  return <div role="status" className="flex items-center gap-2 py-8 text-sm text-slate-500 dark:text-slate-400"><Spinner />{label}</div>
}

export function EmptyState({ title, hint }: { title: string; hint?: string }) {
  return (
    <div className="py-10 text-center">
      <p className="text-sm font-medium">{title}</p>
      {hint && <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">{hint}</p>}
    </div>
  )
}

export function ErrorState({ error, retry }: { error: unknown; retry?: () => void }) {
  const msg = error instanceof ApiError ? error.message : 'Something went wrong.'
  return (
    <div role="alert" className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-800 dark:border-red-900 dark:bg-red-950 dark:text-red-200">
      <p className="font-medium">Could not load this data</p>
      <p className="mt-0.5">{msg}</p>
      {retry && <Button variant="secondary" className="mt-3" onClick={retry}>Try again</Button>}
    </div>
  )
}

export function Notice({ kind, children }: { kind: 'success' | 'error' | 'info'; children: ReactNode }) {
  const tone = {
    success: 'border-green-200 bg-green-50 text-green-900 dark:border-green-900 dark:bg-green-950 dark:text-green-200',
    error: 'border-red-200 bg-red-50 text-red-800 dark:border-red-900 dark:bg-red-950 dark:text-red-200',
    info: 'border-blue-200 bg-blue-50 text-blue-900 dark:border-blue-900 dark:bg-blue-950 dark:text-blue-200',
  }[kind]
  return <div role={kind === 'error' ? 'alert' : 'status'} className={`rounded-lg border px-4 py-3 text-sm ${tone}`}>{children}</div>
}

export function Field({ label, error, hint, htmlFor, children }: { label: string; error?: string; hint?: string; htmlFor: string; children: ReactNode }) {
  return (
    <div>
      <label htmlFor={htmlFor} className="mb-1 block text-sm font-medium">{label}</label>
      {children}
      {hint && !error && <p id={`${htmlFor}-hint`} className="mt-1 text-xs text-slate-500 dark:text-slate-400">{hint}</p>}
      {error && <p id={`${htmlFor}-error`} role="alert" className="mt-1 text-xs font-medium text-red-700 dark:text-red-400">{error}</p>}
    </div>
  )
}

export const inputClass =
  'block w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm placeholder:text-slate-400 dark:border-slate-700 dark:bg-slate-950'

const tones = {
  green: 'bg-green-100 text-green-900 dark:bg-green-950 dark:text-green-200',
  red: 'bg-red-100 text-red-900 dark:bg-red-950 dark:text-red-200',
  amber: 'bg-amber-100 text-amber-900 dark:bg-amber-950 dark:text-amber-200',
  blue: 'bg-blue-100 text-blue-900 dark:bg-blue-950 dark:text-blue-200',
  slate: 'bg-slate-200 text-slate-800 dark:bg-slate-800 dark:text-slate-200',
}
const label: Record<string, [keyof typeof tones, string]> = {
  SUBMITTED: ['slate', '○ Submitted'], PLANNED: ['blue', '◔ Planned'], APPROVED: ['green', '✓ Approved'],
  REJECTED: ['red', '✕ Rejected'], FULFILLED: ['green', '✓ Fulfilled'],
  PROPOSED: ['blue', '◔ Proposed'], APPLIED: ['green', '✓ Applied'], FAILED: ['red', '✕ Failed'], STALE: ['amber', '⚠ Stale'],
  FEASIBLE: ['green', '✓ Feasible'], PARTIAL: ['amber', '⚠ Partial'], INFEASIBLE: ['red', '✕ Infeasible'],
  ACTIVE: ['green', '● Active'], MAINTENANCE: ['amber', '● Maintenance'], DECOMMISSIONED: ['slate', '○ Decommissioned'],
  SUCCEEDED: ['green', '✓ Succeeded'], RUNNING: ['blue', '◔ Running'], SUCCESS: ['green', '✓ Success'], FAILURE: ['red', '✕ Failure'], DENIED: ['amber', '⛔ Denied'],
}

/** Always shows a text label and glyph, so meaning never depends on colour alone. */
export function StatusBadge({ status }: { status: RequestStatus | PlanStatus | Feasibility | ServerStatus | string }) {
  const [tone, text] = label[status] ?? ['slate', status]
  return <span className={`inline-flex items-center whitespace-nowrap rounded-full px-2.5 py-0.5 text-xs font-medium ${tones[tone]}`}>{text}</span>
}

export function Meter({ used, total, name }: { used: number; total: number; name: string }) {
  const p = total > 0 ? Math.min(100, Math.round((used / total) * 100)) : 0
  const bar = p >= 90 ? 'bg-red-600' : p >= 75 ? 'bg-amber-500' : 'bg-blue-600'
  return (
    <div className="flex items-center gap-3">
      <div role="progressbar" aria-label={name} aria-valuenow={p} aria-valuemin={0} aria-valuemax={100} className="h-2.5 w-full min-w-20 max-w-48 overflow-hidden rounded-full bg-slate-200 dark:bg-slate-800">
        <div className={`h-full rounded-full transition-all ${bar}`} style={{ width: `${p}%` }} />
      </div>
      <span className="text-xs tabular-nums text-slate-600 dark:text-slate-400">{p}%</span>
    </div>
  )
}

export function Pager({ page, totalPages, onPage }: { page: number; totalPages: number; onPage: (p: number) => void }) {
  if (totalPages <= 1) return null
  return (
    <nav aria-label="Pagination" className="mt-4 flex items-center justify-between text-sm">
      <Button variant="secondary" disabled={page === 0} onClick={() => onPage(page - 1)}>Previous</Button>
      <span aria-live="polite">Page {page + 1} of {totalPages}</span>
      <Button variant="secondary" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>Next</Button>
    </nav>
  )
}

export function Table({ headers, children, caption }: { headers: string[]; children: ReactNode; caption: string }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left text-sm">
        <caption className="sr-only">{caption}</caption>
        <thead>
          <tr className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-500 dark:text-slate-400 dark:border-slate-800">
            {headers.map((h) => <th key={h} scope="col" className="px-3 py-2 font-medium">{h}</th>)}
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100 dark:divide-slate-800">{children}</tbody>
      </table>
    </div>
  )
}
export const td = 'px-3 py-2.5 align-top'

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: string; actions?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 className="text-xl font-semibold tracking-tight">{title}</h1>
        {subtitle && <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">{subtitle}</p>}
      </div>
      {actions}
    </div>
  )
}
