import { REPORT_STORAGE_KEY } from './reportUtils';

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? import.meta.env.BACKEND_URL ?? '';

export type AuthSession = {
  token: string;
  fullName: string;
  email: string;
  role: string;
};

const AUTH_STORAGE_KEY = 'healthtimeline-auth';

async function readJsonResponse<T>(response: Response): Promise<T | null> {
  const text = await response.text();
  if (!text) {
    return null;
  }

  try {
    return JSON.parse(text) as T;
  } catch {
    return null;
  }
}

export function getStoredAuthSession(): AuthSession | null {
  try {
    const saved = localStorage.getItem(AUTH_STORAGE_KEY);
    if (!saved) return null;
    const parsed = JSON.parse(saved) as Partial<AuthSession>;
    if (!parsed?.token || !parsed?.email || !parsed?.fullName) {
      localStorage.removeItem(AUTH_STORAGE_KEY);
      return null;
    }
    return {
      token: String(parsed.token),
      fullName: String(parsed.fullName),
      email: String(parsed.email),
      role: String(parsed.role ?? 'USER'),
    };
  } catch {
    localStorage.removeItem(AUTH_STORAGE_KEY);
    return null;
  }
}

export function saveAuthSession(session: AuthSession): void {
  clearAuthSession();
  localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(session));
}

export async function validateAuthSession(session: AuthSession | null): Promise<AuthSession | null> {
  if (!session?.token) return null;

  try {
    const response = await fetch(`${API_BASE}/api/auth/me`, {
      headers: { Authorization: `Bearer ${session.token}` },
    });

    if (!response.ok) {
      clearAuthSession();
      return null;
    }

    const data = await readJsonResponse<{ token?: string; fullName?: string; email?: string; role?: string }>(response);
    if (!data?.email) {
      clearAuthSession();
      return null;
    }

    if (session.email && data.email.toLowerCase() !== session.email.toLowerCase()) {
      clearAuthSession();
      return null;
    }

    return {
      token: session.token,
      fullName: data.fullName ?? session.fullName,
      email: data.email,
      role: data.role ?? session.role,
    };
  } catch {
    clearAuthSession();
    return null;
  }
}

export function clearAuthSession(): void {
  Object.keys(localStorage)
    .filter((key) => key.startsWith('healthtimeline'))
    .forEach((key) => localStorage.removeItem(key));
}

export async function loginUser(email: string, password: string): Promise<AuthSession> {
  const response = await fetch(`${API_BASE}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });

  const data = await readJsonResponse<{ token?: string; fullName?: string; email?: string; role?: string; message?: string }>(response);
  if (!response.ok || !data?.token) {
    throw new Error(data?.message ?? 'Unable to sign in.');
  }

  return { token: data.token, fullName: data.fullName ?? '', email: data.email ?? email, role: data.role ?? 'USER' };
}

export async function registerUser(fullName: string, email: string, password: string): Promise<AuthSession> {
  const response = await fetch(`${API_BASE}/api/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ fullName, email, password }),
  });

  const data = await readJsonResponse<{ token?: string; fullName?: string; email?: string; role?: string; message?: string }>(response);
  if (!response.ok || !data?.token) {
    throw new Error(data?.message ?? 'Unable to create account.');
  }

  return { token: data.token, fullName: data.fullName ?? fullName, email: data.email ?? email, role: data.role ?? 'USER' };
}
