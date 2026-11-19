import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Bell, CheckCheck } from 'lucide-react';
import { Badge, EmptyState, PageLoader, useToast } from '../../components/ui';
import { apiGet, apiPost, errorMessage } from '../../lib/api';
import { dateTime } from '../../lib/format';
import type { NotificationItem } from '../../types';

export default function NotificationsPage() {
  const toast = useToast();
  const queryClient = useQueryClient();

  const { data, isLoading } = useQuery({
    queryKey: ['notifications', 'all'],
    queryFn: () => apiGet<NotificationItem[]>('/notifications'),
  });

  const markRead = useMutation({
    mutationFn: (id: number) => apiPost(`/notifications/${id}/read`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
    onError: (error) => toast(errorMessage(error), 'error'),
  });

  const unread = (data ?? []).filter((item) => !item.read);

  const markAll = async () => {
    await Promise.all(unread.map((item) => markRead.mutateAsync(item.id).catch(() => undefined)));
    toast('All notifications marked as read', 'success');
  };

  if (isLoading) return <PageLoader label="Loading notifications…" />;

  return (
    <div className="container-page space-y-5 py-6 sm:py-8">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Notifications</h1>
          <p className="muted mt-1">Order updates, prescription decisions and account alerts.</p>
        </div>
        {unread.length > 0 && (
          <button type="button" className="btn-secondary py-2 text-xs" onClick={markAll}>
            <CheckCheck className="h-3.5 w-3.5" /> Mark all read ({unread.length})
          </button>
        )}
      </div>

      {(data ?? []).length === 0 ? (
        <EmptyState icon={<Bell className="h-5 w-5" />} title="No notifications" description="You are all caught up." />
      ) : (
        <ul className="space-y-2">
          {data!.map((item) => (
            <li key={item.id} className={`card-pad ${item.read ? '' : 'border-l-4 border-l-brand-500'}`}>
              <div className="flex flex-wrap items-start justify-between gap-2">
                <div>
                  <div className="flex items-center gap-2">
                    <p className="font-semibold">{item.title}</p>
                    {!item.read && <Badge tone="bg-mint text-brand-800">New</Badge>}
                  </div>
                  <p className="mt-1 text-sm text-slate-600">{item.body}</p>
                  <p className="mt-1 text-[11px] text-slate-400">{dateTime(item.createdAt)}</p>
                </div>
                <div className="flex items-center gap-2">
                  {item.link && <a href={item.link} className="btn-secondary py-2 text-xs">Open</a>}
                  {!item.read && (
                    <button type="button" className="btn-ghost py-2 text-xs" onClick={() => markRead.mutate(item.id)}>
                      Mark read
                    </button>
                  )}
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
