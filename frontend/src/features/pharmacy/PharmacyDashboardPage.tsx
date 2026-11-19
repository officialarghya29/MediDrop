import { useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  ClipboardList, FileText, IndianRupee, Package, PauseCircle, PlayCircle, Stethoscope, Store, Upload,
} from 'lucide-react';
import { Alert, Badge, PageLoader, Spinner, StatCard, useToast } from '../../components/ui';
import { apiGet, apiPost, apiUpload, errorMessage } from '../../lib/api';
import { dateTime, inr, statusLabel, statusTone } from '../../lib/format';
import type { OrderSummary, PageResponse, PharmacyDashboard } from '../../types';

interface DocumentRow {
  id: number;
  documentType: string;
  fileName: string;
  status: string;
  uploadedAt: string;
  remarks?: string;
}

export default function PharmacyDashboardPage() {
  const toast = useToast();
  const queryClient = useQueryClient();
  const fileRef = useRef<HTMLInputElement>(null);
  const [documentType, setDocumentType] = useState('DRUG_LICENSE');
  const [uploading, setUploading] = useState(false);

  const { data, isLoading } = useQuery({
    queryKey: ['pharmacy-dashboard'],
    queryFn: () => apiGet<PharmacyDashboard>('/pharmacy/dashboard'),
  });

  const { data: recent } = useQuery({
    queryKey: ['pharmacy-orders', 'recent'],
    queryFn: () => apiGet<PageResponse<OrderSummary>>('/pharmacy/orders', { size: 5 }),
  });

  const { data: lowStock } = useQuery({
    queryKey: ['pharmacy-low-stock'],
    queryFn: () => apiGet<{ inventoryId: number; medicineName: string; stockQuantity: number; threshold: number }[]>('/pharmacy/inventory/low-stock'),
  });

  const { data: documents } = useQuery({
    queryKey: ['pharmacy-documents'],
    queryFn: () => apiGet<DocumentRow[]>('/pharmacy/documents'),
  });

  const toggle = useMutation({
    mutationFn: (accepting: boolean) => apiPost<PharmacyDashboard>('/pharmacy/accepting-orders', { acceptingOrders: accepting }),
    onSuccess: (dashboard) => {
      toast(dashboard.acceptingOrders ? 'You are accepting orders' : 'New orders paused', 'success');
      queryClient.setQueryData(['pharmacy-dashboard'], dashboard);
    },
    onError: (error) => toast(errorMessage(error), 'error'),
  });

  const upload = async () => {
    const file = fileRef.current?.files?.[0];
    if (!file) {
      toast('Choose a PDF or image first', 'error');
      return;
    }
    setUploading(true);
    try {
      const form = new FormData();
      form.append('file', file);
      form.append('documentType', documentType);
      await apiUpload('/pharmacy/documents', form);
      toast('Document uploaded for verification', 'success');
      if (fileRef.current) fileRef.current.value = '';
      queryClient.invalidateQueries({ queryKey: ['pharmacy-documents'] });
    } catch (error) {
      toast(errorMessage(error), 'error');
    } finally {
      setUploading(false);
    }
  };

  if (isLoading) return <PageLoader label="Loading pharmacy console…" />;
  if (!data) return <Alert tone="error">Unable to load the pharmacy dashboard.</Alert>;

  const pendingApproval = data.status === 'PENDING' || data.status === 'UNDER_REVIEW';

  return (
    <div className="space-y-6 px-4 py-6 sm:px-6 lg:px-8">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-2xl font-bold tracking-tight">{data.pharmacyName}</h1>
            <Badge tone={statusTone(data.status)}>{statusLabel(data.status)}</Badge>
          </div>
          <p className="muted mt-1">Rating {data.rating.toFixed(1)} ★ · {data.activeListings} active listings</p>
        </div>
        <button
          type="button"
          className={data.acceptingOrders ? 'btn-secondary' : 'btn-primary'}
          onClick={() => toggle.mutate(!data.acceptingOrders)}
          disabled={toggle.isPending || pendingApproval}
        >
          {data.acceptingOrders ? <><PauseCircle className="h-4 w-4" /> Pause orders</> : <><PlayCircle className="h-4 w-4" /> Start accepting</>}
        </button>
      </div>

      {pendingApproval && (
        <Alert tone="warning" title="Verification in progress">
          Your drug licence is being verified. Upload your documents below if you have not already; ordering stays
          disabled until an administrator approves the pharmacy.
        </Alert>
      )}
      {data.status === 'SUSPENDED' && (
        <Alert tone="error" title="Account suspended">
          Contact MediDrop compliance to resolve the suspension. You cannot receive new orders.
        </Alert>
      )}

      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard label="Orders today" value={data.pendingOrders} hint={`${data.totalOrders} lifetime`} icon={<ClipboardList className="h-4 w-4" />} />
        <StatCard label="Awaiting prescription" value={data.awaitingPrescription} hint={`${data.pendingPrescriptionReviews} in review`} icon={<Stethoscope className="h-4 w-4" />} tone="bg-amber-50 text-amber-700" />
        <StatCard label="Ready for pickup" value={data.readyForPickup} hint={`${data.completedOrders} delivered`} icon={<Package className="h-4 w-4" />} tone="bg-sky-50 text-sky-700" />
        <StatCard label="Revenue (30 days)" value={inr(data.revenueLast30Days)} hint={`${data.cancelledOrders} cancelled`} icon={<IndianRupee className="h-4 w-4" />} tone="bg-emerald-50 text-emerald-700" />
      </div>

      <div className="grid gap-5 xl:grid-cols-[1.2fr_1fr]">
        <section className="card-pad">
          <div className="flex items-center justify-between">
            <h2 className="section-title">Latest orders</h2>
            <Link to="/pharmacy/orders" className="link text-xs">View all</Link>
          </div>
          {(recent?.content ?? []).length === 0 ? (
            <p className="mt-3 text-sm text-slate-500">No orders yet. Keep listings priced and in stock to start receiving orders.</p>
          ) : (
            <ul className="mt-2 divide-y divide-slate-100">
              {recent!.content.map((order) => (
                <li key={order.id} className="flex items-center justify-between gap-3 py-2.5">
                  <div>
                    <p className="text-sm font-medium">{order.orderNumber}</p>
                    <p className="text-[11px] text-slate-500">{dateTime(order.placedAt)} · {order.itemCount} item(s)</p>
                  </div>
                  <div className="flex items-center gap-2">
                    <Badge tone={statusTone(order.status)}>{statusLabel(order.status)}</Badge>
                    <span className="text-sm font-semibold">{inr(order.totalAmount)}</span>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>

        <div className="space-y-5">
          <section className="card-pad">
            <div className="flex items-center justify-between">
              <h2 className="section-title">Low stock</h2>
              <Link to="/pharmacy/inventory" className="link text-xs">Manage</Link>
            </div>
            {(lowStock ?? []).length === 0 ? (
              <p className="mt-2 text-sm text-slate-500">All listings are above their low-stock threshold.</p>
            ) : (
              <ul className="mt-2 space-y-2 text-sm">
                {lowStock!.slice(0, 5).map((item) => (
                  <li key={item.inventoryId} className="flex items-center justify-between gap-2">
                    <span className="truncate">{item.medicineName}</span>
                    <span className="text-xs font-semibold text-amber-700">{item.stockQuantity} / {item.threshold}</span>
                  </li>
                ))}
              </ul>
            )}
          </section>

          <section className="card-pad">
            <div className="flex items-center gap-2">
              <FileText className="h-4 w-4 text-brand-700" />
              <h2 className="section-title">Compliance documents</h2>
            </div>
            <ul className="mt-2 space-y-1 text-xs">
              {(documents ?? []).map((doc) => (
                <li key={doc.id} className="flex items-center justify-between gap-2">
                  <span className="truncate">{doc.documentType} · {doc.fileName}</span>
                  <Badge tone={statusTone(doc.status)}>{statusLabel(doc.status)}</Badge>
                </li>
              ))}
              {(documents ?? []).length === 0 && <li className="text-slate-500">No documents uploaded yet.</li>}
            </ul>
            <div className="mt-3 space-y-2 border-t border-slate-100 pt-3">
              <select className="input py-2 text-xs" value={documentType} onChange={(event) => setDocumentType(event.target.value)} aria-label="Document type">
                <option value="DRUG_LICENSE">Drug licence</option>
                <option value="GST_CERTIFICATE">GST certificate</option>
                <option value="PHARMACIST_CERTIFICATE">Pharmacist certificate</option>
                <option value="OWNER_ID">Owner ID proof</option>
              </select>
              <input ref={fileRef} type="file" accept=".pdf,.jpg,.jpeg,.png" className="block w-full text-xs" aria-label="Document file" />
              <button type="button" className="btn-secondary w-full py-2 text-xs" onClick={upload} disabled={uploading}>
                {uploading ? <Spinner /> : <><Upload className="h-3.5 w-3.5" /> Upload document</>}
              </button>
            </div>
          </section>
        </div>
      </div>

      <div className="grid gap-3 sm:grid-cols-3">
        <Link to="/pharmacy/orders" className="card flex items-center gap-3 p-4 hover:shadow-soft">
          <Store className="h-5 w-5 text-brand-700" />
          <span className="text-sm font-medium">Process orders</span>
        </Link>
        <Link to="/pharmacy/prescriptions" className="card flex items-center gap-3 p-4 hover:shadow-soft">
          <Stethoscope className="h-5 w-5 text-brand-700" />
          <span className="text-sm font-medium">Review prescriptions</span>
        </Link>
        <Link to="/pharmacy/analytics" className="card flex items-center gap-3 p-4 hover:shadow-soft">
          <ClipboardList className="h-5 w-5 text-brand-700" />
          <span className="text-sm font-medium">Sales analytics</span>
        </Link>
      </div>
    </div>
  );
}
