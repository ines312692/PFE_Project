import { test, expect } from '@playwright/test';
import { setupPage, BASE_URL } from '../helpers/setup';


test.describe('Job Create — workspace & multi-step form', () => {

  test.beforeEach(async ({ page }) => {
    await setupPage(page);
    await page.goto(`${BASE_URL}/jobs/create`);
  });

  test.describe('type picker', () => {
    test('shows the type picker on first visit', async ({ page }) => {
      await expect(page.getByText('Choose your extraction method')).toBeVisible();
    });

    test('both SCRAPER and CRAWLER cards are visible', async ({ page }) => {
      await expect(page.getByText('Scraper')).toBeVisible();
      await expect(page.getByText('Crawler')).toBeVisible();
    });

    test('selecting SCRAPER hides the picker', async ({ page }) => {
      await page.getByRole('button', { name: 'Scraper' }).click();
      await expect(page.getByText('Choose your extraction method')).not.toBeVisible();
    });

    test('selecting CRAWLER sets the correct job type in the form title', async ({ page }) => {
      await page.getByRole('button', { name: 'Crawler' }).click();
      await expect(page.getByText('New Crawler Job')).toBeVisible();
    });

    test('"Back to jobs" navigates to /jobs', async ({ page }) => {
      await page.getByRole('button', { name: 'Back to jobs' }).click();
      await expect(page).toHaveURL(/\/jobs$/);
    });
  });


  test.describe('step 1 — general information', () => {
    test.beforeEach(async ({ page }) => {
      await page.getByRole('button', { name: 'Scraper' }).click();
    });

    test('shows step 1 form fields', async ({ page }) => {
      await expect(page.locator('#name')).toBeVisible();
      await expect(page.locator('#targetUrl')).toBeVisible();
    });

    test('shows a validation error when clicking Next without a name', async ({ page }) => {
      await page.locator('#targetUrl').fill('https://example.com');
      await page.getByRole('button', { name: /Next/ }).click();
      await expect(page.locator('.alert')).toBeVisible();
      await expect(page.locator('.alert')).toContainText('name');
    });

    test('shows a validation error for an invalid URL', async ({ page }) => {
      await page.locator('#name').fill('My Job');
      await page.locator('#targetUrl').fill('not-a-url');
      await page.getByRole('button', { name: /Next/ }).click();
      await expect(page.locator('.alert')).toContainText('valid URL');
    });

    test('shows a cron field when trigger is SCHEDULED', async ({ page }) => {
      await page.locator('#triggerType').selectOption('SCHEDULED');
      await expect(page.locator('#cron')).toBeVisible();
    });

    test('advances to step 2 when step 1 is valid', async ({ page }) => {
      await page.locator('#name').fill('My Scraper');
      await page.locator('#targetUrl').fill('https://example.com');
      await page.getByRole('button', { name: /Next/ }).click();
      await expect(page.getByText('Scraper Configuration')).toBeVisible();
    });
  });


  test.describe('step navigation', () => {
    async function advanceTo(page: import('@playwright/test').Page, step: number) {
      await page.getByRole('button', { name: 'Scraper' }).click();
      await page.locator('#name').fill('Test Job');
      await page.locator('#targetUrl').fill('https://example.com');
      for (let i = 1; i < step; i++) {
        await page.getByRole('button', { name: /Next/ }).click();
      }
    }

    test('step indicator advances correctly', async ({ page }) => {
      await advanceTo(page, 2);
      await expect(page.locator('.step-nav__item--active')).toContainText('2');
    });

    test('"Previous" button goes back to the prior step', async ({ page }) => {
      await advanceTo(page, 2);
      await page.getByRole('button', { name: 'Previous' }).click();
      await expect(page.locator('.step-nav__item--active')).toContainText('1');
    });

    test('"Cancel" navigates back to /jobs', async ({ page }) => {
      await page.getByRole('button', { name: 'Scraper' }).click();
      await page.getByRole('button', { name: 'Cancel' }).click();
      await expect(page).toHaveURL(/\/jobs$/);
    });

    test('completed steps show a checkmark', async ({ page }) => {
      await advanceTo(page, 2);
      await expect(page.locator('.step-nav__item--done').first()).toBeVisible();
    });
  });

  test.describe('step 3 — browser & network', () => {
    test.beforeEach(async ({ page }) => {
      await page.getByRole('button', { name: 'Scraper' }).click();
      await page.locator('#name').fill('Test');
      await page.locator('#targetUrl').fill('https://example.com');
      await page.getByRole('button', { name: /Next Scraper/ }).click();
      await page.getByRole('button', { name: /Next Browser/ }).click();
    });

    test('shows browser config fields', async ({ page }) => {
      await expect(page.locator('#requestDelayMs')).toBeVisible();
      await expect(page.locator('#timeoutMs')).toBeVisible();
    });

    test('JavaScript Rendering toggle changes state', async ({ page }) => {
      const toggle = page.locator('.toggle-item').filter({ hasText: 'JavaScript Rendering' });
      await toggle.click();
      await expect(toggle).toHaveClass(/toggle-item--on/);
    });
  });


  test.describe('step 5 — submission', () => {
    test.beforeEach(async ({ page }) => {
      await page.getByRole('button', { name: 'Scraper' }).click();
      await page.locator('#name').fill('My New Scraper');
      await page.locator('#targetUrl').fill('https://example.com');
      for (let i = 1; i < 5; i++) {
        await page.getByRole('button', { name: /Next/ }).click();
      }
    });

    test('shows the Create Job button on step 5', async ({ page }) => {
      await expect(page.getByRole('button', { name: 'Create Job' })).toBeVisible();
    });

    test('successful submission shows the success card', async ({ page }) => {
      await page.getByRole('button', { name: 'Create Job' }).click();
      await expect(page.getByText('Job created successfully!')).toBeVisible();
    });

    test('"New job" button resets the form after creation', async ({ page }) => {
      await page.getByRole('button', { name: 'Create Job' }).click();
      await page.getByRole('button', { name: 'New job' }).click();
      await expect(page.getByText('Choose your extraction method')).toBeVisible();
    });
  });
});
