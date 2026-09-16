import { canonicalTenantWorkspaceRoute, type AccessManagementSearchParams } from './canonicalAccessManagementRoute';

/**
 * Temporary compatibility policy for pre-v17 Access Management URLs.
 *
 * These routes are not part of product navigation. They exist only so old
 * bookmarks and external runbooks land on the canonical Tenant workspace.
 * Every hit is logged by the server component so release owners can make a
 * data-backed removal decision before the sunset date.
 */
export const LEGACY_ACCESS_MANAGEMENT_SUNSET = '2026-11-10';

export interface LegacyAccessRouteResolution {
  legacyPath: string;
  target: string;
  known: boolean;
  sunset: string;
}

export function resolveLegacyAccessManagementRoute(
  segments: readonly string[],
  searchParams: AccessManagementSearchParams,
): LegacyAccessRouteResolution {
  const legacyPath = segments.join('/').replace(/^\/+|\/+$/g, '');
  const target = (() => {
    switch (legacyPath) {
      case 'users':
        return canonicalTenantWorkspaceRoute(searchParams, 'people');
      case 'organization/departments':
        return canonicalTenantWorkspaceRoute(searchParams, 'organization', { type: 'DEPARTMENT' });
      case 'organization/groups':
        return canonicalTenantWorkspaceRoute(searchParams, 'organization', { type: 'GROUP' });
      case 'assignments':
        return canonicalTenantWorkspaceRoute(searchParams, 'access', { view: 'ASSIGNMENTS' });
      case 'roles':
        return canonicalTenantWorkspaceRoute(searchParams, 'access', { view: 'TEMPLATES' });
      case 'effective-access':
        return canonicalTenantWorkspaceRoute(searchParams, 'access', { view: 'EFFECTIVE' });
      case 'security-audit':
        return canonicalTenantWorkspaceRoute(searchParams, 'security', { view: 'AUDIT' });
      case 'service-accounts':
        return canonicalTenantWorkspaceRoute(searchParams, 'security', { view: 'CREDENTIALS' });
      case 'tenants':
        return '/admin/tenants';
      default:
        return '/admin/tenants';
    }
  })();

  return {
    legacyPath: legacyPath || '(root)',
    target,
    known: new Set([
      'users',
      'organization/departments',
      'organization/groups',
      'assignments',
      'roles',
      'effective-access',
      'security-audit',
      'service-accounts',
      'tenants',
    ]).has(legacyPath),
    sunset: LEGACY_ACCESS_MANAGEMENT_SUNSET,
  };
}

export function recordLegacyAccessManagementHit(resolution: LegacyAccessRouteResolution): void {
  console.warn(JSON.stringify({
    event: 'LEGACY_ADMIN_ROUTE_REDIRECT',
    surface: 'access-management',
    legacyPath: resolution.legacyPath,
    target: resolution.target,
    known: resolution.known,
    retireAfter: resolution.sunset,
  }));
}
