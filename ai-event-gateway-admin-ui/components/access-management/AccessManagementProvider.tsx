'use client';

import type { ReactNode } from 'react';
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { useAuth } from '@/components/auth/AuthProvider';
import { accessManagementApi } from '@/lib/api/accessManagementApi';
import { iamAuthApi } from '@/lib/api/iamAuthApi';
import { ApiError } from '@/lib/api/errors';
import { useUiEntitlements } from '@/lib/navigation/useUiEntitlements';
import type { Tenant } from '@/lib/iam/types';

export type AccessManagementPreflightState = 'CHECKING' | 'READY' | 'FAILED';

export interface AccessManagementPreflightError {
  status?: number;
  code?: string;
  message: string;
  correlationId?: string;
}

interface AccessManagementContextValue {
  instanceRoot: boolean;
  tenants: Tenant[];
  scopeTenantId: string;
  allTenants: boolean;
  loadingTenants: boolean;
  tenantError: string;
  preflightState: AccessManagementPreflightState;
  preflightError: AccessManagementPreflightError | null;
  retryPreflight: () => Promise<void>;
  setScopeTenantId: (tenantId: string) => Promise<void>;
  refreshTenants: () => Promise<void>;
  requireTenant: () => string;
}

const AccessManagementContext = createContext<AccessManagementContextValue | null>(null);

