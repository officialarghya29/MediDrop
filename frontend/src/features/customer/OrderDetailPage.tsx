import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Client } from '@stomp/stompjs';
import { ArrowLeft, Download, MapPin, Phone, RefreshCcw, ShieldCheck, Star, Truck, XCircle } from 'lucide-react';
import MapView from '../../components/MapView';
import { Alert, Badge, EmptyState, FieldError, Modal, PageLoader, Spinner, useToast } from '../../components/ui';
import { apiGet, apiPost, errorMessage } from '../../lib/api';
import { dateTime, inr, isActiveOrder, statusLabel, statusTone } from '../../lib/format';
import type { Invoice, OrderDetail, OrderStatus } from '../../types';

const STEPS: OrderStatus[] = [
  'PAYMENT_CONFIRMED', 'PHARMACY_ACCEPTED', 'PREPARING', 'READY_FOR_PICKUP', 'OUT_FOR_DELIVERY', 'DELIVERED',
];

const stepLabel: Partial<Record<OrderStatus, string>> = {
  PAYMENT_CONFIRMED: 'Order confirmed',
  PHARMACY_ACCEPTED: 'Pharmacy accepted',
  PREPARING: 'Being packed',
  READY_FOR_PICKUP: 'Ready for pickup',
  OUT_FOR_DELIVERY: 'Out for delivery',
  DELIVERED: 'Delivered',
};

const WS_URL =
  (import.meta.env.VITE_WS_URL as string | undefined) ||
  `${window.location.protocol === 'https:' ? 'wss' : 'ws'}://${window.location.host}/ws`;

