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
    name: 'cart',
    path: '/cart',
    role: 'CUSTOMER' as const,
    heading: /^Your cart$/i,
  },
  {
    name: 'orders',
    path: '/orders',
    role: 'CUSTOMER' as const,
    heading: /^Your orders$/i,
  },
  {
    name: 'order detail',
    path: '/orders/1',
    role: 'CUSTOMER' as const,
    heading: /MD-2601-0001/i,
  },
  {
    name: 'prescriptions',
    path: '/prescriptions',
    role: 'CUSTOMER' as const,
    heading: /^Prescriptions$/i,
  },
  {
    name: 'profile',
    path: '/profile',
    role: 'CUSTOMER' as const,
    heading: /^Your profile$/i,
  },
  {
    name: 'notifications',
    path: '/notifications',
    role: 'CUSTOMER' as const,
    heading: /^Notifications$/i,
  },
  {
    name: 'delivery tracking',
    path: '/track/demo-tracking-key',
    role: 'CUSTOMER' as const,
    heading: /^Delivery status$/i,
  },
  {
    name: 'pharmacy dashboard',
    path: '/pharmacy',
    role: 'PHARMACY' as const,
    heading: /Salt Lake Care Pharmacy/i,
  },
  {
    name: 'pharmacy orders',
    path: '/pharmacy/orders',
    role: 'PHARMACY' as const,
    heading: /^Orders$/i,
  },
  {
    name: 'pharmacy inventory',
    path: '/pharmacy/inventory',
    role: 'PHARMACY' as const,
    heading: /^Inventory$/i,
  },
  {
    name: 'pharmacy prescription review',
    path: '/pharmacy/prescriptions',
    role: 'PHARMACY' as const,
    heading: /^Prescription review$/i,
  },
  {
    name: 'pharmacy analytics',
    path: '/pharmacy/analytics',
    role: 'PHARMACY' as const,
    heading: /^Analytics$/i,
  },
  {
    name: 'delivery dashboard',
    path: '/delivery',
    role: 'DELIVERY' as const,
    heading: /Namaste, Rakesh/i,
  },
  {
    name: 'delivery history',
    path: '/delivery/history',
    role: 'DELIVERY' as const,
    heading: /^Delivery history$/i,
  },
  {
    name: 'admin dashboard',
    path: '/admin',
    role: 'ADMIN' as const,
    heading: /Platform overview/i,
  },
  {
    name: 'admin approvals',
    path: '/admin/approvals',
    role: 'ADMIN' as const,
    heading: /^Approvals$/i,
  },
  {
    name: 'admin orders',
    path: '/admin/orders',
    role: 'ADMIN' as const,
    heading: /^Orders$/i,
  },
  {
    name: 'admin users',
    path: '/admin/users',
    role: 'ADMIN' as const,
    heading: /^Users$/i,
  },
  {
    name: 'admin configuration',
    path: '/admin/config',
    role: 'ADMIN' as const,
    heading: /^Configuration$/i,
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
