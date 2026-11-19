import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Alert, Badge, EmptyState } from '../components/ui';

describe('EmptyState', () => {
  it('shows the title and description', () => {
    render(<EmptyState title="No orders yet" description="Place your first order." />);
    expect(screen.getByText('No orders yet')).toBeInTheDocument();
    expect(screen.getByText('Place your first order.')).toBeInTheDocument();
  });
});

describe('Badge', () => {
  it('renders its content with the requested tone', () => {
    render(<Badge tone="bg-emerald-50 text-emerald-700">Approved</Badge>);
    const badge = screen.getByText('Approved');
    expect(badge).toHaveClass('badge');
    expect(badge).toHaveClass('text-emerald-700');
  });
});

describe('Alert', () => {
  it('renders a titled error alert', () => {
    render(<Alert tone="error" title="Payment failed">Try another method.</Alert>);
    expect(screen.getByText('Payment failed')).toBeInTheDocument();
    expect(screen.getByText('Try another method.')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveClass('border-rose-200');
  });
});
