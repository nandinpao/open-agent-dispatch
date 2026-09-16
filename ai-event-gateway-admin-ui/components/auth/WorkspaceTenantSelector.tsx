'use client';

import { usePathname, useRouter } from 'next/navigation';
import { useAuth } from '@/components/auth/AuthProvider';
import { useUiEntitlements } from '@/lib/navigation/useUiEntitlements';

interface WorkspaceTenantSelectorProps {
  compact?: boolean;
  className?: string;
}

function tenantLabel(tenant: {
  tenantId: string;
  tenantCode?: string;
  tenantName?: string;
  membershipStatus?: string;
}): string {
  const name = tenant.tenantName || tenant.tenantCode || tenant.tenantId;
  const suffix = tenant.membershipStatus && tenant.membershipStatus !== 'ACTIVE'
    ? ` · ${tenant.membershipStatus}`
    : '';
  return `${name}${suffix}`;
}

export function tenantFromAdministrationPath(pathname: string): string {
  const match = pathname.match(/^\/admin\/tenants\/([^/?#]+)/);
  return match?.[1] ? decodeURIComponent(match[1]) : '';
}

export function replaceAdministrationTenantPath(pathname: string, tenantId: string): string {
  const normalized = tenantId.trim();
  if (!/^\/admin\/tenants\/[^/?#]+/.test(pathname)) return pathname;
  if (!normalized) return '/admin/tenants';
  return pathname.replace(/^\/admin\/tenants\/[^/?#]+/, `/admin/tenants/${encodeURIComponent(normalized)}`);
}

/**
 * Displays the authenticated workspace for normal People and an administration-context selector
 * for Instance Root only. A human user never chooses a Tenant during or after sign-in: the backend
 * session is bound to the Default Tenant Membership configured by an administrator.
 */
export function WorkspaceTenantSelector({
  compact = false,
  className = '',
}: Readonly<WorkspaceTenantSelectorProps>) {
  const { tenants, selectedTenantId, administrationTenantId, setAdministrationTenantId, status } = useAuth();
  const router = useRouter();
  const pathname = usePathname();
  const entitlements = useUiEntitlements();
  const authorityResolved = Boolean(entitlements.value);
  const instanceRoot = entitlements.value?.workspaceKind === 'INSTANCE_ROOT';

  if (!instanceRoot) {
    const tenant = tenants.find((item) => item.tenantId === selectedTenantId);
    const value = tenant ? tenantLabel(tenant) : selectedTenantId || 'Workspace unavailable';
    return (
      <div className={`flex min-w-0 flex-col gap-1 ${className}`} aria-label="Current workspace">
        {!compact ? (
          <span className="text-xs font-black uppercase tracking-[0.14em] text-slate-500">
            Workspace
          </span>
        ) : null}
        <div
          role="status"
          className="max-w-[18rem] truncate rounded-xl border border-slate-200 bg-slate-50 px-3 py-1.5 text-xs font-bold text-slate-700 shadow-sm"
          title={value}
        >
          {value}
        </div>
      </div>
    );
  }

  const value = administrationTenantId;
  const disabled = status !== 'AUTHENTICATED' || !authorityResolved || tenants.length === 0;
  const changeAdministrationTenant = (tenantId: string) => {
    const normalized = tenantId.trim();
    // Root keeps one Instance Session. Selecting an Administration Tenant changes only the
    // request-scoped X-Tenant-Id context. It must never navigate a Dashboard/Task/Agent user to
    // People & Access. If the operator is already inside a Tenant resource route, preserve the
    // current People & Access section and replace only the Tenant segment.
    setAdministrationTenantId(normalized);
    const currentPathTenant = tenantFromAdministrationPath(pathname);
    if (currentPathTenant) {
      router.replace(replaceAdministrationTenantPath(pathname, normalized));
    }
  };

  return (
    <label className={`flex min-w-0 flex-col gap-1 ${className}`}>
      {!compact ? (
        <span className="text-xs font-black uppercase tracking-[0.14em] text-slate-500">
          Administration Tenant
        </span>
      ) : null}
      <select
        aria-label="Administration Tenant"
        value={value}
        disabled={disabled}
        onChange={(event) => changeAdministrationTenant(event.target.value)}
        className="max-w-[18rem] rounded-xl border border-slate-200 bg-white px-3 py-1.5 text-xs font-bold text-slate-700 shadow-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100 disabled:cursor-not-allowed disabled:bg-slate-50 disabled:text-slate-500"
      >
        <option value="">All Tenants</option>
        {tenants.map((tenant) => (
          <option key={tenant.tenantId} value={tenant.tenantId}>
            {tenantLabel(tenant)}
          </option>
        ))}
      </select>
    </label>
  );
}

export function WorkspaceTenantField({
  label = 'Workspace',
  className = '',
}: Readonly<{ label?: string; className?: string }>) {
  const { selectedTenantId, administrationTenantId, user, tenants } = useAuth();
  const instanceRoot = user?.roles.includes('INSTANCE_ROOT') === true;
  const workspaceTenantId = instanceRoot ? administrationTenantId : selectedTenantId;
  const tenant = tenants.find((item) => item.tenantId === workspaceTenantId);
  return (
    <label className={`flex flex-col gap-1 text-xs font-semibold uppercase tracking-wide text-slate-500 ${className}`}>
      {label}
      <input
        aria-label={label}
        value={tenant ? tenantLabel(tenant) : workspaceTenantId || 'Instance scope'}
        readOnly
        className="rounded-lg border border-slate-200 bg-slate-100 px-3 py-2 text-sm font-semibold normal-case tracking-normal text-slate-700 shadow-sm"
      />
      <span className="normal-case font-normal tracking-normal text-slate-500">
        {instanceRoot
          ? 'Instance Root uses the global Administration Tenant selector. Forms inherit that context and cannot override it.'
          : 'Workspace context is resolved from the authenticated Session and cannot be selected or overridden by a form field.'}
      </span>
    </label>
  );
}