function tenantIdFromWorkspacePath(pathname: string): string {
  const match = pathname.match(/^\/admin\/tenants\/([^/?#]+)/);
  return match?.[1] ? decodeURIComponent(match[1]).trim() : '';
}

function replaceWorkspaceTenant(pathname: string, tenantId: string): string {
  const encoded = encodeURIComponent(tenantId);
  if (/^\/admin\/tenants\/[^/]+/.test(pathname)) {
    return pathname.replace(/^\/admin\/tenants\/[^/]+/, `/admin/tenants/${encoded}`);
  }
  return `/admin/tenants/${encoded}`;
}

export function useAccessManagement() {
  const context = useContext(AccessManagementContext);
  if (!context) throw new Error('useAccessManagement must be used inside AccessManagementProvider.');
  return context;
}

export function AccessManagementProvider({ children }: Readonly<{ children: ReactNode }>) {
  const { selectedTenantId, administrationTenantId, setAdministrationTenantId, hasPermission } = useAuth();
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const entitlements = useUiEntitlements();
  const instanceRoot = entitlements.value?.workspaceKind === 'INSTANCE_ROOT';
  const [tenants, setTenants] = useState<Tenant[]>([]);
  const [scopeTenantId, setScopeTenantIdState] = useState(() => instanceRoot ? (tenantIdFromWorkspacePath(pathname) || searchParams.get('tenantId')?.trim() || administrationTenantId || '') : selectedTenantId);
  const [loadingTenants, setLoadingTenants] = useState(false);
  const [tenantDirectoryLoaded, setTenantDirectoryLoaded] = useState(false);
  const [tenantError, setTenantError] = useState('');
  const [preflightState, setPreflightState] = useState<AccessManagementPreflightState>('CHECKING');
  const [preflightError, setPreflightError] = useState<AccessManagementPreflightError | null>(null);

  const retryPreflight = useCallback(async () => {
    setPreflightState('CHECKING');
    setPreflightError(null);
    try {
      await iamAuthApi.preflight();
      setPreflightState('READY');
    } catch (cause) {
      if (cause instanceof ApiError) {
        setPreflightError({
          status: cause.status,
          code: cause.code,
          message: cause.message,
          correlationId: cause.correlationId,
        });
      } else {
        setPreflightError({ message: cause instanceof Error ? cause.message : 'The management session could not be verified.' });
      }
      setPreflightState('FAILED');
    }
  }, []);

  const refreshTenants = useCallback(async () => {
    if (!instanceRoot && !hasPermission('instance.tenant.read')) return;
    setLoadingTenants(true);
    setTenantDirectoryLoaded(false);
    setTenantError('');
    try {
      const page = await accessManagementApi.tenants(0, 100);
      setTenants(page.items);
    } catch (cause) {
      setTenantError(cause instanceof Error ? cause.message : 'Unable to load Tenant scope choices.');
    } finally {
      setLoadingTenants(false);
      setTenantDirectoryLoaded(true);
    }
  }, [hasPermission, instanceRoot]);

  useEffect(() => { void retryPreflight(); }, [retryPreflight]);

  useEffect(() => {
    if (!instanceRoot) {
      setScopeTenantIdState(selectedTenantId);
      return;
    }
    const routeTenant = tenantIdFromWorkspacePath(pathname) || searchParams.get('tenantId')?.trim() || administrationTenantId || '';
    setScopeTenantIdState(routeTenant);
    if (routeTenant && routeTenant !== administrationTenantId) setAdministrationTenantId(routeTenant);
  }, [administrationTenantId, instanceRoot, pathname, searchParams, selectedTenantId, setAdministrationTenantId]);

  useEffect(() => {
    if (preflightState === 'READY') void refreshTenants();
  }, [preflightState, refreshTenants]);


  useEffect(() => {
    if (!instanceRoot || loadingTenants || !tenantDirectoryLoaded || !scopeTenantId) return;
    if (tenants.some((tenant) => tenant.tenantId === scopeTenantId)) return;
    setScopeTenantIdState('');
    setAdministrationTenantId('');
    const next = new URLSearchParams(searchParams.toString());
    next.delete('tenantId');
    router.replace(pathname.startsWith('/admin/tenants/')
      ? '/admin/tenants'
      : next.size ? `${pathname}?${next.toString()}` : pathname);
    setTenantError('The Tenant in this URL is no longer available. Select another Tenant.');
  }, [instanceRoot, loadingTenants, tenantDirectoryLoaded, pathname, router, scopeTenantId, searchParams, tenants, setAdministrationTenantId]);

  const setScopeTenantId = useCallback(async (tenantId: string) => {
    const normalized = tenantId.trim();
    if (instanceRoot) {
      setScopeTenantIdState(normalized);
      setAdministrationTenantId(normalized);
      if (pathname.startsWith('/admin/tenants/')) {
        router.replace(normalized ? replaceWorkspaceTenant(pathname, normalized) : '/admin/tenants');
        return;
      }
      const next = new URLSearchParams(searchParams.toString());
      if (normalized) next.set('tenantId', normalized);
      else next.delete('tenantId');
      router.replace(normalized ? `/admin/tenants/${encodeURIComponent(normalized)}` : next.size ? `${pathname}?${next.toString()}` : pathname);
      return;
    }
    if (normalized && normalized !== selectedTenantId) {
      throw new Error('Tenant context is fixed by the authenticated home workspace and cannot be switched from this session.');
    }
    setScopeTenantIdState(selectedTenantId);
  }, [instanceRoot, pathname, router, searchParams, selectedTenantId, setAdministrationTenantId]);

  const requireTenant = useCallback(() => {
    if (!scopeTenantId) throw new Error('The authenticated Tenant workspace is unavailable for this administration view.');
    return scopeTenantId;
  }, [scopeTenantId]);

  const value = useMemo<AccessManagementContextValue>(() => ({
    instanceRoot,
    tenants,
    scopeTenantId,
    allTenants: instanceRoot && !scopeTenantId,
    loadingTenants,
    tenantError,
    preflightState,
    preflightError,
    retryPreflight,
    setScopeTenantId,
    refreshTenants,
    requireTenant,
  }), [instanceRoot, tenants, scopeTenantId, loadingTenants, tenantError, preflightState, preflightError, retryPreflight, setScopeTenantId, refreshTenants, requireTenant]);

  return <AccessManagementContext.Provider value={value}>{children}</AccessManagementContext.Provider>;
}
