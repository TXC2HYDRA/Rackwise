import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { useAuth } from '../app/auth'
import { Card, EmptyState, ErrorState, Loading, Meter, PageHeader, Table, td } from '../components/ui'
import { api, qs } from '../lib/api'
import { dateTime, pct, watts } from '../lib/format'
import type { CapacityRequest, CapacitySummary, Page } from '../lib/types'

function Stat({ label, value, sub }: { label: string; value: string | number; sub?: string }) {
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm dark:border-slate-800 dark:bg-slate-900">
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
  const chart = c?.rackGroups.map((r) => ({ name: `${r.dataCenterCode}/${r.code}`, Used: r.slotsUsed, Available: r.slotsAvailable })) ?? []

  return (
    <>
      <PageHeader title={`Welcome, ${user?.displayName.split(' ')[0]}`} subtitle="Capacity and approval overview" />
      {capacity.isLoading ? <Loading /> : capacity.isError ? <ErrorState error={capacity.error} retry={() => capacity.refetch()} /> : c && (
        <>
          <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
            <Stat label="Available slots" value={c.availableSlots} sub={`of ${c.totalSlots} total`} />
            <Stat label="Allocated slots" value={c.usedSlots} sub={`${pct(c.usedSlots, c.totalSlots)}% utilised`} />
            <Stat label="Available power" value={watts(c.availablePowerWatts)} sub={`of ${watts(c.totalPowerWatts)}`} />
            <Stat label={reviewer ? 'Awaiting review' : 'Plans awaiting review'} value={reviewer ? (pending.data?.totalElements ?? '…') : (planned.data?.totalElements ?? '…')} sub={reviewer ? 'proposed plans' : 'your planned requests'} />
          </div>
          <div className="mt-4 grid grid-cols-3 gap-4">
            <Stat label="Approved" value={approved.data?.totalElements ?? '…'} sub="approved, not yet applied" />
            <Stat label="Rejected" value={rejected.data?.totalElements ?? '…'} />
            <Stat label="Planned" value={planned.data?.totalElements ?? '…'} />
          </div>

          <div className="mt-6 grid gap-6 lg:grid-cols-2">
            <Card title="Slot utilisation by rack group">
              {chart.length === 0 ? <EmptyState title="No rack groups yet" /> : (
                <div className="h-64 text-slate-600 dark:text-slate-300" role="img" aria-label={`Bar chart of used and available slots for ${chart.length} rack groups. A table with the same data follows.`}>
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={chart}>
                      <CartesianGrid strokeDasharray="3 3" stroke="#94a3b8" strokeOpacity={0.3} />
                      <XAxis dataKey="name" tick={{ fontSize: 12, fill: 'currentColor' }} />
                      <YAxis tick={{ fontSize: 12, fill: 'currentColor' }} allowDecimals={false} />
                      <Tooltip />
                      <Legend wrapperStyle={{ color: 'currentColor' }} />
                      <Bar dataKey="Used" stackId="s" fill="#2563eb" />
                      <Bar dataKey="Available" stackId="s" fill="#94a3b8" />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              )}
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
