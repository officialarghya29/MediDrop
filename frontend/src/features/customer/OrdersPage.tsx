import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ClipboardList, ShieldCheck } from 'lucide-react';
import { Badge, EmptyState, PageLoader, Tabs } from '../../components/ui';
import { apiGet } from '../../lib/api';
import { dateTime, inr, isActiveOrder, statusLabel, statusTone } from '../../lib/format';
import type { OrderSummary, PageResponse } from '../../types';

type Filter = 'all' | 'active' | 'delivered' | 'cancelled';

export default function OrdersPage() {
  const [filter, setFilter] = useState<Filter>('all');

  const { data: counts } = useQuery({
    queryKey: ['order-counts'],
    queryFn: () => apiGet<{ total: number; active: number; delivered: number; cancelled: number }>('/orders/counts'),
  });

  const { data, isLoading } = useQuery({
    queryKey: ['orders', filter],
    queryFn: () => apiGet<PageResponse<OrderSummary>>('/orders', { filter, size: 20 }),
  });

  return (
    <div className="container-page space-y-5 py-6 sm:py-8">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Your orders</h1>
        <p className="muted mt-1">Track live deliveries, download invoices and reorder essentials.</p>
      </div>

      <Tabs
        value={filter}
        onChange={setFilter}
        tabs={[
          { id: 'all', label: 'All', count: counts?.total },
          { id: 'active', label: 'Active', count: counts?.active },
          { id: 'delivered', label: 'Delivered', count: counts?.delivered },
          { id: 'cancelled', label: 'Cancelled', count: counts?.cancelled },
        ]}
      />

      {isLoading && <PageLoader label="Loading orders…" />}

      {!isLoading && (data?.content ?? []).length === 0 && (
        <EmptyState
          icon={<ClipboardList className="h-5 w-5" />}
          title={filter === 'all' ? 'No orders yet' : `No ${filter} orders`}
          description="When you place an order it will appear here with live tracking and invoices."
          action={<Link to="/search" className="btn-primary">Start shopping</Link>}
        />
      )}

      <div className="grid gap-3">
        {data?.content.map((order) => (
          <Link key={order.id} to={`/orders/${order.id}`} className="card-pad transition hover:shadow-soft">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <div className="flex flex-wrap items-center gap-2">
                  <p className="font-mono text-xs font-semibold text-slate-500">{order.orderNumber}</p>
                  <Badge tone={statusTone(order.status)}>{statusLabel(order.status)}</Badge>
                  {order.prescriptionRequired && (
                    <Badge tone="bg-amber-50 text-amber-800"><ShieldCheck className="h-3 w-3" /> Rx</Badge>
                  )}
                </div>
                <p className="mt-2 font-semibold">{order.pharmacyName}</p>
                <p className="mt-0.5 text-xs text-slate-500">
                  {dateTime(order.placedAt)} · {order.itemCount} item(s) · {order.city}
                </p>
              </div>
              <div className="text-right">
                <p className="font-bold">{inr(order.totalAmount)}</p>
                <p className="text-[11px] uppercase tracking-wide text-slate-500">{order.paymentMethod}</p>
                <p className="mt-2 text-xs font-semibold text-brand-700">
                  {isActiveOrder(order.status) ? 'Track order →' : 'View details →'}
                </p>
              </div>
            </div>
          </Link>
        ))}
      </div>
    </div>
  );
}
