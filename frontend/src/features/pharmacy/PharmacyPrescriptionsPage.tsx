import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Eye, FileCheck2, Stethoscope } from 'lucide-react';
import { Alert, Badge, EmptyState, FieldError, Modal, PageLoader, Spinner, useToast } from '../../components/ui';
import { apiGet, apiPost, http, errorMessage } from '../../lib/api';
import { dateTime, statusLabel, statusTone } from '../../lib/format';

interface PendingPrescription {
  id: number;
  customerId: number;
  customerName: string;
  fileName: string;
  uploadedAt: string;
  status: string;
  notes?: string;
  orderId?: number;
  orderNumber?: string;
}

export default function PharmacyPrescriptionsPage() {
  const toast = useToast();
  const queryClient = useQueryClient();
  const [selected, setSelected] = useState<PendingPrescription | null>(null);
  const [approve, setApprove] = useState(true);
  const [comments, setComments] = useState('');
  const [verifiedItems, setVerifiedItems] = useState('');
  const [viewing, setViewing] = useState(false);

  const queue = useQuery({
    queryKey: ['pharmacy-prescription-queue'],
    queryFn: () => apiGet<PendingPrescription[]>('/pharmacy/prescriptions'),
  });

  const review = useMutation({
    mutationFn: () =>
      apiPost(`/pharmacy/prescriptions/${selected?.id}/review`, {
        approve,
        comments: comments.trim() || undefined,
        verifiedItems: verifiedItems.trim() || undefined,
      }),
    onSuccess: () => {
      toast(approve ? 'Prescription approved' : 'Prescription rejected', 'success');
      setSelected(null);
      setComments('');
      setVerifiedItems('');
      queryClient.invalidateQueries({ queryKey: ['pharmacy-prescription-queue'] });
      queryClient.invalidateQueries({ queryKey: ['pharmacy-dashboard'] });
      queryClient.invalidateQueries({ queryKey: ['pharmacy-orders'] });
    },
    onError: (error) => toast(errorMessage(error), 'error'),
  });

  const openFile = async (id: number) => {
    setViewing(true);
    try {
      const response = await http.get(`/pharmacy/prescriptions/${id}/file`, { responseType: 'blob' });
      const url = URL.createObjectURL(response.data as Blob);
      window.open(url, '_blank', 'noopener');
      window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (error) {
      toast(errorMessage(error), 'error');
    } finally {
      setViewing(false);
    }
  };

  return (
    <div className="space-y-5 px-4 py-6 sm:px-6 lg:px-8">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Prescription review</h1>
        <p className="muted mt-1">
          Only a registered pharmacist may approve a prescription. Approvals unlock dispensing for the linked order.
        </p>
      </div>

      <Alert tone="warning" title="Regulatory responsibility">
        Verify the patient name, prescriber registration, medicine strength and date before approving. Never approve
        an altered or unreadable prescription — reject it with a clear reason so the customer can re-upload.
      </Alert>

      {queue.isLoading && <PageLoader label="Loading review queue…" />}
      {queue.isError && <Alert tone="error">{errorMessage(queue.error)}</Alert>}
      {!queue.isLoading && (queue.data ?? []).length === 0 && (
        <EmptyState icon={<FileCheck2 className="h-5 w-5" />} title="Queue is clear" description="No prescriptions are waiting for review." />
      )}

      <div className="grid gap-3 lg:grid-cols-2">
        {(queue.data ?? []).map((item) => (
          <div key={item.id} className="card-pad">
            <div className="flex items-start justify-between gap-3">
              <div>
                <p className="font-semibold">{item.customerName}</p>
                <p className="text-xs text-slate-500">{item.fileName} · {dateTime(item.uploadedAt)}</p>
                {item.orderNumber && <p className="mt-0.5 text-xs text-slate-500">Order {item.orderNumber}</p>}
              </div>
              <Badge tone={statusTone(item.status)}>{statusLabel(item.status)}</Badge>
            </div>
            {item.notes && <p className="mt-2 rounded-lg bg-slate-50 p-2 text-xs text-slate-600">Customer note: {item.notes}</p>}
            <div className="mt-3 flex gap-2">
              <button type="button" className="btn-secondary py-2 text-xs" onClick={() => openFile(item.id)}>
                <Eye className="h-3.5 w-3.5" /> View file
              </button>
              <button
                type="button"
                className="btn-primary py-2 text-xs"
                onClick={() => {
                  setSelected(item);
                  setApprove(true);
                  setComments('');
                  setVerifiedItems('');
                }}
              >
                <Stethoscope className="h-3.5 w-3.5" /> Review
              </button>
            </div>
          </div>
        ))}
      </div>

      <Modal
        open={!!selected}
        onClose={() => setSelected(null)}
        title={selected ? `Review · ${selected.customerName}` : 'Review prescription'}
        footer={
          <>
            <button type="button" className="btn-secondary" onClick={() => setSelected(null)}>Cancel</button>
            <button
              type="button"
              className={approve ? 'btn-primary' : 'btn-danger'}
              disabled={review.isPending || (!approve && comments.trim().length < 3)}
              onClick={() => review.mutate()}
            >
              {review.isPending ? <Spinner /> : approve ? 'Approve prescription' : 'Reject prescription'}
            </button>
          </>
        }
      >
        <div className="space-y-3">
          <div className="flex gap-2">
            <button type="button" className={`flex-1 rounded-xl border p-3 text-sm font-medium ${approve ? 'border-brand-500 bg-mint/40 text-brand-800' : 'border-slate-200'}`} onClick={() => setApprove(true)}>
              Approve
            </button>
            <button type="button" className={`flex-1 rounded-xl border p-3 text-sm font-medium ${!approve ? 'border-rose-400 bg-rose-50 text-rose-800' : 'border-slate-200'}`} onClick={() => setApprove(false)}>
              Reject
            </button>
          </div>
          <button type="button" className="btn-secondary w-full py-2 text-xs" onClick={() => selected && openFile(selected.id)} disabled={viewing}>
            {viewing ? <Spinner /> : <><Eye className="h-3.5 w-3.5" /> Open prescription file</>}
          </button>
          {!approve && (
            <div>
              <label className="label" htmlFor="review-comments">Rejection reason (shown to customer)</label>
              <textarea id="review-comments" className="input resize-none" rows={3} maxLength={500} value={comments} onChange={(event) => setComments(event.target.value)} />
              <FieldError message={comments.trim().length > 0 && comments.trim().length < 3 ? 'Add a clearer reason' : undefined} />
            </div>
          )}
          {approve && (
            <>
              <div>
                <label className="label" htmlFor="verified-items">Verified items (optional)</label>
                <input id="verified-items" className="input" maxLength={2000} placeholder="Paracetamol 500 mg — 1 strip" value={verifiedItems} onChange={(event) => setVerifiedItems(event.target.value)} />
              </div>
              <div>
                <label className="label" htmlFor="approve-comments">Pharmacist comments (optional)</label>
                <textarea id="approve-comments" className="input resize-none" rows={2} maxLength={500} value={comments} onChange={(event) => setComments(event.target.value)} />
              </div>
            </>
          )}
          <p className="text-[11px] text-slate-500">
            Your name, the timestamp and the decision are stored in the prescription audit trail.
          </p>
        </div>
      </Modal>
    </div>
  );
}
