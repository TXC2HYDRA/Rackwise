import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Card, EmptyState, ErrorState, Loading, Meter, PageHeader, Pager, StatusBadge, Table, inputClass, td } from '../components/ui'
import { api, qs } from '../lib/api'
import { watts } from '../lib/format'
import type { CapacitySummary, DataCenter, Page, ServerStatus, ServerView } from '../lib/types'

export function InventoryPage() {
  const [dc, setDc] = useState('')
  const [rack, setRack] = useState('')
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(0)
  const dcs = useQuery({ queryKey: ['dcs'], queryFn: () => api<DataCenter[]>('/inventory/datacenters') })
  const capacity = useQuery({ queryKey: ['capacity', dc], queryFn: () => api<CapacitySummary>(`/inventory/capacity${qs({ dataCenterId: dc })}`) })
  const servers = useQuery({
    queryKey: ['servers', rack, status, page],
    queryFn: () => api<Page<ServerView>>(`/inventory/servers${qs({ rackGroupId: rack, status, page, size: 10 })}`),
  })

  return (
    <>
      <PageHeader title="Infrastructure inventory" subtitle="Simulated data centers, rack groups and servers" />
      <div className="mb-4 max-w-xs">
        <label htmlFor="dc" className="mb-1 block text-sm font-medium">Data center</label>
        <select id="dc" className={inputClass} value={dc} onChange={(e) => { setDc(e.target.value); setRack(''); setPage(0) }}>
          <option value="">All data centers</option>
          {dcs.data?.map((d) => <option key={d.id} value={d.id}>{d.code} — {d.name}</option>)}
        </select>
      </div>

      <Card title="Capacity by rack group">
        {capacity.isLoading ? <Loading /> : capacity.isError ? <ErrorState error={capacity.error} retry={() => capacity.refetch()} /> : !capacity.data?.rackGroups.length ? (
          <EmptyState title="No rack groups" hint="An administrator can add them under Manage inventory." />
        ) : (
          <Table caption="Capacity by rack group" headers={['Rack group', 'Slots free', 'Slot use', 'Power free', 'Power use', 'Version']}>
            {capacity.data.rackGroups.map((r) => (
              <tr key={r.id}>
                <th scope="row" className={`${td} font-medium`}>{r.dataCenterCode} / {r.code}<span className="block text-xs font-normal text-slate-500">{r.name}</span></th>
                <td className={`${td} tabular-nums`}>{r.slotsAvailable} of {r.slotCapacity}</td>
                <td className={td}><Meter used={r.slotsUsed} total={r.slotCapacity} name={`${r.code} slot use`} /></td>
                <td className={`${td} tabular-nums`}>{watts(r.powerAvailableWatts)} of {watts(r.powerCapacityWatts)}</td>
                <td className={td}><Meter used={r.powerUsedWatts} total={r.powerCapacityWatts} name={`${r.code} power use`} /></td>
                <td className={`${td} tabular-nums text-slate-500`}>v{r.version}</td>
              </tr>
            ))}
          </Table>
        )}
      </Card>

      <Card title="Servers" className="mt-6">
        <div className="mb-4 flex flex-wrap gap-4">
          <div>
            <label htmlFor="rack" className="mb-1 block text-sm font-medium">Rack group</label>
            <select id="rack" className={inputClass} value={rack} onChange={(e) => { setRack(e.target.value); setPage(0) }}>
              <option value="">All rack groups</option>
              {capacity.data?.rackGroups.map((r) => <option key={r.id} value={r.id}>{r.dataCenterCode} / {r.code}</option>)}
            </select>
          </div>
          <div>
            <label htmlFor="status" className="mb-1 block text-sm font-medium">Status</label>
            <select id="status" className={inputClass} value={status} onChange={(e) => { setStatus(e.target.value as ServerStatus | ''); setPage(0) }}>
              <option value="">Any status</option>
              <option value="ACTIVE">Active</option><option value="MAINTENANCE">Maintenance</option><option value="DECOMMISSIONED">Decommissioned</option>
            </select>
          </div>
        </div>
        {servers.isLoading ? <Loading /> : servers.isError ? <ErrorState error={servers.error} retry={() => servers.refetch()} /> : !servers.data?.content.length ? (
          <EmptyState title="No servers match" hint="Try a different filter." />
        ) : (
          <>
            <Table caption="Servers" headers={['Hostname', 'Location', 'CPU', 'RAM', 'Storage', 'Power', 'Status']}>
              {servers.data.content.map((s) => (
                <tr key={s.id}>
                  <th scope="row" className={`${td} font-mono text-xs font-medium`}>{s.hostname}</th>
                  <td className={td}>{s.dataCenterCode} / {s.rackGroupCode}</td>
                  <td className={`${td} tabular-nums`}>{s.cpuCores} cores</td>
                  <td className={`${td} tabular-nums`}>{s.ramGb} GB</td>
                  <td className={`${td} tabular-nums`}>{s.storageTb} TB</td>
                  <td className={`${td} tabular-nums`}>{s.powerWatts} W</td>
                  <td className={td}><StatusBadge status={s.status} /></td>
                </tr>
              ))}
            </Table>
            <Pager page={page} totalPages={servers.data.totalPages} onPage={setPage} />
          </>
        )}
      </Card>
    </>
  )
}
