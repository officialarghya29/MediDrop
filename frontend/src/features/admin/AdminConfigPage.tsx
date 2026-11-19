import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Percent, Settings, Tags, Trash2 } from 'lucide-react';
import { Alert, Badge, EmptyState, FieldError, Modal, PageLoader, Spinner, Tabs, useToast } from '../../components/ui';
import { apiDelete, apiGet, apiPost, apiPut, errorMessage } from '../../lib/api';
import { dateOnly, inr } from '../../lib/format';
import type { Category, CouponRow, CouponRow as Coupon, SystemSetting } from '../../types';

type Tab = 'settings' | 'coupons' | 'categories';

const emptyCoupon = {
  code: '',
  type: 'PERCENT',
  value: '',
  minOrderValue: '',
  maxDiscount: '',
  usageLimit: '',
  description: '',
};

export default function AdminConfigPage() {
  const toast = useToast();
  const queryClient = useQueryClient();
  const [tab, setTab] = useState<Tab>('settings');
  const [draft, setDraft] = useState<Record<string, string>>({});
  const [couponOpen, setCouponOpen] = useState(false);
  const [coupon, setCoupon] = useState(emptyCoupon);
  const [couponError, setCouponError] = useState<string | null>(null);
  const [categoryOpen, setCategoryOpen] = useState(false);
  const [categoryName, setCategoryName] = useState('');
  const [categoryDescription, setCategoryDescription] = useState('');
  const [categoryRx, setCategoryRx] = useState(false);
  const [categoryError, setCategoryError] = useState<string | null>(null);

  const settings = useQuery({
    queryKey: ['admin-settings'],
    queryFn: () => apiGet<SystemSetting[]>('/admin/settings'),
  });

  const coupons = useQuery({
    queryKey: ['admin-coupons'],
    queryFn: () => apiGet<Coupon[]>('/admin/coupons'),
  });

  const categories = useQuery({
    queryKey: ['admin-categories'],
    queryFn: () => apiGet<Category[]>('/admin/categories'),
  });

  useEffect(() => {
    if (settings.data) {
      setDraft(Object.fromEntries(settings.data.map((setting) => [setting.key, setting.value])));
    }
  }, [settings.data]);

  const saveSetting = useMutation({
    mutationFn: (setting: SystemSetting) =>
      apiPut(`/admin/settings/${encodeURIComponent(setting.key)}`, {
        value: draft[setting.key] ?? setting.value,
        description: setting.description,
        publicSetting: setting.publicSetting,
      }),
    onSuccess: () => {
      toast('Setting saved', 'success');
      queryClient.invalidateQueries({ queryKey: ['admin-settings'] });
    },
    onError: (error) => toast(errorMessage(error), 'error'),
  });

  const createCoupon = useMutation({
    mutationFn: () =>
      apiPost<CouponRow>('/admin/coupons', {
        code: coupon.code.trim().toUpperCase(),
        type: coupon.type,
        value: Number(coupon.value),
        minOrderValue: coupon.minOrderValue ? Number(coupon.minOrderValue) : 0,
        maxDiscount: coupon.maxDiscount ? Number(coupon.maxDiscount) : null,
        usageLimit: coupon.usageLimit ? Number(coupon.usageLimit) : null,
        active: true,
        description: coupon.description || null,
      }),
    onSuccess: () => {
      toast('Coupon created', 'success');
      setCoupon(emptyCoupon);
      setCouponError(null);
      setCouponOpen(false);
      queryClient.invalidateQueries({ queryKey: ['admin-coupons'] });
    },
    onError: (error) => setCouponError(errorMessage(error)),
  });

  const removeCoupon = useMutation({
    mutationFn: (id: number) => apiDelete(`/admin/coupons/${id}`),
    onSuccess: () => {
      toast('Coupon deactivated', 'info');
      queryClient.invalidateQueries({ queryKey: ['admin-coupons'] });
    },
    onError: (error) => toast(errorMessage(error), 'error'),
  });

  const createCategory = useMutation({
    mutationFn: () =>
      apiPost('/admin/categories', {
        name: categoryName.trim(),
        description: categoryDescription || undefined,
        prescriptionOnly: categoryRx,
        displayOrder: 0,
        active: true,
      }),
    onSuccess: () => {
      toast('Category created', 'success');
      setCategoryName('');
      setCategoryDescription('');
      setCategoryRx(false);
      setCategoryError(null);
      setCategoryOpen(false);
      queryClient.invalidateQueries({ queryKey: ['admin-categories'] });
      queryClient.invalidateQueries({ queryKey: ['categories'] });
    },
    onError: (error) => setCategoryError(errorMessage(error)),
  });

  return (
    <div className="space-y-4 px-4 py-6 sm:px-6 lg:px-8">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Configuration</h1>
        <p className="muted mt-1">Runtime settings, coupons and the shared medicine catalogue categories.</p>
      </div>

      <Tabs
        value={tab}
        onChange={setTab}
        tabs={[
          { id: 'settings', label: 'Settings' },
          { id: 'coupons', label: 'Coupons', count: coupons.data?.length },
          { id: 'categories', label: 'Categories', count: categories.data?.length },
        ]}
      />

      {tab === 'settings' && (
        <>
          {settings.isLoading && <PageLoader label="Loading settings…" />}
          {settings.isError && <Alert tone="error">{errorMessage(settings.error)}</Alert>}
          {!settings.isLoading && (settings.data ?? []).length === 0 && (
            <EmptyState icon={<Settings className="h-5 w-5" />} title="No settings" description="System settings appear here once seeded." />
          )}
          <div className="grid gap-3 lg:grid-cols-2">
            {(settings.data ?? []).map((setting) => (
              <div key={setting.key} className="card-pad">
                <div className="flex items-start justify-between gap-2">
                  <p className="font-mono text-xs font-semibold text-navy-800">{setting.key}</p>
                  {setting.publicSetting && <Badge tone="bg-sky-50 text-sky-700">Public</Badge>}
                </div>
                {setting.description && <p className="mt-1 text-xs text-slate-500">{setting.description}</p>}
                <div className="mt-2 flex gap-2">
                  <input
                    className="input py-2 text-xs"
                    value={draft[setting.key] ?? setting.value}
                    onChange={(event) => setDraft((prev) => ({ ...prev, [setting.key]: event.target.value }))}
                    aria-label={`Value for ${setting.key}`}
                  />
                  <button
                    type="button"
                    className="btn-secondary py-2 text-xs"
                    disabled={saveSetting.isPending || (draft[setting.key] ?? setting.value) === setting.value}
                    onClick={() => saveSetting.mutate(setting)}
                  >
                    Save
                  </button>
                </div>
              </div>
            ))}
          </div>
          <p className="text-[11px] text-slate-500">
            Public settings are exposed to the storefront (for example delivery fee). Never store secrets here — use
            environment variables for credentials.
          </p>
        </>
      )}

      {tab === 'coupons' && (
        <>
          <div className="flex justify-end">
            <button type="button" className="btn-primary" onClick={() => setCouponOpen(true)}>
              <Percent className="h-4 w-4" /> New coupon
            </button>
          </div>
          {coupons.isLoading && <PageLoader label="Loading coupons…" />}
          {!coupons.isLoading && (coupons.data ?? []).length === 0 && (
            <EmptyState icon={<Percent className="h-5 w-5" />} title="No coupons" description="Create a coupon to run a promotion." />
          )}
          {(coupons.data ?? []).length > 0 && (
            <div className="overflow-x-auto rounded-2xl border border-slate-200 bg-white">
              <table className="table-base min-w-[42rem]">
                <thead>
                  <tr>
                    <th>Code</th>
                    <th>Type</th>
                    <th className="text-right">Value</th>
                    <th className="text-right">Min order</th>
                    <th className="text-right">Used</th>
                    <th>Status</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {coupons.data!.map((row) => (
                    <tr key={row.id}>
                      <td className="font-mono text-xs font-semibold">{row.code}</td>
                      <td className="text-xs">{row.type}</td>
                      <td className="text-right text-sm">{row.type === 'PERCENT' ? `${row.value}%` : inr(row.value)}</td>
                      <td className="text-right text-xs">{inr(row.minOrderValue)}</td>
                      <td className="text-right text-xs">{row.usedCount}{row.usageLimit ? ` / ${row.usageLimit}` : ''}</td>
                      <td>
                        <Badge tone={row.active ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-600'}>
                          {row.active ? 'Active' : 'Inactive'}
                        </Badge>
                        {row.validTo && <p className="mt-1 text-[10px] text-slate-400">Until {dateOnly(row.validTo)}</p>}
                      </td>
                      <td className="text-right">
                        <button type="button" className="btn-ghost py-1.5 text-xs text-rose-700 hover:bg-rose-50" onClick={() => removeCoupon.mutate(row.id)}>
                          <Trash2 className="h-3.5 w-3.5" /> Disable
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}

      {tab === 'categories' && (
        <>
          <div className="flex justify-end">
            <button type="button" className="btn-primary" onClick={() => setCategoryOpen(true)}>
              <Tags className="h-4 w-4" /> New category
            </button>
          </div>
          {categories.isLoading && <PageLoader label="Loading categories…" />}
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {(categories.data ?? []).map((category) => (
              <div key={category.id} className="card-pad">
                <div className="flex items-start justify-between gap-2">
                  <p className="font-semibold">{category.name}</p>
                  {category.prescriptionOnly && <Badge tone="bg-amber-50 text-amber-800">Rx only</Badge>}
                </div>
                {category.description && <p className="mt-1 text-xs text-slate-500">{category.description}</p>}
                <p className="mt-2 font-mono text-[10px] text-slate-400">/{category.slug}</p>
              </div>
            ))}
          </div>
        </>
      )}

      <Modal
        open={couponOpen}
        onClose={() => setCouponOpen(false)}
        title="Create coupon"
        footer={
          <>
            <button type="button" className="btn-secondary" onClick={() => setCouponOpen(false)}>Cancel</button>
            <button
              type="button"
              className="btn-primary"
              disabled={createCoupon.isPending || !coupon.code.trim() || Number(coupon.value) <= 0}
              onClick={() => createCoupon.mutate()}
            >
              {createCoupon.isPending ? <Spinner /> : 'Create coupon'}
            </button>
          </>
        }
      >
        <div className="space-y-3">
          {couponError && <Alert tone="error">{couponError}</Alert>}
          <div className="grid gap-3 sm:grid-cols-2">
            <div>
              <label className="label" htmlFor="coupon-code">Code</label>
              <input id="coupon-code" className="input uppercase" maxLength={40} value={coupon.code} onChange={(event) => setCoupon({ ...coupon, code: event.target.value })} />
            </div>
            <div>
              <label className="label" htmlFor="coupon-type">Type</label>
              <select id="coupon-type" className="input" value={coupon.type} onChange={(event) => setCoupon({ ...coupon, type: event.target.value })}>
                <option value="PERCENT">Percentage</option>
                <option value="FLAT">Flat amount</option>
              </select>
            </div>
            <div>
              <label className="label" htmlFor="coupon-value">Value</label>
              <input id="coupon-value" className="input" inputMode="decimal" value={coupon.value} onChange={(event) => setCoupon({ ...coupon, value: event.target.value })} />
            </div>
            <div>
              <label className="label" htmlFor="coupon-min">Minimum order (₹)</label>
              <input id="coupon-min" className="input" inputMode="decimal" value={coupon.minOrderValue} onChange={(event) => setCoupon({ ...coupon, minOrderValue: event.target.value })} />
            </div>
            <div>
              <label className="label" htmlFor="coupon-max">Max discount (₹)</label>
              <input id="coupon-max" className="input" inputMode="decimal" value={coupon.maxDiscount} onChange={(event) => setCoupon({ ...coupon, maxDiscount: event.target.value })} />
            </div>
            <div>
              <label className="label" htmlFor="coupon-usage">Usage limit</label>
              <input id="coupon-usage" className="input" inputMode="numeric" value={coupon.usageLimit} onChange={(event) => setCoupon({ ...coupon, usageLimit: event.target.value })} />
            </div>
          </div>
          <div>
            <label className="label" htmlFor="coupon-description">Description</label>
            <input id="coupon-description" className="input" maxLength={200} value={coupon.description} onChange={(event) => setCoupon({ ...coupon, description: event.target.value })} />
          </div>
          <FieldError message={coupon.type === 'PERCENT' && Number(coupon.value) > 90 ? 'Percentage discounts above 90% need finance approval' : undefined} />
        </div>
      </Modal>

      <Modal
        open={categoryOpen}
        onClose={() => setCategoryOpen(false)}
        title="Create category"
        footer={
          <>
            <button type="button" className="btn-secondary" onClick={() => setCategoryOpen(false)}>Cancel</button>
            <button type="button" className="btn-primary" disabled={createCategory.isPending || !categoryName.trim()} onClick={() => createCategory.mutate()}>
              {createCategory.isPending ? <Spinner /> : 'Create'}
            </button>
          </>
        }
      >
        <div className="space-y-3">
          {categoryError && <Alert tone="error">{categoryError}</Alert>}
          <div>
            <label className="label" htmlFor="category-name">Name</label>
            <input id="category-name" className="input" maxLength={80} value={categoryName} onChange={(event) => setCategoryName(event.target.value)} />
          </div>
          <div>
            <label className="label" htmlFor="category-description">Description</label>
            <textarea id="category-description" className="input resize-none" rows={2} maxLength={300} value={categoryDescription} onChange={(event) => setCategoryDescription(event.target.value)} />
          </div>
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={categoryRx} onChange={(event) => setCategoryRx(event.target.checked)} />
            All medicines in this category require a prescription
          </label>
        </div>
      </Modal>
    </div>
  );
}
