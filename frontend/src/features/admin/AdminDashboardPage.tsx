import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { ClipboardList, IndianRupee, LifeBuoy, Stethoscope, Store, Truck, Users } from 'lucide-react';
import { Alert, PageLoader, StatCard } from '../../components/ui';
import { apiGet, errorMessage } from '../../lib/api';
import { dateOnly, inr } from '../../lib/format';
import type { AdminDashboard, PlatformReport } from '../../types';

export default function AdminDashboardPage() {
  const dashboard = useQuery({
    queryKey: ['admin-dashboard'],
    queryFn: () => apiGet<AdminDashboard>('/admin/dashboard'),
    refetchInterval: 60_000,
  });

  const report = useQuery({
    queryKey: ['admin-report'],
    queryFn: () => apiGet<PlatformReport>('/admin/reports/platform', { days: 30 }),
  });

  if (dashboard.isLoading) return <PageLoader label="Loading platform metrics…" />;
  if (dashboard.isError || !dashboard.data) return <Alert tone="error">{errorMessage(dashboard.error)}</Alert>;

  const data = dashboard.data;
  const trend = report.data?.orders ?? data.orderTrend;
  const maxOrders = Math.max(1, ...trend.map((point) => point.orders));

  return (
    <div className="space-y-5 px-4 py-6 sm:px-6 lg:px-8">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Platform overview</h1>
        <p className="muted mt-1">All figures are computed server-side in Asia/Kolkata time.</p>
      </div>

      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard label="Orders today" value={data.ordersToday} hint={`${data.activeOrders} active now`} icon={<ClipboardList className="h-4 w-4" />} />
        <StatCard label="Gross revenue" value={inr(data.revenue)} hint={`${inr(data.refunded)} refunded`} icon={<IndianRupee className="h-4 w-4" />} tone="bg-emerald-50 text-emerald-700" />
        <StatCard label="Customers" value={data.totalCustomers} icon={<Users className="h-4 w-4" />} tone="bg-sky-50 text-sky-700" />
        <StatCard label="Pharmacies" value={data.totalPharmacies} hint={`${data.pendingPharmacies} awaiting approval`} icon={<Store className="h-4 w-4" />} tone="bg-amber-50 text-amber-700" />
      </div>

      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard label="Delivery partners" value={data.totalPartners} hint={`${data.pendingPartners} pending`} icon={<Truck className="h-4 w-4" />} />
        <StatCard label="Prescriptions in review" value={data.pendingPrescriptions} icon={<Stethoscope className="h-4 w-4" />} tone="bg-amber-50 text-amber-700" />
        <StatCard label="Open support tickets" value={data.openSupportTickets} icon={<LifeBuoy className="h-4 w-4" />} tone="bg-rose-50 text-rose-700" />
        <StatCard label="Completed orders" value={data.completedOrders} hint={`${data.cancelledOrders} cancelled`} />
      </div>

      {(data.pendingPharmacies > 0 || data.pendingPartners > 0) && (
        <Alert tone="warning" title="Approvals waiting">
          {data.pendingPharmacies} pharmacy application(s) and {data.pendingPartners} delivery partner(s) need review.{' '}
          <Link to="/admin/approvals" className="font-semibold underline">Open approvals</Link>
        </Alert>
      )}

      <section className="card-pad">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 className="section-title">Orders · last 30 days</h2>
          <span className="text-xs text-slate-500">
            {report.data ? `${report.data.deliveredOrders} delivered · AOV ${inr(report.data.averageOrderValue)}` : 'Loading report…'}
          </span>
        </div>
        <div className="mt-4 flex h-40 items-end gap-[2px]" role="img" aria-label="Orders per day">
          {trend.map((point) => (
            <div key={point.date} className="group relative flex-1">
              <div
                className="w-full rounded-t bg-gradient-to-t from-brand-700 via-brand-500 to-cyan-400 transition group-hover:from-grape-600 group-hover:to-coral-400"
                style={{ height: `${Math.max(2, (point.orders / maxOrders) * 150)}px` }}
                title={`${dateOnly(point.date)}: ${point.orders} orders · ${inr(point.revenue)}`}
              />
            </div>
          ))}
        </div>
        <div className="mt-2 flex justify-between text-[10px] text-slate-400">
          <span>{trend[0]?.date}</span>
          <span>{trend[trend.length - 1]?.date}</span>
        </div>
        {report.data && (
          <div className="mt-4 grid gap-3 border-t border-slate-100 pt-3 text-xs text-slate-600 sm:grid-cols-3">
            <p>Cancellation rate: <strong>{report.data.cancellationRate}%</strong></p>
            <p>Avg delivery time: <strong>{report.data.averageDeliveryMinutes} min</strong></p>
            <p>Gross revenue: <strong>{inr(report.data.grossRevenue)}</strong></p>
          </div>
        )}
      </section>

      <div className="grid gap-5 lg:grid-cols-2">
        <section className="card-pad">
          <h2 className="section-title">Top pharmacies</h2>
          {(report.data?.topPharmacies ?? []).length === 0 ? (
            <p className="mt-2 text-sm text-slate-500">No orders in this window.</p>
          ) : (
            <ul className="mt-3 space-y-2 text-sm">
              {report.data!.topPharmacies.map((row) => (
                <li key={row.label} className="flex justify-between gap-3">
                  <span className="truncate">{row.label}</span>
                  <span className="font-medium">{row.count} orders</span>
                </li>
              ))}
            </ul>
          )}
        </section>
        <section className="card-pad">
          <h2 className="section-title">Top medicines</h2>
          {(report.data?.topMedicines ?? []).length === 0 ? (
            <p className="mt-2 text-sm text-slate-500">No sales in this window.</p>
          ) : (
            <ul className="mt-3 space-y-2 text-sm">
              {report.data!.topMedicines.map((row) => (
                <li key={row.label} className="flex justify-between gap-3">
                  <span className="truncate">{row.label}</span>
                  <span className="font-medium">{row.count} units</span>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>

      <div className="grid gap-3 sm:grid-cols-3">
        <Link to="/admin/orders" className="card flex items-center gap-3 p-4 hover:shadow-soft">
          <ClipboardList className="h-5 w-5 text-brand-700" /> <span className="text-sm font-medium">Manage orders</span>
        </Link>
        <Link to="/admin/users" className="card flex items-center gap-3 p-4 hover:shadow-soft">
          <Users className="h-5 w-5 text-brand-700" /> <span className="text-sm font-medium">Manage users</span>
        </Link>
        <Link to="/admin/config" className="card flex items-center gap-3 p-4 hover:shadow-soft">
          <Store className="h-5 w-5 text-brand-700" /> <span className="text-sm font-medium">Platform configuration</span>
        </Link>
      </div>
    </div>
  );
}
