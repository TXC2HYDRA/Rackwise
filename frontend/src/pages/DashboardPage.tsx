import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { useAuth } from '../app/auth'
import { Button, Card, EmptyState, ErrorState, Loading, Meter, PageHeader, Table, td } from '../components/ui'
import { api, qs } from '../lib/api'
import { dateTime, pct, watts } from '../lib/format'
import type { CapacityRequest, CapacitySummary, Page } from '../lib/types'

const accents = {
  neutral: 'border-t-slate-300 dark:border-t-slate-700',
  blue: 'border-t-blue-500', green: 'border-t-emerald-500', amber: 'border-t-amber-500', red: 'border-t-red-500',
}

function Stat({ label, value, sub, accent = 'neutral' }: { label: string; value: string | number; sub?: string; accent?: keyof typeof accents }) {
  return (
    <div className={`rounded-xl border border-t-4 border-slate-200 bg-white p-4 shadow-sm dark:border-slate-800 dark:bg-slate-900 ${accents[accent]}`}>
      <p className="text-xs font-medium uppercase tracking-wide text-slate-500 dark:text-slate-400">{label}</p>
      <p className="mt-1 text-2xl font-semibold tabular-nums">{value}</p>
      {sub && <p className="mt-0.5 text-xs text-slate-500 dark:text-slate-400">{sub}</p>}
    </div>
  )
}

function useCount(status: string) {
  return useQuery({ queryKey: ['requests-count', status], queryFn: () => api<Page<CapacityRequest>>(`/capacity-requests${qs({ status, size: 1 })}`) })
}

export function DashboardPage() {
  const { user, has } = useAuth()
  const reviewer = has('REVIEWER', 'ADMIN')
  const capacity = useQuery({ queryKey: ['capacity'], queryFn: () => api<CapacitySummary>('/inventory/capacity') })
  const planned = useCount('PLANNED')
  const approved = useCount('APPROVED')
  const rejected = useCount('REJECTED')
  const pending = useQuery({ queryKey: ['pending-count'], enabled: reviewer, queryFn: () => api<Page<unknown>>('/approvals/pending?size=1') })
  const recent = useQuery({ queryKey: ['recent-allocations'], queryFn: () => api<Page<CapacityRequest>>('/capacity-requests?status=FULFILLED&size=5') })

  const c = capacity.data
  const busiest = [...(c?.rackGroups ?? [])].sort((a, b) => b.slotsUsed / b.slotCapacity - a.slotsUsed / a.slotCapacity).slice(0, 6)

  return (
    <>
      <PageHeader title={`Welcome, ${user?.displayName.split(' ')[0]}`} subtitle="Capacity and approval overview"
        actions={has('REQUESTER', 'ADMIN') ? <Link to="/requests/new"><Button>Create a request</Button></Link> : reviewer ? <Link to="/approvals"><Button>Review pending plans</Button></Link> : undefined} />
      {capacity.isLoading ? <Loading /> : capacity.isError ? <ErrorState error={capacity.error} retry={() => capacity.refetch()} /> : c && (
        <>
          <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
            <Stat accent="blue" label="Available slots" value={c.availableSlots} sub={`of ${c.totalSlots} total`} />
            <Stat accent={pct(c.usedSlots, c.totalSlots) >= 90 ? 'red' : pct(c.usedSlots, c.totalSlots) >= 75 ? 'amber' : 'blue'} label="Allocated slots" value={c.usedSlots} sub={`${pct(c.usedSlots, c.totalSlots)}% utilised`} />
            <Stat accent="blue" label="Available power" value={watts(c.availablePowerWatts)} sub={`of ${watts(c.totalPowerWatts)}`} />
            <Stat accent="amber" label={reviewer ? 'Awaiting review' : 'Plans awaiting review'} value={reviewer ? (pending.data?.totalElements ?? '…') : (planned.data?.totalElements ?? '…')} sub={reviewer ? 'proposed plans' : 'your planned requests'} />
          </div>
          <div className="mt-4 grid grid-cols-3 gap-4">
            <Stat accent="green" label="Approved" value={approved.data?.totalElements ?? '…'} sub="approved, not yet applied" />
            <Stat accent="red" label="Rejected" value={rejected.data?.totalElements ?? '…'} />
            <Stat accent="blue" label="Planned" value={planned.data?.totalElements ?? '…'} />
          </div>

          <div className="mt-6 grid gap-6 lg:grid-cols-2">
            <Card title="Busiest rack groups">
              {busiest.length === 0 ? <EmptyState title="No rack groups yet" /> : (
                <ul className="space-y-3">
                  {busiest.map((r) => (
                    <li key={r.id} className="grid grid-cols-[7rem_1fr_auto] items-center gap-3 text-sm">
                      <span className="truncate font-medium">{r.dataCenterCode} / {r.code}</span>
                      <Meter used={r.slotsUsed} total={r.slotCapacity} name={`${r.code} busiest slot use`} />
                      <span className="text-xs tabular-nums text-slate-500 dark:text-slate-400">{r.slotsAvailable} free</span>
                    </li>
                  ))}
                </ul>
              )}
              <p className="mt-4 text-xs text-slate-500 dark:text-slate-400">Top {busiest.length} of {c.rackGroups.length} by slot use. The full table is below.</p>
            </Card>
            <Card title="Recent allocations">
              {recent.isLoading ? <Loading /> : recent.isError ? <ErrorState error={recent.error} /> : recent.data?.content.length ? (
                <ul className="divide-y divide-slate-100 dark:divide-slate-800">
                  {recent.data.content.map((r) => (
                    <li key={r.id} className="flex items-center justify-between py-2.5 text-sm">
                      <Link className="font-medium text-blue-700 hover:underline dark:text-blue-300" to={`/requests/${r.id}`}>{r.serverCount} servers</Link>
                      <span className="text-xs text-slate-500 dark:text-slate-400">{r.requesterName} · {dateTime(r.createdAt)}</span>
                    </li>
                  ))}
                </ul>
              ) : <EmptyState title="Nothing applied yet" hint="Applied plans appear here." />}
            </Card>
          </div>

          <Card title="Utilisation table" className="mt-6">
            <Table caption="Utilisation by rack group" headers={['Rack group', 'Slots', 'Slot use', 'Power', 'Power use']}>
              {c.rackGroups.map((r) => (
                <tr key={r.id}>
                  <th scope="row" className={`${td} font-medium`}>{r.dataCenterCode} / {r.code}</th>
                  <td className={`${td} tabular-nums`}>{r.slotsUsed} / {r.slotCapacity}</td>
                  <td className={td}><Meter used={r.slotsUsed} total={r.slotCapacity} name={`${r.code} slot use`} /></td>
                  <td className={`${td} tabular-nums`}>{watts(r.powerUsedWatts)} / {watts(r.powerCapacityWatts)}</td>
                  <td className={td}><Meter used={r.powerUsedWatts} total={r.powerCapacityWatts} name={`${r.code} power use`} /></td>
                </tr>
              ))}
            </Table>
          </Card>
        </>
      )}
    </>
  )
}
