import AxeBuilder from '@axe-core/playwright'
import { expect, test } from '@playwright/test'
import { signIn } from './support'

async function check(page: import('@playwright/test').Page, name: string) {
  const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze()
  const bad = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical')
  expect(bad.map((v) => `${name}: ${v.id} (${v.nodes.length}) ${v.help}`), 'serious/critical axe violations').toEqual([])
}

test('login page has no serious accessibility violations', async ({ page }) => {
  await page.goto('/login')
  await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible()
  await check(page, 'login')
})

test('signed-in pages have no serious accessibility violations (light and dark)', async ({ page }) => {
  await signIn(page, 'bob')
  for (const theme of ['Dark mode', 'Light mode']) {
    for (const [link, heading] of [['Dashboard', /Welcome/], ['Inventory', 'Infrastructure inventory'], ['Approval queue', 'Approval queue'], ['Audit trail', 'Audit trail']] as const) {
      await page.getByRole('link', { name: link }).click()
      await expect(page.getByRole('heading', { name: heading }).first()).toBeVisible()
      await check(page, `${link} (${theme === 'Dark mode' ? 'light' : 'dark'})`)
    }
    await page.getByRole('button', { name: theme }).click()
  }
})

test('new request form is accessible', async ({ page }) => {
  await signIn(page, 'alice')
  await page.getByRole('link', { name: 'New request' }).click()
  await expect(page.getByRole('heading', { name: 'New capacity request' })).toBeVisible()
  await check(page, 'new request')
})
