import { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Banknote, CreditCard, MapPin, Smartphone, Wallet } from 'lucide-react';
import { Alert, Badge, EmptyState, FieldError, Modal, PageLoader, Spinner, useToast } from '../../components/ui';
import { apiGet, apiPost, errorMessage } from '../../lib/api';
import { inr } from '../../lib/format';
import { useGeolocation } from '../../hooks/useGeolocation';
import { useAuth } from '../../store/auth';
import type { Address, CartView, CheckoutSession, Prescription } from '../../types';

declare global {
  interface Window {
    Razorpay?: any;
  }
}

type Method = 'COD' | 'UPI' | 'CARD' | 'NETBANKING' | 'WALLET';

const METHODS: { id: Method; label: string; hint: string; icon: typeof Banknote; online: boolean }[] = [
  { id: 'UPI', label: 'UPI', hint: 'Google Pay, PhonePe, Paytm', icon: Smartphone, online: true },
  { id: 'CARD', label: 'Card', hint: 'Credit or debit card', icon: CreditCard, online: true },
  { id: 'NETBANKING', label: 'Net banking', hint: 'All major Indian banks', icon: Wallet, online: true },
  { id: 'WALLET', label: 'Wallet', hint: 'Supported prepaid wallets', icon: Wallet, online: true },
  { id: 'COD', label: 'Cash on delivery', hint: 'Pay the rider on handover', icon: Banknote, online: false },
];

async function loadRazorpay(): Promise<any> {
  if (window.Razorpay) return window.Razorpay;
  await new Promise<void>((resolve, reject) => {
    const script = document.createElement('script');
    script.src = 'https://checkout.razorpay.com/v1/checkout.js';
    script.onload = () => resolve();
    script.onerror = () => reject(new Error('Unable to load the payment gateway'));
    document.head.appendChild(script);
  });
  return window.Razorpay;
}

const emptyAddress = {
  label: 'Home',
  recipientName: '',
  phone: '',
  line1: '',
  line2: '',
  city: 'Kolkata',
  state: 'West Bengal',
  pincode: '',
  latitude: 22.5726,
  longitude: 88.3639,
  defaultAddress: false,
};

