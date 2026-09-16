import { defineConfig, devices } from '@playwright/test';
const baseURL = process.env.PLAYWRIGHT_BASE_URL ?? 'http://127.0.0.1:3000';
export default defineConfig({
  testDir:'./tests/e2e',
  testMatch: /(?:rs9-resource-scope-release-certification|r6-multi-persona-rbac-certification|phase6-rbac-access-live)\.spec\.ts/,
  timeout:180_000,
  expect:{timeout:20_000},
  fullyParallel:false,
  workers:1,
  retries:0,
  reporter:[['list'],['html',{outputFolder:'../.ci-output/rs9-persona-report',open:'never'}],['junit',{outputFile:'../.ci-output/rs9-persona.xml'}]],
  use:{baseURL,...devices['Desktop Chrome'],viewport:{width:1440,height:900},trace:'retain-on-failure',screenshot:'only-on-failure',video:'retain-on-failure',ignoreHTTPSErrors:true,navigationTimeout:30_000,actionTimeout:15_000},
});
