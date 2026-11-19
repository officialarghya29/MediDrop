import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { AlertTriangle, Boxes, Plus } from 'lucide-react';
import { Alert, Badge, EmptyState, FieldError, Modal, PageLoader, Spinner, useToast } from '../../components/ui';
import { apiDelete, apiGet, apiPatch, apiPost, errorMessage } from '../../lib/api';
import { inr } from '../../lib/format';
import type { Category, InventoryRow, PageResponse } from '../../types';

const emptyMedicine = {
  name: '',
  brand: '',
  genericName: '',
  strength: '',
  dosageForm: 'TABLET',
  packSize: '',
  categoryId: '',
  prescriptionRequired: false,
  description: '',
  warnings: '',
  price: '',
  mrp: '',
  stockQuantity: '0',
};

export default function InventoryPage() {
  const toast = useToast();
  const queryClient = useQueryClient();
  const [addOpen, setAddOpen] = useState(false);
  const [form, setForm] = useState(emptyMedicine);
  const [formError, setFormError] = useState<string | null>(null);
  const [adjusting, setAdjusting] = useState<InventoryRow | null>(null);
  const [delta, setDelta] = useState('10');
  const [adjustReason, setAdjustReason] = useState('');

  const inventory = useQuery({
    queryKey: ['pharmacy-inventory'],
    queryFn: () => apiGet<PageResponse<InventoryRow>>('/pharmacy/inventory', { size: 50 }),
  });

  const lowStock = useQuery({
    queryKey: ['pharmacy-low-stock'],
    queryFn: () => apiGet<{ inventoryId: number; medicineName: string; stockQuantity: number; threshold: number }[]>('/pharmacy/inventory/low-stock'),
  });

  const categories = useQuery({
    queryKey: ['categories'],
    queryFn: () => apiGet<Category[]>('/medicines/categories'),
  });

  const create = useMutation({
    mutationFn: () =>
      apiPost('/pharmacy/inventory/medicines', {
        name: form.name,
        brand: form.brand || undefined,
        genericName: form.genericName || undefined,
        strength: form.strength || undefined,
        dosageForm: form.dosageForm,
        packSize: form.packSize || undefined,
        categoryId: Number(form.categoryId),
        prescriptionRequired: form.prescriptionRequired,
        description: form.description || undefined,
        warnings: form.warnings || undefined,
        price: form.price ? Number(form.price) : undefined,
        mrp: form.mrp ? Number(form.mrp) : undefined,
        stockQuantity: Number(form.stockQuantity || 0),
      }),
    onSuccess: () => {
      toast('Medicine added to your catalogue', 'success');
      setAddOpen(false);
      setForm(emptyMedicine);
      setFormError(null);
      queryClient.invalidateQueries({ queryKey: ['pharmacy-inventory'] });
      queryClient.invalidateQueries({ queryKey: ['pharmacy-dashboard'] });
    },
    onError: (error) => setFormError(errorMessage(error)),
  });

  const adjust = useMutation({
    mutationFn: () =>
      apiPatch(`/pharmacy/inventory/${adjusting?.id}/stock`, {
        delta: Number(delta),
        reason: adjustReason.trim(),
      }),
    onSuccess: () => {
      toast('Stock updated', 'success');
      setAdjusting(null);
      setAdjustReason('');
      queryClient.invalidateQueries({ queryKey: ['pharmacy-inventory'] });
      queryClient.invalidateQueries({ queryKey: ['pharmacy-low-stock'] });
    },
    onError: (error) => toast(errorMessage(error), 'error'),
  });

  const removeListing = useMutation({
    mutationFn: (id: number) => apiDelete(`/pharmacy/inventory/${id}`),
    onSuccess: () => {
      toast('Listing removed', 'info');
      queryClient.invalidateQueries({ queryKey: ['pharmacy-inventory'] });
      queryClient.invalidateQueries({ queryKey: ['pharmacy-low-stock'] });
    },
    onError: (error) => toast(errorMessage(error), 'error'),
  });

  return (
    <div className="space-y-5 px-4 py-6 sm:px-6 lg:px-8">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Inventory</h1>
          <p className="muted mt-1">Prices, stock and thresholds for every listing. Expiry batches are tracked per item.</p>
        </div>
        <button type="button" className="btn-primary" onClick={() => setAddOpen(true)}>
          <Plus className="h-4 w-4" /> Add medicine
        </button>
      </div>

      {(lowStock.data ?? []).length > 0 && (
        <Alert tone="warning" title={`${lowStock.data!.length} item(s) below threshold`}>
          <ul className="mt-1 space-y-0.5 text-xs">
            {lowStock.data!.slice(0, 4).map((item) => (
              <li key={item.inventoryId}>{item.medicineName} — {item.stockQuantity} left (threshold {item.threshold})</li>
            ))}
          </ul>
        </Alert>
      )}

      {inventory.isLoading && <PageLoader label="Loading inventory…" />}
      {inventory.isError && <Alert tone="error">{errorMessage(inventory.error)}</Alert>}

      {!inventory.isLoading && (inventory.data?.content ?? []).length === 0 && (
        <EmptyState icon={<Boxes className="h-5 w-5" />} title="No listings yet" description="Add your first medicine to start selling on MediDrop." />
      )}

      {(inventory.data?.content ?? []).length > 0 && (
        <div className="overflow-x-auto rounded-2xl border border-slate-200 bg-white">
          <table className="table-base min-w-[52rem]">
            <thead>
              <tr>
                <th>Medicine</th>
                <th className="text-right">Price</th>
                <th className="text-right">MRP</th>
                <th className="text-right">Stock</th>
                <th>Status</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {inventory.data!.content.map((row) => (
                <tr key={row.id}>
                  <td>
                    <p className="font-medium">{row.medicineName}</p>
                    <p className="text-[11px] text-slate-500">
                      {[row.brand, row.strength, row.packSize].filter(Boolean).join(' · ')}
                      {row.prescriptionRequired ? ' · Rx' : ''}
                    </p>
                  </td>
                  <td className="text-right">{inr(row.price)}</td>
                  <td className="text-right text-xs text-slate-500">{inr(row.mrp)}</td>
                  <td className="text-right">
                    <span className={row.stockQuantity <= row.lowStockThreshold ? 'font-semibold text-amber-700' : ''}>
                      {row.stockQuantity}
                    </span>
                    <span className="block text-[10px] text-slate-400">{row.availableQuantity} available</span>
                  </td>
                  <td>
                    <Badge tone={row.active ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-600'}>
                      {row.active ? 'Active' : 'Inactive'}
                    </Badge>
                  </td>
                  <td className="text-right">
                    <div className="flex justify-end gap-1">
                      <button type="button" className="btn-secondary py-1.5 text-xs" onClick={() => { setAdjusting(row); setDelta('10'); }}>
                        Adjust
                      </button>
                      <button
                        type="button"
                        className="btn-ghost py-1.5 text-xs text-rose-700 hover:bg-rose-50"
                        onClick={() => removeListing.mutate(row.id)}
                        disabled={removeListing.isPending}
                      >
                        Remove
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <p className="text-[11px] text-slate-500">
        Stock adjustments are written to an append-only ledger with the reason you supply, so every change is
        auditable during compliance review.
      </p>

      <Modal
        open={addOpen}
        onClose={() => setAddOpen(false)}
        title="Add a medicine to your catalogue"
        footer={
          <>
            <button type="button" className="btn-secondary" onClick={() => setAddOpen(false)}>Cancel</button>
            <button
              type="button"
              className="btn-primary"
              disabled={create.isPending || !form.name || !form.categoryId || !form.dosageForm}
              onClick={() => create.mutate()}
            >
              {create.isPending ? <Spinner /> : 'Add listing'}
            </button>
          </>
        }
      >
        <div className="space-y-3">
          {formError && <Alert tone="error">{formError}</Alert>}
          <div className="grid gap-3 sm:grid-cols-2">
            <div>
              <label className="label" htmlFor="med-name">Medicine name</label>
              <input id="med-name" className="input" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
            </div>
            <div>
              <label className="label" htmlFor="med-brand">Brand</label>
              <input id="med-brand" className="input" value={form.brand} onChange={(e) => setForm({ ...form, brand: e.target.value })} />
            </div>
            <div>
              <label className="label" htmlFor="med-generic">Generic name</label>
              <input id="med-generic" className="input" value={form.genericName} onChange={(e) => setForm({ ...form, genericName: e.target.value })} />
            </div>
            <div>
              <label className="label" htmlFor="med-strength">Strength</label>
              <input id="med-strength" className="input" placeholder="500 mg" value={form.strength} onChange={(e) => setForm({ ...form, strength: e.target.value })} />
            </div>
            <div>
              <label className="label" htmlFor="med-form">Dosage form</label>
              <select id="med-form" className="input" value={form.dosageForm} onChange={(e) => setForm({ ...form, dosageForm: e.target.value })}>
                {['TABLET', 'CAPSULE', 'SYRUP', 'INJECTION', 'OINTMENT', 'DROPS', 'INHALER', 'POWDER'].map((form) => (
                  <option key={form} value={form}>{form}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="label" htmlFor="med-pack">Pack size</label>
              <input id="med-pack" className="input" placeholder="10 tablets" value={form.packSize} onChange={(e) => setForm({ ...form, packSize: e.target.value })} />
            </div>
            <div>
              <label className="label" htmlFor="med-category">Category</label>
              <select id="med-category" className="input" value={form.categoryId} onChange={(e) => setForm({ ...form, categoryId: e.target.value })}>
                <option value="">Select category</option>
                {(categories.data ?? []).map((category) => (
                  <option key={category.id} value={category.id}>{category.name}</option>
                ))}
              </select>
            </div>
            <div className="flex items-end pb-2">
              <label className="flex items-center gap-2 text-sm">
                <input type="checkbox" checked={form.prescriptionRequired} onChange={(e) => setForm({ ...form, prescriptionRequired: e.target.checked })} />
                Prescription required
              </label>
            </div>
            <div>
              <label className="label" htmlFor="med-price">Selling price (₹)</label>
              <input id="med-price" className="input" inputMode="decimal" value={form.price} onChange={(e) => setForm({ ...form, price: e.target.value })} />
            </div>
            <div>
              <label className="label" htmlFor="med-mrp">MRP (₹)</label>
              <input id="med-mrp" className="input" inputMode="decimal" value={form.mrp} onChange={(e) => setForm({ ...form, mrp: e.target.value })} />
            </div>
            <div>
              <label className="label" htmlFor="med-stock">Opening stock</label>
              <input id="med-stock" className="input" inputMode="numeric" value={form.stockQuantity} onChange={(e) => setForm({ ...form, stockQuantity: e.target.value.replace(/\D/g, '') })} />
            </div>
          </div>
          <div>
            <label className="label" htmlFor="med-desc">Description</label>
            <textarea id="med-desc" className="input resize-none" rows={2} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
          </div>
          <div>
            <label className="label" htmlFor="med-warnings">Warnings</label>
            <textarea id="med-warnings" className="input resize-none" rows={2} value={form.warnings} onChange={(e) => setForm({ ...form, warnings: e.target.value })} />
          </div>
          <FieldError message={!form.categoryId ? 'Choose a category' : undefined} />
        </div>
      </Modal>

      <Modal
        open={!!adjusting}
        onClose={() => setAdjusting(null)}
        title={adjusting ? `Adjust stock · ${adjusting.medicineName}` : 'Adjust stock'}
        footer={
          <>
            <button type="button" className="btn-secondary" onClick={() => setAdjusting(null)}>Cancel</button>
            <button
              type="button"
              className="btn-primary"
              disabled={adjust.isPending || !adjustReason.trim() || Number.isNaN(Number(delta)) || Number(delta) === 0}
              onClick={() => adjust.mutate()}
            >
              {adjust.isPending ? <Spinner /> : 'Apply adjustment'}
            </button>
          </>
        }
      >
        <div className="space-y-3">
          <p className="text-sm text-slate-600">
            Current stock: <strong>{adjusting?.stockQuantity}</strong>. Use a negative number to reduce stock for
            damage or expiry.
          </p>
          <div>
            <label className="label" htmlFor="stock-delta">Change (± units)</label>
            <input id="stock-delta" className="input" value={delta} onChange={(event) => setDelta(event.target.value)} />
          </div>
          <div>
            <label className="label" htmlFor="stock-reason">Reason</label>
            <input id="stock-reason" className="input" maxLength={300} value={adjustReason} onChange={(event) => setAdjustReason(event.target.value)} placeholder="New purchase, expiry write-off…" />
            <FieldError message={!adjustReason.trim() ? 'A reason is required for the audit ledger' : undefined} />
          </div>
          <p className="flex items-start gap-2 rounded-lg bg-amber-50 p-2 text-xs text-amber-900">
            <AlertTriangle className="mt-0.5 h-3.5 w-3.5 shrink-0" /> Negative adjustments cannot take stock below zero.
          </p>
        </div>
      </Modal>
    </div>
  );
}
