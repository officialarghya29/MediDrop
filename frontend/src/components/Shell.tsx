import { useEffect, useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  Bell, ClipboardList, FileText, Home, LayoutDashboard, LogOut, Menu, Package, Search,
  Settings, ShoppingCart, Sparkles, Stethoscope, Truck, UserRound, Users, X,
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

  const isDashboard = !!user && user.role !== 'CUSTOMER';
  const cartLink = user?.role === 'CUSTOMER';

  const signOut = async () => {
    await logout();
    navigate('/login');
  };

  return (
    <div className="flex min-h-screen flex-col">
      <header className="sticky top-0 z-30 border-b border-white/70 bg-white/75 backdrop-blur-xl no-print">
        <div className="h-1 w-full bg-gradient-to-r from-brand-400 via-grape-500 to-coral-400" />
        <div className="container-page flex h-[4.4rem] items-center gap-3">
          <Logo to={user ? (isDashboard ? nav[0].to : '/') : '/'} />

          {!isDashboard && (
            <nav className="ml-6 hidden items-center gap-1 lg:flex" aria-label="Main navigation">
              {customerNav.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={item.end}
                  className={({ isActive }) =>
                    `rounded-full px-3.5 py-2 text-sm font-semibold transition ${
                      isActive
                        ? 'bg-gradient-to-r from-brand-100 to-sky-100 text-brand-900 shadow-sm'
                        : 'text-navy-700 hover:bg-white hover:text-brand-800'
                    }`
                  }
                >
                  {item.label}
                </NavLink>
              ))}
            </nav>
          )}

          {isDashboard && (
            <span className="ml-2 hidden items-center gap-1.5 rounded-full bg-navy-900 px-3 py-1.5 text-[10px] font-bold uppercase tracking-[0.14em] text-white sm:inline-flex">
              <Sparkles className="h-3 w-3 text-brand-300" />
              {user?.role?.toLowerCase()} console
            </span>
          )}

          <div className="ml-auto flex items-center gap-1.5">
            {cartLink && (
              <Link
                to="/cart"
                className="rounded-full border border-white bg-white/80 p-2.5 text-navy-700 shadow-sm transition hover:-translate-y-0.5 hover:text-brand-800"
                aria-label="Cart"
              >
                <ShoppingCart className="h-5 w-5" />
              </Link>
            )}
            {user && (
              <div className="relative">
                <button
                  type="button"
                  onClick={() => setBellOpen((open) => !open)}
                  className="relative rounded-full p-2.5 text-navy-700 transition hover:bg-white"
                  aria-label="Notifications"
                  aria-expanded={bellOpen}
                >
                  <Bell className="h-5 w-5" />
                  {(unread?.count ?? 0) > 0 && (
                    <span className="absolute right-1 top-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-gradient-to-r from-coral-500 to-rose-600 px-1 text-[10px] font-bold text-white shadow">
                      {unread!.count > 9 ? '9+' : unread!.count}
                    </span>
                  )}
                </button>
                {bellOpen && (
                  <div className="absolute right-0 mt-2 w-[min(92vw,22rem)] overflow-hidden rounded-3xl border border-white bg-white/95 shadow-soft backdrop-blur-xl">
                    <div className="flex items-center justify-between border-b border-slate-100 px-4 py-3">
                      <span className="text-sm font-bold">Notifications</span>
                      <Sparkles className="h-4 w-4 text-grape-500" />
                    </div>
                    <div className="max-h-80 overflow-y-auto">
                      {(notifications ?? []).length === 0 && (
                        <p className="px-4 py-6 text-center text-sm text-slate-500">No notifications yet</p>
                      )}
                      {notifications?.map((item) => (
                        <Link
                          key={item.id}
                          to={item.link ?? '/'}
                          className={`block border-b border-slate-50 px-4 py-3 text-sm transition hover:bg-mint/40 ${
                            item.read ? '' : 'bg-gradient-to-r from-mint/70 to-transparent'
                          }`}
                        >
                          <p className="font-bold text-navy-900">{item.title}</p>
                          <p className="mt-0.5 line-clamp-2 text-xs text-slate-600">{item.body}</p>
                        </Link>
                      ))}
                    </div>
                    <Link to="/notifications" className="block px-4 py-3 text-center text-xs font-bold text-brand-700 hover:bg-mint">
                      View all notifications
                    </Link>
                  </div>
                )}
              </div>
            )}

            {user ? (
              <div className="hidden items-center gap-2 sm:flex">
                <div className="text-right">
                  <p className="max-w-[10rem] truncate text-sm font-bold leading-tight">{user.fullName}</p>
                  <p className="text-[10px] font-semibold uppercase tracking-[0.12em] text-slate-500">{user.role}</p>
                </div>
                <button type="button" onClick={signOut} className="btn-ghost px-2.5" aria-label="Sign out">
                  <LogOut className="h-4 w-4" />
                </button>
              </div>
            ) : (
              <div className="hidden items-center gap-2 sm:flex">
                <Link to="/login" className="btn-secondary">Sign in</Link>
                <Link to="/register" className="btn-primary">Get started</Link>
              </div>
            )}

            <button
              type="button"
              className="rounded-full p-2.5 text-navy-700 hover:bg-white lg:hidden"
              onClick={() => setMobileOpen((open) => !open)}
              aria-label="Toggle menu"
              aria-expanded={mobileOpen}
            >
              {mobileOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
            </button>
          </div>
        </div>

        {mobileOpen && (
          <div className="border-t border-white/80 bg-white/90 backdrop-blur-xl lg:hidden">
            <nav className="container-page grid gap-1 py-3" aria-label="Mobile navigation">
              {nav.map((item) => {
                const Icon = item.icon;
                return (
                  <NavLink
                    key={item.to}
                    to={item.to}
                    end={item.end}
                    className={({ isActive }) =>
                      `flex items-center gap-3 rounded-2xl px-3 py-2.5 text-sm font-semibold ${
                        isActive ? 'bg-gradient-to-r from-brand-100 to-sky-100 text-brand-900' : 'text-navy-700 hover:bg-white'
                      }`
                    }
                  >
                    <Icon className="h-4 w-4" /> {item.label}
                  </NavLink>
                );
              })}
              {user && (
                <button type="button" onClick={signOut} className="flex items-center gap-3 rounded-2xl px-3 py-2.5 text-left text-sm font-semibold text-rose-700 hover:bg-rose-50">
                  <LogOut className="h-4 w-4" /> Sign out
                </button>
              )}
              {!user && (
                <div className="flex gap-2 pt-2">
                  <Link to="/login" className="btn-secondary flex-1">Sign in</Link>
                  <Link to="/register" className="btn-primary flex-1">Get started</Link>
                </div>
              )}
            </nav>
          </div>
        )}
      </header>

      <div className={isDashboard ? 'mx-auto flex w-full max-w-[92rem] flex-1 flex-col lg:flex-row' : 'flex-1'}>
        {isDashboard && (
          <aside className="hidden w-60 shrink-0 border-r border-white/70 bg-white/55 px-3 py-6 backdrop-blur-sm lg:block no-print">
            <nav className="space-y-1" aria-label="Console navigation">
              {nav.map((item) => {
                const Icon = item.icon;
                return (
                  <NavLink
                    key={item.to}
                    to={item.to}
                    end={item.end}
                    className={({ isActive }) =>
                      `flex items-center gap-3 rounded-2xl px-3 py-2.5 text-sm font-semibold transition ${
                        isActive
                          ? 'bg-gradient-to-r from-brand-100 to-sky-100 text-brand-900 shadow-sm'
                          : 'text-navy-700 hover:bg-white'
                      }`
                    }
                  >
                    <Icon className="h-4 w-4" /> {item.label}
                  </NavLink>
                );
              })}
            </nav>
            <div className="mt-6 overflow-hidden rounded-3xl bg-gradient-to-br from-navy-900 via-brand-900 to-grape-800 p-4 text-white shadow-soft">
              <Sparkles className="h-5 w-5 text-brand-300" />
              <p className="mt-3 font-display text-sm font-bold">MediDrop OS</p>
              <p className="mt-1 text-[11px] leading-relaxed text-white/70">Live healthcare operations, all in one calm console.</p>
              <Badge tone="bg-white/10 text-white mt-3" className="ring-1 ring-white/15">v1.0</Badge>
            </div>
          </aside>
        )}

        <main className="min-w-0 flex-1 pb-20 lg:pb-10">
          <Outlet />
        </main>
      </div>

      {!isDashboard && (
        <footer className="no-print border-t border-white/70 bg-white/45 backdrop-blur-sm">
          <div className="container-page grid gap-8 py-10 sm:grid-cols-2 lg:grid-cols-[1.4fr_1fr_1fr]">
            <div>
              <Logo />
              <p className="mt-4 max-w-sm text-sm leading-relaxed text-slate-600">
                Neighbourhood pharmacies, live stock and pharmacist-checked prescriptions — with delivery you can
                actually follow.
              </p>
              <span className="mt-4 inline-flex items-center gap-2 rounded-full bg-mint px-3 py-1.5 text-[11px] font-bold text-brand-800">
                <ShieldDot /> Kolkata · licensed partners only
              </span>
            </div>
            <div>
              <p className="text-[11px] font-bold uppercase tracking-[0.16em] text-slate-500">Shop</p>
              <div className="mt-3 grid gap-2 text-sm text-slate-600">
                <Link to="/search" className="hover:text-brand-800">All medicines</Link>
                <Link to="/search?rx=true" className="hover:text-brand-800">Prescription medicines</Link>
                <Link to="/prescriptions" className="hover:text-brand-800">Upload prescription</Link>
                <Link to="/orders" className="hover:text-brand-800">Track an order</Link>
              </div>
            </div>
            <div>
              <p className="text-[11px] font-bold uppercase tracking-[0.16em] text-slate-500">Care</p>
              <div className="mt-3 grid gap-2 text-sm text-slate-600">
                <Link to="/pharmacy/register" className="hover:text-brand-800">Partner with us</Link>
                <Link to="/delivery/register" className="hover:text-brand-800">Deliver with MediDrop</Link>
                <Link to="/profile#support" className="hover:text-brand-800">Support</Link>
                <a href="https://github.com/officialarghya29/MediDrop" className="hover:text-brand-800" target="_blank" rel="noreferrer">Source</a>
              </div>
            </div>
          </div>
          <div className="container-page flex flex-col gap-2 border-t border-slate-200/60 py-4 text-[11px] leading-relaxed text-slate-500 sm:flex-row sm:items-center sm:justify-between">
            <p>MediDrop does not provide medical advice or substitute prescribed medicines.</p>
            <p>Prescription-only medicines require pharmacist approval.</p>
          </div>
        </footer>
      )}

      {user?.role === 'CUSTOMER' && (
        <nav className="fixed bottom-0 left-0 right-0 z-30 grid grid-cols-5 border-t border-white/80 bg-white/85 pb-[env(safe-area-inset-bottom)] backdrop-blur-xl lg:hidden no-print">
          {customerNav.map((item) => {
            const Icon = item.icon;
            return (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className="flex flex-col items-center gap-1 py-2 text-[10px] font-bold"
              >
                {({ isActive }) => (
                  <>
                    <span className={`flex h-8 w-10 items-center justify-center rounded-full transition ${
                      isActive ? 'bg-gradient-to-r from-brand-600 to-cyan-600 text-white shadow-glow' : 'text-slate-500'
                    }`}>
                      <Icon className="h-[18px] w-[18px]" />
                    </span>
                    <span className={isActive ? 'text-brand-800' : 'text-slate-500'}>{item.label}</span>
                  </>
                )}
              </NavLink>
            );
          })}
        </nav>
      )}
    </div>
  );
}

function ShieldDot() {
  return <span className="h-1.5 w-1.5 rounded-full bg-brand-500 shadow-[0_0_0_3px_rgba(16,185,129,0.16)]" />;
}
