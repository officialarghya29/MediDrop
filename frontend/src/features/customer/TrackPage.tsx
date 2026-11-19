import { useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Clock, MapPin, Phone, ShieldCheck } from 'lucide-react';
import MapView from '../../components/MapView';
import { Alert, Badge, EmptyState, PageLoader } from '../../components/ui';
import { apiGet } from '../../lib/api';
import { dateTime, statusLabel, statusTone } from '../../lib/format';

interface TrackingView {
  orderNumber: string;
  status: string;
  trackingKey: string;
  updatedAt: string;
  partner?: { name: string; phone?: string; vehicleType?: string } | null;
  partnerLatitude?: number;
  partnerLongitude?: number;
  partnerUpdatedAt?: string;
  etaMinutes?: number;
  destinationLabel: string;
  destinationLatitude: number;
  destinationLongitude: number;
  pharmacyLatitude?: number;
  pharmacyLongitude?: number;
}

export default function TrackPage() {
  const { key } = useParams();
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['tracking', key],
    queryFn: () => apiGet<TrackingView>(`/tracking/${key}`),
    refetchInterval: 20_000,
    retry: 1,
  });

  if (isLoading) return <PageLoader label="Looking up your delivery…" />;

  if (isError || !data) {
    return (
      <div className="container-page py-12">
        <EmptyState
          title="Tracking link not found"
          description={
            error instanceof Error
              ? error.message
              : 'This link may have expired. Sign in and open the order to see its live status.'
          }
        />
      </div>
    );
  }

  const center = { lat: data.destinationLatitude, lng: data.destinationLongitude };
  const markers = [
    { lat: data.destinationLatitude, lng: data.destinationLongitude, label: 'Delivery address', tone: 'brand' as const },
    ...(data.pharmacyLatitude && data.pharmacyLongitude
      ? [{ lat: data.pharmacyLatitude, lng: data.pharmacyLongitude, label: 'Pharmacy', tone: 'navy' as const }]
      : []),
    ...(data.partnerLatitude && data.partnerLongitude
      ? [{ lat: data.partnerLatitude, lng: data.partnerLongitude, label: 'Delivery partner', tone: 'amber' as const }]
      : []),
  ];

  return (
    <div className="container-page max-w-3xl space-y-4 py-6 sm:py-8">
      <div className="card-pad">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <p className="font-mono text-xs font-semibold text-slate-500">{data.orderNumber}</p>
            <h1 className="mt-1 text-xl font-bold">Delivery status</h1>
          </div>
          <Badge tone={statusTone(data.status)}>{statusLabel(data.status)}</Badge>
        </div>

        <div className="mt-4 grid gap-2 text-sm sm:grid-cols-2">
          <p className="flex items-center gap-2 text-slate-600">
            <MapPin className="h-4 w-4 text-brand-700" /> {data.destinationLabel}
          </p>
          {data.etaMinutes !== undefined && (
            <p className="flex items-center gap-2 text-slate-600">
              <Clock className="h-4 w-4 text-brand-700" /> ETA ~{data.etaMinutes} min
            </p>
          )}
        </div>
        <p className="mt-2 text-[11px] text-slate-400">Last update {dateTime(data.updatedAt)}</p>
      </div>

      {data.partner && (
        <div className="card-pad">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <p className="text-xs uppercase tracking-wide text-slate-500">Delivery partner</p>
              <p className="mt-1 font-semibold">{data.partner.name}</p>
              <p className="text-xs text-slate-500">{data.partner.vehicleType ?? 'Two-wheeler'}</p>
            </div>
            {data.partner.phone && (
              <a href={`tel:${data.partner.phone}`} className="btn-secondary py-2 text-xs">
                <Phone className="h-3.5 w-3.5" /> Call
              </a>
            )}
          </div>
          {data.partnerUpdatedAt && (
            <p className="mt-2 text-[11px] text-slate-400">Rider position updated {dateTime(data.partnerUpdatedAt)}</p>
          )}
        </div>
      )}

      <MapView center={center} markers={markers} height={300} caption="Rider positions are approximate and refresh automatically." />

      <Alert tone="info" title="Privacy">
        This page only shows the delivery status for your tracking link. It cannot be used to access your account,
        prescription or payment details.
      </Alert>

      <p className="flex items-center gap-1.5 text-xs text-slate-500">
        <ShieldCheck className="h-3.5 w-3.5" /> For sensitive medical queries, contact your pharmacy directly.
      </p>
    </div>
  );
}
