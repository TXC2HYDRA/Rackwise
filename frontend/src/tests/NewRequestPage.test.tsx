import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { NewRequestPage, validate, type RequestForm } from '../pages/NewRequestPage'
import { fail, makeUser, ok, renderApp } from './helpers'

const base: RequestForm = {
  dataCenterId: 'dc1', preferredRackGroupId: '', allowOtherRackGroups: false, serverCount: '40', cpuCores: '32',
  ramGb: '256', storageTb: '8', powerWatts: '500', priority: 'NORMAL', justification: 'Growth',
}
const dcs = [{ id: 'dc1', code: 'DC-A', name: 'Data Center A' }]
const capacity = { rackGroups: [{ id: 'rg1', code: 'X', slotsAvailable: 55 }] }

async function chooseDataCenter() {
  await screen.findByRole('option', { name: /DC-A/ })
  await userEvent.selectOptions(screen.getByLabelText('Data center'), 'dc1')
}

describe('validate()', () => {
  it('accepts a good form', () => expect(validate(base)).toEqual({}))
  it.each([
    ['serverCount', '0'], ['serverCount', '-3'], ['serverCount', '501'], ['serverCount', ''], ['serverCount', '2.5'],
    ['cpuCores', '4'], ['ramGb', '8'], ['storageTb', '0'], ['powerWatts', '99'], ['powerWatts', 'abc'],
  ] as const)('rejects %s=%s', (k, v) => expect(validate({ ...base, [k]: v })).toHaveProperty(k))
  it('requires data center and justification', () => {
    expect(validate({ ...base, dataCenterId: '', justification: '  ' })).toEqual({
      dataCenterId: 'Choose a data center', justification: 'Explain why this capacity is needed',
    })
  })
})

describe('NewRequestPage', () => {
  const routes = { 'GET /inventory/datacenters': ok(dcs), 'GET /inventory/capacity?dataCenterId=dc1': ok(capacity) }

  it('shows field errors and does not submit an invalid form', async () => {
    const { api } = renderApp(<NewRequestPage />, { user: makeUser(['REQUESTER']), routes })
    await userEvent.click(await screen.findByRole('button', { name: 'Submit request' }))
    expect(await screen.findByText('Choose a data center')).toBeInTheDocument()
    expect(screen.getByText('Explain why this capacity is needed')).toBeInTheDocument()
    expect(api.count('POST /capacity-requests')).toBe(0)
  })

  it('submits a valid request with an Idempotency-Key and the typed numbers', async () => {
    const { api } = renderApp(<NewRequestPage />, {
      routes: { ...routes, 'POST /capacity-requests': ok({ id: 'r-1' }), 'GET /capacity-requests/r-1': ok({}) },
    })
    await chooseDataCenter()
    await screen.findByRole('option', { name: /X — 55 slots free/ })
    await userEvent.selectOptions(screen.getByLabelText('Preferred rack group'), 'rg1')
    await userEvent.type(screen.getByLabelText('Why is this capacity needed?'), 'Q4 launch')
    await userEvent.click(screen.getByRole('button', { name: 'Submit request' }))
    const post = api.calls.find((c) => c.key === 'POST /capacity-requests')!
    expect(post.body).toMatchObject({ dataCenterId: 'dc1', preferredRackGroupId: 'rg1', serverCount: 40, cpuCores: 32, justification: 'Q4 launch' })
    expect(post.headers['Idempotency-Key']).toMatch(/[0-9a-f-]{36}/)
  })

  it('maps server-side field errors onto the form', async () => {
    renderApp(<NewRequestPage />, {
      routes: { ...routes, 'POST /capacity-requests': fail(400, 'VALIDATION_FAILED', 'Request validation failed', ['justification: must not be blank']) },
    })
    await chooseDataCenter()
    await userEvent.type(screen.getByLabelText('Why is this capacity needed?'), 'ok')
    await userEvent.click(screen.getByRole('button', { name: 'Submit request' }))
    expect(await screen.findByText('must not be blank')).toBeInTheDocument()
    expect(screen.getByText('Please fix the highlighted fields.')).toBeInTheDocument()
  })

  it('shows a general error when the server rejects the request', async () => {
    renderApp(<NewRequestPage />, { routes: { ...routes, 'POST /capacity-requests': fail(422, 'UNKNOWN_DATA_CENTER', 'Data center does not exist') } })
    await chooseDataCenter()
    await userEvent.type(screen.getByLabelText('Why is this capacity needed?'), 'ok')
    await userEvent.click(screen.getByRole('button', { name: 'Submit request' }))
    expect(await screen.findByText('Data center does not exist')).toBeInTheDocument()
  })
})
