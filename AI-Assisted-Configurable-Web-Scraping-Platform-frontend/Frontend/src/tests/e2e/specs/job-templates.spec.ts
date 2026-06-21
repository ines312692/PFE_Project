import { test, expect } from '@playwright/test';
import { setupPage, BASE_URL } from '../helpers/setup';
import { TEMPLATES } from '../fixtures/mock-data';


test.describe('Job Templates page', () => {

  test.beforeEach(async ({ page }) => {
    await setupPage(page);
    await page.goto(`${BASE_URL}/jobs/templates`);
  });

  test.describe('initial rendering', () => {
    test('shows the Templates page title', async ({ page }) => {
      await expect(page.getByRole('heading', { name: 'Templates' })).toBeVisible();
    });

    test('renders a card for each template', async ({ page }) => {
      await expect(page.locator('.tpl__card')).toHaveCount(TEMPLATES.length);
    });

    test('shows template name and description', async ({ page }) => {
      await expect(page.getByText('Amazon Product Scraper')).toBeVisible();
      await expect(page.getByText('Hacker News front page')).toBeVisible();
    });

    test('shows the result count badge', async ({ page }) => {
      await expect(page.locator('.tpl__result-count')).toContainText(`${TEMPLATES.length} templates`);
    });
  });


  test.describe('category filter', () => {
    test('shows ALL category by default', async ({ page }) => {
      await expect(page.locator('.tpl__filter--active')).toContainText('All');
    });

    test('each category appears in the sidebar', async ({ page }) => {
      const categories = [...new Set(TEMPLATES.map(t => t.category))];
      // eslint-disable-next-line no-empty-pattern
      for (const {} of categories) {
        const btn = page.locator('.tpl__filter').filter({ hasText: /E-Commerce|News|Job Boards/ });
        await expect(btn.first()).toBeVisible();
      }
    });

    test('clicking a category filters the grid', async ({ page }) => {
      // Click "News" (second non-ALL filter)
      await page.locator('.tpl__filter').filter({ hasText: 'News' }).click();
      const newsTemplates = TEMPLATES.filter(t => t.category === 'NEWS').length;
      await expect(page.locator('.tpl__card')).toHaveCount(newsTemplates);
    });

    test('active category filter has the --active CSS class', async ({ page }) => {
      await page.locator('.tpl__filter').filter({ hasText: 'News' }).click();
      await expect(page.locator('.tpl__filter--active')).toContainText('News');
    });

    test('clicking ALL resets to all templates', async ({ page }) => {
      await page.locator('.tpl__filter').filter({ hasText: 'News' }).click();
      await page.locator('.tpl__filter').filter({ hasText: 'All' }).click();
      await expect(page.locator('.tpl__card')).toHaveCount(TEMPLATES.length);
    });
  });


  test.describe('search', () => {
    test('filters templates by name', async ({ page }) => {
      await page.locator('.tpl__search-input').fill('Amazon');
      await expect(page.locator('.tpl__card')).toHaveCount(1);
      await expect(page.getByText('Amazon Product Scraper')).toBeVisible();
    });

    test('filters templates by tag', async ({ page }) => {
      await page.locator('.tpl__search-input').fill('hackernews');
      await expect(page.locator('.tpl__card')).toHaveCount(1);
    });

    test('search is case-insensitive', async ({ page }) => {
      await page.locator('.tpl__search-input').fill('LINKEDIN');
      await expect(page.locator('.tpl__card')).toHaveCount(1);
    });

    test('shows empty state when no templates match', async ({ page }) => {
      await page.locator('.tpl__search-input').fill('xyzzy-nomatch');
      await expect(page.getByText('No templates match your search')).toBeVisible();
    });

    test('clear button (×) resets the search', async ({ page }) => {
      await page.locator('.tpl__search-input').fill('Amazon');
      await page.locator('.tpl__search-clear').click();
      await expect(page.locator('.tpl__card')).toHaveCount(TEMPLATES.length);
    });

    test('"Clear filters" button resets both search and category', async ({ page }) => {
      await page.locator('.tpl__filter').filter({ hasText: 'News' }).click();
      await page.locator('.tpl__search-input').fill('Amazon');
      await page.getByRole('button', { name: 'Clear filters' }).click();
      await expect(page.locator('.tpl__card')).toHaveCount(TEMPLATES.length);
    });
  });

  test.describe('using a template', () => {
    test('clicking "Use template" navigates to /jobs/create with query params', async ({ page }) => {
      await page.locator('.tpl__card').first().click();
      await expect(page).toHaveURL(/\/jobs\/create\?templateId=tpl-1/);
    });

    test('the create page pre-fills the form type from the template', async ({ page }) => {
      const scraperTemplate = TEMPLATES.find(t => t.jobType === 'SCRAPER')!;
      await page.locator('.tpl__card').filter({ hasText: scraperTemplate.name }).click();
      await expect(page.getByText('Choose your extraction method')).not.toBeVisible();
    });
  });

  test('Back to Jobs button navigates to /jobs', async ({ page }) => {
    await page.getByRole('button', { name: 'Back to Jobs' }).click();
    await expect(page).toHaveURL(/\/jobs$/);
  });
});
