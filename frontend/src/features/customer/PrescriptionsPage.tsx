import { useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Eye, FileText, Trash2, Upload } from 'lucide-react';
import { Alert, Badge, EmptyState, Modal, PageLoader, Spinner, useToast } from '../../components/ui';
import { apiDelete, apiGet, apiUpload, http, errorMessage } from '../../lib/api';
import { dateTime, fileSize, statusLabel, statusTone } from '../../lib/format';
import type { Prescription } from '../../types';

const ACCEPTED = ['application/pdf', 'image/jpeg', 'image/png'];
const MAX_BYTES = 8 * 1024 * 1024;

export default function PrescriptionsPage() {
  const queryClient = useQueryClient();
  const toast = useToast();
  const inputRef = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [notes, setNotes] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [viewing, setViewing] = useState<Prescription | null>(null);
  const [viewLoading, setViewLoading] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState<Prescription | null>(null);

  const { data, isLoading } = useQuery({
    queryKey: ['prescriptions'],
    queryFn: () => apiGet<Prescription[]>('/customer/prescriptions'),
  });

  const upload = useMutation({
    mutationFn: async () => {
      if (!file) throw new Error('Choose a prescription file first');
      const form = new FormData();
      form.append('file', file);
      if (notes.trim()) form.append('notes', notes.trim());
      return apiUpload<Prescription>('/customer/prescriptions', form);
    },
    onSuccess: (prescription) => {
      toast(
        prescription.status === 'APPROVED'
          ? 'Prescription uploaded and approved'
          : 'Prescription uploaded. A pharmacist will review it shortly.',
        'success',
      );
      setFile(null);
      setNotes('');
      setError(null);
      if (inputRef.current) inputRef.current.value = '';
      queryClient.invalidateQueries({ queryKey: ['prescriptions'] });
    },
    onError: (err) => setError(errorMessage(err)),
  });

  const remove = useMutation({
    mutationFn: (id: number) => apiDelete(`/customer/prescriptions/${id}`),
    onSuccess: () => {
      toast('Prescription removed', 'info');
      setConfirmDelete(null);
      queryClient.invalidateQueries({ queryKey: ['prescriptions'] });
    },
    onError: (err) => toast(errorMessage(err), 'error'),
  });

  const chooseFile = (selected: File | null) => {
    setError(null);
    if (!selected) {
      setFile(null);
      return;
    }
    if (!ACCEPTED.includes(selected.type)) {
      setError('Upload a PDF, JPEG or PNG file');
      return;
    }
    if (selected.size > MAX_BYTES) {
      setError('The file must be smaller than 8 MB');
      return;
    }
    setFile(selected);
  };

  const openFile = async (prescription: Prescription) => {
    setViewing(prescription);
    setViewLoading(true);
    try {
      const response = await http.get(`/customer/prescriptions/${prescription.id}/file`, { responseType: 'blob' });
      const url = URL.createObjectURL(response.data as Blob);
      window.open(url, '_blank', 'noopener');
      window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (err) {
      toast(errorMessage(err), 'error');
    } finally {
      setViewLoading(false);
    }
  };

  return (
    <div className="container-page space-y-6 py-6 sm:py-8">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Prescriptions</h1>
        <p className="muted mt-1">
          Upload a clear photo or PDF. A registered pharmacist reviews every prescription before dispensing.
        </p>
      </div>

      <div className="grid gap-5 lg:grid-cols-[1fr_22rem]">
        <div className="space-y-4 order-2 lg:order-1">
          {isLoading && <PageLoader label="Loading prescriptions…" />}
          {!isLoading && (data ?? []).length === 0 && (
            <EmptyState
              icon={<FileText className="h-5 w-5" />}
              title="No prescriptions uploaded"
              description="Prescription-only medicines need an approved prescription before checkout."
            />
          )}
          {(data ?? []).map((prescription) => (
            <div key={prescription.id} className="card-pad">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="flex gap-3">
                  <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-mint text-brand-700">
                    <FileText className="h-5 w-5" />
                  </div>
                  <div>
                    <p className="font-semibold">{prescription.fileName}</p>
                    <p className="text-xs text-slate-500">
                      {fileSize(prescription.sizeBytes)} · uploaded {dateTime(prescription.uploadedAt)}
                    </p>
                    {prescription.orderId && (
                      <Link to={`/orders/${prescription.orderId}`} className="link text-xs">Linked order #{prescription.orderId}</Link>
                    )}
                  </div>
                </div>
                <Badge tone={statusTone(prescription.status)}>{statusLabel(prescription.status)}</Badge>
              </div>

              {prescription.notes && <p className="mt-3 text-xs text-slate-600">Note: {prescription.notes}</p>}
              {prescription.rejectionReason && (
                <Alert tone="error" title="Reviewer comments">{prescription.rejectionReason}</Alert>
              )}
              {prescription.status === 'REJECTED' && (
                <p className="mt-2 text-xs text-slate-500">
                  Upload a new prescription with the full page visible, the doctor&apos;s signature and the date.
                </p>
              )}

              <div className="mt-3 flex flex-wrap gap-2">
                <button type="button" className="btn-secondary py-2 text-xs" onClick={() => openFile(prescription)}>
                  <Eye className="h-3.5 w-3.5" /> View
                </button>
                {(prescription.status === 'UPLOADED' || prescription.status === 'REJECTED') && (
                  <button type="button" className="btn-ghost py-2 text-xs text-rose-700 hover:bg-rose-50" onClick={() => setConfirmDelete(prescription)}>
                    <Trash2 className="h-3.5 w-3.5" /> Remove
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>

        <aside className="order-1 h-fit space-y-3 lg:order-2">
          <div className="card-pad">
            <h2 className="text-sm font-semibold">Upload prescription</h2>
            <input
              ref={inputRef}
              type="file"
              accept=".pdf,.jpg,.jpeg,.png"
              className="sr-only"
              id="prescription-file"
              onChange={(event) => chooseFile(event.target.files?.[0] ?? null)}
            />
            <label
              htmlFor="prescription-file"
              className="mt-3 flex cursor-pointer flex-col items-center gap-2 rounded-xl border border-dashed border-slate-300 bg-slate-50 px-4 py-8 text-center hover:border-brand-400"
            >
              <Upload className="h-5 w-5 text-brand-700" />
              <span className="text-sm font-medium">{file ? file.name : 'Choose a file'}</span>
              <span className="text-[11px] text-slate-500">PDF, JPG or PNG · max 8 MB</span>
            </label>
            <label className="label mt-4" htmlFor="prescription-notes">Notes for the pharmacist</label>
            <textarea
              id="prescription-notes"
              className="input resize-none"
              rows={2}
              maxLength={500}
              placeholder="Doctor name, clinic, or specific instructions"
              value={notes}
              onChange={(event) => setNotes(event.target.value)}
            />
            {error && <p className="mt-2 text-xs font-medium text-rose-600">{error}</p>}
            <button
              type="button"
              className="btn-primary mt-3 w-full"
              disabled={!file || upload.isPending}
              onClick={() => upload.mutate()}
            >
              {upload.isPending ? <Spinner /> : 'Upload for review'}
            </button>
          </div>

          <div className="rounded-2xl bg-white p-4 text-xs leading-relaxed text-slate-600 ring-1 ring-slate-200">
            <p className="font-semibold text-navy-900">What we can accept</p>
            <ul className="mt-2 list-disc space-y-1 pl-4">
              <li>Issued by a doctor registered in India</li>
              <li>Patient name, date and prescriber signature visible</li>
              <li>No edits, stickers or overlaid text</li>
            </ul>
            <p className="mt-2">Files are stored privately and shared only with the reviewing pharmacy.</p>
          </div>
        </aside>
      </div>

      <Modal open={!!viewing} onClose={() => setViewing(null)} title={viewing?.fileName ?? 'Prescription'}>
        {viewLoading ? <Spinner label="Opening secure file…" /> : <p className="text-sm text-slate-600">The document opens in a new tab with a short-lived signed request.</p>}
      </Modal>

      <Modal
        open={!!confirmDelete}
        onClose={() => setConfirmDelete(null)}
        title="Remove prescription"
        footer={
          <>
            <button type="button" className="btn-secondary" onClick={() => setConfirmDelete(null)}>Keep</button>
            <button
              type="button"
              className="btn-danger"
              onClick={() => confirmDelete && remove.mutate(confirmDelete.id)}
              disabled={remove.isPending}
            >
              {remove.isPending ? <Spinner /> : 'Remove'}
            </button>
          </>
        }
      >
        <p className="text-sm text-slate-600">This permanently deletes the uploaded file. You can upload a fresh copy at any time.</p>
      </Modal>
    </div>
  );
}
