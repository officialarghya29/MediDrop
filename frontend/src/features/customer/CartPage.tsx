import { Link, useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowRight, Minus, Plus, ShieldAlert, ShoppingBag, Trash2 } from 'lucide-react';
import { Alert, Badge, EmptyState, PageLoader, useToast } from '../../components/ui';
import { apiDelete, apiGet, apiPatch, errorMessage } from '../../lib/api';
import { inr } from '../../lib/format';
import type { CartView } from '../../types';

export default function CartPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const queryClient = useQueryClient();

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['cart'],
    queryFn: () => apiGet<CartView>('/cart'),
  });

  const update = useMutation({
    mutationFn: ({ itemId, quantity }: { itemId: number; quantity: number }) =>
      apiPatch<CartView>(`/cart/items/${itemId}`, { quantity }),
    onSuccess: (cart) => {
      queryClient.setQueryData(['cart'], cart);
      queryClient.invalidateQueries({ queryKey: ['cart'] });
    },
    onError: (err) => toast(errorMessage(err), 'error'),
  });

  const remove = useMutation({
    mutationFn: (itemId: number) => apiDelete<CartView>(`/cart/items/${itemId}`),
    onSuccess: (cart) => {
      queryClient.setQueryData(['cart'], cart);
      toast('Item removed', 'info');
    },
    onError: (err) => toast(errorMessage(err), 'error'),
  });

  if (isLoading) return <PageLoader label="Loading your cart…" />;
  if (isError || !data) {
    return (
      <div className="container-page py-10">
        <EmptyState title="Cart unavailable" description={errorMessage(error)} />
      </div>
    );
  }

  if (data.lines.length === 0) {
    return (
      <div className="container-page py-12">
        <EmptyState
          icon={<ShoppingBag className="h-5 w-5" />}
          title="Your cart is empty"
          description="Search for a medicine or browse a nearby pharmacy to start an order."
          action={<Link to="/search" className="btn-primary">Browse medicines</Link>}
        />
      </div>
    );
  }

  return (
    <div className="container-page space-y-6 py-6 sm:py-8">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Your cart</h1>
        <p className="muted mt-1">
          {data.itemCount} item(s) from <span className="font-semibold text-navy-800">{data.pharmacyName}</span>
          {data.distanceKm > 0 ? ` · ${data.distanceKm} km away` : ''}
        </p>
      </div>

      <div className="grid gap-5 lg:grid-cols-[1fr_22rem]">
        <div className="space-y-3">
          {data.warnings.map((warning) => (
            <Alert key={warning} tone="warning">{warning}</Alert>
          ))}
          {data.prescriptionNotice && (
            <Alert tone="info" title="Prescription required">
              {data.prescriptionNotice}
            </Alert>
          )}

          {data.lines.map((line) => (
            <div key={line.id} className="card-pad">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="min-w-0">
                  <div className="flex items-center gap-2">
                    <p className="truncate font-semibold">{line.medicineName}</p>
                    {line.prescriptionRequired && (
                      <Badge tone="bg-amber-50 text-amber-800">
                        <ShieldAlert className="h-3 w-3" /> Rx
                      </Badge>
                    )}
                  </div>
                  <p className="mt-0.5 text-xs text-slate-500">
                    {[line.brand, line.strength, line.packSize].filter(Boolean).join(' · ')}
                  </p>
                  <p className={`mt-1 text-xs ${line.stockSufficient ? 'text-slate-500' : 'font-semibold text-rose-600'}`}>
                    {line.stockSufficient
                      ? `${line.availableQuantity} available`
                      : `Only ${line.availableQuantity} available`}
                  </p>
                </div>
                <div className="text-right">
                  <p className="font-bold">{inr(line.lineTotal)}</p>
                  <p className="text-[11px] text-slate-500">{inr(line.unitPrice)} each</p>
                </div>
              </div>

              <div className="mt-3 flex items-center justify-between">
                <div className="inline-flex items-center rounded-xl border border-slate-300">
                  <button
                    type="button"
                    aria-label="Decrease quantity"
                    className="px-2.5 py-2 text-slate-600 disabled:opacity-40"
                    disabled={line.quantity <= 1 || update.isPending}
                    onClick={() => update.mutate({ itemId: line.id, quantity: line.quantity - 1 })}
                  >
                    <Minus className="h-3.5 w-3.5" />
                  </button>
                  <span className="min-w-8 text-center text-sm font-semibold">{line.quantity}</span>
                  <button
                    type="button"
                    aria-label="Increase quantity"
                    className="px-2.5 py-2 text-slate-600 disabled:opacity-40"
                    disabled={line.quantity >= Math.min(10, line.availableQuantity) || update.isPending}
                    onClick={() => update.mutate({ itemId: line.id, quantity: line.quantity + 1 })}
                  >
                    <Plus className="h-3.5 w-3.5" />
                  </button>
                </div>
                <button
                  type="button"
                  className="btn-ghost py-1.5 text-xs text-rose-700 hover:bg-rose-50"
                  onClick={() => remove.mutate(line.id)}
                  disabled={remove.isPending}
                >
                  <Trash2 className="h-3.5 w-3.5" /> Remove
                </button>
              </div>
            </div>
          ))}
        </div>

        <aside className="h-fit space-y-4">
          <div className="card-pad">
            <h2 className="text-sm font-semibold">Bill details</h2>
            <dl className="mt-3 space-y-2 text-sm">
              <div className="flex justify-between">
                <dt className="text-slate-600">Item subtotal</dt>
                <dd className="font-medium">{inr(data.subtotal)}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-slate-600">Delivery fee</dt>
                <dd className="font-medium">{data.deliveryFee === 0 ? 'Free' : inr(data.deliveryFee)}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-slate-600">Taxes</dt>
                <dd className="font-medium">{inr(data.taxAmount)}</dd>
              </div>
              <div className="flex justify-between border-t border-slate-200 pt-2 text-base font-bold">
                <dt>To pay</dt>
                <dd>{inr(data.totalAmount)}</dd>
              </div>
            </dl>
            <p className="mt-2 text-[11px] text-slate-500">
              Free delivery on orders above ₹499. Coupons can be applied at checkout.
            </p>
            <button
              type="button"
              className="btn-primary mt-4 w-full"
              disabled={!data.checkoutReady}
              onClick={() => navigate('/checkout')}
            >
              Proceed to checkout <ArrowRight className="h-4 w-4" />
            </button>
            {!data.checkoutReady && (
              <p className="mt-2 text-xs font-medium text-rose-600">
                Resolve the warnings above to continue.
              </p>
            )}
            <Link to="/search" className="btn-secondary mt-2 w-full py-2 text-xs">Add more items</Link>
          </div>

          <div className="rounded-2xl bg-white p-4 text-xs leading-relaxed text-slate-500 ring-1 ring-slate-200">
            Stock and prices are revalidated on the server when you place the order. If stock changes, you will be
            asked to review your cart before payment.
          </div>
        </aside>
      </div>
    </div>
  );
}
