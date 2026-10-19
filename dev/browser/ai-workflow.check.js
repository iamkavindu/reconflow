import {test, expect} from '@playwright/test';
import fs from 'node:fs';

test('AI-enabled browser investigation shows citations and requires an explicit human resolution', async ({page}) => {
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  const demo = JSON.parse(fs.readFileSync('/tmp/reconflow-ai-demo.json', 'utf8'));
  expect(demo.aiAcceptance).toEqual({stateUnchanged: true, citationsReadable: true,
    lifecycleAuditVerified: true, tenantIsolationVerified: true});
  const fee = demo.scenarios.find(row => row.name === 'provider_fee');
  await page.goto('/');
  await page.getByRole('button', {name: 'Sign in', exact: true}).click();
  await page.locator('#username').fill('analyst');
  await page.locator('#password').fill('demo-password');
  await page.locator('#kc-login').click();
  await expect(page.getByRole('heading', {name: 'Reconciliation overview'})).toBeVisible();
  await page.getByRole('button', {name: 'Exception cases', exact: true}).click();
  await page.getByLabel('Case ID', {exact: true}).fill(fee.caseId);
  await page.getByRole('button', {name: 'Search', exact: true}).click();
  await page.getByRole('button', {name: 'Open', exact: true}).click();
  await expect(page.locator('.badge').first()).toHaveText('OPEN');
  await page.getByRole('button', {name: 'Start review', exact: true}).click();
  const request = page.waitForResponse(response => response.url().endsWith(`/api/cases/${fee.caseId}/investigations`)
    && response.request().method() === 'POST');
  await page.getByRole('button', {name: 'Request AI investigation', exact: true}).click();
  const response = await request;
  expect(response.status()).toBe(202);
  const investigation = (await response.json()).investigationId.value;
  await expect(page.getByRole('status')).toContainText('Investigation requested');
  const findings = page.locator('article').filter({has: page.getByRole('heading', {name: investigation, exact: true})});
  await expect(async () => {
    await page.getByRole('button', {name: 'Refresh case', exact: true}).click();
    await expect(findings.locator('.badge')).toHaveText('COMPLETED');
  }).toPass({timeout: 30000, intervals: [500]});
  await expect(findings).toContainText('The reported fee explains the difference; human review is required.');
  await expect(findings).toContainText('The contractual tariff has not been verified.');
  await findings.getByRole('button', {name: 'Read cited passage', exact: true}).click();
  await expect(findings.getByRole('heading', {name: 'Cited documentation'})).toBeVisible();
  await expect(findings).toContainText('An analyst must verify settlement evidence');
  await expect(page.locator('.badge').first()).toHaveText('UNDER REVIEW');
  await expect(page.getByLabel('Resolution type')).toHaveValue('MANUAL_REVIEW_COMPLETED');
  await expect(page.getByLabel('Reason for resolution')).toHaveValue('');
  await expect(page.getByRole('checkbox')).not.toBeChecked();
  await page.screenshot({path: 'screenshots/ai-investigation.png', fullPage: true});
  await page.getByLabel('Resolution type').selectOption('ACCEPTED_FEE_VARIANCE');
  await page.getByLabel('Reason for resolution').fill('Human review of the reported fee and cited policy accepted this variance.');
  await page.getByLabel('Supporting evidence references').fill('investigation:' + investigation);
  await page.getByRole('checkbox').check();
  await page.getByRole('button', {name: 'Confirm resolution', exact: true}).click();
  await expect(page.locator('.badge').first()).toHaveText('RESOLVED');
  await expect(page.getByRole('heading', {name: 'Human resolution'})).toBeVisible();
  await expect(page.getByRole('button', {name: 'Request AI investigation'})).toHaveCount(0);
  await expect(async () => {
    await page.getByRole('button', {name: 'Refresh case', exact: true}).click();
    await page.getByRole('button', {name: 'Load audit history', exact: true}).last().click();
    await expect(page.getByRole('cell', {name: 'CASE RESOLVED', exact: true})).toBeVisible();
  }).toPass({timeout: 10000, intervals: [500]});
  await page.screenshot({path: 'screenshots/ai-resolved-case.png', fullPage: true});
  await page.getByRole('button', {name: 'View financial evidence'}).click();
  await expect(page.locator('.badge').first()).toHaveText('REVIEW REQUIRED');
  expect(errors).toEqual([]);
});
