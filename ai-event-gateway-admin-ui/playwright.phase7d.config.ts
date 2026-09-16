import { defineConfig, devices } from '@playwright/test';
export default defineConfig({
  testDir: './tests/e2e', testMatch: 'phase7d-issue-agent-ux.spec.ts', timeout: 120_000,
  expect: { timeout: 20_000 }, fullyParallel: false, workers: 1, retries: process.env.CI ? 1 : 0,
  reporter: [['list'], ['html', { outputFolder: '../.ci-output/phase7d/playwright-report', open: 'never' }], ['junit', { outputFile: '../.ci-output/phase7d/playwright-junit.xml' }]],
  use: { baseURL: process.env.PLAYWRIGHT_BASE_URL ?? 'http://127.0.0.1:3000', trace: 'retain-on-failure', screenshot: 'only-on-failure', video: 'retain-on-failure', ignoreHTTPSErrors: true },
  projects: [
    { name: 'chromium-phase7d-desktop', use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } } },
    { name: 'chromium-phase7d-mobile', use: { ...devices['Pixel 7'] } },
  ],
});
