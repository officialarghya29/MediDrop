import { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  Activity, ArrowRight, BadgeCheck, Bandage, Clock3, HeartPulse, Leaf, MapPin, Pill,
  Search, ShieldCheck, ShieldPlus, Sparkles, Star, Stethoscope, Truck,
} from 'lucide-react';
import MedicineCard from '../../components/MedicineCard';
import { Badge, EmptyState, SkeletonRows } from '../../components/ui';
import { apiGet } from '../../lib/api';
import { inr, isActiveOrder, statusLabel } from '../../lib/format';
import { useGeolocation } from '../../hooks/useGeolocation';
import { useAuth } from '../../store/auth';
import type { Category, MedicineSummary, NearbyPharmacy, OrderSummary, PageResponse } from '../../types';

const DEMO_KOLKATA = { latitude: 22.5726, longitude: 88.3639 };

const categoryStyles = [
  { icon: Pill, tile: 'from-grape-500 to-fuchsia-500', wash: 'from-grape-50' },
  { icon: HeartPulse, tile: 'from-brand-400 to-teal-500', wash: 'from-mint' },
  { icon: Sparkles, tile: 'from-coral-400 to-rose-500', wash: 'from-coral-50' },
  { icon: ShieldPlus, tile: 'from-sky-400 to-blue-500', wash: 'from-sky-50' },
  { icon: Bandage, tile: 'from-sun-400 to-orange-500', wash: 'from-sun-50' },
  { icon: Activity, tile: 'from-indigo-400 to-grape-500', wash: 'from-indigo-50' },
  { icon: Leaf, tile: 'from-emerald-400 to-lime-500', wash: 'from-lime-50' },
];

