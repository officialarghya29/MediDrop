import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Bike, CheckCircle2 } from 'lucide-react';
import { Alert, FieldError, Spinner } from '../../components/ui';
import { apiPost, errorMessage } from '../../lib/api';

const initial = {
  fullName: '',
  email: '',
  phone: '',
  password: '',
  address: '',
  idProofType: 'AADHAAR',
  idProofNumber: '',
  vehicleType: 'BIKE',
  vehicleNumber: '',
  bankAccountName: '',
  bankAccountLast4: '',
  payoutIfsc: '',
};

export default function DeliveryRegisterPage() {
  const [form, setForm] = useState(initial);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);

  const set = (key: keyof typeof initial, value: string) => setForm((prev) => ({ ...prev, [key]: value }));

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await apiPost('/delivery/register', form);
      setDone(true);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  if (done) {
    return (
      <div className="container-page max-w-xl py-12">
        <div className="card-pad text-center">
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-mint text-brand-700">
            <CheckCircle2 className="h-6 w-6" />
          </div>
          <h1 className="mt-4 text-xl font-bold">Application received</h1>
          <p className="mt-2 text-sm text-slate-600">
            Our operations team verifies your ID proof before activation. You will be able to sign in and go online
            once approved.
          </p>
          <Link to="/login" className="btn-primary mt-5">Go to sign in</Link>
        </div>
      </div>
    );
  }

  return (
    <div className="container-page max-w-3xl space-y-5 py-6 sm:py-8">
      <div className="flex items-center gap-3">
        <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-mint text-brand-700">
          <Bike className="h-5 w-5" />
        </div>
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Join as a delivery partner</h1>
          <p className="muted">Earn per delivery within your city. ID verification required.</p>
        </div>
      </div>

      <form onSubmit={submit} className="card-pad space-y-4" noValidate>
        {error && <Alert tone="error">{error}</Alert>}
        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <label className="label" htmlFor="dp-name">Full name</label>
            <input id="dp-name" className="input" value={form.fullName} onChange={(e) => set('fullName', e.target.value)} required />
          </div>
          <div>
            <label className="label" htmlFor="dp-email">Email</label>
            <input id="dp-email" type="email" className="input" value={form.email} onChange={(e) => set('email', e.target.value)} required />
          </div>
          <div>
            <label className="label" htmlFor="dp-phone">Mobile number</label>
            <input id="dp-phone" className="input" inputMode="numeric" maxLength={10} value={form.phone} onChange={(e) => set('phone', e.target.value.replace(/\D/g, ''))} required />
          </div>
          <div>
            <label className="label" htmlFor="dp-password">Password</label>
            <input id="dp-password" type="password" className="input" value={form.password} onChange={(e) => set('password', e.target.value)} required />
            <FieldError message={form.password.length > 0 && form.password.length < 8 ? 'At least 8 characters' : undefined} />
          </div>
        </div>

        <div>
          <label className="label" htmlFor="dp-address">Residential address</label>
          <input id="dp-address" className="input" value={form.address} onChange={(e) => set('address', e.target.value)} required />
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <label className="label" htmlFor="dp-id-type">ID proof type</label>
            <select id="dp-id-type" className="input" value={form.idProofType} onChange={(e) => set('idProofType', e.target.value)}>
              <option value="AADHAAR">Aadhaar</option>
              <option value="PAN">PAN</option>
              <option value="DRIVING_LICENSE">Driving licence</option>
            </select>
          </div>
          <div>
            <label className="label" htmlFor="dp-id-number">ID proof number</label>
            <input id="dp-id-number" className="input" value={form.idProofNumber} onChange={(e) => set('idProofNumber', e.target.value)} required />
          </div>
          <div>
            <label className="label" htmlFor="dp-vehicle">Vehicle type</label>
            <select id="dp-vehicle" className="input" value={form.vehicleType} onChange={(e) => set('vehicleType', e.target.value)}>
              <option value="BIKE">Bike</option>
              <option value="SCOOTER">Scooter</option>
              <option value="BICYCLE">Bicycle</option>
              <option value="EV">Electric vehicle</option>
            </select>
          </div>
          <div>
            <label className="label" htmlFor="dp-vehicle-number">Vehicle number (optional)</label>
            <input id="dp-vehicle-number" className="input" value={form.vehicleNumber} onChange={(e) => set('vehicleNumber', e.target.value.toUpperCase())} />
          </div>
        </div>

        <fieldset className="rounded-xl border border-slate-200 p-4">
          <legend className="px-1 text-xs font-semibold uppercase tracking-wide text-slate-500">Payout details (optional)</legend>
          <div className="grid gap-3 sm:grid-cols-3">
            <div>
              <label className="label" htmlFor="dp-bank-name">Account holder</label>
              <input id="dp-bank-name" className="input" value={form.bankAccountName} onChange={(e) => set('bankAccountName', e.target.value)} />
            </div>
            <div>
              <label className="label" htmlFor="dp-bank-last4">Account last 4 digits</label>
              <input id="dp-bank-last4" className="input" inputMode="numeric" maxLength={4} value={form.bankAccountLast4} onChange={(e) => set('bankAccountLast4', e.target.value.replace(/\D/g, ''))} />
            </div>
            <div>
              <label className="label" htmlFor="dp-ifsc">IFSC</label>
              <input id="dp-ifsc" className="input" maxLength={20} value={form.payoutIfsc} onChange={(e) => set('payoutIfsc', e.target.value.toUpperCase())} />
            </div>
          </div>
          <p className="mt-2 text-[11px] text-slate-500">Only the last four digits are stored. Payouts are reconciled by operations.</p>
        </fieldset>

        <div className="flex flex-wrap gap-2">
          <button type="submit" className="btn-primary" disabled={busy}>{busy ? <Spinner /> : 'Submit application'}</button>
          <Link to="/login" className="btn-secondary">Already a partner? Sign in</Link>
        </div>
      </form>
    </div>
  );
}
