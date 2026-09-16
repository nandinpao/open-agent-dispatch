import { defineConfig, devices } from '@playwright/test';
export default defineConfig({testDir:'./tests/e2e',testMatch:/phase7f-operations-export-attachment-ux\.spec\.ts/,use:{baseURL:process.env.PHASE7F_BASE_URL??'http://127.0.0.1:3000',trace:'retain-on-failure'},projects:[{name:'chromium',use:{...devices['Desktop Chrome']}}]});
