import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Card, EmptyState, ErrorState, Loading, PageHeader, Pager, StatusBadge, Table, td } from '../components/ui'
import { api } from '../lib/api'
import { dateTime } from '../lib/format'
import type { Page, QueueItem } from '../lib/types'

export function ApprovalQueuePage() {
  const [page, setPage] = useState(0)
  const q = useQuery({ queryKey: ['pending', page], queryFn: () => api<Page<QueueItem>>(`/approvals/pending?page=${page}&size=10`) })
  return (
    <>
      <PageHeader title="Approval queue" subtitle="Proposed plans waiting for a decision, oldest first" />
      <Card>
        {q.isLoading ? <Loading /> : q.isError ? <ErrorState error={q.error} retry={() => q.refetch()} /> : !q.data?.content.length ? (
          <EmptyState title="The queue is empty" hint="New proposals appear here." />
        ) : (
          <>
            <Table caption="Plans awaiting review" headers={['Request', 'Requester', 'Priority', 'Servers placed', 'Result', 'Proposed', '']}>
              {q.data.content.map(({ plan, request }) => (
                <tr key={plan.id}>
                  <th scope="row" className={td}>{request.id.slice(0, 8)} · plan #{plan.planNumber}</th>
                  <td className={td}>{request.requesterName}</td>
                  <td className={td}>{request.priority.charAt(0) + request.priority.slice(1).toLowerCase()}</td>
                  <td className={`${td} tabular-nums`}>{plan.allocatedServers} of {plan.requestedServers}</td>
                  <td className={td}><StatusBadge status={plan.feasibility} /></td>
                  <td className={td}>{dateTime(plan.createdAt)}</td>
                  <td className={td}><Link className="font-medium text-blue-700 hover:underline dark:text-blue-300" to={`/plans/${plan.id}`}>Review<span className="sr-only"> plan {plan.planNumber} of request {request.id.slice(0, 8)}</span></Link></td>
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
