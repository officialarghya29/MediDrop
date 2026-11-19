import { useEffect, useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  Bell, ClipboardList, FileText, Home, LayoutDashboard, LogOut, Menu, Package, Search,
  Settings, ShoppingCart, Stethoscope, Truck, UserRound, Users, X,
} from 'lucide-react';
import Logo from './Logo';
import { apiGet } from '../lib/api';
import { useAuth } from '../store/auth';
import { Badge } from './ui';

const customerNav = [
  { to: '/', label: 'Home', icon: Home, end: true },
  { to: '/search', label: 'Medicines', icon: Search },
  { to: '/prescriptions', label: 'Prescriptions', icon: FileText },
  { to: '/orders', label: 'Orders', icon: ClipboardList },
  { to: '/profile', label: 'Profile', icon: UserRound },
];

const pharmacyNav = [
  { to: '/pharmacy', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/pharmacy/orders', label: 'Orders', icon: Package },
  { to: '/pharmacy/inventory', label: 'Inventory', icon: ClipboardList },
  { to: '/pharmacy/prescriptions', label: 'Prescriptions', icon: Stethoscope },
  { to: '/pharmacy/analytics', label: 'Analytics', icon: LayoutDashboard },
];

const deliveryNav = [
  { to: '/delivery', label: 'Deliveries', icon: Truck, end: true },
  { to: '/delivery/history', label: 'History', icon: ClipboardList },
];

const adminNav = [
  { to: '/admin', label: 'Overview', icon: LayoutDashboard, end: true },
  { to: '/admin/approvals', label: 'Approvals', icon: Users },
  { to: '/admin/orders', label: 'Orders', icon: Package },
  { to: '/admin/users', label: 'Users', icon: UserRound },
  { to: '/admin/config', label: 'Configuration', icon: Settings },
];

export default function Shell() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [bellOpen, setBellOpen] = useState(false);

  useEffect(() => {
    setMobileOpen(false);
    setBellOpen(false);
  }, [location.pathname]);

  const { data: unread } = useQuery({
    queryKey: ['notifications', 'unread'],
    queryFn: () => apiGet<{ count: number }>('/notifications/unread-count'),
    enabled: !!user,
    refetchInterval: 60_000,
  });

  const { data: notifications } = useQuery({
    queryKey: ['notifications', 'recent'],
    queryFn: () => apiGet<{ id: number; title: string; body: string; read: boolean; link?: string }[]>('/notifications'),
    enabled: !!user && bellOpen,
  });

  const nav =
    user?.role === 'PHARMACY'
      ? pharmacyNav
      : user?.role === 'DELIVERY'
        ? deliveryNav
        : user?.role === 'ADMIN'
          ? adminNav
          : customerNav;

  const isDashboard = user && user.role !== 'CUSTOMER';
  const cartLink = user?.role === 'CUSTOMER';

  const signOut = async () => {
    await logout();
    navigate('/login');
  };

  return (
    <div className="min-h-screen bg-canvas">
      <header className="sticky top-0 z-30 border-b border-slate-200/80 bg-white/95 backdrop-blur no-print">
        <div className="container-page flex h-16 items-center gap-3">
          <Logo to={user ? (isDashboard ? nav[0].to : '/') : '/'} />

          {!isDashboard && (
            <nav className="ml-6 hidden items-center gap-1 lg:flex" aria-label="Main navigation">
              {customerNav.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={item.end}
                  className={({ isActive }) =>
                    `rounded-lg px-3 py-2 text-sm font-medium ${
                      isActive ? 'bg-mint text-brand-800' : 'text-navy-700 hover:bg-slate-100'
                    }`
                  }
                >
                  {item.label}
                </NavLink>
              ))}
            </nav>
          )}

          {isDashboard && (
            <span className="ml-2 hidden rounded-full bg-navy-50 px-3 py-1 text-xs font-semibold uppercase tracking-wide text-navy-700 sm:inline">
              {user?.role?.toLowerCase()} console
            </span>
          )}

          <div className="ml-auto flex items-center gap-1.5">
            {cartLink && (
              <Link to="/cart" className="btn-ghost px-2.5" aria-label="Cart">
                <ShoppingCart className="h-5 w-5" />
              </Link>
            )}
            {user && (
              <div className="relative">
                <button
                  type="button"
                  onClick={() => setBellOpen((open) => !open)}
                  className="relative rounded-lg p-2.5 text-navy-700 hover:bg-slate-100"
                  aria-label="Notifications"
                  aria-expanded={bellOpen}
                >
                  <Bell className="h-5 w-5" />
                  {(unread?.count ?? 0) > 0 && (
                    <span className="absolute right-1.5 top-1.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-rose-600 px-1 text-[10px] font-bold text-white">
                      {unread!.count > 9 ? '9+' : unread!.count}
                    </span>
                  )}
                </button>
                {bellOpen && (
                  <div className="absolute right-0 mt-2 w-[min(92vw,22rem)] overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-soft">
                    <div className="border-b border-slate-100 px-4 py-3 text-sm font-semibold">Notifications</div>
                    <div className="max-h-80 overflow-y-auto">
                      {(notifications ?? []).length === 0 && (
                        <p className="px-4 py-6 text-center text-sm text-slate-500">No notifications yet</p>
                      )}
                      {notifications?.map((item) => (
                        <Link
                          key={item.id}
                          to={item.link ?? '/'}
                          className={`block border-b border-slate-50 px-4 py-3 text-sm hover:bg-slate-50 ${
                            item.read ? '' : 'bg-mint/40'
                          }`}
                        >
                          <p className="font-semibold text-navy-900">{item.title}</p>
                          <p className="mt-0.5 line-clamp-2 text-xs text-slate-600">{item.body}</p>
                        </Link>
                      ))}
                    </div>
                    <Link to="/notifications" className="block px-4 py-2.5 text-center text-xs font-semibold text-brand-700 hover:bg-mint">
                      View all notifications
                    </Link>
                  </div>
                )}
              </div>
            )}

            {user ? (
              <div className="hidden items-center gap-2 sm:flex">
                <div className="text-right">
                  <p className="max-w-[10rem] truncate text-sm font-semibold leading-tight">{user.fullName}</p>
                  <p className="text-[11px] uppercase tracking-wide text-slate-500">{user.role}</p>
                </div>
                <button type="button" onClick={signOut} className="btn-ghost px-2.5" aria-label="Sign out">
                  <LogOut className="h-4 w-4" />
                </button>
              </div>
            ) : (
              <div className="hidden items-center gap-2 sm:flex">
                <Link to="/login" className="btn-secondary">Sign in</Link>
                <Link to="/register" className="btn-primary">Create account</Link>
              </div>
            )}

            <button
              type="button"
              className="rounded-lg p-2.5 text-navy-700 hover:bg-slate-100 lg:hidden"
              onClick={() => setMobileOpen((open) => !open)}
              aria-label="Toggle menu"
              aria-expanded={mobileOpen}
            >
              {mobileOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
            </button>
          </div>
        </div>

        {mobileOpen && (
          <div className="border-t border-slate-200 bg-white lg:hidden">
            <nav className="container-page grid gap-1 py-3" aria-label="Mobile navigation">
              {nav.map((item) => {
                const Icon = item.icon;
                return (
                  <NavLink
                    key={item.to}
                    to={item.to}
                    end={item.end}
                    className={({ isActive }) =>
                      `flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium ${
                        isActive ? 'bg-mint text-brand-800' : 'text-navy-700 hover:bg-slate-100'
                      }`
                    }
                  >
                    <Icon className="h-4 w-4" /> {item.label}
                  </NavLink>
                );
              })}
              {user && (
                <button type="button" onClick={signOut} className="flex items-center gap-3 rounded-lg px-3 py-2.5 text-left text-sm font-medium text-rose-700 hover:bg-rose-50">
                  <LogOut className="h-4 w-4" /> Sign out
                </button>
              )}
              {!user && (
                <div className="flex gap-2 pt-2">
                  <Link to="/login" className="btn-secondary flex-1">Sign in</Link>
                  <Link to="/register" className="btn-primary flex-1">Create account</Link>
                </div>
              )}
            </nav>
          </div>
        )}
      </header>

      <div className={user && isDashboard ? 'mx-auto flex w-full max-w-[92rem] flex-col lg:flex-row' : ''}>
        {user && isDashboard && (
          <aside className="hidden w-60 shrink-0 border-r border-slate-200 bg-white px-3 py-6 lg:block no-print">
            <nav className="space-y-1" aria-label="Console navigation">
              {nav.map((item) => {
                const Icon = item.icon;
                return (
                  <NavLink
                    key={item.to}
                    to={item.to}
                    end={item.end}
                    className={({ isActive }) =>
                      `flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium ${
                        isActive ? 'bg-mint text-brand-800' : 'text-navy-700 hover:bg-slate-50'
                      }`
                    }
                  >
                    <Icon className="h-4 w-4" /> {item.label}
                  </NavLink>
                );
              })}
            </nav>
            <div className="mt-6 rounded-xl bg-slate-50 p-3 text-xs text-slate-600">
              <p className="font-semibold text-navy-800">MediDrop</p>
              <p className="mt-1">Healthcare, delivered to your doorstep.</p>
              <Badge tone="bg-white text-slate-600 mt-2" className="border border-slate-200">v1.0</Badge>
            </div>
          </aside>
        )}

        <main className="min-w-0 flex-1 pb-20 lg:pb-10">
          <Outlet />
        </main>
      </div>

      {user?.role === 'CUSTOMER' && (
        <nav className="fixed bottom-0 left-0 right-0 z-30 grid grid-cols-5 border-t border-slate-200 bg-white/95 backdrop-blur lg:hidden no-print">
          {customerNav.map((item) => {
            const Icon = item.icon;
            return (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }) =>
                  `flex flex-col items-center gap-1 py-2.5 text-[10.5px] font-medium ${
                    isActive ? 'text-brand-800' : 'text-slate-500'
                  }`
                }
              >
                <Icon className="h-5 w-5" />
                {item.label}
              </NavLink>
            );
          })}
        </nav>
      )}
    </div>
  );
}
