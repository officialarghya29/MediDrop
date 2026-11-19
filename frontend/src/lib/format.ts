export const inr = (value?: number | null): string => {
  if (value === undefined || value === null || Number.isNaN(value)) return '—';
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: 2,
  }).format(Number(value));
};

export const dateTime = (value?: string | null): string => {
  if (!value) return '—';
  return new Date(value).toLocaleString('en-IN', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
};

export const dateOnly = (value?: string | null): string => {
  if (!value) return '—';
  return new Date(value).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
};

export const statusLabel = (status?: string | null): string => {
  if (!status) return '—';
  return status
    .toLowerCase()
    .split('_')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ');
};

export const statusTone = (status?: string | null): string => {
  switch (status) {
    case 'DELIVERED':
    case 'APPROVED':
    case 'CAPTURED':
      return 'bg-emerald-50 text-emerald-700';
    case 'CANCELLED':
    case 'REJECTED':
    case 'FAILED':
    case 'SUSPENDED':
      return 'bg-rose-50 text-rose-700';
    case 'PRESCRIPTION_REJECTED':
      return 'bg-rose-50 text-rose-700';
    case 'OUT_FOR_DELIVERY':
    case 'PICKED_UP':
    case 'PREPARING':
    case 'READY_FOR_PICKUP':
      return 'bg-sky-50 text-sky-700';
    case 'PAYMENT_PENDING':
    case 'PRESCRIPTION_REVIEW_PENDING':
    case 'PENDING':
      return 'bg-amber-50 text-amber-800';
    default:
      return 'bg-slate-100 text-slate-700';
  }
};

export const isActiveOrder = (status?: string): boolean =>
  !!status &&
  [
    'CREATED',
    'PAYMENT_PENDING',
    'PAYMENT_CONFIRMED',
    'PRESCRIPTION_REVIEW_PENDING',
    'PRESCRIPTION_APPROVED',
    'PHARMACY_ACCEPTED',
    'PREPARING',
    'READY_FOR_PICKUP',
    'DELIVERY_ASSIGNED',
    'PICKED_UP',
    'OUT_FOR_DELIVERY',
    'CANCELLATION_REQUESTED',
  ].includes(status);

export const truncate = (value: string, length = 140): string =>
  value.length > length ? `${value.slice(0, length).trimEnd()}…` : value;

export const fileSize = (bytes?: number): string => {
  if (!bytes) return '—';
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
};
