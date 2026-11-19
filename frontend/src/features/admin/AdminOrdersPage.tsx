import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { RefreshCcw, Truck, Undo2 } from 'lucide-react';
import { Alert, Badge, EmptyState, FieldError, Modal, PageLoader, Spinner, useToast } from '../../components/ui';
import { apiGet, apiPatch, apiPost, errorMessage } from '../../lib/api';
import { dateTime, inr, statusLabel, statusTone } from '../../lib/format';
import type { AdminOrderRow, OrderStatus, PageResponse } from '../../types';

const STATUSES: OrderStatus[] = [
  'PAYMENT_CONFIRMED', 'PHARMACY_ACCEPTED', 'PREPARING', 'READY_FOR_PICKUP', 'CANCELLATION_REQUESTED', 'CANCELLED', 'REFUND_PENDING', 'REFUNDED',
];

export default function AdminOrdersPage() {
  const toast = useToast();
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState('');
  const [refundTarget, setRefundTarget] = useState<AdminOrderRow | null>(null);
  const [refundAmount, setRefundAmount] = useState('');
  const [refundReason, setRefundReason] = useState('');
  const [transitionTarget, setTransitionTarget] = useState<AdminOrderRow | null>(null);
  const [nextStatus, setNextStatus] = useState<OrderStatus>('CANCELLED');
  const [transitionReason, setTransitionReason] = useState('');

  const orders = useQuery({
    queryKey: ['admin-orders', page, status],
    queryFn: () => apiGet<PageResponse<AdminOrderRow>>('/admin/orders', { status: status || undefined, page, size: 20 }),
  });

  const assign = useMutation({
    mutationFn: (id: number) => apiPost(`/admin/orders/${id}/assign-delivery`),
    onSuccess: () => {
      toast('Assignment attempted', 'success');
      queryClient.invalidateQueries({ queryKey: ['admin-orders'] });
    },
    onError: (error) => toast(errorMessage(error), 'error'),
  });

  const transition = useMutation({
    mutationFn: () =>
      apiPatch(`/admin/orders/${transitionTarget?.id}/status`, {
        status: nextStatus,
        reason: transitionReason.trim() || undefined,
      }),
    onSuccess: () => {
      toast('Order status updated', 'success');
      setTransitionTarget(null);
      setTransitionReason('');
      queryClient.invalidateQueries({ queryKey: ['admin-orders'] });
    },
    onError: (error) => toast(errorMessage(error), 'error'),
  });

  const refund = useMutation({
    mutationFn: () =>
      apiPost(`/admin/orders/${refundTarget?.id}/refund`, {
        orderId: refundTarget?.id,
        amount: Number(refundAmount),
        reason: refundReason.trim(),
      }),
    onSuccess: () => {
      toast('Refund processed', 'success');
      setRefundTarget(null);
      setRefundAmount('');
      setRefundReason('');
      queryClient.invalidateQueries({ queryKey: ['admin-orders'] });
      queryClient.invalidateQueries({ queryKey: ['admin-dashboard'] });
    },
    onError: (error) => toast(errorMessage(error), 'error'),
  });

  return (
    <div className="space-y-4 px-4 py-6 sm:px-6 lg:px-8">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Orders</h1>
          <p className="muted mt-1">Platform-wide order oversight, delivery assignment and refunds.</p>
        </div>
        <select className="input w-auto py-2 text-xs" value={status} onChange={(event) => { setStatus(event.target.value); setPage(0); }} aria-label="Filter by status">
          <option value="">All statuses</option>
          {['CREATED', 'PAYMENT_CONFIRMED', 'PRESCRIPTION_REVIEW_PENDING', 'PRESCRIPTION_APPROVED', 'PHARMACY_ACCEPTED', 'PREPARING', 'READY_FOR_PICKUP', 'DELIVERY_ASSIGNED', 'OUT_FOR_DELIVERY', 'DELIVERED', 'CANCELLED', 'REFUND_PENDING', 'REFUNDED', 'FAILED'].map((item) => (
            <option key={item} value={item}>{statusLabel(item)}</option>
          ))}
        </select>
      </div>

      {orders.isLoading && <PageLoader label="Loading orders…" />}
      {orders.isError && <Alert tone="error">{errorMessage(orders.error)}</Alert>}
      {!orders.isLoading && (orders.data?.content ?? []).length === 0 && (
        <EmptyState title="No orders" description="No orders match this filter." />
      )}

      {(orders.data?.content ?? []).length > 0 && (
        <div className="overflow-x-auto rounded-2xl border border-slate-200 bg-white">
          <table className="table-base min-w-[58rem]">
            <thead>
              <tr>
                <th>Order</th>
                <th>Customer</th>
                <th>Pharmacy</th>
                <th className="text-right">Total</th>
                <th>Status</th>
                <th>Placed</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {orders.data!.content.map((order) => (
                <tr key={order.id}>
                  <td className="font-mono text-xs">{order.orderNumber}</td>
                  <td className="text-xs">{order.customerName}</td>
                  <td className="text-xs">{order.pharmacyName}</td>
                  <td className="text-right font-medium">{inr(order.totalAmount)}</td>
                  <td><Badge tone={statusTone(order.status)}>{statusLabel(order.status)}</Badge></td>
                  <td className="text-xs text-slate-500">{dateTime(order.placedAt)}</td>
                  <td>
                    <div className="flex justify-end gap-1">
                      {order.status === 'READY_FOR_PICKUP' && (
                        <button type="button" className="btn-secondary py-1.5 text-xs" onClick={() => assign.mutate(order.id)} disabled={assign.isPending}>
                          <Truck className="h-3.5 w-3.5" /> Assign
                        </button>
                      )}
                      <button type="button" className="btn-secondary py-1.5 text-xs" onClick={() => { setTransitionTarget(order); setNextStatus('CANCELLED'); }}>
                        <RefreshCcw className="h-3.5 w-3.5" /> Status
                      </button>
                      <button type="button" className="btn-ghost py-1.5 text-xs" onClick={() => { setRefundTarget(order); setRefundAmount(String(order.totalAmount)); }}>
                        <Undo2 className="h-3.5 w-3.5" /> Refund
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {orders.data && orders.data.totalPages > 1 && (
        <div className="flex items-center justify-center gap-2">
          <button type="button" className="btn-secondary py-2 text-xs" disabled={page === 0} onClick={() => setPage((value) => value - 1)}>Previous</button>
          <span className="text-xs text-slate-500">Page {page + 1} of {orders.data.totalPages}</span>
          <button type="button" className="btn-secondary py-2 text-xs" disabled={page + 1 >= orders.data.totalPages} onClick={() => setPage((value) => value + 1)}>Next</button>
        </div>
      )}

      <Modal
        open={!!transitionTarget}
        onClose={() => setTransitionTarget(null)}
        title={`Change status · ${transitionTarget?.orderNumber ?? ''}`}
        footer={
          <>
            <button type="button" className="btn-secondary" onClick={() => setTransitionTarget(null)}>Cancel</button>
            <button type="button" className="btn-primary" onClick={() => transition.mutate()} disabled={transition.isPending}>
              {transition.isPending ? <Spinner /> : 'Apply transition'}
            </button>
          </>
        }
      >
        <div className="space-y-3">
          <p className="text-sm text-slate-600">Only transitions allowed by the order state machine will be accepted.</p>
          <div>
            <label className="label" htmlFor="next-status">New status</label>
            <select id="next-status" className="input" value={nextStatus} onChange={(event) => setNextStatus(event.target.value as OrderStatus)}>
              {STATUSES.map((item) => <option key={item} value={item}>{statusLabel(item)}</option>)}
            </select>
          </div>
          <div>
            <label className="label" htmlFor="transition-reason">Reason</label>
            <input id="transition-reason" className="input" maxLength={500} value={transitionReason} onChange={(event) => setTransitionReason(event.target.value)} />
          </div>
        </div>
      </Modal>

      <Modal
        open={!!refundTarget}
        onClose={() => setRefundTarget(null)}
        title={`Refund · ${refundTarget?.orderNumber ?? ''}`}
        footer={
          <>
            <button type="button" className="btn-secondary" onClick={() => setRefundTarget(null)}>Cancel</button>
            <button
              type="button"
              className="btn-danger"
              onClick={() => refund.mutate()}
              disabled={refund.isPending || !refundReason.trim() || Number(refundAmount) <= 0}
            >
              {refund.isPending ? <Spinner /> : 'Process refund'}
            </button>
          </>
        }
      >
        <div className="space-y-3">
          <Alert tone="warning">
            Refunds call the payment gateway and cannot be undone. The order moves to REFUND_PENDING/REFUNDED per the gateway result.
          </Alert>
          <div>
            <label className="label" htmlFor="refund-amount">Amount (₹)</label>
            <input id="refund-amount" className="input" inputMode="decimal" value={refundAmount} onChange={(event) => setRefundAmount(event.target.value)} />
          </div>
          <div>
            <label className="label" htmlFor="refund-reason">Reason</label>
            <textarea id="refund-reason" className="input resize-none" rows={2} maxLength={300} value={refundReason} onChange={(event) => setRefundReason(event.target.value)} />
            <FieldError message={!refundReason.trim() ? 'A reason is required' : undefined} />
          </div>
        </div>
      </Modal>
    </div>
  );
}
