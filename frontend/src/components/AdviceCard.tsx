import { useQuery } from '@tanstack/react-query'
import { api } from '../lib/api'
import type { Advice } from '../lib/types'
import { Card, ErrorState, Loading } from './ui'

/** Advisory only: text and what-ifs. Nothing here changes a plan or feeds a decision. */
export function AdviceCard({ planId }: { planId: string }) {
  const q = useQuery({ queryKey: ['advice', planId], queryFn: () => api<Advice>(`/plans/${planId}/advice`) })
  return (
    <Card title="Planning assistant (advisory)">
      {q.isLoading ? <Loading /> : q.isError ? <ErrorState error={q.error} retry={() => q.refetch()} /> : (
        <div className="space-y-3 text-sm">
          <p>{q.data!.narrative}</p>
          {q.data!.risks.length > 0 && (
            <div>
              <h3 className="font-medium">Risks</h3>
              <ul className="mt-1 list-disc space-y-1 pl-5">{q.data!.risks.map((r) => <li key={r}>{r}</li>)}</ul>
            </div>
          )}
          {q.data!.suggestions.length > 0 && (
            <div>
              <h3 className="font-medium">What you could change</h3>
              <ul className="mt-1 list-disc space-y-1 pl-5">{q.data!.suggestions.map((s) => <li key={s.type}>{s.text}</li>)}</ul>
            </div>
          )}
          <p className="text-xs text-slate-500 dark:text-slate-400">
            {q.data!.disclaimer} Source: {q.data!.source === 'LLM_NARRATIVE' ? 'language model text (validated)' : 'deterministic rules'}.
            {q.data!.fallbackReason && ` The language model answer was not used (${q.data!.fallbackReason}).`}
          </p>
        </div>
      )}
    </Card>
  )
}