const carePoints = [
  { icon: Stethoscope, title: 'Pharmacist reviewed', body: 'Every Rx gets a real clinical check.', tone: 'from-amber-400/20 to-orange-400/5 text-amber-200' },
  { icon: Truck, title: 'Live rider trail', body: 'Follow the handover in real time.', tone: 'from-sky-400/20 to-cyan-400/5 text-sky-200' },
  { icon: ShieldCheck, title: 'OTP handover', body: 'No OTP, no delivery. Simple.', tone: 'from-grape-400/20 to-fuchsia-400/5 text-grape-200' },
  { icon: Clock3, title: 'Same-day local', body: 'Stock held by pharmacies near you.', tone: 'from-brand-400/20 to-emerald-400/5 text-brand-200' },
];

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
    if (geo.state === 'denied') return 'Location off — showing Salt Lake, Kolkata';
    return 'Salt Lake, Kolkata · demo area';
  }, [geo.coords, geo.state]);

  const submitSearch = (event: React.FormEvent) => {
    event.preventDefault();
    navigate(`/search${query.trim() ? `?q=${encodeURIComponent(query.trim())}` : ''}`);
  };

  const active = (activeOrders?.content ?? []).filter((order) => isActiveOrder(order.status));

  return (
    <div className="container-page space-y-12 py-6 sm:py-10">
      <section className="mesh-hero relative overflow-hidden rounded-[2rem] text-white shadow-soft sm:rounded-[2.5rem]">
        <div className="grid-lines pointer-events-none absolute inset-0 opacity-70" />
        <div className="pointer-events-none absolute -right-24 -top-24 h-80 w-80 rounded-full bg-grape-500/30 blur-3xl" />
        <div className="pointer-events-none absolute -bottom-32 left-1/3 h-72 w-72 rounded-full bg-brand-400/20 blur-3xl" />

        <div className="relative grid gap-10 p-5 sm:p-9 lg:grid-cols-[1.12fr_0.88fr] lg:items-center lg:p-12">
          <div>
            <Badge tone="border border-white/15 bg-white/10 text-brand-100 backdrop-blur">
              <BadgeCheck className="h-3.5 w-3.5" /> Licensed Kolkata pharmacies
            </Badge>
            <h1 className="mt-5 font-display text-[2.5rem] font-bold leading-[0.99] tracking-[-0.04em] sm:text-6xl">
              Healthcare that <span className="gradient-text">keeps up</span> with you.
            </h1>
            <p className="mt-5 max-w-xl text-sm leading-relaxed text-slate-300 sm:text-base">
              Real stock from pharmacies in your neighbourhood, prescriptions checked by real pharmacists, and a
              delivery you can follow from shelf to doorstep. No queues. No guesswork.
            </p>

            <form onSubmit={submitSearch} className="mt-7 flex flex-col gap-2 sm:flex-row" role="search">
              <div className="relative flex-1">
                <Search className="pointer-events-none absolute left-4 top-1/2 h-[18px] w-[18px] -translate-y-1/2 text-slate-400" />
                <input
                  value={query}
                  onChange={(event) => setQuery(event.target.value)}
                  placeholder="Search a medicine, salt or brand"
                  aria-label="Search medicines"
                  className="w-full rounded-2xl border border-white/20 bg-white px-11 py-3.5 text-sm text-navy-900 shadow-lg shadow-navy-950/20 placeholder:text-slate-400 focus:outline-none focus:ring-4 focus:ring-brand-400/30"
                />
              </div>
              <button type="submit" className="btn-primary px-6 py-3.5">
                Search <ArrowRight className="h-4 w-4" />
              </button>
            </form>

            <div className="mt-4 flex flex-wrap items-center gap-2 text-xs text-slate-300">
              <span className="inline-flex items-center gap-1.5 rounded-full bg-white/5 px-3 py-1.5 ring-1 ring-white/10">
                <MapPin className="h-3.5 w-3.5 text-brand-300" /> {locationLabel}
              </span>
              {geo.state !== 'granted' && (
                <button
                  type="button"
                  onClick={geo.request}
                  className="rounded-full border border-white/20 px-3 py-1.5 font-bold text-white transition hover:bg-white/10"
                >
                  Use my location
                </button>
              )}
              {geo.error && <span className="text-amber-300">{geo.error}</span>}
            </div>

            <div className="mt-7 grid max-w-xl grid-cols-2 gap-2 sm:grid-cols-4">
              {[
                ['Live stock', 'from pharmacies'],
                ['Rx review', 'by pharmacists'],
                ['Tracked', 'to your door'],
                ['OTP', 'at handover'],
              ].map(([title, sub]) => (
                <div key={title} className="rounded-2xl bg-white/5 px-3 py-2.5 ring-1 ring-white/10">
                  <p className="text-[11px] font-bold text-white">{title}</p>
                  <p className="mt-0.5 text-[10px] text-slate-400">{sub}</p>
                </div>
              ))}
            </div>
          </div>

          <div className="relative">
            <div className="absolute -inset-3 rounded-[2rem] bg-gradient-to-br from-brand-400/20 via-grape-500/10 to-coral-400/20 blur-2xl" />
            <div className="relative grid gap-3 sm:grid-cols-2">
              {carePoints.map((point, index) => {
                const Icon = point.icon;
                return (
                  <div
                    key={point.title}
                    className={`glass rounded-3xl p-4 transition hover:-translate-y-1 hover:bg-white/15 ${index % 2 === 1 ? 'sm:translate-y-5' : ''}`}
                  >
                    <span className={`inline-flex h-10 w-10 items-center justify-center rounded-2xl bg-gradient-to-br ${point.tone} ring-1 ring-white/15`}>
                      <Icon className="h-5 w-5" />
                    </span>
                    <p className="mt-4 text-sm font-bold">{point.title}</p>
                    <p className="mt-1 text-xs leading-relaxed text-slate-300">{point.body}</p>
                  </div>
                );
              })}
            </div>
            <div className="relative mt-3 flex items-center gap-3 rounded-3xl bg-white/10 p-3 ring-1 ring-white/15 backdrop-blur sm:mt-8">
              <div className="flex -space-x-2">
                {['from-grape-500 to-fuchsia-500', 'from-brand-400 to-cyan-500', 'from-sun-400 to-coral-500'].map((tone) => (
                  <span key={tone} className={`h-8 w-8 rounded-full bg-gradient-to-br ${tone} ring-2 ring-navy-900`} />
                ))}
              </div>
              <p className="text-[11px] leading-relaxed text-slate-300">
                <span className="font-bold text-white">One local network.</span> Pharmacies, riders and pharmacists in sync.
              </p>
            </div>
          </div>
        </div>
      </section>

      {active.length > 0 && (
        <section className="space-y-3">
          <div className="flex items-end justify-between">
            <div>
              <p className="eyebrow">On the move</p>
              <h2 className="section-title mt-2">Your active orders</h2>
            </div>
            <Link to="/orders" className="link text-sm">View all</Link>
          </div>
          <div className="grid gap-3 md:grid-cols-3">
            {active.map((order) => (
              <Link key={order.id} to={`/orders/${order.id}`} className="card-pad group relative overflow-hidden transition hover:-translate-y-1 hover:shadow-soft">
                <div className="absolute right-0 top-0 h-24 w-24 rounded-full bg-gradient-to-br from-brand-200/40 to-transparent blur-2xl" />
                <div className="relative flex items-center justify-between">
                  <span className="font-mono text-[11px] font-bold text-slate-500">{order.orderNumber}</span>
                  <Badge tone="bg-sky-50 text-sky-700">{statusLabel(order.status)}</Badge>
                </div>
                <p className="relative mt-3 font-bold">{order.pharmacyName}</p>
                <p className="text-xs text-slate-500">{order.itemCount} item(s) · {inr(order.totalAmount)}</p>
                <p className="relative mt-4 inline-flex items-center gap-1 text-xs font-bold text-brand-700">
                  Track order <ArrowRight className="h-3 w-3 transition group-hover:translate-x-1" />
                </p>
              </Link>
            ))}
          </div>
        </section>
      )}

      <section className="space-y-4">
        <div className="flex items-end justify-between gap-3">
          <div>
            <p className="eyebrow">Find your thing</p>
            <h2 className="section-title mt-2">Shop by category</h2>
            <p className="muted mt-1">Daily essentials to prescription-only care, all in one place.</p>
          </div>
          <Link to="/search" className="link hidden text-sm sm:block">Browse all</Link>
        </div>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-7">
          {(categories ?? []).map((category, index) => {
            const style = categoryStyles[index % categoryStyles.length];
            const Icon = style.icon;
            return (
              <Link
                key={category.id}
                to={`/search?categoryId=${category.id}`}
                className={`group relative overflow-hidden rounded-3xl border border-white/80 bg-gradient-to-b ${style.wash} to-white/80 p-4 shadow-card transition duration-300 hover:-translate-y-1 hover:shadow-soft`}
              >
                <span className={`inline-flex h-11 w-11 items-center justify-center rounded-2xl bg-gradient-to-br ${style.tile} text-white shadow-lg`}>
                  <Icon className="h-5 w-5" />
                </span>
                <span className="mt-4 block text-sm font-bold leading-tight">{category.name}</span>
                <span className="mt-1 block text-[10px] font-semibold uppercase tracking-wide text-slate-400">
                  {category.prescriptionOnly ? 'Rx only' : 'Explore'}
                </span>
                <ArrowRight className="absolute bottom-4 right-3 h-3.5 w-3.5 text-slate-300 transition group-hover:translate-x-1 group-hover:text-brand-700" />
              </Link>
            );
          })}
          {!categories && Array.from({ length: 7 }).map((_, index) => <div key={index} className="skeleton h-32" />)}
        </div>
      </section>

      <section className="relative overflow-hidden rounded-[2rem] bg-gradient-to-br from-grape-700 via-brand-700 to-cyan-700 p-6 text-white shadow-soft sm:p-9">
        <div className="pointer-events-none absolute -right-10 -top-20 h-64 w-64 rounded-full bg-white/10 blur-3xl" />
        <div className="relative grid gap-7 lg:grid-cols-[0.7fr_1.3fr] lg:items-center">
          <div>
            <p className="text-[11px] font-bold uppercase tracking-[0.18em] text-white/60">The MediDrop loop</p>
            <h2 className="mt-2 font-display text-3xl font-bold leading-tight">Care, without the chaos.</h2>
            <p className="mt-2 max-w-sm text-sm text-white/70">Search once. We handle stock, clinical review and the last mile.</p>
          </div>
          <div className="grid gap-3 sm:grid-cols-3">
            {[
              ['01', 'Search live stock', 'Only pharmacies that can actually fulfil appear.'],
              ['02', 'Get it checked', 'A pharmacist reviews every prescription before dispensing.'],
              ['03', 'Follow it home', 'Track the rider and share the handover OTP.'],
            ].map(([step, title, body]) => (
              <div key={step} className="rounded-3xl bg-white/10 p-4 ring-1 ring-white/15 backdrop-blur">
                <span className="font-display text-2xl font-bold text-brand-200">{step}</span>
                <p className="mt-3 text-sm font-bold">{title}</p>
                <p className="mt-1 text-[11px] leading-relaxed text-white/65">{body}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section className="space-y-4">
        <div className="flex items-end justify-between">
          <div>
            <p className="eyebrow">Trending nearby</p>
            <h2 className="section-title mt-2">Popular right now</h2>
            <p className="muted mt-1">Live prices and stock from partner pharmacies around you.</p>
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
        <div className="flex items-end justify-between gap-3">
          <div>
            <p className="eyebrow">Hyperlocal</p>
            <h2 className="section-title mt-2">Pharmacies in your orbit</h2>
            <p className="muted mt-1">Verified partners inside their own delivery radius.</p>
          </div>
          <span className="rounded-full bg-white/80 px-3 py-1 text-[11px] font-bold text-slate-500 ring-1 ring-slate-200">12 km radius</span>
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
            {(pharmacies ?? []).slice(0, 6).map((pharmacy, index) => (
              <div key={pharmacy.id} className="card-pad group relative overflow-hidden transition hover:-translate-y-1 hover:shadow-soft">
                <div className={`absolute inset-x-0 top-0 h-1 bg-gradient-to-r ${categoryStyles[index % categoryStyles.length].tile}`} />
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <p className="font-bold">{pharmacy.name}</p>
                    <p className="mt-0.5 text-xs text-slate-500">{pharmacy.address}, {pharmacy.pincode}</p>
                  </div>
                  <Badge tone="bg-sun-50 text-sun-700">
                    <Star className="h-3 w-3 fill-current" /> {pharmacy.rating.toFixed(1)}
                  </Badge>
                </div>
                <div className="mt-3 flex flex-wrap gap-2 text-[11px] font-semibold text-slate-600">
                  <span className="inline-flex items-center gap-1 rounded-full bg-sky-50 px-2.5 py-1"><MapPin className="h-3 w-3" /> {pharmacy.distanceKm} km</span>
                  <span className="rounded-full bg-slate-50 px-2.5 py-1">Min {inr(pharmacy.minOrderValue)}</span>
                  <span className={`rounded-full px-2.5 py-1 ${pharmacy.acceptingOrders ? 'bg-emerald-50 text-emerald-700' : 'bg-rose-50 text-rose-600'}`}>
                    {pharmacy.acceptingOrders ? 'Accepting orders' : 'Paused'}
                  </span>
                </div>
                <button
                  type="button"
                  onClick={() => navigate(`/search?pharmacyId=${pharmacy.id}`)}
                  className="btn-secondary mt-4 w-full py-2 text-xs"
                >
                  Browse this pharmacy <ArrowRight className="h-3.5 w-3.5" />
                </button>
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