export default function CheckoutPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const geo = useGeolocation();
  const [addressId, setAddressId] = useState<number | null>(null);
  const [prescriptionId, setPrescriptionId] = useState<string>('');
  const [coupon, setCoupon] = useState('');
  const [note, setNote] = useState('');
  const [method, setMethod] = useState<Method>('COD');
  const [placing, setPlacing] = useState(false);
  const [sandbox, setSandbox] = useState<{ session: CheckoutSession; orderId: number } | null>(null);
  const [addOpen, setAddOpen] = useState(false);
  const [form, setForm] = useState({ ...emptyAddress });
  const [formError, setFormError] = useState<string | null>(null);

  const { data: cart, isLoading } = useQuery({
    queryKey: ['cart'],
    queryFn: () => apiGet<CartView>('/cart'),
  });

  const { data: addresses } = useQuery({
    queryKey: ['addresses'],
    queryFn: () => apiGet<Address[]>('/customer/addresses'),
  });

  const { data: prescriptions } = useQuery({
    queryKey: ['prescriptions'],
    queryFn: () => apiGet<Prescription[]>('/customer/prescriptions'),
  });

  const selectedAddress = useMemo(
    () => addresses?.find((address) => address.id === addressId) ?? addresses?.find((address) => address.defaultAddress) ?? addresses?.[0],
    [addresses, addressId],
  );

  const approvedPrescriptions = (prescriptions ?? []).filter((item) => item.status === 'APPROVED');

  const saveAddress = useMutation({
    mutationFn: () => apiPost<Address>('/customer/addresses', form),
    onSuccess: (address) => {
      toast('Address saved', 'success');
      queryClient.invalidateQueries({ queryKey: ['addresses'] });
      setAddressId(address.id);
      setAddOpen(false);
      setForm({ ...emptyAddress });
    },
    onError: (error) => setFormError(errorMessage(error)),
  });

  const useCurrentLocation = () => {
    geo.request();
    if (geo.coords) {
      setForm((prev) => ({ ...prev, latitude: geo.coords!.latitude, longitude: geo.coords!.longitude }));
    }
  };

  const completeSandbox = async () => {
    if (!sandbox) return;
    try {
      await apiPost('/payments/sandbox/complete', { providerOrderId: sandbox.session.providerOrderId });
      toast('Test payment captured', 'success');
      queryClient.invalidateQueries({ queryKey: ['order', String(sandbox.orderId)] });
      navigate(`/orders/${sandbox.orderId}`, { replace: true });
    } catch (error) {
      toast(errorMessage(error), 'error');
    }
  };

  const payOnline = async (orderId: number, session: CheckoutSession) => {
    if (session.testMode) {
      setSandbox({ session, orderId });
      return;
    }
    try {
      const Razorpay = await loadRazorpay();
      const checkout = new Razorpay({
        key: session.publicKey,
        amount: session.amountPaise,
        currency: session.currency,
        name: 'MediDrop',
        description: `Order payment`,
        order_id: session.providerOrderId,
        prefill: { name: user?.fullName, email: user?.email, contact: user?.phone },
        theme: { color: '#0b8b68' },
        handler: async (response: { razorpay_payment_id: string; razorpay_order_id: string; razorpay_signature: string }) => {
          try {
            await apiPost('/payments/verify', {
              providerOrderId: response.razorpay_order_id,
              providerPaymentId: response.razorpay_payment_id,
              signature: response.razorpay_signature,
            });
            toast('Payment confirmed', 'success');
            navigate(`/orders/${orderId}`, { replace: true });
          } catch (error) {
            toast(errorMessage(error), 'error');
          }
        },
        modal: { ondismiss: () => toast('Payment window closed. You can retry from the order page.', 'info') },
      });
      checkout.open();
    } catch (error) {
      toast(errorMessage(error), 'error');
    }
  };

  const placeOrder = async () => {
    if (!selectedAddress) {
      toast('Select a delivery address', 'error');
      return;
    }
    if (cart?.prescriptionNotice && !prescriptionId) {
      toast('Attach an approved prescription to continue', 'error');
      return;
    }
    setPlacing(true);
    try {
      const order = await apiPost<{ id: number }>(
        '/orders',
        {
          addressId: selectedAddress.id,
          prescriptionId: prescriptionId ? Number(prescriptionId) : null,
          couponCode: coupon.trim().toUpperCase() || null,
          paymentMethod: method,
          note: note.trim() || null,
        },
        { 'X-Idempotency-Key': crypto.randomUUID() },
      );
      queryClient.invalidateQueries({ queryKey: ['cart'] });
      if (method === 'COD') {
        toast('Order placed', 'success');
        navigate(`/orders/${order.id}`, { replace: true });
        return;
      }
      const session = await apiPost<CheckoutSession>('/payments/create', { orderId: order.id, method });
      await payOnline(order.id, session);
    } catch (error) {
      toast(errorMessage(error), 'error');
    } finally {
      setPlacing(false);
    }
  };

  if (isLoading) return <PageLoader label="Preparing checkout…" />;
  if (!cart || cart.lines.length === 0) {
    return (
      <div className="container-page py-10">
        <EmptyState
          title="Your cart is empty"
          description="Add medicines before checking out."
          action={<Link to="/search" className="btn-primary">Browse medicines</Link>}
        />
      </div>
    );
  }

  return (
    <div className="container-page space-y-6 py-6 sm:py-8">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Checkout</h1>
        <p className="muted mt-1">Review your delivery details and confirm the order.</p>
      </div>

      <div className="grid gap-5 lg:grid-cols-[1fr_22rem]">
        <div className="space-y-5">
          <section className="card-pad">
            <div className="flex items-center justify-between">
              <h2 className="section-title">Delivery address</h2>
              <button type="button" className="btn-ghost py-1.5 text-xs" onClick={() => setAddOpen(true)}>+ Add new</button>
            </div>
            {(addresses ?? []).length === 0 ? (
              <p className="mt-2 text-sm text-slate-500">Add an address to continue.</p>
            ) : (
              <div className="mt-3 grid gap-2 sm:grid-cols-2">
                {addresses!.map((address) => (
                  <label
                    key={address.id}
                    className={`cursor-pointer rounded-xl border p-3 text-sm transition ${
                      selectedAddress?.id === address.id ? 'border-brand-500 bg-mint/40' : 'border-slate-200 hover:border-slate-300'
                    }`}
                  >
                    <input
                      type="radio"
                      name="address"
                      className="sr-only"
                      checked={selectedAddress?.id === address.id}
                      onChange={() => setAddressId(address.id)}
                    />
                    <div className="flex items-center justify-between">
                      <span className="font-semibold">{address.label}</span>
                      {address.defaultAddress && <Badge tone="bg-white text-brand-700">Default</Badge>}
                    </div>
                    <p className="mt-1 text-xs text-slate-600">{address.recipientName} · {address.phone}</p>
                    <p className="mt-1 text-xs text-slate-500">
                      {address.line1}{address.line2 ? `, ${address.line2}` : ''}, {address.city} {address.pincode}
                    </p>
                  </label>
                ))}
              </div>
            )}
          </section>

          <section className="card-pad">
            <h2 className="section-title">Prescription</h2>
            {cart.prescriptionNotice ? (
              <>
                <Alert tone="warning" title="Pharmacist review required">
                  {cart.prescriptionNotice}
                </Alert>
                <select className="input mt-3" value={prescriptionId} onChange={(event) => setPrescriptionId(event.target.value)}>
                  <option value="">Select an approved prescription</option>
                  {approvedPrescriptions.map((prescription) => (
                    <option key={prescription.id} value={prescription.id}>
                      {prescription.fileName} · approved {new Date(prescription.uploadedAt).toLocaleDateString('en-IN')}
                    </option>
                  ))}
                </select>
                {approvedPrescriptions.length === 0 && (
                  <p className="mt-2 text-xs text-rose-600">
                    No approved prescription found. <Link to="/prescriptions" className="underline">Upload one</Link> and wait for pharmacist review.
                  </p>
                )}
              </>
            ) : (
              <p className="muted mt-1 text-sm">Your cart contains only over-the-counter medicines, so no prescription is needed.</p>
            )}
          </section>

          <section className="card-pad">
            <h2 className="section-title">Payment method</h2>
            <div className="mt-3 grid gap-2 sm:grid-cols-2">
              {METHODS.map((option) => {
                const Icon = option.icon;
                return (
                  <label
                    key={option.id}
                    className={`flex cursor-pointer items-center gap-3 rounded-xl border p-3 text-sm transition ${
                      method === option.id ? 'border-brand-500 bg-mint/40' : 'border-slate-200 hover:border-slate-300'
                    }`}
                  >
                    <input type="radio" name="payment" className="sr-only" checked={method === option.id} onChange={() => setMethod(option.id)} />
                    <Icon className="h-4 w-4 text-brand-700" />
                    <span>
                      <span className="block font-semibold">{option.label}</span>
                      <span className="block text-[11px] text-slate-500">{option.hint}</span>
                    </span>
                  </label>
                );
              })}
            </div>
            <p className="mt-3 text-[11px] text-slate-500">
              Online payments are processed by Razorpay. In sandbox mode the API simulates capture and flags the payment as a test.
            </p>
          </section>

          <section className="card-pad">
            <h2 className="section-title">Order notes</h2>
            <textarea
              className="input mt-2 resize-none"
              rows={2}
              maxLength={500}
              placeholder="Optional instructions for the pharmacy or rider"
              value={note}
              onChange={(event) => setNote(event.target.value)}
            />
          </section>
        </div>

        <aside className="h-fit space-y-4">
          <div className="card-pad">
            <h2 className="text-sm font-semibold">Order summary</h2>
            <p className="mt-1 text-xs text-slate-500">{cart.pharmacyName} · {cart.itemCount} item(s)</p>
            <ul className="mt-3 space-y-2 border-t border-slate-100 pt-3 text-xs">
              {cart.lines.map((line) => (
                <li key={line.id} className="flex justify-between gap-3">
                  <span className="text-slate-600">{line.medicineName} × {line.quantity}</span>
                  <span className="font-medium">{inr(line.lineTotal)}</span>
                </li>
              ))}
            </ul>
            <div className="mt-3 flex gap-2 border-t border-slate-100 pt-3">
              <input
                className="input py-2 text-xs uppercase"
                placeholder="Coupon code"
                value={coupon}
                onChange={(event) => setCoupon(event.target.value)}
                aria-label="Coupon code"
              />
            </div>
            <dl className="mt-3 space-y-2 text-sm">
              <div className="flex justify-between"><dt className="text-slate-600">Subtotal</dt><dd>{inr(cart.subtotal)}</dd></div>
              <div className="flex justify-between"><dt className="text-slate-600">Delivery</dt><dd>{cart.deliveryFee === 0 ? 'Free' : inr(cart.deliveryFee)}</dd></div>
              <div className="flex justify-between"><dt className="text-slate-600">Taxes</dt><dd>{inr(cart.taxAmount)}</dd></div>
              <div className="flex justify-between border-t border-slate-200 pt-2 text-base font-bold"><dt>Total</dt><dd>{inr(cart.totalAmount)}</dd></div>
            </dl>
            <p className="mt-2 text-[11px] text-slate-500">Coupon discounts are validated and applied by the server when the order is placed.</p>
            <button type="button" className="btn-primary mt-4 w-full" onClick={placeOrder} disabled={placing || !selectedAddress}>
              {placing ? <Spinner /> : method === 'COD' ? 'Place order' : `Pay ${inr(cart.totalAmount)}`}
            </button>
            <Link to="/cart" className="btn-secondary mt-2 w-full py-2 text-xs">Back to cart</Link>
          </div>
          <div className="flex items-start gap-2 rounded-2xl bg-white p-4 text-[11px] leading-relaxed text-slate-500 ring-1 ring-slate-200">
            <MapPin className="mt-0.5 h-3.5 w-3.5 shrink-0" />
            Your delivery address must fall inside the pharmacy&apos;s service radius. MediDrop verifies this on the server.
          </div>
        </aside>
      </div>

      <Modal
        open={addOpen}
        onClose={() => setAddOpen(false)}
        title="Add delivery address"
        footer={
          <>
            <button type="button" className="btn-secondary" onClick={() => setAddOpen(false)}>Cancel</button>
            <button type="button" className="btn-primary" onClick={() => saveAddress.mutate()} disabled={saveAddress.isPending}>
              {saveAddress.isPending ? <Spinner /> : 'Save address'}
            </button>
          </>
        }
      >
        <div className="space-y-3">
          {formError && <Alert tone="error">{formError}</Alert>}
          <div className="grid gap-3 sm:grid-cols-2">
            <div>
              <label className="label" htmlFor="addr-label">Label</label>
              <input id="addr-label" className="input" value={form.label} onChange={(e) => setForm({ ...form, label: e.target.value })} />
            </div>
            <div>
              <label className="label" htmlFor="addr-name">Recipient</label>
              <input id="addr-name" className="input" value={form.recipientName} onChange={(e) => setForm({ ...form, recipientName: e.target.value })} />
            </div>
            <div>
              <label className="label" htmlFor="addr-phone">Mobile</label>
              <input id="addr-phone" className="input" inputMode="numeric" maxLength={10} value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value.replace(/\D/g, '') })} />
            </div>
            <div>
              <label className="label" htmlFor="addr-pin">PIN code</label>
              <input id="addr-pin" className="input" inputMode="numeric" maxLength={6} value={form.pincode} onChange={(e) => setForm({ ...form, pincode: e.target.value.replace(/\D/g, '') })} />
            </div>
          </div>
          <div>
            <label className="label" htmlFor="addr-line1">Address line 1</label>
            <input id="addr-line1" className="input" value={form.line1} onChange={(e) => setForm({ ...form, line1: e.target.value })} />
          </div>
          <div>
            <label className="label" htmlFor="addr-line2">Address line 2 (optional)</label>
            <input id="addr-line2" className="input" value={form.line2} onChange={(e) => setForm({ ...form, line2: e.target.value })} />
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            <div>
              <label className="label" htmlFor="addr-city">City</label>
              <input id="addr-city" className="input" value={form.city} onChange={(e) => setForm({ ...form, city: e.target.value })} />
            </div>
            <div>
              <label className="label" htmlFor="addr-state">State</label>
              <input id="addr-state" className="input" value={form.state} onChange={(e) => setForm({ ...form, state: e.target.value })} />
            </div>
          </div>
          <div className="rounded-xl bg-slate-50 p-3">
            <div className="flex items-center justify-between gap-2">
              <p className="text-xs text-slate-600">
                Coordinates: {form.latitude.toFixed(4)}, {form.longitude.toFixed(4)}
              </p>
              <button type="button" className="btn-secondary py-1.5 text-xs" onClick={useCurrentLocation}>Use my location</button>
            </div>
            {geo.error && <p className="mt-2 text-xs text-rose-600">{geo.error}</p>}
            <label className="mt-3 flex items-center gap-2 text-xs text-slate-600">
              <input
                type="checkbox"
                checked={form.defaultAddress}
                onChange={(e) => setForm({ ...form, defaultAddress: e.target.checked })}
              />
              Make this my default address
            </label>
          </div>
          <FieldError message={form.recipientName === '' ? 'Recipient name is required' : undefined} />
        </div>
      </Modal>

      <Modal
        open={!!sandbox}
        onClose={() => setSandbox(null)}
        title="Sandbox payment"
        footer={
          <>
            <button type="button" className="btn-secondary" onClick={() => navigate(`/orders/${sandbox?.orderId}`)}>Pay later</button>
            <button type="button" className="btn-primary" onClick={completeSandbox}>Complete test payment</button>
          </>
        }
      >
        <div className="space-y-3 text-sm text-slate-600">
          <Alert tone="info" title="Test mode">
            No real money moves. The backend records this payment with the <code>test</code> flag so it is never
            mistaken for a live transaction.
          </Alert>
          <dl className="space-y-1 text-xs">
            <div className="flex justify-between"><dt>Provider order</dt><dd className="font-mono">{sandbox?.session.providerOrderId}</dd></div>
            <div className="flex justify-between"><dt>Amount</dt><dd>{inr((sandbox?.session.amountPaise ?? 0) / 100)}</dd></div>
            <div className="flex justify-between"><dt>Method</dt><dd>{method}</dd></div>
          </dl>
        </div>
      </Modal>
    </div>
  );
}
