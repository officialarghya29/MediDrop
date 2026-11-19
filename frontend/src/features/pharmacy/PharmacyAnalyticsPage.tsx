import { useQuery } from '@tanstack/react-query';
import { TrendingUp } from 'lucide-react';
import { Alert, PageLoader, StatCard } from '../../components/ui';
import { apiGet, errorMessage } from '../../lib/api';
import { inr } from '../../lib/format';
import type { PharmacyAnalytics } from '../../types';

export default function PharmacyAnalyticsPage() {
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['pharmacy-analytics'],
    queryFn: () => apiGet<PharmacyAnalytics>('/pharmacy/analytics', { days: 30 }),
  });

  if (isLoading) return <PageLoader label="Crunching 30 days of sales…" />;
  if (isError || !data) return <Alert tone="error">{errorMessage(error)}</Alert>;

  const maxOrders = Math.max(1, ...data.series.map((point) => point.orders));

  return (
    <div className="space-y-5 px-4 py-6 sm:px-6 lg:px-8">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Analytics</h1>
        <p className="muted mt-1">Last {data.days} days, calculated from delivered orders in Asia/Kolkata time.</p>
      </div>

      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard label="Revenue" value={inr(data.revenue)} hint={`${data.deliveredOrders} delivered orders`} icon={<TrendingUp className="h-4 w-4" />} />
        <StatCard label="Average order value" value={inr(data.averageOrderValue)} />
        <StatCard label="Cancelled" value={data.cancelledOrders} hint="Cancelled in the window" tone="bg-rose-50 text-rose-700" />
        <StatCard label="Low stock items" value={data.lowStockItems} tone="bg-amber-50 text-amber-700" />
      </div>

      <section className="card-pad">
        <h2 className="section-title">Daily orders</h2>
        <div className="mt-4 flex h-40 items-end gap-[2px]" role="img" aria-label="Daily order volume chart">
          {data.series.map((point) => (
            <div key={point.date} className="group relative flex-1">
              <div
                className="w-full rounded-t bg-brand-600/80 transition group-hover:bg-brand-700"
                style={{ height: `${Math.max(2, (point.orders / maxOrders) * 150)}px` }}
                title={`${point.date}: ${point.orders} orders · ${inr(point.revenue)}`}
              />
            </div>
          ))}
        </div>
        <div className="mt-2 flex justify-between text-[10px] text-slate-400">
          <span>{data.series[0]?.date}</span>
          <span>{data.series[data.series.length - 1]?.date}</span>
        </div>
      </section>

      <div className="grid gap-5 lg:grid-cols-2">
        <section className="card-pad">
          <h2 className="section-title">Top products</h2>
          {(data.topProducts ?? []).length === 0 ? (
            <p className="mt-2 text-sm text-slate-500">No sales in this period.</p>
          ) : (
            <ul className="mt-3 space-y-2">
              {data.topProducts.map((product) => (
                <li key={product.label}>
                  <div className="flex justify-between text-sm">
                    <span className="truncate">{product.label}</span>
                    <span className="font-medium">{product.count} units</span>
                  </div>
                  <div className="mt-1 h-1.5 rounded-full bg-slate-100">
                    <div
                      className="h-1.5 rounded-full bg-brand-600"
                      style={{ width: `${(product.count / Math.max(1, data.topProducts[0].count)) * 100}%` }}
                    />
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="card-pad">
          <h2 className="section-title">Restock forecast</h2>
          <p className="muted mt-1 text-xs">Suggested purchase quantity for the next 7 days.</p>
          {(data.forecast ?? []).length === 0 ? (
            <p className="mt-2 text-sm text-slate-500">Not enough sales history yet.</p>
          ) : (
            <ul className="mt-3 divide-y divide-slate-100 text-sm">
              {data.forecast.map((item) => (
                <li key={item.medicine} className="flex items-center justify-between gap-3 py-2">
                  <div>
                    <p className="font-medium">{item.medicine}</p>
                    <p className="text-[11px] text-slate-500">{item.unitsLast30Days} units in 30 days · {item.method}</p>
                  </div>
                  <span className="whitespace-nowrap rounded-lg bg-mint px-2 py-1 text-xs font-semibold text-brand-800">
                    +{item.suggestedNext7Days}
                  </span>
                </li>
              ))}
            </ul>
          )}
          <p className="mt-3 text-[11px] text-slate-500">
            Forecasts are a planning aid only. Always confirm actual demand and expiry before ordering stock.
          </p>
        </section>
      </div>
    </div>
  );
}
