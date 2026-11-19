import { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  ArrowRight, BadgeCheck, Clock, MapPin, PackageCheck, Search, ShieldCheck, Truck, Upload,
} from 'lucide-react';
import MedicineCard from '../../components/MedicineCard';
import { Badge, EmptyState, SkeletonRows } from '../../components/ui';
import { apiGet } from '../../lib/api';
import { inr, isActiveOrder, statusLabel } from '../../lib/format';
import { useGeolocation } from '../../hooks/useGeolocation';
import { useAuth } from '../../store/auth';
import type { Category, MedicineSummary, NearbyPharmacy, OrderSummary, PageResponse } from '../../types';

const DEMO_KOLKATA = { latitude: 22.5726, longitude: 88.3639 };

export default function HomePage() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [query, setQuery] = useState('');
  const geo = useGeolocation();
  const position = geo.coords ?? DEMO_KOLKATA;

  const { data: categories } = useQuery({
    queryKey: ['categories'],
    queryFn: () => apiGet<Category[]>('/medicines/categories'),
  });

  const { data: featured, isLoading: featuredLoading } = useQuery({
    queryKey: ['medicines', 'featured'],
    queryFn: () => apiGet<PageResponse<MedicineSummary>>('/medicines', { size: 8 }),
  });

  const { data: pharmacies, isLoading: pharmaciesLoading } = useQuery({
    queryKey: ['pharmacies', 'nearby', position.latitude, position.longitude],
    queryFn: () =>
      apiGet<NearbyPharmacy[]>('/pharmacies/nearby', {
        lat: position.latitude,
        lng: position.longitude,
        radiusKm: 12,
      }),
  });

  const { data: activeOrders } = useQuery({
    queryKey: ['orders', 'active', 'home'],
    queryFn: () => apiGet<PageResponse<OrderSummary>>('/orders', { filter: 'active', size: 3 }),
    enabled: !!user && user.role === 'CUSTOMER',
  });

  const locationLabel = useMemo(() => {
    if (geo.coords) return 'Using your current location';
    if (geo.state === 'denied') return 'Location denied — showing Salt Lake, Kolkata';
    return 'Salt Lake, Kolkata (demo area)';
  }, [geo.coords, geo.state]);

  const submitSearch = (event: React.FormEvent) => {
    event.preventDefault();
    navigate(`/search${query.trim() ? `?q=${encodeURIComponent(query.trim())}` : ''}`);
  };

  const active = (activeOrders?.content ?? []).filter((order) => isActiveOrder(order.status));

  return (
    <div className="container-page space-y-10 py-6 sm:py-10">
      <section className="overflow-hidden rounded-3xl bg-navy-900 text-white">
        <div className="grid gap-8 p-6 sm:p-10 lg:grid-cols-[1.15fr_0.85fr] lg:items-center">
          <div>
            <Badge tone="bg-white/10 text-brand-200">
              <BadgeCheck className="h-3.5 w-3.5" /> Licensed pharmacy partners
            </Badge>
            <h1 className="mt-4 text-3xl font-extrabold leading-tight tracking-tight sm:text-4xl">
              Healthcare, delivered to your doorstep.
            </h1>
            <p className="mt-3 max-w-xl text-sm leading-relaxed text-slate-300">
              Order medicines from verified pharmacies near you, upload prescriptions for pharmacist review,
              and track your delivery live — all in one place.
            </p>

            <form onSubmit={submitSearch} className="mt-6 flex gap-2" role="search">
              <div className="relative flex-1">
                <Search className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                <input
                  value={query}
                  onChange={(event) => setQuery(event.target.value)}
                  placeholder="Search medicines, brands or salts"
                  aria-label="Search medicines"
                  className="w-full rounded-xl border border-white/10 bg-white px-10 py-3 text-sm text-navy-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-brand-400"
                />
              </div>
              <button type="submit" className="btn-primary px-5">Search</button>
            </form>

            <div className="mt-4 flex flex-wrap items-center gap-3 text-xs text-slate-300">
              <span className="inline-flex items-center gap-1.5">
                <MapPin className="h-3.5 w-3.5" /> {locationLabel}
              </span>
              {geo.state !== 'granted' && (
                <button type="button" onClick={geo.request} className="rounded-full border border-white/20 px-3 py-1 font-semibold hover:bg-white/10">
                  Use my location
                </button>
              )}
              {geo.error && <span className="text-amber-300">{geo.error}</span>}
            </div>
          </div>

          <div className="grid gap-3 sm:grid-cols-2">
            <Link to="/prescriptions" className="rounded-2xl bg-white/5 p-4 ring-1 ring-white/10 transition hover:bg-white/10">
              <Upload className="h-5 w-5 text-brand-300" />
              <p className="mt-3 text-sm font-semibold">Upload prescription</p>
              <p className="mt-1 text-xs text-slate-300">Reviewed by a licensed pharmacist</p>
            </Link>
            <Link to="/orders" className="rounded-2xl bg-white/5 p-4 ring-1 ring-white/10 transition hover:bg-white/10">
              <Truck className="h-5 w-5 text-brand-300" />
              <p className="mt-3 text-sm font-semibold">Track delivery</p>
              <p className="mt-1 text-xs text-slate-300">Live updates from your rider</p>
            </Link>
            <div className="rounded-2xl bg-white/5 p-4 ring-1 ring-white/10">
              <ShieldCheck className="h-5 w-5 text-brand-300" />
              <p className="mt-3 text-sm font-semibold">Verified partners</p>
              <p className="mt-1 text-xs text-slate-300">Drug licence checked before activation</p>
            </div>
            <div className="rounded-2xl bg-white/5 p-4 ring-1 ring-white/10">
              <Clock className="h-5 w-5 text-brand-300" />
              <p className="mt-3 text-sm font-semibold">Same-day delivery</p>
              <p className="mt-1 text-xs text-slate-300">Within partner delivery radius</p>
            </div>
          </div>
        </div>
      </section>

      {active.length > 0 && (
        <section className="space-y-3">
          <div className="flex items-center justify-between">
            <h2 className="section-title">Active orders</h2>
            <Link to="/orders" className="link text-sm">View all</Link>
          </div>
          <div className="grid gap-3 md:grid-cols-3">
            {active.map((order) => (
              <Link key={order.id} to={`/orders/${order.id}`} className="card-pad transition hover:shadow-soft">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-semibold text-slate-500">{order.orderNumber}</span>
                  <Badge tone="bg-sky-50 text-sky-700">{statusLabel(order.status)}</Badge>
                </div>
                <p className="mt-2 font-semibold">{order.pharmacyName}</p>
                <p className="text-xs text-slate-500">{order.itemCount} item(s) · {inr(order.totalAmount)}</p>
                <p className="mt-3 inline-flex items-center gap-1 text-xs font-semibold text-brand-700">
                  Track order <ArrowRight className="h-3 w-3" />
                </p>
              </Link>
            ))}
          </div>
        </section>
      )}

      <section className="space-y-4">
        <div className="flex items-end justify-between">
          <div>
            <h2 className="section-title">Shop by category</h2>
            <p className="muted mt-1">Everything from daily essentials to prescription medicines.</p>
          </div>
          <Link to="/search" className="link hidden text-sm sm:block">Browse all</Link>
        </div>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-7">
          {(categories ?? []).map((category) => (
            <Link
              key={category.id}
              to={`/search?categoryId=${category.id}`}
              className="card flex flex-col gap-2 p-4 transition hover:border-brand-300 hover:shadow-soft"
            >
              <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-mint text-brand-700">
                <PackageCheck className="h-4 w-4" />
              </span>
              <span className="text-sm font-semibold leading-tight">{category.name}</span>
              {category.prescriptionOnly && <span className="text-[10px] font-semibold uppercase text-amber-700">Rx only</span>}
            </Link>
          ))}
          {!categories && Array.from({ length: 7 }).map((_, index) => <div key={index} className="skeleton h-24" />)}
        </div>
      </section>

      <section className="space-y-4">
        <div className="flex items-end justify-between">
          <div>
            <h2 className="section-title">Popular medicines</h2>
            <p className="muted mt-1">Prices and stock from nearby partner pharmacies.</p>
          </div>
          <Link to="/search" className="link text-sm">See all</Link>
        </div>
        {featuredLoading ? (
          <SkeletonRows rows={2} />
        ) : (featured?.content ?? []).length === 0 ? (
          <EmptyState title="Catalogue is empty" description="Seed the demo catalogue or add medicines from a pharmacy account." />
        ) : (
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
            {featured!.content.map((medicine) => <MedicineCard key={medicine.id} medicine={medicine} />)}
          </div>
        )}
      </section>

      <section className="space-y-3">
        <div className="flex items-end justify-between">
          <div>
            <h2 className="section-title">Pharmacies near you</h2>
            <p className="muted mt-1">Verified partners within their delivery radius.</p>
          </div>
          <span className="text-xs text-slate-500">Radius 12 km</span>
        </div>
        {pharmaciesLoading ? (
          <SkeletonRows rows={2} />
        ) : (pharmacies ?? []).length === 0 ? (
          <EmptyState
            title="No pharmacies service this area yet"
            description="Try changing your location, or check back as more licensed partners join MediDrop."
          />
        ) : (
          <div className="grid gap-3 md:grid-cols-2 lg:grid-cols-3">
            {(pharmacies ?? []).slice(0, 6).map((pharmacy) => (
              <div key={pharmacy.id} className="card-pad">
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <p className="font-semibold">{pharmacy.name}</p>
                    <p className="mt-0.5 text-xs text-slate-500">{pharmacy.address}, {pharmacy.pincode}</p>
                  </div>
                  <Badge tone="bg-emerald-50 text-emerald-700">{pharmacy.rating.toFixed(1)} ★</Badge>
                </div>
                <div className="mt-3 flex flex-wrap gap-2 text-xs text-slate-600">
                  <span className="inline-flex items-center gap-1"><MapPin className="h-3 w-3" /> {pharmacy.distanceKm} km</span>
                  <span>Min {inr(pharmacy.minOrderValue)}</span>
                  <span className={pharmacy.acceptingOrders ? 'text-emerald-700' : 'text-rose-600'}>
                    {pharmacy.acceptingOrders ? 'Accepting orders' : 'Paused'}
                  </span>
                </div>
                <button
                  type="button"
                  onClick={() => navigate(`/search?pharmacyId=${pharmacy.id}`)}
                  className="btn-secondary mt-4 w-full py-2 text-xs"
                >
                  Browse this pharmacy
                </button>
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
