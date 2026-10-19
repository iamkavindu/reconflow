import {defineConfig} from '@playwright/test';
export default defineConfig({
  testDir: '.', testMatch: '*.spec.js', workers: 1, timeout: 90000,
  use: {baseURL: 'http://localhost:18080', browserName: 'chromium', headless: true},
  reporter: 'list'
});
