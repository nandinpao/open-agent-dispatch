import { defineConfig, devices } from '@playwright/test';
export default defineConfig({
  testDir: './tests/e2e',
  testMatch: /phase7e-integration-access-request\.spec\.ts/,
  fullyParallel: false,
  retries: 0,
  reporter: [['list'], ['junit', { outputFile: '../.ci-output/phase7e-integration-runtime/playwright-junit.xml' }]],
  use: { baseURL: process.env.PHASE7E_BASE_URL ?? 'http://127.0.0.1:3000', trace: 'retain-on-failure', ...devices['Desktop Chrome'] },
});