export default function OrderDetailPage() {
  const { id } = useParams();
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const toast = useToast();
  const queryClient = useQueryClient();
  const [cancelOpen, setCancelOpen] = useState(false);
  const [reason, setReason] = useState('');
  const [reviewOpen, setReviewOpen] = useState(false);
  const [rating, setRating] = useState(5);
  const [comment, setComment] = useState('');
  const [invoiceBusy, setInvoiceBusy] = useState(false);
  const subscribed = useRef<string | null>(null);

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['order', id],
    queryFn: () => apiGet<OrderDetail>(`/orders/${id}`),
    refetchInterval: (query) => {
      const order = query.state.data;
      return order && isActiveOrder(order.status) ? 25_000 : false;
    },
  });

  useEffect(() => {
    if (!data?.trackingKey) return;
    if (subscribed.current === data.trackingKey) return;
    subscribed.current = data.trackingKey;
    const client = new Client({
      brokerURL: WS_URL,
      reconnectDelay: 6000,
      onConnect: () => {
        client.subscribe(`/topic/tracking/${data.trackingKey}`, () => {
          queryClient.invalidateQueries({ queryKey: ['order', id] });
        });
      },
    });
    client.activate();
    return () => {
      client.deactivate();
      subscribed.current = null;
    };
  }, [data?.trackingKey, id, queryClient]);

  const cancel = useMutation({
    mutationFn: () => apiPost<OrderDetail>(`/orders/${id}/cancel`, { reason: reason.trim() || undefined }),
    onSuccess: (order) => {
      toast(order.status === 'CANCELLATION_REQUESTED' ? 'Cancellation requested' : 'Order cancelled', 'success');
      queryClient.invalidateQueries({ queryKey: ['order', id] });
      queryClient.invalidateQueries({ queryKey: ['orders'] });
      setCancelOpen(false);
    },
    onError: (err) => toast(errorMessage(err), 'error'),
  });

  const reorder = useMutation({
    mutationFn: () => apiPost(`/orders/${id}/reorder`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['cart'] });
      toast('Available items added to your cart', 'success');
      navigate('/cart');
    },
    onError: (err) => toast(errorMessage(err), 'error'),
  });

  const review = useMutation({
    mutationFn: () => apiPost(`/orders/${id}/review`, { rating, comment: comment.trim() || undefined }),
    onSuccess: () => {
      toast('Thanks for your feedback', 'success');
      setReviewOpen(false);
    },
    onError: (err) => toast(errorMessage(err), 'error'),
  });

  const openInvoice = async () => {
    setInvoiceBusy(true);
    try {
      const invoice = await apiGet<Invoice>(`/orders/${id}/invoice`);
      const win = window.open('', '_blank', 'width=820,height=900');
      if (!win) {
        toast('Allow pop-ups to view the invoice', 'error');
        return;
      }
      win.document.write(invoiceHtml(invoice));
      win.document.close();
    } catch (err) {
      toast(errorMessage(err), 'error');
    } finally {
      setInvoiceBusy(false);
    }
  };

  if (isLoading) return <PageLoader label="Loading order…" />;
  if (isError || !data) {
    return (
      <div className="container-page py-10">
        <EmptyState title="Order unavailable" description={errorMessage(error)} action={<Link to="/orders" className="btn-secondary">Back to orders</Link>} />
      </div>
    );
  }

  const cancelled = data.status === 'CANCELLED' || data.status === 'PRESCRIPTION_REJECTED' || data.status === 'FAILED';
  const activeStep = STEPS.indexOf(data.status);
  const partner = data.deliveryPartner;
  const destination = { lat: data.latitude, lng: data.longitude };
  const markers = [
    { lat: data.latitude, lng: data.longitude, label: 'Delivery address', tone: 'brand' as const },
    ...(data.pharmacyLatitude && data.pharmacyLongitude
      ? [{ lat: data.pharmacyLatitude, lng: data.pharmacyLongitude, label: data.pharmacyName ?? 'Pharmacy', tone: 'navy' as const }]
      : []),
    ...(partner?.latitude && partner?.longitude
      ? [{ lat: partner.latitude, lng: partner.longitude, label: `${partner.name} (rider)`, tone: 'amber' as const }]
      : []),
  ];

  return (
    <div className="container-page space-y-5 py-6 sm:py-8">
      <button type="button" onClick={() => navigate('/orders')} className="btn-ghost -ml-2 py-1.5 text-xs">
        <ArrowLeft className="h-3.5 w-3.5" /> All orders
      </button>

      {params.get('placed') && (
        <Alert tone="success" title="Order placed successfully">
          The pharmacy will confirm your order shortly. You can follow every step on this page.
        </Alert>
      )}

      {data.prescriptionStatus && (
        <Alert
          tone={data.prescriptionStatus === 'REJECTED' ? 'error' : data.prescriptionStatus === 'APPROVED' ? 'success' : 'warning'}
          title={`Prescription ${statusLabel(data.prescriptionStatus)}`}
        >
          {data.prescriptionStatus === 'REJECTED'
            ? 'A pharmacist could not approve the uploaded prescription. Upload a clearer copy or contact support for a refund.'
            : data.prescriptionStatus === 'APPROVED'
              ? 'A licensed pharmacist approved the prescription and the pharmacy can dispense your medicines.'
              : 'A licensed pharmacist is reviewing your prescription. Dispensing starts only after approval.'}
        </Alert>
      )}

      <div className="grid gap-5 lg:grid-cols-[1fr_22rem]">
        <div className="space-y-5">
          <section className="card-pad">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <div className="flex flex-wrap items-center gap-2">
                  <h1 className="font-mono text-sm font-semibold text-slate-500">{data.orderNumber}</h1>
                  <Badge tone={statusTone(data.status)}>{statusLabel(data.status)}</Badge>
                </div>
                <p className="mt-2 text-lg font-bold">{data.pharmacyName}</p>
                <p className="mt-1 text-xs text-slate-500">
                  Placed {dateTime(data.placedAt)}
                  {data.etaMinutes && isActiveOrder(data.status) ? ` · ETA ~${data.etaMinutes} min` : ''}
                </p>
              </div>
              <div className="text-right">
                <p className="text-xl font-bold">{inr(data.totalAmount)}</p>
                <p className="text-[11px] uppercase tracking-wide text-slate-500">{data.paymentMethod}</p>
              </div>
            </div>

            {!cancelled && (
              <ol className="mt-5 grid gap-2 sm:grid-cols-6">
                {STEPS.map((step, index) => {
                  const done = activeStep >= index;
                  return (
                    <li key={step} className="flex items-center gap-2 sm:flex-col sm:items-start">
                      <span className={`flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-[10px] font-bold ${done ? 'bg-brand-700 text-white' : 'bg-slate-100 text-slate-500'}`}>
                        {index + 1}
                      </span>
                      <span className={`text-[11px] leading-tight ${done ? 'font-semibold text-navy-900' : 'text-slate-500'}`}>
                        {stepLabel[step]}
                      </span>
                    </li>
                  );
                })}
              </ol>
            )}

            {cancelled && (
              <div className="mt-4 rounded-xl bg-rose-50 p-3 text-sm text-rose-800">
                <p className="font-semibold">{statusLabel(data.status)}</p>
                {data.cancellationReason && <p className="mt-1 text-xs">{data.cancellationReason}</p>}
              </div>
            )}
          </section>

          {partner && (
            <section className="card-pad">
              <div className="flex items-center justify-between">
                <h2 className="section-title">Your delivery partner</h2>
                {data.deliveryOtp && !data.deliveryOtpVerified && (
                  <Badge tone="bg-amber-50 text-amber-800">Share OTP on handover</Badge>
                )}
              </div>
              <div className="mt-3 flex flex-wrap items-center justify-between gap-3">
                <div>
                  <p className="font-semibold">{partner.name}</p>
                  <p className="text-xs text-slate-500">{partner.vehicleType ?? 'Two-wheeler'} · {partner.phone}</p>
                </div>
                <div className="flex items-center gap-2">
                  <a href={`tel:${partner.phone}`} className="btn-secondary py-2 text-xs"><Phone className="h-3.5 w-3.5" /> Call</a>
                  {data.trackingKey && (
                    <Link to={`/track/${data.trackingKey}`} className="btn-primary py-2 text-xs"><Truck className="h-3.5 w-3.5" /> Live track</Link>
                  )}
                </div>
              </div>
              {data.deliveryOtp && !data.deliveryOtpVerified && (
                <p className="mt-3 rounded-lg bg-mint/50 px-3 py-2 text-sm">
                  Handover OTP: <span className="font-mono text-lg font-bold tracking-widest">{data.deliveryOtp}</span>
                </p>
              )}
            </section>
          )}

          <section className="space-y-2">
            <h2 className="section-title">Delivery map</h2>
            <MapView
              center={destination}
              markers={markers}
              caption="Rider location updates over a secure WebSocket channel every few seconds while your order is out for delivery."
            />
            <p className="flex items-center gap-1.5 text-xs text-slate-500">
              <MapPin className="h-3.5 w-3.5" /> {data.addressLine}, {data.city} {data.pincode}
            </p>
          </section>

          <section className="card-pad">
            <h2 className="section-title">Items</h2>
            <ul className="mt-3 divide-y divide-slate-100">
              {data.items.map((item) => (
                <li key={item.id} className="flex items-center justify-between gap-3 py-2.5 text-sm">
                  <div>
                    <p className="font-medium">{item.medicineName}</p>
                    <p className="text-xs text-slate-500">{inr(item.unitPrice)} × {item.quantity}</p>
                  </div>
                  <p className="font-semibold">{inr(item.lineTotal)}</p>
                </li>
              ))}
            </ul>
            <dl className="mt-3 space-y-1.5 border-t border-slate-100 pt-3 text-sm">
              <div className="flex justify-between"><dt className="text-slate-600">Subtotal</dt><dd>{inr(data.subtotal)}</dd></div>
              {data.discount > 0 && <div className="flex justify-between text-emerald-700"><dt>Discount {data.couponCode ? `(${data.couponCode})` : ''}</dt><dd>-{inr(data.discount)}</dd></div>}
              <div className="flex justify-between"><dt className="text-slate-600">Delivery</dt><dd>{data.deliveryFee === 0 ? 'Free' : inr(data.deliveryFee)}</dd></div>
              <div className="flex justify-between"><dt className="text-slate-600">Taxes</dt><dd>{inr(data.taxAmount)}</dd></div>
              <div className="flex justify-between border-t border-slate-200 pt-2 font-bold"><dt>Total</dt><dd>{inr(data.totalAmount)}</dd></div>
            </dl>
            {data.customerNote && <p className="mt-3 rounded-lg bg-slate-50 p-3 text-xs text-slate-600">Note: {data.customerNote}</p>}
          </section>

          <section className="card-pad">
            <h2 className="section-title">Order timeline</h2>
            <ol className="mt-3 space-y-3">
              {[...data.history].reverse().map((entry, index) => (
                <li key={`${entry.toStatus}-${entry.at}-${index}`} className="flex gap-3">
                  <span className={`mt-1.5 h-2 w-2 shrink-0 rounded-full ${index === 0 ? 'bg-brand-700' : 'bg-slate-300'}`} />
                  <div>
                    <p className="text-sm font-medium">{statusLabel(entry.toStatus)}</p>
                    <p className="text-[11px] text-slate-500">
                      {dateTime(entry.at)} {entry.actorRole ? `· ${entry.actorRole.toLowerCase()}` : ''}
                    </p>
                    {entry.reason && <p className="text-xs text-slate-600">{entry.reason}</p>}
                  </div>
                </li>
              ))}
            </ol>
          </section>
        </div>

        <aside className="h-fit space-y-3">
          <div className="card-pad">
            <h2 className="text-sm font-semibold">Actions</h2>
            <div className="mt-3 space-y-2">
              {data.trackingKey && (
                <Link to={`/track/${data.trackingKey}`} className="btn-secondary w-full py-2 text-xs">
                  <Truck className="h-3.5 w-3.5" /> Public tracking link
                </Link>
              )}
              <button type="button" className="btn-secondary w-full py-2 text-xs" onClick={openInvoice} disabled={invoiceBusy}>
                {invoiceBusy ? <Spinner /> : <><Download className="h-3.5 w-3.5" /> Download invoice</>}
              </button>
              <button type="button" className="btn-secondary w-full py-2 text-xs" onClick={() => reorder.mutate()} disabled={reorder.isPending}>
                <RefreshCcw className="h-3.5 w-3.5" /> Reorder items
              </button>
              {data.status === 'DELIVERED' && (
                <button type="button" className="btn-secondary w-full py-2 text-xs" onClick={() => setReviewOpen(true)}>
                  <Star className="h-3.5 w-3.5" /> Rate this order
                </button>
              )}
              {isActiveOrder(data.status) && (
                <button
                  type="button"
                  className="btn-ghost w-full py-2 text-xs text-rose-700 hover:bg-rose-50"
                  onClick={() => setCancelOpen(true)}
                >
                  <XCircle className="h-3.5 w-3.5" /> Cancel order
                </button>
              )}
            </div>
          </div>

          <div className="card-pad">
            <div className="flex items-center gap-2 text-sm font-semibold text-brand-800">
              <ShieldCheck className="h-4 w-4" /> Need help?
            </div>
            <p className="mt-2 text-xs text-slate-600">
              For substitution requests or prescription questions, contact your pharmacy directly or raise a
              support ticket from your profile.
            </p>
            <Link to="/profile#support" className="link mt-2 inline-block text-xs">Contact support</Link>
          </div>
        </aside>
      </div>

      <Modal
        open={cancelOpen}
        onClose={() => setCancelOpen(false)}
        title="Cancel this order?"
        footer={
          <>
            <button type="button" className="btn-secondary" onClick={() => setCancelOpen(false)}>Keep order</button>
            <button type="button" className="btn-danger" onClick={() => cancel.mutate()} disabled={cancel.isPending || reason.trim().length < 3}>
              {cancel.isPending ? <Spinner /> : 'Cancel order'}
            </button>
          </>
        }
      >
        <p className="text-sm text-slate-600">
          {['PREPARING', 'READY_FOR_PICKUP', 'DELIVERY_ASSIGNED'].includes(data.status)
            ? 'Your order is already being prepared, so this becomes a cancellation request that the pharmacy must approve. Paid amounts are refunded to the original payment method.'
            : 'Cancelling now stops fulfilment immediately. Any captured payment is reversed by the MediDrop team.'}
        </p>
        <label className="label mt-4" htmlFor="cancel-reason">Reason (required)</label>
        <textarea
          id="cancel-reason"
          className="input resize-none"
          rows={3}
          maxLength={500}
          value={reason}
          onChange={(event) => setReason(event.target.value)}
          placeholder="Tell us why you want to cancel"
        />
        <FieldError message={reason.trim().length > 0 && reason.trim().length < 3 ? 'Add a little more detail' : undefined} />
      </Modal>

      <Modal
        open={reviewOpen}
        onClose={() => setReviewOpen(false)}
        title="Rate your order"
        footer={
          <>
            <button type="button" className="btn-secondary" onClick={() => setReviewOpen(false)}>Later</button>
            <button type="button" className="btn-primary" onClick={() => review.mutate()} disabled={review.isPending}>
              {review.isPending ? <Spinner /> : 'Submit review'}
            </button>
          </>
        }
      >
        <div className="flex gap-1">
          {[1, 2, 3, 4, 5].map((value) => (
            <button
              key={value}
              type="button"
              onClick={() => setRating(value)}
              aria-label={`${value} star${value > 1 ? 's' : ''}`}
              className={value <= rating ? 'text-amber-500' : 'text-slate-300'}
            >
              <Star className="h-7 w-7" fill={value <= rating ? 'currentColor' : 'none'} />
            </button>
          ))}
        </div>
        <textarea
          className="input mt-4 resize-none"
          rows={3}
          maxLength={1000}
          placeholder="How was the packaging, delivery and accuracy?"
          value={comment}
          onChange={(event) => setComment(event.target.value)}
        />
      </Modal>
    </div>
  );
}

