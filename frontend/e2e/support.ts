import { expect, type APIRequestContext, type Page } from '@playwright/test'

export const PASSWORD = process.env.DEV_USER_PASSWORD ?? 'Rackwise-Dev-1!'

export async function signIn(page: Page, username: string) {
  await page.goto('/login')
  await page.getByLabel('Username').fill(username)
  await page.getByLabel('Password').fill(PASSWORD)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible()
}

export async function signOut(page: Page) {
  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible()
}

async function adminToken(request: APIRequestContext) {
  const r = await request.post('/api/v1/auth/login', { data: { username: 'admin', password: PASSWORD } })
  expect(r.ok()).toBeTruthy()
  return (await r.json()).accessToken as string
}

/** Private data center with one rack group "X" (100 slots, 50 kW) holding 45 servers, so runs never share capacity. */
export async function createFixture(request: APIRequestContext) {
  const token = await adminToken(request)
  const headers = { Authorization: `Bearer ${token}` }
  const code = 'E' + Math.random().toString(36).slice(2, 8).toUpperCase()
  const dc = await (await request.post('/api/v1/inventory/datacenters', { headers, data: { code, name: `E2E ${code}` } })).json()
  const rg = await (await request.post('/api/v1/inventory/rack-groups', {
    headers, data: { dataCenterId: dc.id, code: 'X', name: 'Rack X', slotCapacity: 100, powerCapacityWatts: 50000 },
  })).json()
  for (let i = 1; i <= 45; i++) {
    const r = await request.post('/api/v1/inventory/servers', {
      headers, data: { hostname: `${code.toLowerCase()}-x-${String(i).padStart(4, '0')}`, rackGroupId: rg.id, cpuCores: 32, ramGb: 256, storageTb: 8, powerWatts: 500 },
    })
    expect(r.ok()).toBeTruthy()
  }
  return { code, dcId: dc.id as string }
}
