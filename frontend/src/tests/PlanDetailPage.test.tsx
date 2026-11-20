import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { Plan } from '../lib/types'
import { PlanDetailPage } from '../pages/PlanDetailPage'
import { fail, makeUser, ok, renderApp } from './helpers'

const plan = (over: Partial<Plan> = {}): Plan => ({
  id: 'p1', requestId: 'r1', planNumber: 1, status: 'PROPOSED', feasibility: 'FEASIBLE', requestedServers: 40, allocatedServers: 40,
  summary: 'Placed all 40 servers across 1 rack group(s).',
  allocations: [{ rackGroupId: 'rg', rackGroupCode: 'X', dataCenterCode: 'DC-A', serverCount: 40, rackGroupVersion: 0 }],
  evaluation: [{ rackGroupId: 'rg', rackGroupCode: 'X', freeSlots: 55, freeWatts: 27500, maxBySlots: 55, maxByPower: 55, fit: 55, allocated: 40, binding: 'SLOTS' }],
  contentHash: 'a'.repeat(64), createdBy: 'requester-1', createdAt: '2026-01-01T10:00:00Z', appliedAt: null, failureReason: null, ...over,
})
const request = { id: 'r1', requesterId: 'requester-1', requesterName: 'alice', serverCount: 40 }
const render = (p: Plan, user = makeUser(['REVIEWER'], 'reviewer-1'), extra = {}) =>
  renderApp(<PlanDetailPage />, { user, route: '/plans/p1', path: '/plans/:id', routes: { 'GET /plans/p1': ok(p), 'GET /capacity-requests/r1': ok(request), ...extra } })

describe('PlanDetailPage', () => {
  it('lets a different reviewer approve a feasible proposed plan, binding the content hash', async () => {
    const { api } = render(plan(), undefined, { 'POST /plans/p1/approve': ok(plan({ status: 'APPROVED' })) })
    await userEvent.click(await screen.findByRole('button', { name: 'Approve plan' }))
    expect(await screen.findByText(/Plan approved/)).toBeInTheDocument()
    expect(api.calls.find((c) => c.key === 'POST /plans/p1/approve')!.body).toMatchObject({ contentHash: 'a'.repeat(64) })
  })

  it('requires a comment to reject and sends none until provided', async () => {
    const { api } = render(plan(), undefined, { 'POST /plans/p1/reject': ok(plan({ status: 'REJECTED' })) })
    await userEvent.click(await screen.findByRole('button', { name: 'Reject plan' }))
    expect(await screen.findByText('A comment is required when rejecting')).toBeInTheDocument()
    expect(api.count('POST /plans/p1/reject')).toBe(0)
    await userEvent.type(screen.getByLabelText('Comment'), 'Over budget')
    await userEvent.click(screen.getByRole('button', { name: 'Reject plan' }))
    expect(await screen.findByText('Plan rejected.')).toBeInTheDocument()
    expect(api.calls.find((c) => c.key === 'POST /plans/p1/reject')!.body).toEqual({ comment: 'Over budget' })
  })

  it('cannot approve a partial plan but can still reject it', async () => {
    render(plan({ feasibility: 'PARTIAL', allocatedServers: 15, summary: 'PARTIAL: only 15 of 40 servers fit' }))
    expect(await screen.findByRole('button', { name: 'Approve plan' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Reject plan' })).toBeEnabled()
    expect(screen.getByText('⚠ Partial')).toBeInTheDocument()
  })

  it('hides decision buttons for the requester who owns the plan', async () => {
    render(plan(), makeUser(['REVIEWER', 'REQUESTER'], 'requester-1', 'alice'))
    expect(await screen.findByText(/cannot approve or reject a plan for your own request/i)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Approve plan' })).not.toBeInTheDocument()
  })

  it('gives plain requesters no decision controls', async () => {
    render(plan(), makeUser(['REQUESTER'], 'other'))
    await screen.findByText('Plan #1')
    expect(screen.queryByRole('button', { name: 'Approve plan' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Apply plan' })).not.toBeInTheDocument()
  })

  it('offers Apply for approved plans and reports the result', async () => {
    render(plan({ status: 'APPROVED' }), undefined, { 'POST /plans/p1/apply': ok({ plan: plan({ status: 'APPLIED' }), serversCreated: 40 }) })
    await userEvent.click(await screen.findByRole('button', { name: 'Apply plan' }))
    expect(await screen.findByText(/Applied: 40 servers added/)).toBeInTheDocument()
  })

  it('shows the server error when applying fails because inventory changed', async () => {
    render(plan({ status: 'APPROVED' }), undefined, { 'POST /plans/p1/apply': fail(409, 'STALE_INVENTORY', 'Rack group X changed since the plan was computed') })
    await userEvent.click(await screen.findByRole('button', { name: 'Apply plan' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Rack group X changed since the plan was computed')
  })

  it('explains stale plans and shows status as text', async () => {
    render(plan({ status: 'STALE' }))
    expect(await screen.findByText(/can no longer be used/)).toBeInTheDocument()
    expect(screen.getByText('⚠ Stale')).toBeInTheDocument()
  })

  it('shows an error state with retry when the plan cannot be loaded', async () => {
    renderApp(<PlanDetailPage />, { route: '/plans/p1', path: '/plans/:id', routes: { 'GET /plans/p1': fail(404, 'NOT_FOUND', 'Plan not found') } })
    expect(await screen.findByRole('alert')).toHaveTextContent('Plan not found')
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument()
  })
})
