import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { BadgeCheck, FileText, ShieldX, Store, Truck } from 'lucide-react';
import { Alert, Badge, EmptyState, Modal, PageLoader, Spinner, Tabs, useToast } from '../../components/ui';
import { apiGet, apiPost, errorMessage } from '../../lib/api';
import { dateTime, statusLabel, statusTone } from '../../lib/format';
import type { AdminPartnerRow, AdminPharmacyRow, PageResponse } from '../../types';

type Kind = 'pharmacies' | 'partners';
type Decision = { kind: Kind; id: number; name: string; approve: boolean };

export default function AdminApprovalsPage() {
  const toast = useToast();
  const queryClient = useQueryClient();
  const [tab, setTab] = useState<Kind>('pharmacies');
  const [decision, setDecision] = useState<Decision | null>(null);
  const [note, setNote] = useState('');

  const pharmacies = useQuery({
    queryKey: ['admin-pharmacies', 'PENDING'],
    queryFn: () => apiGet<PageResponse<AdminPharmacyRow>>('/admin/pharmacies', { status: 'PENDING', size: 50 }),
  });

  const partners = useQuery({
    queryKey: ['admin-partners', 'PENDING'],
    queryFn: () => apiGet<PageResponse<AdminPartnerRow>>('/admin/delivery-partners', { status: 'PENDING', size: 50 }),
  });

  const submit = useMutation({
    mutationFn: () => {
      if (!decision) throw new Error('No decision selected');
      const path = decision.kind === 'pharmacies'
        ? `/admin/pharmacies/${decision.id}/decision`
        : `/admin/delivery-partners/${decision.id}/decision`;
      return apiPost(path, { approve: decision.approve, note: note.trim() || undefined });
    },
    onSuccess: () => {
      toast(decision?.approve ? 'Approved' : 'Rejected', 'success');
      setDecision(null);
      setNote('');
      queryClient.invalidateQueries({ queryKey: ['admin-pharmacies'] });
      queryClient.invalidateQueries({ queryKey: ['admin-partners'] });
      queryClient.invalidateQueries({ queryKey: ['admin-dashboard'] });
    },
    onError: (error) => toast(errorMessage(error), 'error'),
  });

  const open = (kind: Kind, id: number, name: string, approve: boolean) => {
    setDecision({ kind, id, name, approve });
    setNote('');
  };

  return (
    <div className="space-y-5 px-4 py-6 sm:px-6 lg:px-8">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Approvals</h1>
        <p className="muted mt-1">Verify licences and identity documents before activating partners.</p>
      </div>

      <Tabs
        value={tab}
        onChange={setTab}
        tabs={[
          { id: 'pharmacies', label: 'Pharmacies', count: pharmacies.data?.totalElements },
          { id: 'partners', label: 'Delivery partners', count: partners.data?.totalElements },
        ]}
      />

      {tab === 'pharmacies' && (
        <>
          {pharmacies.isLoading && <PageLoader label="Loading applications…" />}
          {pharmacies.isError && <Alert tone="error">{errorMessage(pharmacies.error)}</Alert>}
          {!pharmacies.isLoading && (pharmacies.data?.content ?? []).length === 0 && (
            <EmptyState icon={<Store className="h-5 w-5" />} title="No pending pharmacies" description="Every pharmacy application has been reviewed." />
          )}
          <div className="grid gap-3 lg:grid-cols-2">
            {(pharmacies.data?.content ?? []).map((pharmacy) => (
              <div key={pharmacy.id} className="card-pad">
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <p className="font-semibold">{pharmacy.name}</p>
                    <p className="text-xs text-slate-500">{pharmacy.city} · {pharmacy.phone}</p>
                    <p className="mt-1 font-mono text-[11px] text-slate-500">Licence {pharmacy.licenseNumber}</p>
                  </div>
                  <Badge tone={statusTone(pharmacy.status)}>{statusLabel(pharmacy.status)}</Badge>
                </div>
                <p className="mt-2 text-[11px] text-slate-500">Applied {dateTime(pharmacy.registeredAt)}</p>
                <ul className="mt-2 space-y-1 text-xs text-slate-600">
                  {(pharmacy.documents ?? []).map((doc) => (
                    <li key={`${doc.documentType}-${doc.fileName}`} className="flex items-center gap-1.5">
                      <FileText className="h-3 w-3" /> {doc.documentType} — {doc.fileName}
                      <Badge tone={statusTone(doc.status)}>{statusLabel(doc.status)}</Badge>
                    </li>
                  ))}
                  {(pharmacy.documents ?? []).length === 0 && <li className="text-amber-700">No documents uploaded yet</li>}
                </ul>
                <div className="mt-3 flex gap-2">
                  <button type="button" className="btn-primary py-1.5 text-xs" onClick={() => open('pharmacies', pharmacy.id, pharmacy.name, true)}>
                    <BadgeCheck className="h-3.5 w-3.5" /> Approve
                  </button>
                  <button type="button" className="btn-secondary py-1.5 text-xs text-rose-700" onClick={() => open('pharmacies', pharmacy.id, pharmacy.name, false)}>
                    <ShieldX className="h-3.5 w-3.5" /> Reject
                  </button>
                </div>
              </div>
            ))}
          </div>
        </>
      )}

      {tab === 'partners' && (
        <>
          {partners.isLoading && <PageLoader label="Loading applications…" />}
          {partners.isError && <Alert tone="error">{errorMessage(partners.error)}</Alert>}
          {!partners.isLoading && (partners.data?.content ?? []).length === 0 && (
            <EmptyState icon={<Truck className="h-5 w-5" />} title="No pending partners" description="Every delivery application has been reviewed." />
          )}
          <div className="grid gap-3 lg:grid-cols-2">
            {(partners.data?.content ?? []).map((partner) => (
              <div key={partner.id} className="card-pad">
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <p className="font-semibold">{partner.fullName}</p>
                    <p className="text-xs text-slate-500">{partner.phone} · {partner.vehicleType ?? 'Vehicle not specified'}</p>
                    <p className="mt-1 text-[11px] text-slate-500">Applied {dateTime(partner.appliedAt)}</p>
                  </div>
                  <Badge tone={statusTone(partner.status)}>{statusLabel(partner.status)}</Badge>
                </div>
                <ul className="mt-2 space-y-1 text-xs text-slate-600">
                  {(partner.documents ?? []).map((doc) => (
                    <li key={doc.documentType} className="flex items-center gap-1.5">
                      <FileText className="h-3 w-3" /> {doc.documentType} — {doc.fileName}
                    </li>
                  ))}
                  {(partner.documents ?? []).length === 0 && <li className="text-amber-700">No documents on file</li>}
                </ul>
                <div className="mt-3 flex gap-2">
                  <button type="button" className="btn-primary py-1.5 text-xs" onClick={() => open('partners', partner.id, partner.fullName, true)}>Approve</button>
                  <button type="button" className="btn-secondary py-1.5 text-xs text-rose-700" onClick={() => open('partners', partner.id, partner.fullName, false)}>Reject</button>
                </div>
              </div>
            ))}
          </div>
        </>
      )}

      <Modal
        open={!!decision}
        onClose={() => setDecision(null)}
        title={decision ? `${decision.approve ? 'Approve' : 'Reject'} ${decision.name}` : 'Decision'}
        footer={
          <>
            <button type="button" className="btn-secondary" onClick={() => setDecision(null)}>Cancel</button>
            <button type="button" className={decision?.approve ? 'btn-primary' : 'btn-danger'} onClick={() => submit.mutate()} disabled={submit.isPending}>
              {submit.isPending ? <Spinner /> : decision?.approve ? 'Approve' : 'Reject application'}
            </button>
          </>
        }
      >
        <p className="text-sm text-slate-600">
          {decision?.approve
            ? 'The partner becomes active immediately and can receive orders. Confirm that the licence and ID documents are valid and legible.'
            : 'The application is rejected and the partner is notified. Add a clear reason so they can reapply with correct documents.'}
        </p>
        <label className="label mt-3" htmlFor="decision-note">Note</label>
        <textarea id="decision-note" className="input resize-none" rows={3} maxLength={400} value={note} onChange={(event) => setNote(event.target.value)} />
      </Modal>
    </div>
  );
}
