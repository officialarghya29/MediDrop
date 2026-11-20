import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ArrowRight, Search, SlidersHorizontal, Sparkles, Store, X } from 'lucide-react';
import MedicineCard from '../../components/MedicineCard';
import { Badge, EmptyState, SkeletonRows } from '../../components/ui';
import { apiGet } from '../../lib/api';
import type { Category, SearchResponse } from '../../types';

const QUICK_SEARCHES = ['Paracetamol', 'Dolo', 'Cetirizine', 'First aid', 'Wellness'];

export default function SearchPage() {
  const [params, setParams] = useSearchParams();
  const [term, setTerm] = useState(params.get('q') ?? '');
  const categoryId = params.get('categoryId') ?? '';
  const pharmacyId = params.get('pharmacyId') ?? '';
  const sort = params.get('sort') ?? 'relevance';
  const rx = params.get('rx') ?? '';
  const page = Number(params.get('page') ?? '0');

  useEffect(() => {
    setTerm(params.get('q') ?? '');
  }, [params]);

  const { data: categories } = useQuery({
    queryKey: ['categories'],
    queryFn: () => apiGet<Category[]>('/medicines/categories'),
  });

  const { data: pharmacy } = useQuery({
    queryKey: ['pharmacy-mini', pharmacyId],
    queryFn: () => apiGet<{ id: number; name: string; city: string }>(`/pharmacies/${pharmacyId}`),
    enabled: !!pharmacyId,
  });

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['search', params.toString()],
    queryFn: () =>
      apiGet<SearchResponse>('/medicines/search', {
        q: params.get('q') ?? undefined,
        categoryId: categoryId || undefined,
        pharmacyId: pharmacyId || undefined,
        prescriptionRequired: rx === '' ? undefined : rx === 'true',
        sort,
        page,
        size: 12,
      }),
  });

  const update = (key: string, value: string) => {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value);
    else next.delete(key);
    if (key !== 'page') next.delete('page');
    setParams(next, { replace: true });
  };

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    update('q', term.trim());
  };

  const clearFilters = () => {
    const next = new URLSearchParams();
    if (params.get('q')) next.set('q', params.get('q')!);
    setParams(next, { replace: true });
  };

  const filtersActive = !!(categoryId || pharmacyId || rx || sort !== 'relevance');

  return (
    <div className="container-page space-y-6 py-6 sm:py-8">
      <section className="relative overflow-hidden rounded-[2rem] border border-white/80 bg-gradient-to-br from-white via-mint/60 to-grape-50 p-5 shadow-card sm:p-8">
        <div className="pointer-events-none absolute -right-16 -top-20 h-64 w-64 rounded-full bg-grape-300/25 blur-3xl" />
        <div className="pointer-events-none absolute -bottom-20 left-1/3 h-52 w-52 rounded-full bg-brand-300/25 blur-3xl" />
        <div className="relative">
          <Badge tone="bg-white/80 text-grape-700 ring-1 ring-grape-100">
            <Sparkles className="h-3.5 w-3.5" /> Live local catalogue
          </Badge>
          <h1 className="mt-4 font-display text-3xl font-bold tracking-tight sm:text-4xl">
            Find your <span className="bg-gradient-to-r from-brand-700 via-cyan-700 to-grape-700 bg-clip-text text-transparent">medicine</span>.
          </h1>
          <p className="muted mt-2 max-w-2xl">
            Search by brand, generic name or strength. Prices and availability come straight from nearby pharmacy
            ledgers — not a stale catalogue.
          </p>

          <form onSubmit={submit} className="mt-5 flex flex-col gap-2 sm:flex-row" role="search">
            <div className="relative flex-1">
              <Search className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
              <input
                value={term}
                onChange={(event) => setTerm(event.target.value)}
                className="input py-3.5 pl-11 shadow-sm"
                placeholder="Try “Paracetamol”, “Dolo” or “Cetirizine”"
                aria-label="Search medicines"
              />
            </div>
            <button type="submit" className="btn-primary px-6 py-3.5">
              Search <ArrowRight className="h-4 w-4" />
            </button>
          </form>

          <div className="mt-3 flex flex-wrap items-center gap-2">
            <span className="text-[10px] font-bold uppercase tracking-[0.14em] text-slate-400">Quick finds</span>
            {QUICK_SEARCHES.map((quick) => (
              <button
                key={quick}
                type="button"
                onClick={() => {
                  setTerm(quick);
                  update('q', quick);
                }}
                className="rounded-full border border-white bg-white/75 px-3 py-1.5 text-[11px] font-semibold text-navy-700 shadow-sm transition hover:-translate-y-0.5 hover:border-brand-200 hover:text-brand-800"
              >
                {quick}
              </button>
            ))}
          </div>
        </div>
      </section>

      <div className="flex flex-wrap items-center gap-2">
        <span className="inline-flex items-center gap-1.5 text-xs font-bold uppercase tracking-wide text-slate-500">
          <SlidersHorizontal className="h-3.5 w-3.5" /> Filters
        </span>
        <select className="input w-auto rounded-full py-2 text-xs" value={categoryId} onChange={(e) => update('categoryId', e.target.value)} aria-label="Category">
          <option value="">All categories</option>
          {(categories ?? []).map((category) => (
            <option key={category.id} value={category.id}>{category.name}</option>
          ))}
        </select>
        <select className="input w-auto rounded-full py-2 text-xs" value={sort} onChange={(e) => update('sort', e.target.value)} aria-label="Sort">
          <option value="relevance">Best match</option>
          <option value="name">Name A–Z</option>
          <option value="price">Lowest price</option>
          <option value="newest">Newest</option>
        </select>
        <select className="input w-auto rounded-full py-2 text-xs" value={rx} onChange={(e) => update('rx', e.target.value)} aria-label="Prescription requirement">
          <option value="">All medicines</option>
          <option value="false">Over the counter</option>
          <option value="true">Prescription required</option>
        </select>
        {pharmacyId && (
          <span className="inline-flex items-center gap-1.5 rounded-full bg-grape-50 px-3 py-2 text-[11px] font-bold text-grape-700 ring-1 ring-grape-100">
            <Store className="h-3.5 w-3.5" /> {pharmacy?.name ?? 'Selected pharmacy'}
            <button type="button" onClick={() => update('pharmacyId', '')} aria-label="Clear pharmacy filter" className="text-grape-500 hover:text-grape-800">
              <X className="h-3 w-3" />
            </button>
          </span>
        )}
        {filtersActive && (
          <button type="button" onClick={clearFilters} className="btn-ghost py-1.5 text-xs">
            <X className="h-3.5 w-3.5" /> Clear
          </button>
        )}
      </div>

      {isLoading && <SkeletonRows rows={4} />}

      {isError && (
        <EmptyState title="Search is unavailable" description={error instanceof Error ? error.message : 'Please try again.'} />
      )}

      {data && (
        <>
          <h2 className="sr-only">Search results</h2>
          <div className="flex flex-wrap items-end justify-between gap-3">
            <div>
              <p className="text-sm font-semibold text-navy-900">
                {data.totalElements} match{data.totalElements === 1 ? '' : 'es'}
                {params.get('q') ? <> for “{params.get('q')}”</> : null}
              </p>
              {data.suggestion && (
                <p className="mt-1 text-xs text-slate-500">
                  Did you mean{' '}
                  <button type="button" className="link" onClick={() => update('q', data.suggestion!)}>
                    {data.suggestion}
                  </button>
                  ?
                </p>
              )}
            </div>
            {data.totalPages > 1 && (
              <span className="text-xs text-slate-500">Page {data.page + 1} of {data.totalPages}</span>
            )}
          </div>

          {data.items.length === 0 ? (
            <EmptyState
              title="No medicines matched"
              description="Try a shorter spelling, the generic (salt) name, or browse a category."
              action={<Link to="/search" className="btn-secondary">Reset search</Link>}
            />
          ) : (
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
              {data.items.map((medicine) => <MedicineCard key={medicine.id} medicine={medicine} />)}
            </div>
          )}

          {data.totalPages > 1 && (
            <div className="flex items-center justify-center gap-2 pt-2">
              <button
                type="button"
                className="btn-secondary py-2 text-xs"
                disabled={data.page === 0}
                onClick={() => update('page', String(data.page - 1))}
              >
                Previous
              </button>
              <span className="text-xs text-slate-500">Page {data.page + 1} of {data.totalPages}</span>
              <button
                type="button"
                className="btn-secondary py-2 text-xs"
                disabled={data.page + 1 >= data.totalPages}
                onClick={() => update('page', String(data.page + 1))}
              >
                Next
              </button>
            </div>
          )}
        </>
      )}

      <p className="rounded-3xl bg-white/70 p-4 text-xs leading-relaxed text-slate-500 ring-1 ring-white/80">
        MediDrop does not provide medical advice, substitute prescribed medicines, or guarantee dosage guidance.
        Always consult a qualified healthcare professional. Prescription-only medicines are dispensed only after
        a licensed pharmacist verifies your prescription.
      </p>
    </div>
  );
}
