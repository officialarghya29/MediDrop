import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { PackageCheck, ShieldAlert, Stethoscope } from 'lucide-react';
import { Alert, Badge, EmptyState, FieldError, Modal, PageLoader, Spinner, Tabs, useToast } from '../../components/ui';
import { apiGet, apiPatch, errorMessage } from '../../lib/api';
import { dateTime, inr, statusLabel, statusTone } from '../../lib/format';
import type { OrderDetail, OrderStatus, OrderSummary, PageResponse } from '../../types';

const FILTERS = [
  { id: 'ALL', label: 'All' },
  { id: 'PAYMENT_CONFIRMED', label: 'New' },
  { id: 'PRESCRIPTION_APPROVED', label: 'Prescription approved' },
  { id: 'PHARMACY_ACCEPTED', label: 'Accepted' },
  { id: 'PREPARING', label: 'Preparing' },
  { id: 'READY_FOR_PICKUP', label: 'Ready' },
  { id: 'CANCELLATION_REQUESTED', label: 'Cancellation requests' },
] as const;

type Filter = (typeof FILTERS)[number]['id'];

const nextActions: Partial<Record<OrderStatus, { status: OrderStatus; label: string }[]>> = {
  PAYMENT_CONFIRMED: [{ status: 'PHARMACY_ACCEPTED', label: 'Accept order' }],
  PRESCRIPTION_APPROVED: [{ status: 'PHARMACY_ACCEPTED', label: 'Accept order' }],
  PHARMACY_ACCEPTED: [{ status: 'PREPARING', label: 'Start preparing' }, { status: 'CANCELLED', label: 'Cancel order' }],
  PREPARING: [{ status: 'READY_FOR_PICKUP', label: 'Mark ready for pickup' }],
  CANCELLATION_REQUESTED: [{ status: 'CANCELLED', label: 'Approve cancellation' }],
};

