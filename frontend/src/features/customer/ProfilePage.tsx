import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { AlertTriangle, LifeBuoy, MapPin, Plus, Trash2, UserRound } from 'lucide-react';
import { Alert, Badge, FieldError, Modal, PageLoader, Spinner, useToast } from '../../components/ui';
import { apiDelete, apiGet, apiPost, apiPut, errorMessage } from '../../lib/api';
import { dateOnly, dateTime, statusLabel, statusTone } from '../../lib/format';
import { useAuth } from '../../store/auth';
import type { Address, SupportTicket } from '../../types';

interface Profile {
  id: number;
  fullName: string;
  email: string;
  phone?: string;
  role: string;
  emailVerified: boolean;
  phoneVerified: boolean;
  avatarUrl?: string;
  createdAt: string;
  deletionRequestedAt?: string;
}

const emptyAddress = {
  label: 'Home',
  recipientName: '',
  phone: '',
  line1: '',
  line2: '',
  city: 'Kolkata',
  state: 'West Bengal',
  pincode: '',
  latitude: 22.5726,
  longitude: 88.3639,
  defaultAddress: false,
};

export default function ProfilePage() {
  const toast = useToast();
  const queryClient = useQueryClient();
  const { user } = useAuth();
  const [fullName, setFullName] = useState('');
  const [phone, setPhone] = useState('');
  const [profileError, setProfileError] = useState<string | null>(null);
  const [addressOpen, setAddressOpen] = useState(false);
  const [editingAddress, setEditingAddress] = useState<Address | null>(null);
  const [addressForm, setAddressForm] = useState({ ...emptyAddress });
  const [addressError, setAddressError] = useState<string | null>(null);
  const [ticketSubject, setTicketSubject] = useState('');
  const [ticketMessage, setTicketMessage] = useState('');
  const [ticketOrderId, setTicketOrderId] = useState('');
  const [ticketError, setTicketError] = useState<string | null>(null);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deleteReason, setDeleteReason] = useState('');

  const { data: profile, isLoading } = useQuery({
    queryKey: ['profile'],
    queryFn: () => apiGet<Profile>('/customer/me'),
  });

  const { data: addresses } = useQuery({
    queryKey: ['addresses'],
    queryFn: () => apiGet<Address[]>('/customer/addresses'),
  });

  const { data: tickets } = useQuery({
    queryKey: ['support-tickets'],
    queryFn: () => apiGet<SupportTicket[]>('/support'),
  });

  useEffect(() => {
    if (profile) {
      setFullName(profile.fullName);
      setPhone(profile.phone ?? '');
    }
  }, [profile]);

  const saveProfile = useMutation({
    mutationFn: () => apiPut<Profile>('/customer/me', { fullName, phone: phone || undefined }),
    onSuccess: () => {
      toast('Profile updated', 'success');
      setProfileError(null);
      queryClient.invalidateQueries({ queryKey: ['profile'] });
    },
    onError: (error) => setProfileError(errorMessage(error)),
  });

  const saveAddress = useMutation({
    mutationFn: () =>
      editingAddress
        ? apiPut<Address>(`/customer/addresses/${editingAddress.id}`, addressForm)
        : apiPost<Address>('/customer/addresses', addressForm),
    onSuccess: () => {
      toast(editingAddress ? 'Address updated' : 'Address added', 'success');
      setAddressOpen(false);
      setEditingAddress(null);
      setAddressForm({ ...emptyAddress });
      queryClient.invalidateQueries({ queryKey: ['addresses'] });
    },
    onError: (error) => setAddressError(errorMessage(error)),
  });

  const removeAddress = useMutation({
    mutationFn: (id: number) => apiDelete(`/customer/addresses/${id}`),
    onSuccess: () => {
      toast('Address removed', 'info');
      queryClient.invalidateQueries({ queryKey: ['addresses'] });
    },
    onError: (error) => toast(errorMessage(error), 'error'),
  });

  const createTicket = useMutation({
    mutationFn: () =>
      apiPost<SupportTicket>('/support', {
        orderId: ticketOrderId ? Number(ticketOrderId) : null,
        subject: ticketSubject,
        message: ticketMessage,
      }),
    onSuccess: () => {
      toast('Support request submitted', 'success');
      setTicketSubject('');
      setTicketMessage('');
      setTicketOrderId('');
      setTicketError(null);
      queryClient.invalidateQueries({ queryKey: ['support-tickets'] });
    },
    onError: (error) => setTicketError(errorMessage(error)),
  });

  const requestDeletion = useMutation({
    mutationFn: () => apiPost('/customer/me/deletion-request', { reason: deleteReason || undefined }),
    onSuccess: () => {
      toast('Deletion request recorded. Our team will contact you before erasing data.', 'info');
      setDeleteOpen(false);
      queryClient.invalidateQueries({ queryKey: ['profile'] });
    },
    onError: (error) => toast(errorMessage(error), 'error'),
  });

  if (isLoading) return <PageLoader label="Loading profile…" />;

  return (
    <div className="container-page space-y-6 py-6 sm:py-8">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Your profile</h1>
        <p className="muted mt-1">Manage your details, saved addresses and support requests.</p>
      </div>

      <div className="grid gap-5 lg:grid-cols-[1fr_22rem]">
        <div className="space-y-5">
          <section className="card-pad">
            <div className="flex items-center gap-2">
              <UserRound className="h-4 w-4 text-brand-700" />
              <h2 className="section-title">Account details</h2>
            </div>
            <div className="mt-4 grid gap-4 sm:grid-cols-2">
              <div>
                <label className="label" htmlFor="profile-name">Full name</label>
                <input id="profile-name" className="input" value={fullName} onChange={(event) => setFullName(event.target.value)} />
              </div>
              <div>
                <label className="label" htmlFor="profile-phone">Mobile number</label>
                <input id="profile-phone" className="input" inputMode="numeric" maxLength={10} value={phone} onChange={(event) => setPhone(event.target.value.replace(/\D/g, ''))} />
              </div>
              <div>
                <label className="label">Email</label>
                <p className="flex items-center gap-2 text-sm">
                  {profile?.email}
                  <Badge tone={profile?.emailVerified ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-800'}>
                    {profile?.emailVerified ? 'Verified' : 'Unverified'}
                  </Badge>
                </p>
              </div>
              <div>
                <label className="label">Member since</label>
                <p className="text-sm text-slate-600">{dateOnly(profile?.createdAt)}</p>
              </div>
            </div>
            {profileError && <Alert tone="error">{profileError}</Alert>}
            <button type="button" className="btn-primary mt-4" onClick={() => saveProfile.mutate()} disabled={saveProfile.isPending || fullName.trim().length < 2}>
              {saveProfile.isPending ? <Spinner /> : 'Save changes'}
            </button>
            {profile?.deletionRequestedAt && (
              <Alert tone="warning" title="Deletion requested">
                Requested on {dateTime(profile.deletionRequestedAt)}. Support will verify your identity before removing
                records that must be retained for pharmacy compliance.
              </Alert>
            )}
          </section>

          <section className="card-pad">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <MapPin className="h-4 w-4 text-brand-700" />
                <h2 className="section-title">Saved addresses</h2>
              </div>
              <button
                type="button"
                className="btn-ghost py-1.5 text-xs"
                onClick={() => {
                  setEditingAddress(null);
                  setAddressForm({ ...emptyAddress, recipientName: profile?.fullName ?? '', phone: profile?.phone ?? '' });
                  setAddressError(null);
                  setAddressOpen(true);
                }}
              >
                <Plus className="h-3.5 w-3.5" /> Add address
              </button>
            </div>
            {(addresses ?? []).length === 0 ? (
              <p className="mt-3 text-sm text-slate-500">No saved addresses yet.</p>
            ) : (
              <ul className="mt-3 divide-y divide-slate-100">
                {addresses!.map((address) => (
                  <li key={address.id} className="flex flex-wrap items-start justify-between gap-3 py-3">
                    <div>
                      <p className="text-sm font-semibold">
                        {address.label}
                        {address.defaultAddress && <Badge className="ml-2" tone="bg-mint text-brand-800">Default</Badge>}
                      </p>
                      <p className="text-xs text-slate-600">{address.recipientName} · {address.phone}</p>
                      <p className="text-xs text-slate-500">
                        {address.line1}{address.line2 ? `, ${address.line2}` : ''}, {address.city}, {address.state} {address.pincode}
                      </p>
                    </div>
                    <div className="flex gap-1">
                      <button
                        type="button"
                        className="btn-ghost py-1.5 text-xs"
                        onClick={() => {
                          setEditingAddress(address);
                          setAddressForm({ ...address, line2: address.line2 ?? '' });
                          setAddressError(null);
                          setAddressOpen(true);
                        }}
                      >
                        Edit
                      </button>
                      <button
                        type="button"
                        className="btn-ghost py-1.5 text-xs text-rose-700 hover:bg-rose-50"
                        onClick={() => removeAddress.mutate(address.id)}
                        aria-label={`Remove ${address.label}`}
                      >
                        <Trash2 className="h-3.5 w-3.5" />
                      </button>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </section>

          <section id="support" className="card-pad scroll-mt-24">
            <div className="flex items-center gap-2">
              <LifeBuoy className="h-4 w-4 text-brand-700" />
              <h2 className="section-title">Support</h2>
            </div>
            <form
              className="mt-3 space-y-3"
              onSubmit={(event) => {
                event.preventDefault();
                createTicket.mutate();
              }}
            >
              {ticketError && <Alert tone="error">{ticketError}</Alert>}
              <div className="grid gap-3 sm:grid-cols-2">
                <div>
                  <label className="label" htmlFor="ticket-subject">Subject</label>
                  <input id="ticket-subject" className="input" maxLength={160} value={ticketSubject} onChange={(event) => setTicketSubject(event.target.value)} />
                </div>
                <div>
                  <label className="label" htmlFor="ticket-order">Order ID (optional)</label>
                  <input id="ticket-order" className="input" inputMode="numeric" value={ticketOrderId} onChange={(event) => setTicketOrderId(event.target.value.replace(/\D/g, ''))} />
                </div>
              </div>
              <div>
                <label className="label" htmlFor="ticket-message">Message</label>
                <textarea id="ticket-message" className="input resize-none" rows={3} minLength={10} maxLength={2000} value={ticketMessage} onChange={(event) => setTicketMessage(event.target.value)} />
                <FieldError message={ticketMessage.length > 0 && ticketMessage.length < 10 ? 'Add at least 10 characters' : undefined} />
              </div>
              <button type="submit" className="btn-secondary" disabled={createTicket.isPending || !ticketSubject || ticketMessage.length < 10}>
                {createTicket.isPending ? <Spinner /> : 'Submit request'}
              </button>
            </form>

            {(tickets ?? []).length > 0 && (
              <ul className="mt-4 space-y-2 border-t border-slate-100 pt-3">
                {tickets!.map((ticket) => (
                  <li key={ticket.id} className="rounded-xl bg-slate-50 p-3">
                    <div className="flex items-center justify-between gap-2">
                      <p className="text-sm font-medium">{ticket.subject}</p>
                      <Badge tone={statusTone(ticket.status)}>{statusLabel(ticket.status)}</Badge>
                    </div>
                    <p className="mt-1 text-xs text-slate-600">{ticket.message}</p>
                    {ticket.response && <p className="mt-2 rounded-lg bg-white p-2 text-xs text-slate-700"><strong>Support:</strong> {ticket.response}</p>}
                    <p className="mt-1 text-[11px] text-slate-500">{dateTime(ticket.createdAt)}</p>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>

        <aside className="h-fit space-y-3">
          <div className="card-pad">
            <h2 className="text-sm font-semibold">Account</h2>
            <p className="mt-1 text-xs text-slate-500">Signed in as {user?.email}</p>
            <button type="button" className="btn-ghost mt-3 w-full py-2 text-xs text-rose-700 hover:bg-rose-50" onClick={() => setDeleteOpen(true)}>
              <AlertTriangle className="h-3.5 w-3.5" /> Request account deletion
            </button>
          </div>
          <div className="rounded-2xl bg-white p-4 text-xs leading-relaxed text-slate-600 ring-1 ring-slate-200">
            <p className="font-semibold text-navy-900">Your data</p>
            <p className="mt-1">
              Prescriptions and order records are retained as required by Indian pharmacy regulations, even after a
              deletion request. Everything else is erased once identity is verified.
            </p>
          </div>
        </aside>
      </div>

      <Modal
        open={addressOpen}
        onClose={() => setAddressOpen(false)}
        title={editingAddress ? 'Edit address' : 'Add address'}
        footer={
          <>
            <button type="button" className="btn-secondary" onClick={() => setAddressOpen(false)}>Cancel</button>
            <button type="button" className="btn-primary" onClick={() => saveAddress.mutate()} disabled={saveAddress.isPending}>
              {saveAddress.isPending ? <Spinner /> : 'Save address'}
            </button>
          </>
        }
      >
        <div className="space-y-3">
          {addressError && <Alert tone="error">{addressError}</Alert>}
          <div className="grid gap-3 sm:grid-cols-2">
            <div>
              <label className="label" htmlFor="edit-label">Label</label>
              <input id="edit-label" className="input" value={addressForm.label} onChange={(event) => setAddressForm({ ...addressForm, label: event.target.value })} />
            </div>
            <div>
              <label className="label" htmlFor="edit-name">Recipient</label>
              <input id="edit-name" className="input" value={addressForm.recipientName} onChange={(event) => setAddressForm({ ...addressForm, recipientName: event.target.value })} />
            </div>
            <div>
              <label className="label" htmlFor="edit-phone">Mobile</label>
              <input id="edit-phone" className="input" inputMode="numeric" maxLength={10} value={addressForm.phone} onChange={(event) => setAddressForm({ ...addressForm, phone: event.target.value.replace(/\D/g, '') })} />
            </div>
            <div>
              <label className="label" htmlFor="edit-pincode">PIN code</label>
              <input id="edit-pincode" className="input" inputMode="numeric" maxLength={6} value={addressForm.pincode} onChange={(event) => setAddressForm({ ...addressForm, pincode: event.target.value.replace(/\D/g, '') })} />
            </div>
          </div>
          <div>
            <label className="label" htmlFor="edit-line1">Address line 1</label>
            <input id="edit-line1" className="input" value={addressForm.line1} onChange={(event) => setAddressForm({ ...addressForm, line1: event.target.value })} />
          </div>
          <div>
            <label className="label" htmlFor="edit-line2">Address line 2</label>
            <input id="edit-line2" className="input" value={addressForm.line2} onChange={(event) => setAddressForm({ ...addressForm, line2: event.target.value })} />
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            <div>
              <label className="label" htmlFor="edit-city">City</label>
              <input id="edit-city" className="input" value={addressForm.city} onChange={(event) => setAddressForm({ ...addressForm, city: event.target.value })} />
            </div>
            <div>
              <label className="label" htmlFor="edit-state">State</label>
              <input id="edit-state" className="input" value={addressForm.state} onChange={(event) => setAddressForm({ ...addressForm, state: event.target.value })} />
            </div>
          </div>
          <label className="flex items-center gap-2 text-xs text-slate-600">
            <input type="checkbox" checked={addressForm.defaultAddress} onChange={(event) => setAddressForm({ ...addressForm, defaultAddress: event.target.checked })} />
            Use as my default delivery address
          </label>
        </div>
      </Modal>

      <Modal
        open={deleteOpen}
        onClose={() => setDeleteOpen(false)}
        title="Request account deletion"
        footer={
          <>
            <button type="button" className="btn-secondary" onClick={() => setDeleteOpen(false)}>Cancel</button>
            <button type="button" className="btn-danger" onClick={() => requestDeletion.mutate()} disabled={requestDeletion.isPending}>
              {requestDeletion.isPending ? <Spinner /> : 'Submit request'}
            </button>
          </>
        }
      >
        <p className="text-sm text-slate-600">
          We will suspend your account immediately and contact you to verify the request. Orders, invoices and
          prescription records required by law are retained; everything else is deleted.
        </p>
        <label className="label mt-3" htmlFor="delete-reason">Reason (optional)</label>
        <textarea id="delete-reason" className="input resize-none" rows={2} value={deleteReason} onChange={(event) => setDeleteReason(event.target.value)} />
      </Modal>

    </div>
  );
}
