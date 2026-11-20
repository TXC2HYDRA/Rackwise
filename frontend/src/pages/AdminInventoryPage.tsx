import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Button, Card, ErrorState, Field, Loading, Notice, PageHeader, StatusBadge, Table, inputClass, td } from '../components/ui'
import { ApiError, api } from '../lib/api'
import { watts } from '../lib/format'
import type { CapacitySummary, DataCenter, Page, ServerStatus, ServerView } from '../lib/types'

type Msg = { kind: 'success' | 'error'; text: string }

function useAdminAction<T>(fn: (v: T) => Promise<unknown>, ok: string, setMsg: (m: Msg) => void) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: () => { setMsg({ kind: 'success', text: ok }); qc.invalidateQueries() },
    onError: (e) => setMsg({ kind: 'error', text: e instanceof ApiError ? `${e.message}${e.details.length ? ': ' + e.details.join('; ') : ''}` : 'Action failed' }),
  })
}

export function AdminInventoryPage() {
  const [msg, setMsg] = useState<Msg | null>(null)
  const dcs = useQuery({ queryKey: ['dcs'], queryFn: () => api<DataCenter[]>('/inventory/datacenters') })
  const capacity = useQuery({ queryKey: ['capacity', ''], queryFn: () => api<CapacitySummary>('/inventory/capacity') })
  const servers = useQuery({ queryKey: ['servers-admin'], queryFn: () => api<Page<ServerView>>('/inventory/servers?size=20') })

  const [dcCode, setDcCode] = useState(''); const [dcName, setDcName] = useState('')
  const addDc = useAdminAction(() => api('/inventory/datacenters', { method: 'POST', body: { code: dcCode.trim(), name: dcName.trim() } }), 'Data center created.', setMsg)

  const [rgDc, setRgDc] = useState(''); const [rgCode, setRgCode] = useState(''); const [rgName, setRgName] = useState('')
  const [rgSlots, setRgSlots] = useState('40'); const [rgPower, setRgPower] = useState('20000')
  const addRg = useAdminAction(() => api('/inventory/rack-groups', { method: 'POST', body: { dataCenterId: rgDc, code: rgCode.trim(), name: rgName.trim(), slotCapacity: Number(rgSlots), powerCapacityWatts: Number(rgPower) } }), 'Rack group created.', setMsg)

  const [svRack, setSvRack] = useState(''); const [svHost, setSvHost] = useState(''); const [svPower, setSvPower] = useState('500')
  const addServer = useAdminAction(() => api('/inventory/servers', { method: 'POST', body: { hostname: svHost.trim(), rackGroupId: svRack, cpuCores: 32, ramGb: 256, storageTb: 8, powerWatts: Number(svPower) } }), 'Server added.', setMsg)

  const [editId, setEditId] = useState<string | null>(null); const [limSlots, setLimSlots] = useState(''); const [limPower, setLimPower] = useState('')
  const limits = useAdminAction((id: string) => api(`/inventory/rack-groups/${id}/limits`, { method: 'PUT', body: { slotCapacity: Number(limSlots), powerCapacityWatts: Number(limPower) } }), 'Limits updated.', setMsg)
  const setStatus = useAdminAction(({ id, status }: { id: string; status: ServerStatus }) => api(`/inventory/servers/${id}/status`, { method: 'PUT', body: { status } }), 'Server status updated.', setMsg)

  const submit = (fn: () => void) => (e: FormEvent) => { e.preventDefault(); setMsg(null); fn() }

  return (
    <>
      <PageHeader title="Manage inventory" subtitle="Administrators only. Every change is audited and bumps the rack group inventory version." />
      {msg && <div className="mb-4"><Notice kind={msg.kind}>{msg.text}</Notice></div>}
      <div className="grid gap-6 lg:grid-cols-3">
        <Card title="Add data center">
          <form onSubmit={submit(() => addDc.mutate(undefined))} className="space-y-3">
            <Field label="Code" htmlFor="dcCode" hint="e.g. DC-C"><input id="dcCode" className={inputClass} value={dcCode} onChange={(e) => setDcCode(e.target.value.toUpperCase())} required /></Field>
            <Field label="Name" htmlFor="dcName"><input id="dcName" className={inputClass} value={dcName} onChange={(e) => setDcName(e.target.value)} required /></Field>
            <Button type="submit" loading={addDc.isPending}>Create data center</Button>
          </form>
        </Card>
        <Card title="Add rack group">
          <form onSubmit={submit(() => addRg.mutate(undefined))} className="space-y-3">
            <Field label="Data center" htmlFor="rgDc"><select id="rgDc" className={inputClass} value={rgDc} onChange={(e) => setRgDc(e.target.value)} required><option value="">Select…</option>{dcs.data?.map((d) => <option key={d.id} value={d.id}>{d.code}</option>)}</select></Field>
            <div className="grid grid-cols-2 gap-3">
              <Field label="Code" htmlFor="rgCode"><input id="rgCode" className={inputClass} value={rgCode} onChange={(e) => setRgCode(e.target.value.toUpperCase())} required /></Field>
              <Field label="Name" htmlFor="rgName"><input id="rgName" className={inputClass} value={rgName} onChange={(e) => setRgName(e.target.value)} required /></Field>
              <Field label="Slots" htmlFor="rgSlots"><input id="rgSlots" inputMode="numeric" className={inputClass} value={rgSlots} onChange={(e) => setRgSlots(e.target.value)} required /></Field>
              <Field label="Power (W)" htmlFor="rgPower"><input id="rgPower" inputMode="numeric" className={inputClass} value={rgPower} onChange={(e) => setRgPower(e.target.value)} required /></Field>
            </div>
            <Button type="submit" loading={addRg.isPending}>Create rack group</Button>
          </form>
        </Card>
        <Card title="Add server">
          <form onSubmit={submit(() => addServer.mutate(undefined))} className="space-y-3">
            <Field label="Rack group" htmlFor="svRack"><select id="svRack" className={inputClass} value={svRack} onChange={(e) => setSvRack(e.target.value)} required><option value="">Select…</option>{capacity.data?.rackGroups.map((r) => <option key={r.id} value={r.id}>{r.dataCenterCode} / {r.code}</option>)}</select></Field>
            <Field label="Hostname" htmlFor="svHost" hint="lowercase, e.g. dc-a-x-0099"><input id="svHost" className={inputClass} value={svHost} onChange={(e) => setSvHost(e.target.value)} required /></Field>
            <Field label="Power (W)" htmlFor="svPower"><input id="svPower" inputMode="numeric" className={inputClass} value={svPower} onChange={(e) => setSvPower(e.target.value)} required /></Field>
            <Button type="submit" loading={addServer.isPending}>Add server</Button>
          </form>
        </Card>
      </div>

      <Card title="Rack group limits" className="mt-6">
        {capacity.isLoading ? <Loading /> : capacity.isError ? <ErrorState error={capacity.error} /> : (
          <Table caption="Rack group limits" headers={['Rack group', 'Slots', 'Power', '']}>
            {capacity.data!.rackGroups.map((r) => (
              <tr key={r.id}>
                <th scope="row" className={td}>{r.dataCenterCode} / {r.code}</th>
                <td className={`${td} tabular-nums`}>{r.slotsUsed} used of {r.slotCapacity}</td>
                <td className={`${td} tabular-nums`}>{watts(r.powerUsedWatts)} used of {watts(r.powerCapacityWatts)}</td>
                <td className={td}>
                  {editId === r.id ? (
                    <form className="flex flex-wrap items-end gap-2" onSubmit={submit(() => limits.mutate(r.id, { onSuccess: () => setEditId(null) }))}>
                      <Field label="Slots" htmlFor={`ls-${r.id}`}><input id={`ls-${r.id}`} inputMode="numeric" className={`${inputClass} w-24`} value={limSlots} onChange={(e) => setLimSlots(e.target.value)} /></Field>
                      <Field label="Power (W)" htmlFor={`lp-${r.id}`}><input id={`lp-${r.id}`} inputMode="numeric" className={`${inputClass} w-28`} value={limPower} onChange={(e) => setLimPower(e.target.value)} /></Field>
                      <Button type="submit" loading={limits.isPending}>Save</Button>
                      <Button type="button" variant="secondary" onClick={() => setEditId(null)}>Cancel</Button>
                    </form>
                  ) : <Button variant="secondary" onClick={() => { setEditId(r.id); setLimSlots(String(r.slotCapacity)); setLimPower(String(r.powerCapacityWatts)) }}>Edit limits<span className="sr-only"> for {r.code}</span></Button>}
                </td>
              </tr>
            ))}
          </Table>
        )}
      </Card>

      <Card title="Servers (first 20)" className="mt-6">
        {servers.isLoading ? <Loading /> : servers.isError ? <ErrorState error={servers.error} /> : (
          <Table caption="Servers" headers={['Hostname', 'Location', 'Status', 'Change status']}>
            {servers.data!.content.map((s) => (
              <tr key={s.id}>
                <th scope="row" className={`${td} font-mono text-xs`}>{s.hostname}</th>
                <td className={td}>{s.dataCenterCode} / {s.rackGroupCode}</td>
                <td className={td}><StatusBadge status={s.status} /></td>
                <td className={td}>
                  <label className="sr-only" htmlFor={`st-${s.id}`}>Status of {s.hostname}</label>
                  <select id={`st-${s.id}`} className={`${inputClass} w-44`} value={s.status} onChange={(e) => { setMsg(null); setStatus.mutate({ id: s.id, status: e.target.value as ServerStatus }) }}>
                    <option value="ACTIVE">Active</option><option value="MAINTENANCE">Maintenance</option><option value="DECOMMISSIONED">Decommissioned</option>
                  </select>
                </td>
              </tr>
            ))}
          </Table>
        )}
      </Card>
    </>
  )
}
