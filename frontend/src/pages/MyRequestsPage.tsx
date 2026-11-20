import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../app/auth'
import { Button, Card, EmptyState, ErrorState, Loading, PageHeader, Pager, StatusBadge, Table, inputClass, td } from '../components/ui'
import { api, qs } from '../lib/api'
import { dateTime } from '../lib/format'
import type { CapacityRequest, Page } from '../lib/types'

export function MyRequestsPage() {
  const { has } = useAuth()
  const [status, setStatus] = useState('')
  const [page, setPage] = useState(0)
  const q = useQuery({ queryKey: ['requests', status, page], queryFn: () => api<Page<CapacityRequest>>(`/capacity-requests${qs({ status, page, size: 10 })}`) })
  const all = has('REVIEWER', 'ADMIN')
  return (
    <>
      <PageHeader title={all ? 'Capacity requests' : 'My requests'} subtitle={all ? 'All requests you are allowed to see' : 'Requests you submitted'}
        actions={has('REQUESTER', 'ADMIN') ? <Link to="/requests/new"><Button>New request</Button></Link> : undefined} />
      <Card>
        <div className="mb-4 max-w-xs">
          <label htmlFor="status" className="mb-1 block text-sm font-medium">Status</label>
          <select id="status" className={inputClass} value={status} onChange={(e) => { setStatus(e.target.value); setPage(0) }}>
            <option value="">All</option>
            {['SUBMITTED', 'PLANNED', 'APPROVED', 'REJECTED', 'FULFILLED'].map((s) => <option key={s} value={s}>{s.charAt(0) + s.slice(1).toLowerCase()}</option>)}
          </select>
        </div>
        {q.isLoading ? <Loading /> : q.isError ? <ErrorState error={q.error} retry={() => q.refetch()} /> : !q.data?.content.length ? (
          <EmptyState title="No requests yet" hint="Submit a capacity request to get a placement proposal." />
        ) : (
          <>
            <Table caption="Capacity requests" headers={['Request', 'Servers', 'Priority', 'Requester', 'Submitted', 'Status']}>
              {q.data.content.map((r) => (
                <tr key={r.id}>
                  <th scope="row" className={td}><Link className="font-medium text-blue-700 hover:underline dark:text-blue-300" to={`/requests/${r.id}`}>{r.id.slice(0, 8)}</Link></th>
                  <td className={`${td} tabular-nums`}>{r.serverCount}</td>
                  <td className={td}>{r.priority.charAt(0) + r.priority.slice(1).toLowerCase()}</td>
                  <td className={td}>{r.requesterName}</td>
                  <td className={td}>{dateTime(r.createdAt)}</td>
                  <td className={td}><StatusBadge status={r.status} /></td>
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
