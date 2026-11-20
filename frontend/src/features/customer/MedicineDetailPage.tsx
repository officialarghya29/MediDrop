import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, MapPin, Pill, ShieldAlert, ShieldCheck, ShoppingCart, TrendingDown } from 'lucide-react';
import { Alert, Badge, EmptyState, PageLoader, useToast } from '../../components/ui';
import { apiGet, apiPost, errorMessage } from '../../lib/api';
import { inr } from '../../lib/format';
import { useAuth } from '../../store/auth';
import { useGeolocation } from '../../hooks/useGeolocation';
import type { Address, CartView, MedicineDetail, SubstituteResponse } from '../../types';

const DEMO_KOLKATA = { latitude: 22.5726, longitude: 88.3639 };

export default function MedicineDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const toast = useToast();
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const geo = useGeolocation();
  const [selected, setSelected] = useState<number | null>(null);
  const [quantity, setQuantity] = useState(1);
  const [comparePharmacyId, setComparePharmacyId] = useState<number | null>(null);

  const { data: address } = useQuery({
    queryKey: ['addresses', 'default'],
    queryFn: () => apiGet<Address[]>('/customer/addresses'),
    enabled: !!user && user.role === 'CUSTOMER',
  });

  const position = geo.coords
    ?? (address?.[0] ? { latitude: address[0].latitude, longitude: address[0].longitude } : DEMO_KOLKATA);

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['medicine', id, position.latitude, position.longitude],
    queryFn: () => apiGet<MedicineDetail>(`/medicines/${id}`, { lat: position.latitude, lng: position.longitude }),
  });

  const comparePharmacy = data?.availability.find((option) => option.pharmacyId === comparePharmacyId)
    ?? data?.cheapest;

  const { data: substitutes } = useQuery({
    queryKey: ['medicine-substitutes', id, comparePharmacy?.pharmacyId],
    queryFn: () => apiGet<SubstituteResponse>(`/medicines/${id}/substitutes`, {
      pharmacyId: comparePharmacy?.pharmacyId,
      limit: 3,
    }),
    enabled: !!data?.genericName && !!data?.strength && !!data?.dosageForm && !!comparePharmacy,
  });

  const addToCart = useMutation({
    mutationFn: (inventoryId: number) => apiPost<CartView>('/cart/items', { inventoryId, quantity }),
    onSuccess: () => {
      toast('Added to cart', 'success');
      queryClient.invalidateQueries({ queryKey: ['cart'] });
      navigate('/cart');
    },
    onError: (err) => toast(errorMessage(err), 'error'),
  });

  const handleAdd = (inventoryId: number) => {
    if (!user) {
      navigate('/login', { state: { from: `/medicines/${id}` } });
      return;
    }
    setSelected(inventoryId);
    addToCart.mutate(inventoryId);
  };

  if (isLoading) return <PageLoader label="Loading medicine…" />;
  if (isError || !data) {
    return (
      <div className="container-page py-10">
        <EmptyState
          title="Medicine not found"
          description={errorMessage(error)}
          action={<Link to="/search" className="btn-secondary">Back to search</Link>}
        />
      </div>
    );
  }

  return (
    <div className="container-page space-y-6 py-6 sm:py-8">
      <button type="button" onClick={() => navigate(-1)} className="btn-ghost -ml-2 py-1.5 text-xs">
        <ArrowLeft className="h-3.5 w-3.5" /> Back
      </button>

      <div className="grid gap-6 lg:grid-cols-[1fr_22rem]">
        <div className="space-y-5">
          <div className="card-pad">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div className="flex gap-4">
                <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-mint text-brand-700">
                  <Pill className="h-7 w-7" />
                </div>
                <div>
                  <h1 className="text-2xl font-bold tracking-tight">{data.name}</h1>
                  <p className="muted mt-1">
                    {[data.brand, data.genericName, data.strength, data.dosageForm, data.packSize]
                      .filter(Boolean)
                      .join(' · ')}
                  </p>
                  <div className="mt-2 flex flex-wrap gap-2">
                    {data.prescriptionRequired ? (
                      <Badge tone="bg-amber-50 text-amber-800">
                        <ShieldAlert className="h-3 w-3" /> Prescription required
                      </Badge>
                    ) : (
                      <Badge tone="bg-emerald-50 text-emerald-700">Over the counter</Badge>
                    )}
                    {data.categoryName && <Badge>{data.categoryName}</Badge>}
                    {data.scheduleClass && <Badge tone="bg-slate-100 text-slate-600">Schedule {data.scheduleClass}</Badge>}
                  </div>
                </div>
              </div>
              <div className="text-right">
                {data.cheapest ? (
                  <>
                    <p className="text-2xl font-bold">{inr(data.cheapest.price)}</p>
                    <p className="text-xs text-slate-500">
                      at {data.cheapest.pharmacyName}
                      {data.cheapest.distanceKm !== undefined ? ` · ${data.cheapest.distanceKm} km` : ''}
                    </p>
                  </>
                ) : (
                  <p className="text-sm font-semibold text-slate-500">Out of stock nearby</p>
                )}
                <p className="mt-1 text-[11px] text-slate-400">{data.totalStock} units available</p>
              </div>
            </div>
          </div>

          {data.description && (
            <section className="card-pad">
              <h2 className="section-title">About this medicine</h2>
              <p className="mt-2 text-sm leading-relaxed text-slate-600">{data.description}</p>
              {data.manufacturerName && (
                <p className="mt-3 text-xs text-slate-500">Manufacturer: {data.manufacturerName}</p>
              )}
            </section>
          )}

          {data.warnings && (
            <Alert tone="warning" title="Important safety information">
              {data.warnings}
            </Alert>
          )}

          {comparePharmacy && substitutes?.referencePrice != null && (
            <section className="card-pad">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <h2 className="section-title flex items-center gap-2">
                    <TrendingDown className="h-4 w-4 text-brand-700" /> Lower-cost substitutes
                  </h2>
                  <p className="muted mt-1 text-xs">
                    Same composition, strength, form and pack size. Not an automatic or clinical recommendation.
                  </p>
                </div>
                {data.availability.length > 1 && (
                  <label className="text-xs text-slate-600">
                    Compare at
                    <select
                      className="input mt-1 py-1.5 text-xs"
                      value={comparePharmacy?.pharmacyId ?? ''}
                      onChange={(event) => setComparePharmacyId(Number(event.target.value))}
                      aria-label="Pharmacy to compare substitutes at"
                    >
                      {data.availability.map((option) => (
                        <option key={option.pharmacyId} value={option.pharmacyId}>{option.pharmacyName}</option>
                      ))}
                    </select>
                  </label>
                )}
              </div>

              {substitutes.options.length === 0 ? (
                <p className="mt-3 text-sm text-slate-600">
                  No lower-cost match with identical composition and pack size at {comparePharmacy.pharmacyName}.
                </p>
              ) : (
                <ul className="mt-3 divide-y divide-slate-100">
                  {substitutes.options.map((option) => (
                    <li key={option.medicineId} className="flex flex-wrap items-center justify-between gap-3 py-3">
                      <div className="min-w-0">
                        <p className="truncate text-sm font-semibold">{option.name}</p>
                        <p className="mt-0.5 text-xs text-slate-500">
                          {[option.brand, option.genericName, option.strength, option.packSize].filter(Boolean).join(' · ')}
                        </p>
                        <p className="mt-1 text-xs text-slate-500">{option.availableQuantity} in stock</p>
                      </div>
                      <div className="flex items-center gap-3">
                        <div className="text-right">
                          <p className="text-sm font-bold">{inr(option.price)}</p>
                          <Badge tone="bg-emerald-50 text-emerald-700">
                            Save {inr(option.savingsAmount)} ({option.savingsPercent}%)
                          </Badge>
                        </div>
                        <Link to={`/medicines/${option.medicineId}`} className="btn-secondary py-2 text-xs">
                          View
                        </Link>
                      </div>
                    </li>
                  ))}
                </ul>
              )}

              <p className="mt-3 text-[11px] leading-relaxed text-slate-500">{substitutes.notice}</p>
              {substitutes.requiresPharmacistReview && (
                <Alert tone="warning" title="Pharmacist review required">
                  This is a prescription medicine. A pharmacist must review any substitution; the cart and prescription
                  are never changed automatically.
                </Alert>
              )}
            </section>
          )}

          <section className="card-pad">
            <div className="flex items-center justify-between">
              <h2 className="section-title">Available pharmacies</h2>
              <span className="text-xs text-slate-500">Sorted by distance</span>
            </div>
            {data.availability.length === 0 ? (
              <EmptyState title="No pharmacy stock" description="No verified partner currently has this medicine in stock." />
            ) : (
              <ul className="mt-3 divide-y divide-slate-100">
                {data.availability.map((option) => (
                  <li key={option.inventoryId} className="flex flex-wrap items-center justify-between gap-3 py-3">
                    <div>
                      <p className="text-sm font-semibold">{option.pharmacyName}</p>
                      <p className="text-xs text-slate-500">
                        <MapPin className="mr-1 inline h-3 w-3" />
                        {option.city}
                        {option.distanceKm !== undefined ? ` · ${option.distanceKm} km` : ''} ·{' '}
                        {option.availableQuantity} in stock
                      </p>
                      {!option.acceptingOrders && (
                        <p className="mt-0.5 text-[11px] font-medium text-rose-600">Not accepting orders right now</p>
                      )}
                    </div>
                    <div className="flex items-center gap-3">
                      <div className="text-right">
                        <p className="text-sm font-bold">{inr(option.price)}</p>
                        {option.mrp > option.price && (
                          <p className="text-[11px] text-slate-400 line-through">{inr(option.mrp)}</p>
                        )}
                      </div>
                      <button
                        type="button"
                        className="btn-primary py-2 text-xs"
                        disabled={!option.acceptingOrders || addToCart.isPending}
                        onClick={() => handleAdd(option.inventoryId)}
                      >
                        <ShoppingCart className="h-3.5 w-3.5" />
                        {selected === option.inventoryId && addToCart.isPending ? 'Adding…' : 'Add'}
                      </button>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>

        <aside className="space-y-4">
          <div className="card-pad">
            <h2 className="text-sm font-semibold">Order summary</h2>
            <div className="mt-3 flex items-center gap-3">
              <label htmlFor="quantity" className="text-sm text-slate-600">Quantity</label>
              <input
                id="quantity"
                type="number"
                min={1}
                max={10}
                value={quantity}
                onChange={(event) => setQuantity(Math.min(10, Math.max(1, Number(event.target.value) || 1)))}
                className="input w-20 py-1.5 text-sm"
              />
            </div>
            <p className="mt-3 text-xs text-slate-500">
              Final price, taxes and delivery are calculated by MediDrop at checkout using live pharmacy stock.
            </p>
          </div>

          <div className="card-pad bg-mint/40">
            <div className="flex items-center gap-2 text-sm font-semibold text-brand-800">
              <ShieldCheck className="h-4 w-4" /> Pharmacist oversight
            </div>
            <p className="mt-2 text-xs leading-relaxed text-brand-900/80">
              Prescription medicines are dispensed only after a licensed pharmacist reviews and approves your
              prescription. MediDrop never substitutes a prescribed medicine on its own.
            </p>
            {data.prescriptionRequired && (
              <Link to="/prescriptions" className="btn-secondary mt-3 w-full py-2 text-xs">Upload prescription</Link>
            )}
          </div>
        </aside>
      </div>
    </div>
  );
}
