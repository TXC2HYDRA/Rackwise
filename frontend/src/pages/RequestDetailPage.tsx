import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { useAuth } from '../app/auth'
import { Button, Card, EmptyState, ErrorState, Loading, Notice, PageHeader, StatusBadge, Table, td } from '../components/ui'
import { api } from '../lib/api'
import { dateTime } from '../lib/format'
import type { CapacityRequest, Plan } from '../lib/types'

export function RequestDetailPage() {
  const { id } = useParams()
  const { user, has } = useAuth()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const created = (useLocation().state as { created?: boolean } | null)?.created
  const req = useQuery({ queryKey: ['request', id], queryFn: () => api<CapacityRequest>(`/capacity-requests/${id}`) })
  const plans = useQuery({ queryKey: ['request-plans', id], queryFn: () => api<Plan[]>(`/capacity-requests/${id}/plans`) })
  const generate = useMutation({
    mutationFn: () => api<Plan>(`/capacity-requests/${id}/plans`, { method: 'POST' }),
    onSuccess: (p) => { qc.invalidateQueries({ queryKey: ['request', id] }); qc.invalidateQueries({ queryKey: ['request-plans', id] }); navigate(`/plans/${p.id}`) },
  })

  if (req.isLoading) return <Loading />
  if (req.isError) return <ErrorState error={req.error} retry={() => req.refetch()} />
  const r = req.data!
  const mine = r.requesterId === user?.id || has('ADMIN')
  const open = r.status !== 'FULFILLED' && r.status !== 'REJECTED'

  return (
    <>
      <PageHeader title={`Request ${r.id.slice(0, 8)}`} subtitle={`Submitted by ${r.requesterName} on ${dateTime(r.createdAt)}`}
        actions={mine && open ? <Button onClick={() => generate.mutate()} loading={generate.isPending}>{plans.data?.length ? 'Generate new plan' : 'Generate plan'}</Button> : undefined} />
      <div className="space-y-4">
        {created && <Notice kind="success">Request submitted. Generate a plan to see where the servers would be placed.</Notice>}
        {generate.isError && <Notice kind="error">{(generate.error as Error).message}</Notice>}
        <Card title="Details" actions={<StatusBadge status={r.status} />}>
          <dl className="grid gap-x-8 gap-y-3 text-sm sm:grid-cols-3">
            {([
              ['Servers', r.serverCount], ['Priority', r.priority], ['CPU', `${r.cpuCores} cores`], ['RAM', `${r.ramGb} GB`],
              ['Storage', `${r.storageTb} TB`], ['Power each', `${r.powerWatts} W`],
              ['Other rack groups', r.allowOtherRackGroups ? 'Allowed' : 'Not allowed'],
            ] as [string, string | number][]).map(([k, v]) => (
              <div key={k}><dt className="text-xs uppercase tracking-wide text-slate-500">{k}</dt><dd className="mt-0.5 font-medium">{v}</dd></div>
            ))}
          </dl>
          <p className="mt-4 whitespace-pre-wrap text-sm text-slate-700 dark:text-slate-300">{r.justification}</p>
        </Card>
        <Card title="Plans">
          {plans.isLoading ? <Loading /> : plans.isError ? <ErrorState error={plans.error} /> : !plans.data?.length ? (
            <EmptyState title="No plan yet" hint={mine ? 'Generate a plan to get a placement proposal.' : undefined} />
          ) : (
            <Table caption="Plans for this request" headers={['Plan', 'Result', 'Servers placed', 'Status', 'Created']}>
              {plans.data.map((p) => (
                <tr key={p.id}>
                  <th scope="row" className={td}><Link className="font-medium text-blue-700 hover:underline dark:text-blue-300" to={`/plans/${p.id}`}>Plan #{p.planNumber}</Link></th>
                  <td className={td}><StatusBadge status={p.feasibility} /></td>
                  <td className={`${td} tabular-nums`}>{p.allocatedServers} of {p.requestedServers}</td>
                  <td className={td}><StatusBadge status={p.status} /></td>
                  <td className={td}>{dateTime(p.createdAt)}</td>
                </tr>
              ))}
            </Table>
          )}
        </Card>
      </div>
    </>
  )
}
