'use client';

import type { ReactNode } from 'react';
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import { authApi } from '@/lib/api/authApi';
import { setCoreTenantContext } from '@/lib/api/coreClient';
import { UNAUTHORIZED_EVENT, clearAuthSession } from '@/lib/auth/session';
import { getPublicEnv } from '@/lib/constants/env';
import type { AdminTenantOption, AdminUser, LoginRequest } from '@/lib/types/admin';

export type AuthStatus = 'CHECKING' | 'AUTHENTICATED' | 'UNAUTHENTICATED';

export interface AuthContextValue {
  status: AuthStatus;
  user: AdminUser | null;
  tenants: AdminTenantOption[];
  selectedTenantId: string;
  tenantChanging: boolean;
  login: (payload: LoginRequest) => Promise<void>;
  logout: () => Promise<void>;
  refreshCurrentUser: () => Promise<void>;
  selectTenant: (tenantId: string) => Promise<void>;
  hasRole: (...roles: AdminUser['roles']) => boolean;
  hasPermission: (...permissions: string[]) => boolean;
}

const AuthContext = createContext<AuthContextValue | null>(null);

const mockAdminUser: AdminUser = {
  authenticationType: 'MOCK',
  userId: 'mock-admin',
  username: 'mock-admin',
  displayName: 'Mock Administrator',
  roles: ['ADMIN'],
  permissions: ['*'],
  allowedTenantIds: ['mock-tenant'],
  selectedTenantId: 'mock-tenant'
};

const mockTenants: AdminTenantOption[] = [{ tenantId: 'mock-tenant', selected: true }];

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside AuthProvider.');
  return context;
}

export function AuthProvider({ children }: Readonly<{ children: ReactNode }>) {
  const env = getPublicEnv();
  const router = useRouter();
  const pathname = usePathname();
  const authBypassed = !env.authEnabled || env.useMock;
  const [status, setStatus] = useState<AuthStatus>(authBypassed ? 'AUTHENTICATED' : 'CHECKING');
  const [user, setUser] = useState<AdminUser | null>(authBypassed ? mockAdminUser : null);
  const [tenants, setTenants] = useState<AdminTenantOption[]>(authBypassed ? mockTenants : []);
  const [tenantChanging, setTenantChanging] = useState(false);
  setCoreTenantContext(user?.selectedTenantId ?? '');

  const loadTenants = useCallback(async () => {
    if (authBypassed) {
      setTenants(mockTenants);
      return;
    }
    const response = await authApi.tenants();
    setTenants(response.tenants);
  }, [authBypassed]);

  const refreshCurrentUser = useCallback(async () => {
    if (authBypassed) {
      setStatus('AUTHENTICATED');
      setUser(mockAdminUser);
      setTenants(mockTenants);
      return;
    }

    setStatus('CHECKING');
    try {
      const [currentUser, tenantResponse] = await Promise.all([
        authApi.me(),
        authApi.tenants()
      ]);
      setUser(currentUser);
      setTenants(tenantResponse.tenants);
      setStatus('AUTHENTICATED');
    } catch {
      clearAuthSession();
      setCoreTenantContext('');
      setUser(null);
      setTenants([]);
      setStatus('UNAUTHENTICATED');
    }
  }, [authBypassed]);

  const login = useCallback(async (payload: LoginRequest) => {
    const currentUser = await authApi.login(payload);
    const tenantResponse = await authApi.tenants();
    setUser(currentUser);
    setTenants(tenantResponse.tenants);
    setStatus('AUTHENTICATED');
    router.replace('/dashboard');
  }, [router]);

  const logout = useCallback(async () => {
    try {
      if (!authBypassed) await authApi.logout();
    } catch {
      // The browser state must still be cleared when the backend is unavailable.
    } finally {
      clearAuthSession('logout');
      setCoreTenantContext(authBypassed ? mockAdminUser.selectedTenantId : '');
      setUser(authBypassed ? mockAdminUser : null);
      setTenants(authBypassed ? mockTenants : []);
      setStatus(authBypassed ? 'AUTHENTICATED' : 'UNAUTHENTICATED');
      router.replace('/login');
    }
  }, [authBypassed, router]);

  const selectTenant = useCallback(async (tenantId: string) => {
    const normalized = tenantId.trim();
    if (!normalized || normalized === user?.selectedTenantId) return;
    setTenantChanging(true);
    try {
      const updated = authBypassed
        ? { ...mockAdminUser, selectedTenantId: normalized, allowedTenantIds: [normalized] }
        : await authApi.selectTenant(normalized);
      setUser(updated);
      if (authBypassed) {
        setTenants([{ tenantId: normalized, selected: true }]);
      } else {
        await loadTenants();
      }
      router.refresh();
    } finally {
      setTenantChanging(false);
    }
  }, [authBypassed, loadTenants, router, user?.selectedTenantId]);

  const hasRole = useCallback((...roles: AdminUser['roles']) => {
    if (!user) return false;
    return roles.some((role) => user.roles.includes(role));
  }, [user]);

  const hasPermission = useCallback((...permissions: string[]) => {
    if (!user) return false;
    const assigned = user.permissions ?? [];
    return assigned.includes('*') || permissions.some((permission) => assigned.includes(permission));
  }, [user]);

  useEffect(() => {
    void refreshCurrentUser();
  }, [refreshCurrentUser]);

  useEffect(() => {
    const onUnauthorized = () => {
      clearAuthSession('cleared');
      setCoreTenantContext('');
      setUser(null);
      setTenants([]);
      setStatus('UNAUTHENTICATED');
      router.replace('/login');
    };
    window.addEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
  }, [router]);

  useEffect(() => {
    if (authBypassed) return;
    if (status === 'UNAUTHENTICATED' && pathname !== '/login') router.replace('/login');
    if (status === 'AUTHENTICATED' && pathname === '/login') router.replace('/dashboard');
  }, [authBypassed, pathname, router, status]);

  const value = useMemo<AuthContextValue>(() => ({
    status,
    user,
    tenants,
    selectedTenantId: user?.selectedTenantId ?? '',
    tenantChanging,
    login,
    logout,
    refreshCurrentUser,
    selectTenant,
    hasRole,
    hasPermission
  }), [hasPermission, hasRole, login, logout, refreshCurrentUser, selectTenant, status, tenantChanging, tenants, user]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
