import {test, expect} from '@playwright/test';
import fs from 'node:fs';

test('indexed telemetry dashboard renders real measurements and correlated workflow logs', async ({page, request}) => {
  test.skip(process.env.VERIFY_OBSERVABILITY !== 'true', 'Requires the bootstrapped local telemetry stack and probe evidence');
  const evidence = JSON.parse(fs.readFileSync('/tmp/reconflow-telemetry.json', 'utf8'));
  const result = await request.post('http://127.0.0.1:9200/reconflow-logs-*/_search', {
    data: {query: {term: {traceId: evidence.traceId}}}
  });
  expect(result.ok()).toBeTruthy();
  const logs = (await result.json()).hits.hits;
  expect(logs.some(hit => hit._source['log.attributes.workflow@resource_id'] === evidence.caseId)).toBeTruthy();
  const failedQueries = [];
  page.on('response', response => {
    if (response.url().includes('/_msearch') && response.status() >= 400) failedQueries.push(response.status());
  });
  await page.goto('http://127.0.0.1:5601/app/dashboards#/view/reconflow-operations');
  await expect(page.getByText('ReconFlow operations', {exact: true}).first()).toBeVisible({timeout: 60000});
  await expect(page.getByText('HTTP mean latency (seconds)', {exact: true}).first()).toBeVisible();
  await expect(page.locator('canvas, svg.marks').first()).toBeVisible({timeout: 30000});
  await expect(page.getByText(/Error loading|Vega error|Cannot read properties/)).toHaveCount(0);
  expect(failedQueries).toEqual([]);
  await page.screenshot({path: 'screenshots/observability.png', fullPage: true});
  await page.goto('http://127.0.0.1:5601/app/observability-traces#/traces?datasourceId=&traceId=' + evidence.traceId);
  await expect(page.getByText(`Spans (${evidence.traceVerified.spanCount})`, {exact: true})).toBeVisible({timeout: 30000});
  await expect(page.locator('body')).toContainText('http post /api/provider-events/simulate');
  await page.screenshot({path: 'screenshots/observability-waterfall.png', fullPage: true});
  const metricResponse = await request.post('http://127.0.0.1:9200/ss4o_metrics-otel-reconflow-v2-*/_search', {
    data: {size: 1, query: {range: {time: {gte: evidence.startedAt}}}}
  });
  expect(metricResponse.ok()).toBeTruthy();
  const metricIndex = (await metricResponse.json()).hits.hits[0]._index;
  await page.goto('http://127.0.0.1:5601/app/observability-metrics');
  await page.locator('[data-test-subj="comboBoxSearchInput"]').first().click();
  await page.getByText('OpenTelemetry', {exact: true}).click();
  await page.locator('[data-test-subj="comboBoxSearchInput"]').nth(1).click();
  await page.getByText(metricIndex, {exact: true}).last().click();
  await expect(page.getByText('http.server.requests', {exact: true})).toBeVisible({timeout: 30000});
  await page.screenshot({path: 'screenshots/observability-metrics.png', fullPage: true});
});