export default function PharmacyOrdersPage() {
  const toast = useToast();
  const queryClient = useQueryClient();
  const [filter, setFilter] = useState<Filter>('ALL');
  const [detailId, setDetailId] = useState<number | null>(null);
  const [reason, setReason] = useState('');
  const [pendingStatus, setPendingStatus] = useState<OrderStatus | null>(null);

  const list = useQuery({
    queryKey: ['pharmacy-orders', filter],
    queryFn: () =>
      apiGet<PageResponse<OrderSummary>>('/pharmacy/orders', {
        status: filter === 'ALL' ? undefined : filter,
        size: 30,
      }),
  });

  const detail = useQuery({
    queryKey: ['pharmacy-order', detailId],
    queryFn: () => apiGet<OrderDetail>(`/pharmacy/orders/${detailId}`),
    enabled: detailId !== null,
  });

  const transition = useMutation({
    mutationFn: ({ id, status }: { id: number; status: OrderStatus }) =>
      apiPatch<OrderDetail>(`/pharmacy/orders/${id}/status`, { status, reason: reason.trim() || undefined }),
    onSuccess: (order) => {
      toast(`Order moved to ${statusLabel(order.status)}`, 'success');
      setPendingStatus(null);
      setReason('');
      queryClient.invalidateQueries({ queryKey: ['pharmacy-orders'] });
      queryClient.invalidateQueries({ queryKey: ['pharmacy-order', detailId] });
      queryClient.invalidateQueries({ queryKey: ['pharmacy-dashboard'] });
    },
    onError: (error) => toast(errorMessage(error), 'error'),
  });

  const runAction = (status: OrderStatus) => {
    if (!detail.data) return;
    if (status === 'CANCELLED') {
      setPendingStatus(status);
      return;
    }
    transition.mutate({ id: detail.data.id, status });
  };

  return (
    <div className="space-y-4 px-4 py-6 sm:px-6 lg:px-8">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Orders</h1>
        <p className="muted mt-1">Accept, prepare and hand over orders. Every transition is validated by the server.</p>
      </div>

      <Tabs value={filter} onChange={setFilter} tabs={FILTERS.map((item) => ({ id: item.id, label: item.label }))} />

      {list.isLoading && <PageLoader label="Loading order queue…" />}
      {list.isError && <Alert tone="error">{errorMessage(list.error)}</Alert>}

      {!list.isLoading && (list.data?.content ?? []).length === 0 && (
        <EmptyState icon={<PackageCheck className="h-5 w-5" />} title="Nothing here" description="No orders match this filter right now." />
      )}

      <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white">
        {(list.data?.content ?? []).length > 0 && (
          <table className="table-base">
            <thead>
              <tr>
                <th>Order</th>
                <th>Placed</th>
                <th className="text-right">Items</th>
                <th className="text-right">Total</th>
                <th>Status</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {list.data!.content.map((order) => (
                <tr key={order.id}>
                  <td className="font-mono text-xs">{order.orderNumber}</td>
                  <td className="text-xs text-slate-500">{dateTime(order.placedAt)}</td>
                  <td className="text-right text-xs">{order.itemCount}</td>
                  <td className="text-right font-medium">{inr(order.totalAmount)}</td>
                  <td>
                    <div className="flex flex-wrap gap-1">
                      <Badge tone={statusTone(order.status)}>{statusLabel(order.status)}</Badge>
                      {order.prescriptionRequired && <Badge tone="bg-amber-50 text-amber-800"><ShieldAlert className="h-3 w-3" /> Rx</Badge>}
                    </div>
                  </td>
                  <td className="text-right">
                    <button type="button" className="btn-secondary py-1.5 text-xs" onClick={() => { setDetailId(order.id); setReason(''); }}>
                      Open
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <Modal
        open={detailId !== null}
        onClose={() => setDetailId(null)}
        title={detail.data ? `Order ${detail.data.orderNumber}` : 'Order details'}
        footer={
          detail.data ? (
            <>
              {detail.data.prescriptionStatus === 'PENDING' || detail.data.prescriptionStatus === 'UNDER_REVIEW' ? (
                <span className="mr-auto text-xs text-amber-700">Prescription review pending</span>
              ) : null}
              {(nextActions[detail.data.status] ?? []).map((action) => (
                <button
                  key={action.status}
                  type="button"
                  className={action.status === 'CANCELLED' ? 'btn-danger' : 'btn-primary'}
                  disabled={transition.isPending}
                  onClick={() => runAction(action.status)}
                >
                  {transition.isPending && pendingStatus !== 'CANCELLED' ? <Spinner /> : action.label}
                </button>
              ))}
            </>
          ) : null
        }
      >
        {detail.isLoading && <Spinner label="Loading order…" />}
        {detail.data && (
          <div className="space-y-4">
            <div className="flex flex-wrap items-center gap-2">
              <Badge tone={statusTone(detail.data.status)}>{statusLabel(detail.data.status)}</Badge>
              <Badge>{detail.data.paymentMethod}</Badge>
              {detail.data.prescriptionRequired && <Badge tone="bg-amber-50 text-amber-800">Prescription: {statusLabel(detail.data.prescriptionStatus)}</Badge>}
            </div>

            <div className="rounded-xl bg-slate-50 p-3 text-sm">
              <p className="font-semibold">{detail.data.customerName}</p>
              <p className="text-xs text-slate-600">{detail.data.customerPhone}</p>
              <p className="mt-1 text-xs text-slate-600">{detail.data.addressLine}, {detail.data.city} {detail.data.pincode}</p>
            </div>

            {detail.data.prescriptionStatus === 'PRESCRIPTION_REVIEW_PENDING' && (
              <Alert tone="warning" title="Prescription required">
                <span className="inline-flex items-center gap-1"><Stethoscope className="h-3.5 w-3.5" /> Review the prescription before accepting this order.</span>
              </Alert>
            )}

            <ul className="divide-y divide-slate-100 text-sm">
              {detail.data.items.map((item) => (
                <li key={item.id} className="flex justify-between py-2">
                  <span>{item.medicineName} × {item.quantity}</span>
                  <span className="font-medium">{inr(item.lineTotal)}</span>
                </li>
              ))}
            </ul>

            <dl className="space-y-1 border-t border-slate-100 pt-2 text-sm">
              <div className="flex justify-between"><dt>Subtotal</dt><dd>{inr(detail.data.subtotal)}</dd></div>
              <div className="flex justify-between"><dt>Delivery</dt><dd>{inr(detail.data.deliveryFee)}</dd></div>
              <div className="flex justify-between"><dt>Taxes</dt><dd>{inr(detail.data.taxAmount)}</dd></div>
              <div className="flex justify-between font-bold"><dt>Total</dt><dd>{inr(detail.data.totalAmount)}</dd></div>
            </dl>

            {detail.data.customerNote && <p className="rounded-lg bg-slate-50 p-2 text-xs text-slate-600">Customer note: {detail.data.customerNote}</p>}

            {pendingStatus === 'CANCELLED' && (
              <div>
                <label className="label" htmlFor="cancel-order-reason">Cancellation reason</label>
                <textarea
                  id="cancel-order-reason"
                  className="input resize-none"
                  rows={2}
                  maxLength={500}
                  value={reason}
                  onChange={(event) => setReason(event.target.value)}
                />
                <FieldError message={reason.trim().length > 0 && reason.trim().length < 3 ? 'Add a short reason' : undefined} />
                <div className="mt-2 flex justify-end gap-2">
                  <button type="button" className="btn-secondary py-1.5 text-xs" onClick={() => setPendingStatus(null)}>Back</button>
                  <button
                    type="button"
                    className="btn-danger py-1.5 text-xs"
                    disabled={transition.isPending || reason.trim().length < 3}
                    onClick={() => transition.mutate({ id: detail.data!.id, status: 'CANCELLED' })}
                  >
                    Confirm cancellation
                  </button>
                </div>
              </div>
            )}
          </div>
        )}
      </Modal>
    </div>
  );
}
