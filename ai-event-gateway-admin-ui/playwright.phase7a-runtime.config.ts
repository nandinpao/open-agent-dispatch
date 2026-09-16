import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './tests/e2e',
  testMatch: 'phase7a-runtime-certification.spec.ts',
  timeout: 120_000,
  expect: { timeout: 20_000 },
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: [
    ['list'],
    ['html', { outputFolder: '../.ci-output/phase7a-runtime/playwright-report', open: 'never' }],
    ['junit', { outputFile: '../.ci-output/phase7a-runtime/playwright-junit.xml' }],
  ],
  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL ?? 'http://127.0.0.1:3000',
    trace: 'retain-on-failure', screenshot: 'only-on-failure', video: 'retain-on-failure',
    ignoreHTTPSErrors: true, navigationTimeout: 30_000, actionTimeout: 15_000,
  },
  webServer: [
    {
      command: 'node scripts/phase7a-runtime/mock-core-server.mjs',
      url: 'http://127.0.0.1:18080/__certification__/health',
      reuseExistingServer: !process.env.CI,
      timeout: 30_000,
    },
    {
      command: 'node scripts/run-next-start.mjs -p 3000',
      url: 'http://127.0.0.1:3000/tasks',
      reuseExistingServer: !process.env.CI,
      timeout: 90_000,
      env: {
        ...process.env,
        CORE_BACKEND_ORIGIN: 'http://127.0.0.1:18080',
        NEXT_PUBLIC_CORE_API_BASE_URL: '/core-api',
        NEXT_PUBLIC_USE_MOCK: 'true',
        NEXT_PUBLIC_ALLOW_MOCK_DATA: 'true',
        NEXT_PUBLIC_ALLOW_FIXTURE_DATA: 'true',
        NEXT_PUBLIC_AUTH_ENABLED: 'false',
        NEXT_PUBLIC_ADMIN_UI_ENV: 'test',
      },
    },
  ],
  projects: [{ name: 'chromium-phase7a', use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } } }],
});
