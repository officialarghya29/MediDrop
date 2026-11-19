import axios, { AxiosError, type AxiosRequestConfig } from 'axios';
import type { ApiResponse, AuthResponse } from '../types';

export const API_BASE =
  (import.meta.env.VITE_API_URL as string | undefined)?.replace(/\/$/, '') || '/api/v1';

const ACCESS_KEY = 'medidrop.access';
const REFRESH_KEY = 'medidrop.refresh';

export const tokenStore = {
  access: () => localStorage.getItem(ACCESS_KEY),
  refresh: () => localStorage.getItem(REFRESH_KEY),
  set(access: string, refresh: string) {
    localStorage.setItem(ACCESS_KEY, access);
    localStorage.setItem(REFRESH_KEY, refresh);
  },
  clear() {
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
  },
};

const http = axios.create({ baseURL: API_BASE, timeout: 20000 });

http.interceptors.request.use((config) => {
  const token = tokenStore.access();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

let refreshRequest: Promise<string | null> | null = null;
let onUnauthorized: (() => void) | null = null;

export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler;
}

async function refreshAccessToken(): Promise<string | null> {
  const refresh = tokenStore.refresh();
  if (!refresh) return null;
  try {
    const response = await axios.post<ApiResponse<AuthResponse>>(`${API_BASE}/auth/refresh`, {
      refreshToken: refresh,
    });
    const data = response.data.data;
    tokenStore.set(data.accessToken, data.refreshToken);
    return data.accessToken;
  } catch {
    tokenStore.clear();
    onUnauthorized?.();
    return null;
  }
}

http.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as (AxiosRequestConfig & { _retried?: boolean }) | undefined;
    const status = error.response?.status;
    const isAuthCall = original?.url?.includes('/auth/');
    if (status === 401 && original && !original._retried && !isAuthCall) {
      original._retried = true;
      refreshRequest = refreshRequest ?? refreshAccessToken();
      const token = await refreshRequest;
      refreshRequest = null;
      if (token) {
        original.headers = { ...(original.headers ?? {}), Authorization: `Bearer ${token}` };
        return http.request(original);
      }
    }
    return Promise.reject(error);
  },
);

export function errorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as { message?: string } | undefined;
    if (data?.message) {
      const fieldErrors = (data as { data?: Record<string, string> }).data;
      if (fieldErrors && typeof fieldErrors === 'object' && !('code' in fieldErrors)) {
        const first = Object.values(fieldErrors)[0];
        if (first) return first;
      }
      return data.message;
    }
    if (error.code === 'ERR_NETWORK') return 'Cannot reach the MediDrop API. Is the backend running?';
    return error.message;
  }
  return error instanceof Error ? error.message : 'Something went wrong';
}

export async function apiGet<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  const response = await http.get<ApiResponse<T>>(url, { params });
  return response.data.data;
}

export async function apiPost<T>(url: string, body?: unknown, headers?: Record<string, string>): Promise<T> {
  const response = await http.post<ApiResponse<T>>(url, body, { headers });
  return response.data.data;
}

export async function apiPut<T>(url: string, body?: unknown): Promise<T> {
  const response = await http.put<ApiResponse<T>>(url, body);
  return response.data.data;
}

export async function apiPatch<T>(url: string, body?: unknown): Promise<T> {
  const response = await http.patch<ApiResponse<T>>(url, body);
  return response.data.data;
}

export async function apiDelete<T>(url: string): Promise<T> {
  const response = await http.delete<ApiResponse<T>>(url);
  return response.data.data;
}

export async function apiUpload<T>(url: string, form: FormData): Promise<T> {
  const response = await http.post<ApiResponse<T>>(url, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return response.data.data;
}

export async function apiRaw(url: string): Promise<Blob> {
  const response = await http.get(url, { responseType: 'blob' });
  return response.data as Blob;
}

export { http };
