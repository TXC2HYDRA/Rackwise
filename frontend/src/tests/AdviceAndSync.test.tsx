import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { AdviceCard } from '../components/AdviceCard'
import { AdminInventoryPage } from '../pages/AdminInventoryPage'
import { fail, makeUser, ok, renderApp } from './helpers'

const advice = (over = {}) => ({
  source: 'LOCAL', narrative: 'Only 55 of 60 requested servers fit.', risks: ['5 of 60 requested servers do not fit.'],
  suggestions: [{ type: 'REDUCE_REQUEST', text: 'Request 55 servers instead.' }], fallbackReason: null,
  disclaimer: 'Advisory only. Decisions are made by reviewers.', ...over,
})

describe('AdviceCard', () => {
  it('shows narrative, risks, suggestions and the advisory disclaimer', async () => {
    renderApp(<AdviceCard planId="p1" />, { routes: { 'GET /plans/p1/advice': ok(advice()) } })
    expect(await screen.findByText('Only 55 of 60 requested servers fit.')).toBeInTheDocument()
    expect(screen.getByText('5 of 60 requested servers do not fit.')).toBeInTheDocument()
    expect(screen.getByText('Request 55 servers instead.')).toBeInTheDocument()
    expect(screen.getByText(/Advisory only/)).toHaveTextContent('deterministic rules')
  })

  it('says when a language model answer was not used', async () => {
    renderApp(<AdviceCard planId="p1" />, { routes: { 'GET /plans/p1/advice': ok(advice({ fallbackReason: 'TIMEOUT' })) } })
    expect(await screen.findByText(/language model answer was not used \(TIMEOUT\)/)).toBeInTheDocument()
  })

  it('labels validated model text', async () => {
    renderApp(<AdviceCard planId="p1" />, { routes: { 'GET /plans/p1/advice': ok(advice({ source: 'LLM_NARRATIVE' })) } })
    expect(await screen.findByText(/language model text \(validated\)/)).toBeInTheDocument()
  })

  it('shows an error with retry when advice cannot be loaded', async () => {
    renderApp(<AdviceCard planId="p1" />, { routes: { 'GET /plans/p1/advice': fail(500, 'INTERNAL_ERROR', 'Unexpected server error') } })
    expect(await screen.findByRole('alert')).toHaveTextContent('Unexpected server error')
  })
})

describe('inventory sync card', () => {
  const run = (over = {}) => ({
    id: 'r1', source: 'mock-cmdb', status: 'SUCCEEDED', startedAt: '2026-01-01T10:00:00Z', finishedAt: '2026-01-01T10:00:01Z',
    created: 5, updated: 0, unchanged: 0, skipped: 1, skipDetails: ['ext-dca-q-0001: UNKNOWN_RACK_GROUP DC-A/Q'], error: null, ...over,
  })
  const base = {
    'GET /inventory/datacenters': ok([]), 'GET /inventory/capacity': ok({ rackGroups: [] }),
    'GET /inventory/servers?size=20': ok({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
  }

  it('lists recent runs including skipped records', async () => {
    renderApp(<AdminInventoryPage />, { user: makeUser(['ADMIN']), routes: { ...base, 'GET /admin/inventory-sync/runs': ok([run()]) } })
    expect(await screen.findByText(/UNKNOWN_RACK_GROUP DC-A\/Q/)).toBeInTheDocument()
    expect(screen.getByText('✓ Succeeded')).toBeInTheDocument()
  })

  it('runs a sync and reports the outcome', async () => {
    const { api } = renderApp(<AdminInventoryPage />, {
      user: makeUser(['ADMIN']), routes: { ...base, 'GET /admin/inventory-sync/runs': ok([]), 'POST /admin/inventory-sync': ok(run()) },
    })
    await userEvent.click(await screen.findByRole('button', { name: 'Run sync now' }))
    expect(await screen.findByText(/Sync finished/)).toBeInTheDocument()
    expect(api.count('POST /admin/inventory-sync')).toBe(1)
  })

  it('shows the server error when a sync is already running', async () => {
    renderApp(<AdminInventoryPage />, {
      user: makeUser(['ADMIN']),
      routes: { ...base, 'GET /admin/inventory-sync/runs': ok([]), 'POST /admin/inventory-sync': fail(409, 'SYNC_IN_PROGRESS', 'An inventory sync is already running') },
    })
    await userEvent.click(await screen.findByRole('button', { name: 'Run sync now' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('An inventory sync is already running')
  })
})
