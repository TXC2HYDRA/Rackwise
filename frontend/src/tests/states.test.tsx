import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { StatusBadge } from '../components/ui'
import { ApprovalQueuePage } from '../pages/ApprovalQueuePage'
import { DashboardPage } from '../pages/DashboardPage'
import { MyRequestsPage } from '../pages/MyRequestsPage'
import { fail, makeUser, ok, renderApp } from './helpers'

const empty = { content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 }

describe('loading, empty and error states', () => {
  it('shows an empty state for requests', async () => {
    renderApp(<MyRequestsPage />, { routes: { 'GET /capacity-requests?page=0&size=10': ok(empty) } })
    expect(await screen.findByText('No requests yet')).toBeInTheDocument()
  })

  it('shows a loading indicator first', () => {
    renderApp(<MyRequestsPage />, { routes: { 'GET /capacity-requests?page=0&size=10': ok(empty) } })
    expect(screen.getByRole('status')).toHaveTextContent('Loading')
  })

  it('shows an error with retry when the queue fails', async () => {
    renderApp(<ApprovalQueuePage />, { user: makeUser(['REVIEWER']), routes: { 'GET /approvals/pending?page=0&size=10': fail(500, 'INTERNAL_ERROR', 'Unexpected server error') } })
    expect(await screen.findByRole('alert')).toHaveTextContent('Unexpected server error')
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument()
  })

  it('shows an empty approval queue', async () => {
    renderApp(<ApprovalQueuePage />, { user: makeUser(['REVIEWER']), routes: { 'GET /approvals/pending?page=0&size=10': ok(empty) } })
    expect(await screen.findByText('The queue is empty')).toBeInTheDocument()
  })

  it('renders dashboard figures from the capacity summary', async () => {
    const summary = {
      totalSlots: 100, usedSlots: 45, availableSlots: 55, totalPowerWatts: 50000, usedPowerWatts: 22500, availablePowerWatts: 27500,
      rackGroups: [{ id: 'rg', dataCenterCode: 'DC-A', code: 'X', name: 'X', slotCapacity: 100, slotsUsed: 45, slotsAvailable: 55, powerCapacityWatts: 50000, powerUsedWatts: 22500, powerAvailableWatts: 27500, version: 0, dataCenterId: 'dc' }],
    }
    renderApp(<DashboardPage />, {
      routes: {
        'GET /inventory/capacity': ok(summary), 'GET /capacity-requests?status=PLANNED&size=1': ok(empty), 'GET /capacity-requests?status=APPROVED&size=1': ok(empty),
        'GET /capacity-requests?status=REJECTED&size=1': ok(empty), 'GET /capacity-requests?status=FULFILLED&size=5': ok(empty),
      },
    })
    expect(await screen.findByText('Available slots')).toBeInTheDocument()
    expect(screen.getByText('55')).toBeInTheDocument()
    expect(screen.getByText('Nothing applied yet')).toBeInTheDocument()
    expect(screen.getByRole('progressbar', { name: 'X slot use' })).toHaveAttribute('aria-valuenow', '45')
  })

  it('status badges always include a text label', () => {
    renderApp(<><StatusBadge status="APPROVED" /><StatusBadge status="REJECTED" /><StatusBadge status="PARTIAL" /></>, { user: null })
    expect(screen.getByText('✓ Approved')).toBeInTheDocument()
    expect(screen.getByText('✕ Rejected')).toBeInTheDocument()
    expect(screen.getByText('⚠ Partial')).toBeInTheDocument()
  })
})
