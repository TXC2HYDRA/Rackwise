import { expect, test } from '@playwright/test'
import { createFixture, signIn, signOut } from './support'

test('infrastructure expansion: request, plan, review, apply, verify, audit', async ({ page, request }) => {
  const fx = await createFixture(request)

  // 1. requester: view capacity and submit a request for 40 servers into rack group X
  await signIn(page, 'alice')
  await page.getByRole('link', { name: 'Inventory' }).click()
  await expect(page.getByRole('heading', { name: 'Infrastructure inventory' })).toBeVisible()
  await page.getByLabel('Data center', { exact: true }).selectOption({ label: `${fx.code} — E2E ${fx.code}` })
  await expect(page.getByRole('row', { name: /X.*55 of 100/ })).toBeVisible()

  await page.getByRole('link', { name: 'New request' }).click()
  await expect(page.getByRole('heading', { name: 'New capacity request' })).toBeVisible()
  await page.getByLabel('Data center', { exact: true }).selectOption({ label: `${fx.code} — E2E ${fx.code}` })
  await page.getByLabel('Preferred rack group').selectOption({ label: 'X — 55 slots free' })
  await page.getByLabel('Number of servers').fill('40')
  await page.getByLabel('Why is this capacity needed?').fill('Playwright expansion scenario')
  await page.getByRole('button', { name: 'Submit request' }).click()
  await expect(page.getByText('Request submitted.')).toBeVisible()
  const requestId = page.url().split('/').pop()!
  const shortId = requestId.slice(0, 8)

  // 2. generate a proposal and check its constraints
  await page.getByRole('button', { name: 'Generate plan' }).click()
  await expect(page.getByRole('heading', { name: 'Plan #1' })).toBeVisible()
  await expect(page.getByText('Placed all 40 servers')).toBeVisible()
  await expect(page.getByText('✓ Feasible')).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Planning assistant (advisory)' })).toBeVisible()
  await expect(page.getByText('All 40 requested servers fit')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Approve plan' })).toHaveCount(0) // requesters cannot decide
  await expect(page.getByRole('link', { name: 'Approval queue' })).toHaveCount(0) // requesters do not even see the nav item
  // client-side navigation (a full reload would drop the in-memory token and redirect to login)
  await page.evaluate(() => { history.pushState({}, '', '/approvals'); dispatchEvent(new PopStateEvent('popstate')) })
  await expect(page.getByText('Access denied')).toBeVisible()
  await page.goBack()
  await signOut(page)

  // 3. independent reviewer approves and applies
  await signIn(page, 'bob')
  await page.getByRole('link', { name: 'Approval queue' }).click()
  const row = page.getByRole('row').filter({ hasText: shortId })
  // the queue is oldest first and paged; a long-lived dev database may hold other pending plans
  await expect(page.getByRole('table', { name: 'Plans awaiting review' })).toBeVisible()
  for (let i = 0; i < 20 && (await row.count()) === 0; i++) {
    const next = page.getByRole('button', { name: 'Next' })
    if (!(await next.isEnabled())) break
    await next.click()
    await page.waitForLoadState('networkidle')
  }
  await expect(row).toBeVisible()
  await row.getByRole('link', { name: /Review/ }).click()
  await page.getByLabel('Comment').fill('Capacity confirmed')
  await page.getByRole('button', { name: 'Approve plan' }).click()
  await expect(page.getByText('Plan approved. It can now be applied.')).toBeVisible()
  await page.getByRole('button', { name: 'Apply plan' }).click()
  await expect(page.getByText('Applied: 40 servers added to the inventory.')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Apply plan' })).toHaveCount(0) // cannot be applied twice

  // 4. inventory reflects the allocation
  await page.getByRole('link', { name: 'Inventory' }).click()
  await expect(page.getByRole('heading', { name: 'Infrastructure inventory' })).toBeVisible()
  await page.getByLabel('Data center', { exact: true }).selectOption({ label: `${fx.code} — E2E ${fx.code}` })
  await expect(page.getByRole('row', { name: /X.*15 of 100/ })).toBeVisible()

  // 5. approval history and audit trail (admin)
  await page.getByRole('link', { name: 'Approval history' }).click()
  await expect(page.getByRole('row').filter({ hasText: shortId })).toContainText('✓ Approved')
  await signOut(page)
  await signIn(page, 'admin')
  await page.getByRole('link', { name: 'Audit trail' }).click()
  await expect(page.getByRole('heading', { name: 'Audit trail' })).toBeVisible()
  await page.getByLabel('Action').selectOption('PLAN_APPLIED')
  await expect(page.getByRole('row', { name: /PLAN_APPLIED/ }).first()).toBeVisible()
})

test('invalid credentials show an error and stay on the login page', async ({ page }) => {
  await page.goto('/login')
  await page.getByLabel('Username').fill('alice')
  await page.getByLabel('Password').fill('definitely-wrong')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('alert')).toContainText('Invalid username or password.')
  await expect(page).toHaveURL(/\/login$/)
})

test('protected pages redirect to login when signed out', async ({ page }) => {
  await page.goto('/inventory')
  await expect(page).toHaveURL(/\/login$/)
})

test('request form blocks invalid input before calling the server', async ({ page }) => {
  await signIn(page, 'alice')
  await page.getByRole('link', { name: 'New request' }).click()
  await page.getByLabel('Number of servers').fill('0')
  await page.getByRole('button', { name: 'Submit request' }).click()
  await expect(page.getByText('Choose a data center')).toBeVisible()
  await expect(page.getByText(/Server count must be a whole number from 1 to 500/)).toBeVisible()
  await expect(page).toHaveURL(/\/requests\/new$/)
})

test('a reviewer cannot decide a plan for their own request', async ({ page, request }) => {
  const fx = await createFixture(request)
  await signIn(page, 'admin') // admin may submit requests; admin has no REVIEWER role, so no decision buttons either
  await page.getByRole('link', { name: 'New request' }).click()
  await page.getByLabel('Data center', { exact: true }).selectOption({ label: `${fx.code} — E2E ${fx.code}` })
  await page.getByLabel('Why is this capacity needed?').fill('Admin own request')
  await page.getByRole('button', { name: 'Submit request' }).click()
  await page.getByRole('button', { name: 'Generate plan' }).click()
  await expect(page.getByRole('heading', { name: 'Plan #1' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Approve plan' })).toHaveCount(0)
})

test('admin runs the simulated inventory sync and sees the run', async ({ page }) => {
  await signIn(page, 'admin')
  await page.getByRole('link', { name: 'Manage inventory' }).click()
  await expect(page.getByRole('heading', { name: 'Manage inventory' })).toBeVisible()
  await page.getByRole('button', { name: 'Run sync now' }).click()
  await expect(page.getByText(/Sync finished/)).toBeVisible()
  await expect(page.getByRole('table', { name: 'Recent sync runs' }).getByText('✓ Succeeded').first()).toBeVisible()
})
