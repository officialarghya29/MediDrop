import { useState } from 'react';
import { Link } from 'react-router-dom';
import { CheckCircle2, Store } from 'lucide-react';
import { Alert, FieldError, Spinner } from '../../components/ui';
import { apiPost, errorMessage } from '../../lib/api';

const initial = {
  pharmacyName: '',
  ownerName: '',
  email: '',
  phone: '',
  password: '',
  licenseNumber: '',
  gstin: '',
  registeredAddress: '',
  city: 'Kolkata',
  state: 'West Bengal',
  pincode: '',
  latitude: '22.5726',
  longitude: '88.3639',
  deliveryRadiusKm: '5',
};

export default function PharmacyRegisterPage() {
  const [form, setForm] = useState(initial);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);

  const set = (key: keyof typeof initial, value: string) => setForm((prev) => ({ ...prev, [key]: value }));

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await apiPost('/pharmacies/register', {
        ...form,
        latitude: Number(form.latitude),
        longitude: Number(form.longitude),
        deliveryRadiusKm: Number(form.deliveryRadiusKm),
        gstin: form.gstin || undefined,
      });
      setDone(true);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setSubmitting(false);
    }
  };

  if (done) {
    return (
      <div className="container-page max-w-xl py-12">
        <div className="card-pad text-center">
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-mint text-brand-700">
            <CheckCircle2 className="h-6 w-6" />
          </div>
          <h1 className="mt-4 text-xl font-bold">Application submitted</h1>
          <p className="mt-2 text-sm text-slate-600">
            Our compliance team verifies your drug licence and GSTIN before activation. You will receive an email
            once the account is approved, then you can sign in and upload your licence documents.
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
          <Store className="h-5 w-5" />
        </div>
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Register your pharmacy</h1>
          <p className="muted">Licensed Indian pharmacies only. Approval usually takes 1–2 working days.</p>
        </div>
      </div>

      <form onSubmit={submit} className="card-pad space-y-4" noValidate>
        {error && <Alert tone="error">{error}</Alert>}
        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <label className="label" htmlFor="ph-name">Pharmacy name</label>
            <input id="ph-name" className="input" value={form.pharmacyName} onChange={(e) => set('pharmacyName', e.target.value)} required />
          </div>
          <div>
            <label className="label" htmlFor="ph-owner">Owner / pharmacist name</label>
            <input id="ph-owner" className="input" value={form.ownerName} onChange={(e) => set('ownerName', e.target.value)} required />
          </div>
          <div>
            <label className="label" htmlFor="ph-email">Contact email</label>
            <input id="ph-email" type="email" className="input" value={form.email} onChange={(e) => set('email', e.target.value)} required />
          </div>
          <div>
            <label className="label" htmlFor="ph-phone">Mobile number</label>
            <input id="ph-phone" className="input" inputMode="numeric" maxLength={10} value={form.phone} onChange={(e) => set('phone', e.target.value.replace(/\D/g, ''))} required />
          </div>
          <div>
            <label className="label" htmlFor="ph-password">Password</label>
            <input id="ph-password" type="password" className="input" value={form.password} onChange={(e) => set('password', e.target.value)} required />
            <FieldError message={form.password.length > 0 && form.password.length < 8 ? 'At least 8 characters' : undefined} />
          </div>
          <div>
            <label className="label" htmlFor="ph-license">Drug licence number</label>
            <input id="ph-license" className="input" value={form.licenseNumber} onChange={(e) => set('licenseNumber', e.target.value)} required />
          </div>
          <div>
            <label className="label" htmlFor="ph-gstin">GSTIN (optional)</label>
            <input id="ph-gstin" className="input" value={form.gstin} onChange={(e) => set('gstin', e.target.value)} />
          </div>
        </div>

        <div>
          <label className="label" htmlFor="ph-address">Registered address</label>
          <input id="ph-address" className="input" value={form.registeredAddress} onChange={(e) => set('registeredAddress', e.target.value)} required />
        </div>

        <div className="grid gap-4 sm:grid-cols-3">
          <div>
            <label className="label" htmlFor="ph-city">City</label>
            <input id="ph-city" className="input" value={form.city} onChange={(e) => set('city', e.target.value)} required />
          </div>
          <div>
            <label className="label" htmlFor="ph-state">State</label>
            <input id="ph-state" className="input" value={form.state} onChange={(e) => set('state', e.target.value)} required />
          </div>
          <div>
            <label className="label" htmlFor="ph-pincode">PIN code</label>
            <input id="ph-pincode" className="input" inputMode="numeric" maxLength={6} value={form.pincode} onChange={(e) => set('pincode', e.target.value.replace(/\D/g, ''))} required />
          </div>
        </div>

        <div className="grid gap-4 sm:grid-cols-3">
          <div>
            <label className="label" htmlFor="ph-lat">Latitude</label>
            <input id="ph-lat" className="input" value={form.latitude} onChange={(e) => set('latitude', e.target.value)} required />
          </div>
          <div>
            <label className="label" htmlFor="ph-lng">Longitude</label>
            <input id="ph-lng" className="input" value={form.longitude} onChange={(e) => set('longitude', e.target.value)} required />
          </div>
          <div>
            <label className="label" htmlFor="ph-radius">Delivery radius (km)</label>
            <input id="ph-radius" className="input" type="number" min={1} max={50} value={form.deliveryRadiusKm} onChange={(e) => set('deliveryRadiusKm', e.target.value)} required />
          </div>
        </div>

        <Alert tone="info">
          Coordinates determine your delivery catchment. You can refine them after approval from the pharmacy console.
        </Alert>

        <div className="flex flex-wrap gap-2">
          <button type="submit" className="btn-primary" disabled={submitting}>{submitting ? <Spinner /> : 'Submit application'}</button>
          <Link to="/login" className="btn-secondary">Already registered? Sign in</Link>
        </div>
      </form>
    </div>
  );
}
