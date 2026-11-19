import { useQuery } from '@tanstack/react-query';
import { Bike } from 'lucide-react';
import { Alert, Badge, EmptyState, PageLoader } from '../../components/ui';
import { apiGet, errorMessage } from '../../lib/api';
import { dateTime, inr, statusLabel, statusTone } from '../../lib/format';
import type { DeliveryAssignment } from '../../types';

export default function DeliveryHistoryPage() {
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['delivery-assignments', 'history'],
    queryFn: () => apiGet<DeliveryAssignment[]>('/delivery/assignments'),
  });

  if (isLoading) return <PageLoader label="Loading delivery history…" />;
  if (isError) return <Alert tone="error">{errorMessage(error)}</Alert>;

  const history = (data ?? []).filter((item) => ['DELIVERED', 'FAILED', 'REJECTED'].includes(item.status));
  const total = history.filter((item) => item.status === 'DELIVERED').reduce((sum, item) => sum + item.payout, 0);

  return (
    <div className="space-y-4 px-4 py-6 sm:px-6 lg:px-8">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Delivery history</h1>
        <p className="muted mt-1">{history.length} completed assignment(s) · {inr(total)} earned on this device</p>
      </div>

      {history.length === 0 ? (
        <EmptyState icon={<Bike className="h-5 w-5" />} title="No past deliveries" description="Completed and failed runs will be listed here." />
      ) : (
        <div className="overflow-x-auto rounded-2xl border border-slate-200 bg-white">
          <table className="table-base min-w-[44rem]">
            <thead>
              <tr>
                <th>Order</th>
                <th>Pharmacy</th>
                <th>Area</th>
                <th className="text-right">Distance</th>
                <th className="text-right">Payout</th>
                <th>Status</th>
                <th>Assigned</th>
              </tr>
            </thead>
            <tbody>
              {history.map((item) => (
                <tr key={item.id}>
                  <td className="font-mono text-xs">{item.orderNumber}</td>
                  <td className="text-xs">{item.pharmacyName}</td>
                  <td className="text-xs">{item.customerArea}</td>
                  <td className="text-right text-xs">{item.distanceKm ?? '—'} km</td>
                  <td className="text-right font-medium">{inr(item.payout)}</td>
                  <td><Badge tone={statusTone(item.status)}>{statusLabel(item.status)}</Badge></td>
                  <td className="text-xs text-slate-500">{dateTime(item.assignedAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
