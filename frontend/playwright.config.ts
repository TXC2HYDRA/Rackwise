import { defineConfig } from '@playwright/test'

// Needs the backend (dev profile) on :8080 and PostgreSQL. scripts/test-e2e.sh starts everything.
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: { baseURL: 'http://localhost:5173', trace: 'retain-on-failure', screenshot: 'only-on-failure' },
  webServer: {
    command: 'npm run dev -- --port 5173 --strictPort',
    url: 'http://localhost:5173',
    reuseExistingServer: false,
    timeout: 60_000,
    env: { BACKEND_URL: process.env.BACKEND_URL ?? 'http://localhost:8080' },
  },
})
