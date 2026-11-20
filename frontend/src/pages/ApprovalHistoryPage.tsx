import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Card, EmptyState, ErrorState, Loading, PageHeader, Pager, StatusBadge, Table, td } from '../components/ui'
import { api } from '../lib/api'
import { dateTime } from '../lib/format'
import type { HistoryItem, Page } from '../lib/types'

export function ApprovalHistoryPage() {
  const [page, setPage] = useState(0)
  const q = useQuery({ queryKey: ['history', page], queryFn: () => api<Page<HistoryItem>>(`/approvals/history?page=${page}&size=10`) })
  return (
    <>
      <PageHeader title="Approval history" subtitle="Past approval and rejection decisions" />
      <Card>
        {q.isLoading ? <Loading /> : q.isError ? <ErrorState error={q.error} retry={() => q.refetch()} /> : !q.data?.content.length ? (
          <EmptyState title="No decisions yet" />
        ) : (
          <>
            <Table caption="Approval decisions" headers={['Plan', 'Requester', 'Decision', 'Reviewer', 'Comment', 'Decided', 'Plan status']}>
              {q.data.content.map((h) => (
                <tr key={h.id}>
                  <th scope="row" className={td}><Link className="font-medium text-blue-700 hover:underline dark:text-blue-300" to={`/plans/${h.planId}`}>{h.requestId.slice(0, 8)} · #{h.planNumber}</Link></th>
                  <td className={td}>{h.requesterName}</td>
                  <td className={td}><StatusBadge status={h.decision} /></td>
                  <td className={td}>{h.reviewerName}</td>
                  <td className={`${td} max-w-xs break-words`}>{h.comment ?? '—'}</td>
                  <td className={td}>{dateTime(h.decidedAt)}</td>
                  <td className={td}><StatusBadge status={h.planStatus} /></td>
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
