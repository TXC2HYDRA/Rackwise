import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useAuth } from '../app/auth'
import { Button, Card, ErrorState, Field, Loading, Notice, PageHeader, StatusBadge, Table, inputClass, td } from '../components/ui'
import { ApiError, api } from '../lib/api'
import { dateTime, watts } from '../lib/format'
import type { CapacityRequest, Plan } from '../lib/types'

export function PlanDetailPage() {
  const { id } = useParams()
  const { user, has } = useAuth()
  const qc = useQueryClient()
  const [comment, setComment] = useState('')
  const [commentError, setCommentError] = useState('')
  const [message, setMessage] = useState<{ kind: 'success' | 'error'; text: string } | null>(null)

  const plan = useQuery({ queryKey: ['plan', id], queryFn: () => api<Plan>(`/plans/${id}`) })
  const request = useQuery({ queryKey: ['request', plan.data?.requestId], enabled: !!plan.data, queryFn: () => api<CapacityRequest>(`/capacity-requests/${plan.data!.requestId}`) })

  const refresh = () => { qc.invalidateQueries(); }
  const onError = (e: unknown) => setMessage({ kind: 'error', text: e instanceof ApiError ? e.message : 'Action failed' })
  const approve = useMutation({
    mutationFn: () => api<Plan>(`/plans/${id}/approve`, { method: 'POST', body: { contentHash: plan.data!.contentHash, comment: comment.trim() || undefined } }),
    onSuccess: () => { setMessage({ kind: 'success', text: 'Plan approved. It can now be applied.' }); refresh() }, onError,
  })
  const reject = useMutation({
    mutationFn: () => api<Plan>(`/plans/${id}/reject`, { method: 'POST', body: { comment: comment.trim() } }),
    onSuccess: () => { setMessage({ kind: 'success', text: 'Plan rejected.' }); refresh() }, onError,
  })
  const apply = useMutation({
    mutationFn: () => api<{ plan: Plan; serversCreated: number }>(`/plans/${id}/apply`, { method: 'POST' }),
    onSuccess: (r) => { setMessage({ kind: 'success', text: `Applied: ${r.serversCreated} servers added to the inventory.` }); refresh() },
    onError: (e) => { onError(e); refresh() },
  })

  if (plan.isLoading) return <Loading />
  if (plan.isError) return <ErrorState error={plan.error} retry={() => plan.refetch()} />
  const p = plan.data!
  const own = !!request.data && (request.data.requesterId === user?.id || p.createdBy === user?.id)
  const canDecide = has('REVIEWER') && p.status === 'PROPOSED' && !own
  const canApply = has('REVIEWER', 'ADMIN') && p.status === 'APPROVED'
  const busy = approve.isPending || reject.isPending || apply.isPending

  function doReject() {
    if (!comment.trim()) { setCommentError('A comment is required when rejecting'); return }
    setCommentError('')
    reject.mutate()
  }

  return (
    <>
      <PageHeader title={`Plan #${p.planNumber}`} subtitle={`Created ${dateTime(p.createdAt)}`}
        actions={<div className="flex gap-2"><StatusBadge status={p.feasibility} /><StatusBadge status={p.status} /></div>} />
      <div className="space-y-4">
        {message && <Notice kind={message.kind}>{message.text}</Notice>}
        {p.status === 'STALE' && <Notice kind="info">Inventory changed or a newer plan exists, so this plan can no longer be used. Generate a new plan from the request.</Notice>}
        {p.status === 'FAILED' && <Notice kind="error">Application failed: {p.failureReason}</Notice>}

        <Card title="Summary">
          <p className="text-sm">{p.summary}</p>
          <dl className="mt-4 grid gap-4 text-sm sm:grid-cols-4">
            <div><dt className="text-xs uppercase tracking-wide text-slate-500">Requested</dt><dd className="mt-0.5 font-medium tabular-nums">{p.requestedServers}</dd></div>
            <div><dt className="text-xs uppercase tracking-wide text-slate-500">Placed</dt><dd className="mt-0.5 font-medium tabular-nums">{p.allocatedServers}</dd></div>
            <div><dt className="text-xs uppercase tracking-wide text-slate-500">Request</dt><dd className="mt-0.5"><Link className="font-medium text-blue-700 hover:underline dark:text-blue-300" to={`/requests/${p.requestId}`}>{p.requestId.slice(0, 8)}</Link></dd></div>
            <div><dt className="text-xs uppercase tracking-wide text-slate-500">Proposal hash</dt><dd className="mt-0.5 font-mono text-xs" title={p.contentHash}>{p.contentHash.slice(0, 12)}…</dd></div>
          </dl>
        </Card>

        <Card title="Proposed allocations">
          {p.allocations.length === 0 ? <p className="text-sm text-slate-500">No servers could be placed.</p> : (
            <Table caption="Proposed allocations" headers={['Rack group', 'Servers', 'Inventory version']}>
              {p.allocations.map((a) => (
                <tr key={a.rackGroupId}><th scope="row" className={td}>{a.dataCenterCode} / {a.rackGroupCode}</th><td className={`${td} tabular-nums`}>{a.serverCount}</td><td className={`${td} tabular-nums`}>v{a.rackGroupVersion}</td></tr>
              ))}
            </Table>
          )}
        </Card>

        <Card title="Constraint evaluation">
          <Table caption="Constraint evaluation per rack group" headers={['Rack group', 'Free slots', 'Free power', 'Fits by slots', 'Fits by power', 'Limiting', 'Allocated']}>
            {p.evaluation.map((e) => (
              <tr key={e.rackGroupId}>
                <th scope="row" className={td}>{e.rackGroupCode}</th>
                <td className={`${td} tabular-nums`}>{e.freeSlots}</td><td className={`${td} tabular-nums`}>{watts(e.freeWatts)}</td>
                <td className={`${td} tabular-nums`}>{e.maxBySlots}</td><td className={`${td} tabular-nums`}>{e.maxByPower}</td>
                <td className={td}>{e.binding === 'NOT_CONSIDERED' ? 'Excluded by request' : e.binding === 'POWER' ? 'Power' : 'Slots'}</td>
                <td className={`${td} font-medium tabular-nums`}>{e.allocated}</td>
              </tr>
            ))}
          </Table>
        </Card>

        {own && p.status === 'PROPOSED' && has('REVIEWER') && <Notice kind="info">You cannot approve or reject a plan for your own request. Another reviewer must decide.</Notice>}
        {(canDecide || canApply) && (
          <Card title="Decision">
            {canDecide && (
              <div className="space-y-3">
                {p.feasibility !== 'FEASIBLE' && <Notice kind="info">Only fully feasible plans can be approved. This plan can be rejected.</Notice>}
                <Field label="Comment" htmlFor="comment" error={commentError} hint="Required to reject, optional to approve">
                  <textarea id="comment" rows={3} maxLength={500} className={inputClass} value={comment} onChange={(e) => setComment(e.target.value)} aria-invalid={!!commentError} />
                </Field>
                <div className="flex gap-2">
                  <Button onClick={() => approve.mutate()} disabled={busy || p.feasibility !== 'FEASIBLE'} loading={approve.isPending}>Approve plan</Button>
                  <Button variant="danger" onClick={doReject} disabled={busy} loading={reject.isPending}>Reject plan</Button>
                </div>
              </div>
            )}
            {canApply && (
              <div className="space-y-3">
                <p className="text-sm">This plan is approved. Applying it adds {p.allocatedServers} servers to the simulated inventory. Capacity is re-checked at that moment.</p>
                <Button onClick={() => apply.mutate()} disabled={busy} loading={apply.isPending}>Apply plan</Button>
              </div>
            )}
          </Card>
        )}
      </div>
    </>
  )
}
