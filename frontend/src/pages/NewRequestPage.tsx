import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button, Card, Field, Notice, PageHeader, inputClass } from '../components/ui'
import { ApiError, api, qs } from '../lib/api'
import type { CapacityRequest, CapacitySummary, DataCenter, Priority } from '../lib/types'

const num = (v: string) => (v.trim() === '' ? NaN : Number(v))

export interface RequestForm {
  dataCenterId: string; preferredRackGroupId: string; allowOtherRackGroups: boolean
  serverCount: string; cpuCores: string; ramGb: string; storageTb: string; powerWatts: string
  priority: Priority; justification: string
}

export function validate(f: RequestForm): Record<string, string> {
  const e: Record<string, string> = {}
  const range = (k: keyof RequestForm, label: string, min: number, max: number) => {
    const n = num(f[k] as string)
    if (!Number.isInteger(n) || n < min || n > max) e[k] = `${label} must be a whole number from ${min} to ${max}`
  }
  if (!f.dataCenterId) e.dataCenterId = 'Choose a data center'
  range('serverCount', 'Server count', 1, 500)
  range('cpuCores', 'CPU cores', 8, 128)
  range('ramGb', 'RAM', 32, 2048)
  range('storageTb', 'Storage', 1, 100)
  range('powerWatts', 'Power per server', 100, 3000)
  if (!f.justification.trim()) e.justification = 'Explain why this capacity is needed'
  else if (f.justification.length > 1000) e.justification = 'Keep the justification under 1000 characters'
  return e
}

export function NewRequestPage() {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const [form, setForm] = useState<RequestForm>({
    dataCenterId: '', preferredRackGroupId: '', allowOtherRackGroups: false, serverCount: '40',
    cpuCores: '32', ramGb: '256', storageTb: '8', powerWatts: '500', priority: 'NORMAL', justification: '',
  })
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [idempotencyKey] = useState(() => crypto.randomUUID())
  const dcs = useQuery({ queryKey: ['dcs'], queryFn: () => api<DataCenter[]>('/inventory/datacenters') })
  const racks = useQuery({
    queryKey: ['capacity', form.dataCenterId], enabled: !!form.dataCenterId,
    queryFn: () => api<CapacitySummary>(`/inventory/capacity${qs({ dataCenterId: form.dataCenterId })}`),
  })

  const create = useMutation({
    mutationFn: () => api<CapacityRequest>('/capacity-requests', {
      method: 'POST',
      headers: { 'Idempotency-Key': idempotencyKey },
      body: {
        dataCenterId: form.dataCenterId, preferredRackGroupId: form.preferredRackGroupId || null,
        allowOtherRackGroups: form.allowOtherRackGroups, serverCount: num(form.serverCount), cpuCores: num(form.cpuCores),
        ramGb: num(form.ramGb), storageTb: num(form.storageTb), powerWatts: num(form.powerWatts),
        priority: form.priority, justification: form.justification.trim(),
      },
    }),
    onSuccess: (r) => { qc.invalidateQueries({ queryKey: ['requests'] }); navigate(`/requests/${r.id}`, { state: { created: true } }) },
    onError: (err) => { if (err instanceof ApiError) setErrors(err.fieldErrors()) },
  })

  const set = <K extends keyof RequestForm>(k: K, v: RequestForm[K]) => setForm((f) => ({ ...f, [k]: v }))
  const submit = (e: FormEvent) => {
    e.preventDefault()
    const v = validate(form)
    setErrors(v)
    if (Object.keys(v).length === 0) create.mutate()
  }
  const field = (k: keyof RequestForm, label: string, hint?: string) => (
    <Field label={label} htmlFor={k} error={errors[k]} hint={hint}>
      <input id={k} inputMode="numeric" className={inputClass} value={form[k] as string} onChange={(e) => set(k, e.target.value as never)}
        aria-invalid={!!errors[k]} aria-describedby={errors[k] ? `${k}-error` : hint ? `${k}-hint` : undefined} />
    </Field>
  )

  return (
    <>
      <PageHeader title="New capacity request" subtitle="Ask for servers; the planner will propose a placement you can review." />
      <form onSubmit={submit} noValidate className="max-w-3xl space-y-6">
        {create.isError && !Object.keys(errors).length && <Notice kind="error">{(create.error as Error).message}</Notice>}
        {create.isError && !!Object.keys(errors).length && <Notice kind="error">Please fix the highlighted fields.</Notice>}
        <Card title="Placement">
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Data center" htmlFor="dataCenterId" error={errors.dataCenterId}>
              <select id="dataCenterId" className={inputClass} value={form.dataCenterId} aria-invalid={!!errors.dataCenterId}
                onChange={(e) => setForm((f) => ({ ...f, dataCenterId: e.target.value, preferredRackGroupId: '' }))}>
                <option value="">Select…</option>
                {dcs.data?.map((d) => <option key={d.id} value={d.id}>{d.code} — {d.name}</option>)}
              </select>
            </Field>
            <Field label="Preferred rack group" htmlFor="preferredRackGroupId" error={errors.preferredRackGroupId} hint="Optional">
              <select id="preferredRackGroupId" className={inputClass} value={form.preferredRackGroupId} disabled={!form.dataCenterId}
                onChange={(e) => set('preferredRackGroupId', e.target.value)}>
                <option value="">No preference</option>
                {racks.data?.rackGroups.map((r) => <option key={r.id} value={r.id}>{r.code} — {r.slotsAvailable} slots free</option>)}
              </select>
            </Field>
          </div>
          <label className="mt-4 flex items-center gap-2 text-sm">
            <input type="checkbox" checked={form.allowOtherRackGroups} onChange={(e) => set('allowOtherRackGroups', e.target.checked)} />
            Allow other rack groups in this data center if the preferred one is too small
          </label>
        </Card>
        <Card title="Server configuration">
          <div className="grid gap-4 sm:grid-cols-3">
            {field('serverCount', 'Number of servers', '1–500')}
            {field('cpuCores', 'CPU cores', '8–128')}
            {field('ramGb', 'RAM (GB)', '32–2048')}
            {field('storageTb', 'Storage (TB)', '1–100')}
            {field('powerWatts', 'Power per server (W)', '100–3000')}
            <Field label="Priority" htmlFor="priority">
              <select id="priority" className={inputClass} value={form.priority} onChange={(e) => set('priority', e.target.value as Priority)}>
                <option value="LOW">Low</option><option value="NORMAL">Normal</option><option value="HIGH">High</option><option value="CRITICAL">Critical</option>
              </select>
            </Field>
          </div>
        </Card>
        <Card title="Business justification">
          <Field label="Why is this capacity needed?" htmlFor="justification" error={errors.justification}>
            <textarea id="justification" rows={4} maxLength={1000} className={inputClass} value={form.justification}
              onChange={(e) => set('justification', e.target.value)} aria-invalid={!!errors.justification} />
          </Field>
        </Card>
        <Button type="submit" loading={create.isPending}>Submit request</Button>
      </form>
    </>
  )
}
