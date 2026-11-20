import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Bike, CheckCircle2, MapPin, Navigation, PackageCheck, Power, Wallet } from 'lucide-react';
import { Alert, Badge, EmptyState, FieldError, Modal, PageLoader, Spinner, StatCard, useToast } from '../../components/ui';
import { apiGet, apiPatch, apiPost, errorMessage } from '../../lib/api';
import { dateTime, inr, statusLabel, statusTone } from '../../lib/format';
import { useGeolocation } from '../../hooks/useGeolocation';
import type { DeliveryAssignment, DeliveryDashboard } from '../../types';

const FLOW: Record<string, { next: string; label: string }[]> = {
  ACCEPTED: [{ next: 'AT_PHARMACY', label: 'Reached pharmacy' }],
  AT_PHARMACY: [{ next: 'PICKED_UP', label: 'Picked up order' }],
  PICKED_UP: [{ next: 'OUT_FOR_DELIVERY', label: 'Start delivery' }],
};

export default function DeliveryDashboardPage() {
  const toast = useToast();
  const queryClient = useQueryClient();
  const geo = useGeolocation();
  const [otpTarget, setOtpTarget] = useState<DeliveryAssignment | null>(null);
  const [otp, setOtp] = useState('');
  const [proof, setProof] = useState(false);
  const [otpError, setOtpError] = useState<string | null>(null);

  const dashboard = useQuery({
    queryKey: ['delivery-dashboard'],
    queryFn: () => apiGet<DeliveryDashboard>('/delivery/dashboard'),
    refetchInterval: 30_000,
  });

  const offers = useQuery({
    queryKey: ['delivery-assignments', 'ASSIGNED'],
    queryFn: () => apiGet<DeliveryAssignment[]>('/delivery/assignments', { status: 'ASSIGNED' }),
    refetchInterval: 20_000,
  });

  const availability = useMutation({
    mutationFn: (online: boolean) =>
      apiPost('/delivery/availability', {
        online,
        latitude: geo.coords?.latitude,
        longitude: geo.coords?.longitude,
      }),
    onSuccess: () => {
      toast('Availability updated', 'success');
      queryClient.invalidateQueries({ queryKey: ['delivery-dashboard'] });
    },
    onError: (error) => toast(errorMessage(error), 'error'),
  });

  const respond = useMutation({
    mutationFn: ({ id, accept }: { id: number; accept: boolean }) =>
      apiPost(`/delivery/assignments/${id}/${accept ? 'accept' : 'reject'}`, accept ? undefined : { note: 'Declined by partner' }),
    onSuccess: () => {
      toast('Assignment updated', 'success');
      queryClient.invalidateQueries({ queryKey: ['delivery-dashboard'] });
      queryClient.invalidateQueries({ queryKey: ['delivery-assignments'] });
    },
    onError: (error) => toast(errorMessage(error), 'error'),
  });

  const progress = useMutation({
    mutationFn: ({ id, status }: { id: number; status: string }) =>
      apiPatch(`/delivery/assignments/${id}/status`, { status }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['delivery-dashboard'] });
      queryClient.invalidateQueries({ queryKey: ['delivery-assignments'] });
    },
    onError: (error) => toast(errorMessage(error), 'error'),
  });

  const complete = useMutation({
    mutationFn: () => apiPost(`/delivery/assignments/${otpTarget?.id}/complete`, { otp, proofSubmitted: proof }),
    onSuccess: () => {
      toast('Delivery completed', 'success');
      setOtpTarget(null);
      setOtp('');
      setProof(false);
      setOtpError(null);
      queryClient.invalidateQueries({ queryKey: ['delivery-dashboard'] });
      queryClient.invalidateQueries({ queryKey: ['delivery-assignments'] });
    },
    onError: (error) => setOtpError(errorMessage(error)),
  });

  const ping = async () => {
    if (!geo.coords) {
      geo.request();
      toast('Allow location access, then tap again to share your live position', 'info');
      return;
    }
    try {
      await apiPost('/delivery/location', { latitude: geo.coords.latitude, longitude: geo.coords.longitude });
      toast('Live location shared', 'success');
    } catch (error) {
      toast(errorMessage(error), 'error');
    }
  };

  if (dashboard.isLoading) return <PageLoader label="Loading your runs…" />;
  if (dashboard.isError || !dashboard.data) return <Alert tone="error">{errorMessage(dashboard.error)}</Alert>;

  const data = dashboard.data;
  const active = data.active ?? [];
  const waiting = data.profile.status !== 'APPROVED';

  return (
    <div className="space-y-5 px-4 py-6 sm:px-6 lg:px-8">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Namaste, {data.profile.fullName.split(' ')[0]}</h1>
          <p className="muted mt-1">
            {data.profile.completedDeliveries} deliveries · {data.profile.rating.toFixed(1)} ★
          </p>
        </div>
        <div className="flex gap-2">
          <button type="button" className="btn-secondary" onClick={ping}>
            <Navigation className="h-4 w-4" /> Share location
          </button>
          <button
            type="button"
            className={data.online ? 'btn-secondary' : 'btn-primary'}
            onClick={() => {
              if (!data.online && !geo.coords) geo.request();
              availability.mutate(!data.online);
            }}
            disabled={availability.isPending || waiting}
          >
            <Power className="h-4 w-4" /> {data.online ? 'Go offline' : 'Go online'}
          </button>
        </div>
      </div>

      {waiting && (
        <Alert tone="warning" title="Approval pending">
          Your documents are being verified by the operations team. You cannot accept deliveries until approved.
        </Alert>
      )}
      {data.online && !geo.coords && (
        <Alert tone="info">
          Location permission is off. Allow it so the platform can assign nearby orders and customers can see live
          tracking.
        </Alert>
      )}
      {geo.error && <Alert tone="warning">{geo.error}</Alert>}

      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard label="Open offers" value={data.pendingAssignments} hint="Accept before they expire" icon={<PackageCheck className="h-4 w-4" />} />
        <StatCard label="Active runs" value={data.activeDeliveries} icon={<Bike className="h-4 w-4" />} tone="bg-sky-50 text-sky-700" />
        <StatCard label="Earned today" value={inr(data.earningsToday)} icon={<Wallet className="h-4 w-4" />} tone="bg-emerald-50 text-emerald-700" />
        <StatCard label="Lifetime earnings" value={inr(data.earningsTotal)} />
      </div>

      <section className="card-pad">
        <div className="flex items-center justify-between">
          <h2 className="section-title">New offers</h2>
          <span className="text-xs text-slate-500">{offers.data?.length ?? 0} nearby</span>
        </div>
        {offers.isLoading && <Spinner label="Checking offers…" />}
        {(offers.data ?? []).length === 0 ? (
          <p className="mt-2 text-sm text-slate-500">No offers right now. Stay online to receive the next one.</p>
        ) : (
          <ul className="mt-3 divide-y divide-slate-100">
            {offers.data!.map((offer) => (
              <li key={offer.id} className="flex flex-wrap items-center justify-between gap-3 py-3">
                <div>
                  <p className="text-sm font-semibold">{offer.orderNumber} · {offer.pharmacyName}</p>
                  <p className="text-xs text-slate-500">
                    {offer.customerArea} · {offer.distanceKm ?? '—'} km · ~{offer.estimatedMinutes ?? '—'} min · {inr(offer.payout)}
                  </p>
                </div>
                <div className="flex gap-2">
                  <button type="button" className="btn-secondary py-1.5 text-xs" onClick={() => respond.mutate({ id: offer.id, accept: false })}>Decline</button>
                  <button type="button" className="btn-primary py-1.5 text-xs" onClick={() => respond.mutate({ id: offer.id, accept: true })}>Accept</button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="space-y-3">
        <h2 className="section-title">Active deliveries</h2>
        {active.length === 0 ? (
          <EmptyState icon={<Bike className="h-5 w-5" />} title="No active run" description="Accepted deliveries appear here with pickup and drop details." />
        ) : (
          active.map((assignment) => (
            <div key={assignment.id} className="card-pad">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <div className="flex items-center gap-2">
                    <p className="font-semibold">{assignment.orderNumber}</p>
                    <Badge tone={statusTone(assignment.status)}>{statusLabel(assignment.status)}</Badge>
                  </div>
                  <p className="mt-1 text-xs text-slate-600"><strong>Pickup:</strong> {assignment.pharmacyName} · {assignment.pharmacyAddress}</p>
                  <p className="text-xs text-slate-600"><strong>Drop:</strong> {assignment.customerArea}</p>
                  <p className="mt-1 text-xs text-slate-500">
                    {assignment.distanceKm ?? '—'} km · {inr(assignment.payout)} payout · assigned {dateTime(assignment.assignedAt)}
                  </p>
                </div>
                <a
                  href={
                    assignment.destinationLatitude && assignment.destinationLongitude
                      ? `https://www.google.com/maps/dir/?api=1&destination=${assignment.destinationLatitude},${assignment.destinationLongitude}`
                      : undefined
                  }
                  target="_blank"
                  rel="noreferrer"
                  className="btn-secondary py-2 text-xs"
                >
                  <MapPin className="h-3.5 w-3.5" /> Directions
                </a>
              </div>

              <div className="mt-3 flex flex-wrap gap-2">
                {(FLOW[assignment.status] ?? []).map((step) => (
                  <button
                    key={step.next}
                    type="button"
                    className="btn-primary py-2 text-xs"
                    disabled={progress.isPending}
                    onClick={() => progress.mutate({ id: assignment.id, status: step.next })}
                  >
                    {progress.isPending ? <Spinner /> : step.label}
                  </button>
                ))}
                {assignment.status === 'OUT_FOR_DELIVERY' && (
                  <button type="button" className="btn-primary py-2 text-xs" onClick={() => { setOtpTarget(assignment); setOtp(''); setOtpError(null); }}>
                    <CheckCircle2 className="h-3.5 w-3.5" /> Complete with OTP
                  </button>
                )}
                {assignment.status === 'ASSIGNED' && (
                  <button type="button" className="btn-primary py-2 text-xs" onClick={() => respond.mutate({ id: assignment.id, accept: true })}>Accept run</button>
                )}
              </div>
            </div>
          ))
        )}
      </section>

      <Modal
        open={!!otpTarget}
        onClose={() => setOtpTarget(null)}
        title="Confirm handover"
        footer={
          <>
            <button type="button" className="btn-secondary" onClick={() => setOtpTarget(null)}>Cancel</button>
            <button type="button" className="btn-primary" disabled={complete.isPending || otp.length < 4} onClick={() => complete.mutate()}>
              {complete.isPending ? <Spinner /> : 'Complete delivery'}
            </button>
          </>
        }
      >
        <div className="space-y-3">
          {otpError && <Alert tone="error">{otpError}</Alert>}
          <p className="text-sm text-slate-600">
            Ask the customer for the 4-digit handover OTP shown in their app. Do not mark a delivery complete without it.
          </p>
          <div>
            <label className="label" htmlFor="delivery-otp">Handover OTP</label>
            <input
              id="delivery-otp"
              className="input tracking-[0.4em]"
              inputMode="numeric"
              maxLength={4}
              value={otp}
              onChange={(event) => setOtp(event.target.value.replace(/\D/g, ''))}
            />
            <FieldError message={otp.length > 0 && otp.length < 4 ? 'Enter the full OTP' : undefined} />
          </div>
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={proof} onChange={(event) => setProof(event.target.checked)} />
            Photo proof captured (optional)
          </label>
        </div>
      </Modal>
    </div>
  );
}
