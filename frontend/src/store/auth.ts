import { create } from 'zustand';
import { apiGet, apiPost, setUnauthorizedHandler, tokenStore } from '../lib/api';
import type { AuthResponse, AuthUser, Role } from '../types';

const USER_KEY = 'medidrop.user';

interface AuthState {
  user: AuthUser | null;
  loading: boolean;
  setSession: (response: AuthResponse) => void;
  login: (identifier: string, password: string) => Promise<AuthUser>;
  logout: () => Promise<void>;
  hydrate: () => Promise<void>;
  hasRole: (...roles: Role[]) => boolean;
  markVerified: () => void;
}

function readStoredUser(): AuthUser | null {
  const raw = localStorage.getItem(USER_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as AuthUser;
  } catch {
    return null;
  }
}

export const useAuth = create<AuthState>((set, get) => ({
  user: readStoredUser(),
  loading: false,

  setSession(response) {
    tokenStore.set(response.accessToken, response.refreshToken);
    localStorage.setItem(USER_KEY, JSON.stringify(response.user));
    set({ user: response.user });
  },

  async login(identifier, password) {
    set({ loading: true });
    try {
      const response = await apiPost<AuthResponse>('/auth/login', { identifier, password });
      get().setSession(response);
      return response.user;
    } finally {
      set({ loading: false });
    }
  },

  async logout() {
    try {
      await apiPost('/auth/logout', { refreshToken: tokenStore.refresh() });
    } catch {
      // Local sign-out must succeed even if the API is unreachable.
    }
    tokenStore.clear();
    localStorage.removeItem(USER_KEY);
    set({ user: null });
  },

  async hydrate() {
    if (!tokenStore.access()) {
      set({ user: null });
      return;
    }
    try {
      const user = await apiGet<AuthUser>('/customer/me');
      localStorage.setItem(USER_KEY, JSON.stringify(user));
      set({ user });
    } catch {
      // Keep the cached identity; the API interceptor handles expired sessions.
    }
  },

  hasRole(...roles) {
    const user = get().user;
    return !!user && roles.includes(user.role);
  },

  markVerified() {
    const user = get().user;
    if (!user) return;
    const updated = { ...user, emailVerified: true, phoneVerified: true };
    localStorage.setItem(USER_KEY, JSON.stringify(updated));
    set({ user: updated });
  },
}));

setUnauthorizedHandler(() => {
  tokenStore.clear();
  localStorage.removeItem(USER_KEY);
  useAuth.setState({ user: null });
});

export const dashboardPathFor = (role?: Role | null): string => {
  switch (role) {
    case 'PHARMACY':
      return '/pharmacy';
    case 'DELIVERY':
      return '/delivery';
    case 'ADMIN':
      return '/admin';
    default:
      return '/';
  }
};
