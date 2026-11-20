import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Card, EmptyState, ErrorState, Loading, PageHeader, Pager, StatusBadge, Table, inputClass, td } from '../components/ui'
import { api, qs } from '../lib/api'
import { dateTime } from '../lib/format'
import type { AuditEvent, Page } from '../lib/types'

const ACTIONS = ['LOGIN', 'USER_ROLES_CHANGED', 'DATA_CENTER_CREATED', 'RACK_GROUP_CREATED', 'RACK_GROUP_LIMITS_CHANGED', 'SERVER_ADDED',
  'SERVER_STATUS_CHANGED', 'REQUEST_CREATED', 'PLAN_GENERATED', 'PLAN_APPROVED', 'PLAN_REJECTED', 'PLAN_APPLIED', 'PLAN_APPLY_FAILED', 'PLAN_MARKED_STALE']
const TARGETS = ['USER', 'DATA_CENTER', 'RACK_GROUP', 'SERVER', 'CAPACITY_REQUEST', 'PLAN']

export function AuditPage() {
  const [action, setAction] = useState('')
  const [targetType, setTargetType] = useState('')
  const [page, setPage] = useState(0)
  const q = useQuery({ queryKey: ['audit', action, targetType, page], queryFn: () => api<Page<AuditEvent>>(`/audit-events${qs({ action, targetType, page, size: 15 })}`) })
  return (
    <>
      <PageHeader title="Audit trail" subtitle="Append-only record of security-relevant and business actions" />
      <Card>
        <div className="mb-4 flex flex-wrap gap-4">
          <div>
            <label htmlFor="action" className="mb-1 block text-sm font-medium">Action</label>
            <select id="action" className={inputClass} value={action} onChange={(e) => { setAction(e.target.value); setPage(0) }}>
              <option value="">Any action</option>{ACTIONS.map((a) => <option key={a} value={a}>{a}</option>)}
            </select>
          </div>
          <div>
            <label htmlFor="target" className="mb-1 block text-sm font-medium">Target</label>
            <select id="target" className={inputClass} value={targetType} onChange={(e) => { setTargetType(e.target.value); setPage(0) }}>
              <option value="">Any target</option>{TARGETS.map((a) => <option key={a} value={a}>{a}</option>)}
            </select>
          </div>
        </div>
        {q.isLoading ? <Loading /> : q.isError ? <ErrorState error={q.error} retry={() => q.refetch()} /> : !q.data?.content.length ? (
          <EmptyState title="No audit events match" />
        ) : (
          <>
            <Table caption="Audit events" headers={['When', 'Actor', 'Action', 'Target', 'Result', 'Details']}>
              {q.data.content.map((e) => (
                <tr key={e.id}>
                  <td className={`${td} whitespace-nowrap`}>{dateTime(e.occurredAt)}</td>
                  <td className={td}>{e.actorName}</td>
                  <td className={`${td} font-mono text-xs`}>{e.action}</td>
                  <td className={td}>{e.targetType}<span className="block font-mono text-xs text-slate-500 dark:text-slate-400">{e.targetId.slice(0, 13)}</span></td>
                  <td className={td}><StatusBadge status={e.result} /></td>
                  <td className={td}>
                    <details>
                      <summary className="cursor-pointer text-xs text-blue-700 dark:text-blue-300">Show</summary>
                      <dl className="mt-1 space-y-1 text-xs">
                        {e.detail && <div><dt className="inline font-medium">Note: </dt><dd className="inline">{e.detail}</dd></div>}
                        {e.before && <div><dt className="inline font-medium">Before: </dt><dd className="inline font-mono break-all">{e.before}</dd></div>}
                        {e.after && <div><dt className="inline font-medium">After: </dt><dd className="inline font-mono break-all">{e.after}</dd></div>}
                        <div><dt className="inline font-medium">Correlation: </dt><dd className="inline font-mono">{e.correlationId ?? '—'}</dd></div>
                      </dl>
                    </details>
                  </td>
                </tr>
              ))}
            </Table>
            <Pager page={page} totalPages={q.data.totalPages} onPage={setPage} />
          </>
        )}
      </Card>
    </>
  )
}
