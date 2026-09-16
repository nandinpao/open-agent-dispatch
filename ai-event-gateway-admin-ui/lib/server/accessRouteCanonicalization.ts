export type CanonicalCoreProxyTarget = {
  path: string[];
  query: URLSearchParams;
  rewritten: boolean;
};

/**
 * Converts the explicitly retired query/header-only Tenant access routes emitted by an older
 * Admin UI bundle into their canonical Tenant-addressed forms before the request reaches Core.
 * The compatibility boundary is intentionally restricted to known IAM routes and never derives
 * a Tenant from arbitrary request bodies. Core still validates the path Tenant and X-Tenant-Id.
 */
export function canonicalizeLegacyTenantAccessTarget(
  path: string[],
  sourceQuery: URLSearchParams,
  tenantHeader?: string | null,
): CanonicalCoreProxyTarget {
  const query = new URLSearchParams(sourceQuery.toString());
  const queryTenant = query.get('tenantId')?.trim() ?? '';
  const headerTenant = tenantHeader?.trim() ?? '';
  if (queryTenant && headerTenant && queryTenant !== headerTenant) {
    return { path, query, rewritten: false };
  }
  const tenantId = queryTenant || headerTenant;
  if (!tenantId) return { path, query, rewritten: false };

  let canonicalPath: string[] | null = null;
  const dashboardRoute = path.length >= 3 && path[0] === 'admin'
    ? path.slice(1).join('/')
    : '';
  const tenantDashboardRoutes = new Set([
    'dashboard/snapshot',
    'agents/runtime-view',
    'tasks/runtime-view',
    'security-events',
    'agent-governance/summary',
  ]);
  if (tenantDashboardRoutes.has(dashboardRoute)) {
    canonicalPath = ['admin', 'tenants', tenantId, ...path.slice(1)];
  } else {
    const accessPrefix = path.length >= 4
      && path[0] === 'api'
      && path[1] === 'admin'
      && path[2] === 'access';
    if (!accessPrefix) return { path, query, rewritten: false };
  }

  if (canonicalPath) {
    query.delete('tenantId');
    return { path: canonicalPath, query, rewritten: true };
  }

  if (path.length === 4 && path[3] === 'user-onboarding') {
    canonicalPath = ['api', 'admin', 'access', 'tenants', tenantId, 'user-onboarding'];
  } else if (path.length >= 6
      && path.length <= 7
      && path[3] === 'users'
      && path[5] === 'invitation'
      && (path.length === 6 || path[6] === 'resend' || path[6] === 'revoke')) {
    canonicalPath = [
      'api', 'admin', 'access', 'tenants', tenantId, 'users', path[4], 'invitation',
      ...(path.length === 7 ? [path[6]] : []),
    ];
  }

  if (!canonicalPath) return { path, query, rewritten: false };
  query.delete('tenantId');
  return { path: canonicalPath, query, rewritten: true };
}

