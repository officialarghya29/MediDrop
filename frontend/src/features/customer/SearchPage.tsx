import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Search, SlidersHorizontal, X } from 'lucide-react';
import MedicineCard from '../../components/MedicineCard';
import { EmptyState, SkeletonRows } from '../../components/ui';
import { apiGet } from '../../lib/api';
import type { Category, SearchResponse } from '../../types';

export default function SearchPage() {
  const [params, setParams] = useSearchParams();
  const [term, setTerm] = useState(params.get('q') ?? '');
  const categoryId = params.get('categoryId') ?? '';
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

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['search', params.toString()],
    queryFn: () =>
      apiGet<SearchResponse>('/medicines/search', {
        q: params.get('q') ?? undefined,
        categoryId: categoryId || undefined,
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

  const clearFilters = () => setParams(new URLSearchParams(params.get('q') ? { q: params.get('q')! } : {}), { replace: true });

  return (
    <div className="container-page space-y-6 py-6 sm:py-8">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Find your medicine</h1>
        <p className="muted mt-1">Search by brand, generic name or strength. Availability updates live.</p>
      </div>

      <form onSubmit={submit} className="flex gap-2" role="search">
        <div className="relative flex-1">
          <Search className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
          <input
            value={term}
            onChange={(event) => setTerm(event.target.value)}
            className="input pl-10"
            placeholder="Try “Paracetamol”, “Dolo” or “Cetirizine”"
            aria-label="Search medicines"
          />
        </div>
        <button type="submit" className="btn-primary px-5">Search</button>
      </form>

      <div className="flex flex-wrap items-center gap-2">
        <span className="inline-flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-slate-500">
          <SlidersHorizontal className="h-3.5 w-3.5" /> Filters
        </span>
        <select className="input w-auto py-2 text-xs" value={categoryId} onChange={(e) => update('categoryId', e.target.value)} aria-label="Category">
          <option value="">All categories</option>
          {(categories ?? []).map((category) => (
            <option key={category.id} value={category.id}>{category.name}</option>
          ))}
        </select>
        <select className="input w-auto py-2 text-xs" value={sort} onChange={(e) => update('sort', e.target.value)} aria-label="Sort">
          <option value="relevance">Best match</option>
          <option value="name">Name A–Z</option>
          <option value="price">Lowest price</option>
          <option value="newest">Newest</option>
        </select>
        <select className="input w-auto py-2 text-xs" value={rx} onChange={(e) => update('rx', e.target.value)} aria-label="Prescription requirement">
          <option value="">All medicines</option>
          <option value="false">Over the counter</option>
          <option value="true">Prescription required</option>
        </select>
        {(categoryId || rx || sort !== 'relevance') && (
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
          <p className="text-xs text-slate-500">
            {data.totalElements} result{data.totalElements === 1 ? '' : 's'}
            {data.suggestion && (
              <>
                {' · Did you mean '}
                <button type="button" className="link font-semibold" onClick={() => update('q', data.suggestion!)}>
                  {data.suggestion}
                </button>
                ?
              </>
            )}
          </p>

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

      <p className="rounded-xl bg-white p-4 text-xs leading-relaxed text-slate-500 ring-1 ring-slate-200">
        MediDrop does not provide medical advice, substitute prescribed medicines, or guarantee dosage guidance.
        Always consult a qualified healthcare professional. Prescription-only medicines are dispensed only after
        a licensed pharmacist verifies your prescription.
      </p>
    </div>
  );
}
