import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockRoleApi, signInAs } from './mock-api';

const pages = [
  {
    name: 'checkout',
    path: '/checkout',
    role: 'CUSTOMER' as const,
    heading: /^Checkout$/i,
  },
  {
    name: 'pharmacy dashboard',
    path: '/pharmacy',
    role: 'PHARMACY' as const,
    heading: /Salt Lake Care Pharmacy/i,
  },
  {
    name: 'delivery dashboard',
    path: '/delivery',
    role: 'DELIVERY' as const,
    heading: /Namaste, Rakesh/i,
  },
  {
    name: 'admin dashboard',
    path: '/admin',
    role: 'ADMIN' as const,
    heading: /Platform overview/i,
  },
];

for (const item of pages) {
  test(`${item.name} has no WCAG A/AA violations`, async ({ page }) => {
    await signInAs(page, item.role);
    await mockRoleApi(page, item.role);

    await page.goto(item.path);
    await expect(page).not.toHaveURL(/\/login$/);
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
