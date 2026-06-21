import { test, expect } from '@playwright/test';
import { setupPage, BASE_URL } from '../helpers/setup';
import { JOBS } from '../fixtures/mock-data';


test.describe('Job List page', () => {

  test.beforeEach(async ({ page }) => {
    await setupPage(page);
    await page.goto(`${BASE_URL}/jobs`);
  });

  test.describe('header stats', () => {
    test('displays the correct total count', async ({ page }) => {
      const total = page.locator('.jobs-stat__value').first();
      await expect(total).toHaveText(String(JOBS.length));
    });

    test('displays the running count correctly', async ({ page }) => {
      const running = JOBS.filter(j => j.status === 'RUNNING').length;
      await expect(page.locator('.jobs-stat__value--running')).toHaveText(String(running));
    });

    test('displays the failed count correctly', async ({ page }) => {
      const failed = JOBS.filter(j => j.status === 'FAILED').length;
      await expect(page.locator('.jobs-stat__value--failed')).toHaveText(String(failed));
    });
  });

  test.describe('job table', () => {
    test('renders a row for each job', async ({ page }) => {
      const rows = page.locator('tbody .jobs-row');
      await expect(rows).toHaveCount(JOBS.length);
    });

    test('shows job name in each row', async ({ page }) => {
      await expect(page.getByText('E-commerce Scraper')).toBeVisible();
      await expect(page.getByText('News Crawler')).toBeVisible();
    });

    test('shows the correct status pill', async ({ page }) => {
      await expect(page.locator('.status-pill--running').first()).toBeVisible();
      await expect(page.locator('.status-pill--completed').first()).toBeVisible();
    });
  });


  test.describe('search', () => {
    test('filters rows by job name', async ({ page }) => {
      await page.getByPlaceholder('Search jobs by name').fill('News');
      await expect(page.locator('.jobs-row')).toHaveCount(1);
      await expect(page.getByText('News Crawler')).toBeVisible();
    });

    test('shows "No results" when nothing matches', async ({ page }) => {
      await page.getByPlaceholder('Search jobs by name').fill('xyzzy-nomatch');
      await expect(page.getByText('No results')).toBeVisible();
    });

    test('clear button resets the search', async ({ page }) => {
      await page.getByPlaceholder('Search jobs by name').fill('News');
      await page.locator('.jobs-toolbar__search-clear').click();
      await expect(page.locator('.jobs-row')).toHaveCount(JOBS.length);
    });
  });

  test.describe('dropdown filters', () => {
    test('status filter shows only matching jobs', async ({ page }) => {
      await page.locator('select.filter-select').first().selectOption('COMPLETED');
      const completed = JOBS.filter(j => j.status === 'COMPLETED').length;
      await expect(page.locator('.jobs-row')).toHaveCount(completed);
    });

    test('"Clear filters" button appears when a filter is active', async ({ page }) => {
      await page.locator('select.filter-select').first().selectOption('RUNNING');
      await expect(page.locator('.filter-clear-btn')).toBeVisible();
    });

    test('"Clear filters" resets all filters', async ({ page }) => {
      await page.locator('select.filter-select').first().selectOption('RUNNING');
      await page.locator('.filter-clear-btn').click();
      await expect(page.locator('.jobs-row')).toHaveCount(JOBS.length);
    });
  });


  test.describe('navigation', () => {
    test('"New Job" button navigates to /jobs/create', async ({ page }) => {
      await page.locator('.btn-new').first().click();
      await expect(page).toHaveURL(/\/jobs\/create/);
    });

    test('clicking a row navigates to the job detail', async ({ page }) => {
      await page.locator('.jobs-row').first().click();
      await expect(page).toHaveURL(/\/jobs\/j1/);
    });

    test('edit button navigates to /jobs/:id/edit', async ({ page }) => {
      await page.locator('.row-action--edit').first().click();
      await expect(page).toHaveURL(/\/jobs\/j1\/edit/);
    });

    test('launch button navigates to /jobs/:id/run', async ({ page }) => {
      // j1 is RUNNING, find a job that can be launched (j2 is COMPLETED)
      const launchBtn = page.locator('.row-action--launch').first();
      await launchBtn.click();
      await expect(page).toHaveURL(/\/jobs\/.*\/run/);
    });
  });

  test.describe('delete confirmation', () => {
    test('clicking delete shows a confirmation modal', async ({ page }) => {
      const deleteBtn = page.locator('.row-action--delete').first();
      await deleteBtn.click();
      await expect(page.getByText('Delete job?')).toBeVisible();
      await expect(page.getByText('This action cannot be undone.')).toBeVisible();
    });

    test('"Cancel" closes the modal without deleting', async ({ page }) => {
      await page.locator('.row-action--delete').first().click();
      await page.getByRole('button', { name: 'Cancel' }).click();
      await expect(page.locator('.modal-box')).not.toBeVisible();
    });

    test('"Delete" submits the deletion and closes the modal', async ({ page }) => {
      await page.locator('.row-action--delete').first().click();
      await page.getByRole('button', { name: 'Delete' }).click();
      await expect(page.locator('.modal-box')).not.toBeVisible();
    });

    test('clicking the backdrop closes the modal', async ({ page }) => {
      await page.locator('.row-action--delete').first().click();
      await page.locator('.modal-backdrop').click({ position: { x: 10, y: 10 } });
      await expect(page.locator('.modal-box')).not.toBeVisible();
    });
  });

  test.describe('bulk selection', () => {
    test('selecting a row shows the bulk action bar', async ({ page }) => {
      await page.locator('.checkbox-custom').first().click();
      await expect(page.locator('.bulk-bar')).toBeVisible();
    });

    test('bulk bar shows the selected count', async ({ page }) => {
      await page.locator('.col-check .checkbox-custom').nth(1).click();
      await page.locator('.col-check .checkbox-custom').nth(2).click();
      await expect(page.locator('.bulk-bar__count')).toContainText('2 jobs selected');
    });

    test('"Deselect all" clears the selection', async ({ page }) => {
      await page.locator('.col-check .checkbox-custom').nth(1).click();
      await page.getByRole('button', { name: 'Deselect all' }).click();
      await expect(page.locator('.bulk-bar')).not.toBeVisible();
    });
  });

  test.describe('empty state', () => {
    test('shows empty state when the API returns no jobs', async ({ page }) => {
      await setupPage(page, { jobs: [] });
      await page.goto(`${BASE_URL}/jobs`);
      await expect(page.getByText('No jobs yet')).toBeVisible();
      await expect(page.getByText('Create your first scraping job')).toBeVisible();
    });
  });
});
