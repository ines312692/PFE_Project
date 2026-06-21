import { test, expect } from '@playwright/test';
import { setupPage, BASE_URL } from '../helpers/setup';
import { JOBS } from '../fixtures/mock-data';

const RUNNING_JOB   = JOBS.find(j => j.status === 'RUNNING')!;   // j1
const COMPLETED_JOB = JOBS.find(j => j.status === 'COMPLETED')!;  // j2


test.describe('Job Detail page', () => {


  test.describe('RUNNING job', () => {
    test.beforeEach(async ({ page }) => {
      await setupPage(page);
      await page.goto(`${BASE_URL}/jobs/${RUNNING_JOB.id}`);
    });

    test('shows the job name in the hero card', async ({ page }) => {
      await expect(page.getByText(RUNNING_JOB.name)).toBeVisible();
    });

    test('shows the correct status pill', async ({ page }) => {
      await expect(page.locator('.pill--status-running')).toBeVisible();
    });

    test('shows the target URL as a link', async ({ page }) => {
      await expect(page.getByRole('link', { name: RUNNING_JOB.targetUrl })).toBeVisible();
    });

    test('shows the Pause button for a RUNNING job', async ({ page }) => {
      await expect(page.getByRole('button', { name: 'Pause' })).toBeVisible();
    });

    test('does not show the Run Job button for a RUNNING job', async ({ page }) => {
      await expect(page.getByRole('button', { name: 'Run Job' })).not.toBeVisible();
    });

    test('Edit button navigates to /jobs/:id/edit', async ({ page }) => {
      await page.getByRole('button', { name: 'Edit' }).click();
      await expect(page).toHaveURL(new RegExp(`/jobs/${RUNNING_JOB.id}/edit`));
    });

    test('"Jobs" back button navigates to /jobs', async ({ page }) => {
      await page.locator('.back-btn').click();
      await expect(page).toHaveURL(/\/jobs$/);
    });
  });


  test.describe('COMPLETED job', () => {
    test.beforeEach(async ({ page }) => {
      await setupPage(page);
      await page.goto(`${BASE_URL}/jobs/${COMPLETED_JOB.id}`);
    });

    test('shows Run Job button for COMPLETED job', async ({ page }) => {
      await expect(page.getByRole('button', { name: 'Run Job' })).toBeVisible();
    });

    test('overview tab shows configuration section', async ({ page }) => {
      await expect(page.getByText('Configuration')).toBeVisible();
    });

    test('Results tab appears for COMPLETED job', async ({ page }) => {
      await expect(page.getByRole('button', { name: /Results/ })).toBeVisible();
    });
  });


  test.describe('tab navigation', () => {
    test.beforeEach(async ({ page }) => {
      await setupPage(page);
      await page.goto(`${BASE_URL}/jobs/${RUNNING_JOB.id}`);
    });

    test('clicking Schema tab shows the extraction mode', async ({ page }) => {
      await page.getByRole('button', { name: 'Schema' }).click();
      await expect(page.locator('.mode-badge')).toBeVisible();
    });

    test('clicking Network & Retry tab shows retry policy', async ({ page }) => {
      await page.getByRole('button', { name: 'Network & Retry' }).click();
      await expect(page.getByText('Retry Policy')).toBeVisible();
      await expect(page.getByText('Circuit Breaker')).toBeVisible();
    });

    test('clicking History tab shows execution list', async ({ page }) => {
      await page.getByRole('button', { name: /History/ }).click();
      await expect(page.getByText('No runs yet')).toBeVisible();
    });

    test('active tab has the correct CSS class', async ({ page }) => {
      await page.getByRole('button', { name: 'Schema' }).click();
      const schemaBtn = page.getByRole('button', { name: 'Schema' });
      await expect(schemaBtn).toHaveClass(/tabs__btn--active/);
    });
  });


  test.describe('pause action', () => {
    test('Pause button updates the status pill', async ({ page }) => {
      await setupPage(page);
      await page.goto(`${BASE_URL}/jobs/${RUNNING_JOB.id}`);
      await page.getByRole('button', { name: 'Pause' }).click();
      await expect(page.locator('.pill--status-paused')).toBeVisible();
    });
  });


  test.describe('results tab (COMPLETED job)', () => {
    test.beforeEach(async ({ page }) => {
      await setupPage(page);
      await page.goto(`${BASE_URL}/jobs/${COMPLETED_JOB.id}`);
      await page.getByRole('button', { name: /Results/ }).click();
    });

    test('shows records count in the results pill', async ({ page }) => {
      await expect(page.locator('.mp--purple')).toContainText('2 records');
    });

    test('table view shows extracted data', async ({ page }) => {
      await expect(page.locator('.res-tbl')).toBeVisible();
      await expect(page.getByText('AI Tops Charts')).toBeVisible();
    });

    test('switching to JSON view shows formatted JSON', async ({ page }) => {
      await page.getByRole('button', { name: 'JSON' }).click();
      await expect(page.locator('.res-json pre')).toBeVisible();
    });

    test('Export CSV button does not throw', async ({ page }) => {
      const errors: string[] = [];
      page.on('pageerror', e => errors.push(e.message));
      await page.getByRole('button', { name: 'Export CSV' }).click();
      expect(errors.filter(e => !e.includes('createObjectURL'))).toHaveLength(0);
    });
  });
});
