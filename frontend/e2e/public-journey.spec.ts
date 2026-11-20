import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockPublicApi } from './mock-api';

const pages = [
  { name: 'home', path: '/', heading: /Healthcare that keeps up with you/i },
  { name: 'search', path: '/search?q=Paracetamol', heading: /Find your medicine/i },
  { name: 'medicine detail', path: '/medicines/1', heading: /Paracetamol 500 mg Tablet/i },
  { name: 'sign in', path: '/login', heading: /Sign in to MediDrop/i },
];

test.beforeEach(async ({ page }) => {
  await mockPublicApi(page);
});

for (const item of pages) {
  test(`${item.name} has no WCAG A/AA violations`, async ({ page }) => {
    await page.goto(item.path);
    await expect(page.getByRole('heading', { name: item.heading }).first()).toBeVisible();
    await page.waitForLoadState('networkidle');

    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();

    expect(
      results.violations,
      results.violations
        .map((violation) => `${violation.id}: ${violation.help} (${violation.nodes.length} nodes)`)
        .join('\n'),
    ).toEqual([]);
  });
}

test('customer can search and open a medicine', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: /Healthcare that keeps up with you/i })).toBeVisible();

  await page.getByLabel('Search medicines').fill('Paracetamol');
  await page.getByRole('button', { name: 'Search', exact: true }).click();

  await expect(page).toHaveURL(/\/search\?q=Paracetamol$/);
  await expect(page.getByText(/1 match for/i)).toBeVisible();
  await page.getByRole('link', { name: /Paracetamol 500 mg Tablet/i }).click();

  await expect(page).toHaveURL(/\/medicines\/1$/);
  await expect(page.getByRole('heading', { name: 'Paracetamol 500 mg Tablet', level: 1 })).toBeVisible();
  await expect(page.getByText('Salt Lake Care Pharmacy').first()).toBeVisible();
});