function invoiceHtml(invoice: Invoice): string {
  const rows = invoice.items
    .map(
      (item) =>
        `<tr><td>${escapeHtml(item.medicineName)}</td><td style="text-align:right">${item.quantity}</td>` +
        `<td style="text-align:right">₹${item.unitPrice.toFixed(2)}</td><td style="text-align:right">₹${item.lineTotal.toFixed(2)}</td></tr>`,
    )
    .join('');
  return `<!doctype html><html><head><meta charset="utf-8"><title>Invoice ${escapeHtml(invoice.orderNumber)}</title>
  <style>
    body{font-family:-apple-system,Segoe UI,Roboto,sans-serif;color:#17313d;padding:32px;max-width:760px;margin:auto}
    h1{font-size:20px;margin:0}small{color:#64748b}
    table{width:100%;border-collapse:collapse;margin-top:24px;font-size:13px}
    th,td{padding:8px;border-bottom:1px solid #e2e8f0;text-align:left}
    .totals{margin-top:16px;margin-left:auto;width:280px;font-size:13px}
    .totals div{display:flex;justify-content:space-between;padding:4px 0}
    .grand{font-weight:700;border-top:1px solid #cbd5e1;padding-top:8px}
    .meta{display:grid;grid-template-columns:1fr 1fr;gap:16px;margin-top:24px;font-size:12px;line-height:1.6}
    .note{margin-top:24px;font-size:11px;color:#64748b;line-height:1.6}
  </style></head><body>
  <div style="display:flex;justify-content:space-between;align-items:flex-start">
    <div><h1>MediDrop</h1><small>Tax invoice / bill of supply</small></div>
    <div style="text-align:right"><strong>${escapeHtml(invoice.orderNumber)}</strong><br><small>${new Date(invoice.issuedAt).toLocaleString('en-IN')}</small></div>
  </div>
  <div class="meta">
    <div><strong>Sold by</strong><br>${escapeHtml(invoice.sellerName)}<br>${escapeHtml(invoice.sellerAddress)}<br>
      ${invoice.sellerGstin ? `GSTIN ${escapeHtml(invoice.sellerGstin)}<br>` : ''}
      ${invoice.sellerLicense ? `Drug licence ${escapeHtml(invoice.sellerLicense)}` : ''}</div>
    <div><strong>Billed to</strong><br>${escapeHtml(invoice.buyerName)}<br>${escapeHtml(invoice.buyerAddress)}<br>
      Payment: ${escapeHtml(invoice.paymentMethod)}</div>
  </div>
  <table><thead><tr><th>Item</th><th style="text-align:right">Qty</th><th style="text-align:right">Rate</th><th style="text-align:right">Amount</th></tr></thead>
  <tbody>${rows}</tbody></table>
  <div class="totals">
    <div><span>Subtotal</span><span>₹${invoice.subtotal.toFixed(2)}</span></div>
    <div><span>Discount</span><span>-₹${invoice.discount.toFixed(2)}</span></div>
    <div><span>Delivery</span><span>₹${invoice.deliveryFee.toFixed(2)}</span></div>
    <div><span>Taxes</span><span>₹${invoice.taxAmount.toFixed(2)}</span></div>
    <div class="grand"><span>Total paid</span><span>₹${invoice.totalAmount.toFixed(2)}</span></div>
  </div>
  <p class="note">This invoice is generated by MediDrop for a licensed pharmacy partner. Prescription medicines are
  dispensed against a prescription approved by a registered pharmacist. ${escapeHtml(invoice.note ?? '')}</p>
  <script>window.onload=()=>window.print()</script></body></html>`;
}

function escapeHtml(value: string): string {
  return value.replace(/[&<>"']/g, (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char] ?? char);
}
