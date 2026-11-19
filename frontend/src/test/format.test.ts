import { describe, expect, it } from 'vitest';
import { fileSize, inr, isActiveOrder, statusLabel, statusTone, truncate } from '../lib/format';

describe('inr', () => {
  it('formats rupees with Indian grouping', () => {
    expect(inr(125000)).toBe('₹1,25,000.00');
  });

  it('returns an em dash for missing values', () => {
    expect(inr(undefined)).toBe('—');
    expect(inr(null)).toBe('—');
  });

  it('formats zero without falling back to the empty marker', () => {
    expect(inr(0)).toBe('₹0.00');
  });
});

describe('statusLabel', () => {
  it('turns enum values into readable labels', () => {
    expect(statusLabel('OUT_FOR_DELIVERY')).toBe('Out For Delivery');
    expect(statusLabel('PRESCRIPTION_REVIEW_PENDING')).toBe('Prescription Review Pending');
  });

  it('handles empty input', () => {
    expect(statusLabel('')).toBe('—');
    expect(statusLabel(null)).toBe('—');
  });
});

describe('isActiveOrder', () => {
  it('treats in-flight statuses as active', () => {
    expect(isActiveOrder('PAYMENT_CONFIRMED')).toBe(true);
    expect(isActiveOrder('OUT_FOR_DELIVERY')).toBe(true);
    expect(isActiveOrder('CANCELLATION_REQUESTED')).toBe(true);
  });

  it('treats terminal statuses as inactive', () => {
    expect(isActiveOrder('DELIVERED')).toBe(false);
    expect(isActiveOrder('CANCELLED')).toBe(false);
    expect(isActiveOrder('REFUNDED')).toBe(false);
    expect(isActiveOrder(undefined)).toBe(false);
  });
});

describe('statusTone', () => {
  it('returns distinct tones for success and failure', () => {
    expect(statusTone('DELIVERED')).toContain('emerald');
    expect(statusTone('CANCELLED')).toContain('rose');
    expect(statusTone('PREPARING')).toContain('sky');
  });
});

describe('fileSize', () => {
  it('renders kilobytes and megabytes', () => {
    expect(fileSize(2048)).toBe('2 KB');
    expect(fileSize(3 * 1024 * 1024)).toBe('3.0 MB');
    expect(fileSize(0)).toBe('—');
  });
});

describe('truncate', () => {
  it('keeps short text and ellipsizes long text', () => {
    expect(truncate('Paracetamol', 20)).toBe('Paracetamol');
    expect(truncate('a'.repeat(30), 10)).toBe('aaaaaaaaaa…');
  });
});
