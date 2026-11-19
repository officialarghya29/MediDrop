import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Search, ShieldOff } from 'lucide-react';
import { Alert, Badge, EmptyState, FieldError, Modal, PageLoader, Spinner, useToast } from '../../components/ui';
import { apiGet, apiPost, errorMessage } from '../../lib/api';
import { dateOnly } from '../../lib/format';
import type { AdminUserRow, PageResponse, Role } from '../../types';

export default function AdminUsersPage() {
  const toast = useToast();
  const queryClient = useQueryClient();
  const [term, setTerm] = useState('');
  const [query, setQuery] = useState('');
  const [role, setRole] = useState('');
  const [page, setPage] = useState(0);
  const [target, setTarget] = useState<AdminUserRow | null>(null);
  const [reason, setReason] = useState('');

  const users = useQuery({
    queryKey: ['admin-users', query, role, page],
    queryFn: () =>
      apiGet<PageResponse<AdminUserRow>>('/admin/users', {
        q: query || undefined,
        role: (role || undefined) as Role | undefined,
        page,
        size: 20,
      }),
  });

  const suspend = useMutation({
    mutationFn: () =>
      apiPost(`/admin/users/${target?.id}/suspend`, {
        suspend: target?.enabled ?? true,
        reason: reason.trim() || undefined,
      }),
    onSuccess: () => {
      toast(target?.enabled ? 'User suspended' : 'User restored', 'success');
      setTarget(null);
      setReason('');
      queryClient.invalidateQueries({ queryKey: ['admin-users'] });
    },
    onError: (error) => toast(errorMessage(error), 'error'),
  });

  return (
    <div className="space-y-4 px-4 py-6 sm:px-6 lg:px-8">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Users</h1>
        <p className="muted mt-1">Search customers, pharmacy owners, riders and administrators.</p>
      </div>

      <form
        className="flex flex-wrap gap-2"
        onSubmit={(event) => {
          event.preventDefault();
          setQuery(term.trim());
          setPage(0);
        }}
      >
        <div className="relative min-w-[14rem] flex-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
          <input className="input pl-9" placeholder="Search name, email or phone" value={term} onChange={(event) => setTerm(event.target.value)} aria-label="Search users" />
        </div>
        <select className="input w-auto py-2 text-xs" value={role} onChange={(event) => { setRole(event.target.value); setPage(0); }} aria-label="Filter by role">
          <option value="">All roles</option>
          <option value="CUSTOMER">Customers</option>
          <option value="PHARMACY">Pharmacies</option>
          <option value="DELIVERY">Delivery</option>
          <option value="ADMIN">Admins</option>
        </select>
        <button type="submit" className="btn-secondary">Search</button>
      </form>

      {users.isLoading && <PageLoader label="Loading users…" />}
      {users.isError && <Alert tone="error">{errorMessage(users.error)}</Alert>}
      {!users.isLoading && (users.data?.content ?? []).length === 0 && (
        <EmptyState title="No users found" description="Adjust the search or role filter." />
      )}

      {(users.data?.content ?? []).length > 0 && (
        <div className="overflow-x-auto rounded-2xl border border-slate-200 bg-white">
          <table className="table-base min-w-[48rem]">
            <thead>
              <tr>
                <th>Name</th>
                <th>Contact</th>
                <th>Role</th>
                <th>Joined</th>
                <th>Status</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {users.data!.content.map((user) => (
                <tr key={user.id}>
                  <td className="text-sm font-medium">{user.fullName}</td>
                  <td className="text-xs text-slate-600">{user.email}<br />{user.phone ?? '—'}</td>
                  <td><Badge>{user.role}</Badge></td>
                  <td className="text-xs text-slate-500">{dateOnly(user.joinedAt)}</td>
                  <td>
                    <div className="flex flex-wrap gap-1">
                      <Badge tone={user.enabled ? 'bg-emerald-50 text-emerald-700' : 'bg-rose-50 text-rose-700'}>
                        {user.enabled ? 'Active' : 'Suspended'}
                      </Badge>
                      {!user.emailVerified && <Badge tone="bg-amber-50 text-amber-800">Unverified</Badge>}
                      {user.locked && <Badge tone="bg-rose-50 text-rose-700">Locked</Badge>}
                    </div>
                  </td>
                  <td className="text-right">
                    <button
                      type="button"
                      className="btn-ghost py-1.5 text-xs"
                      onClick={() => { setTarget(user); setReason(''); }}
                      disabled={user.role === 'ADMIN'}
                    >
                      <ShieldOff className="h-3.5 w-3.5" /> {user.enabled ? 'Suspend' : 'Restore'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {users.data && users.data.totalPages > 1 && (
        <div className="flex items-center justify-center gap-2">
          <button type="button" className="btn-secondary py-2 text-xs" disabled={page === 0} onClick={() => setPage((value) => value - 1)}>Previous</button>
          <span className="text-xs text-slate-500">Page {page + 1} of {users.data.totalPages}</span>
          <button type="button" className="btn-secondary py-2 text-xs" disabled={page + 1 >= users.data.totalPages} onClick={() => setPage((value) => value + 1)}>Next</button>
        </div>
      )}

      <Modal
        open={!!target}
        onClose={() => setTarget(null)}
        title={target ? `${target.enabled ? 'Suspend' : 'Restore'} ${target.fullName}` : 'Account'}
        footer={
          <>
            <button type="button" className="btn-secondary" onClick={() => setTarget(null)}>Cancel</button>
            <button type="button" className={target?.enabled ? 'btn-danger' : 'btn-primary'} onClick={() => suspend.mutate()} disabled={suspend.isPending}>
              {suspend.isPending ? <Spinner /> : target?.enabled ? 'Suspend account' : 'Restore account'}
            </button>
          </>
        }
      >
        <p className="text-sm text-slate-600">
          {target?.enabled
            ? 'Suspension revokes active sessions on the next token refresh and blocks new orders. Existing prescriptions and invoices are retained.'
            : 'Restoring the account re-enables sign-in and ordering immediately.'}
        </p>
        <label className="label mt-3" htmlFor="suspend-reason">Reason</label>
        <textarea id="suspend-reason" className="input resize-none" rows={2} maxLength={400} value={reason} onChange={(event) => setReason(event.target.value)} />
        <FieldError message={target?.enabled && !reason.trim() ? 'A reason is required for suspension' : undefined} />
      </Modal>
    </div>
  );
}
