export type AccessManagementSearchParams = Record<string, string | string[] | undefined>;

type TenantWorkspaceSection = 'people' | 'organization' | 'access' | 'security';

export function canonicalTenantWorkspaceRoute(
  searchParams: AccessManagementSearchParams,
  section: TenantWorkspaceSection,
  forcedParams: Record<string, string | undefined> = {},
): string {
  const rawTenantId = searchParams.tenantId;
  const tenantId = typeof rawTenantId === 'string' ? rawTenantId.trim() : '';
  if (!tenantId) return '/admin/tenants';

  const next = new URLSearchParams();
  for (const [key, value] of Object.entries(searchParams)) {
    if (key === 'tenantId' || typeof value !== 'string' || !value.trim()) continue;
    next.set(key, value);
  }
  for (const [key, value] of Object.entries(forcedParams)) {
    if (value) next.set(key, value);
    else next.delete(key);
  }

  const query = next.toString();
  const base = `/admin/tenants/${encodeURIComponent(tenantId)}/${section}`;
  return query ? `${base}?${query}` : base;
}
