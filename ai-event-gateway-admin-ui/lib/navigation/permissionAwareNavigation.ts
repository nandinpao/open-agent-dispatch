import { resolveEntitlementRoute, type UiDisplayMode, type UiEntitlementResponse, type UiNavigationItem } from '@/lib/navigation/uiEntitlements';

/** Compatibility facade for consumers of /api/ui/navigation.
 * Authorization and navigation metadata are projected exclusively by backend UI Entitlements 3.0.
 */
export type NavigationWorkspaceKind = 'TENANT' | 'INSTANCE_ROOT';
export interface PermissionAwareNavigationItem {
  navigationId: string;
  featureId: string;
  parentFeatureId?: string | null;
  section: string;
  order: number;
  href: string;
  label: string;
  purpose: string;
  displayMode: Exclude<UiDisplayMode, 'HIDDEN'>;
  children: PermissionAwareNavigationItem[];
}
export interface PermissionAwareNavigationResponse {
  contractVersion: '3.0';
  workspaceKind: NavigationWorkspaceKind;
  tenantId: string;
  items: PermissionAwareNavigationItem[];
  generatedAt: string;
}

function project(item: UiNavigationItem, tenantId: string): PermissionAwareNavigationItem {
  return {
    navigationId: `feature.${item.featureId}`,
    featureId: item.featureId,
    parentFeatureId: item.parentFeatureId ?? null,
    section: item.section,
    order: item.order,
    href: resolveEntitlementRoute(item.route, tenantId),
    label: item.label,
    purpose: item.purpose,
    displayMode: item.displayMode === 'READ_ONLY' ? 'READ_ONLY' : 'ENABLED',
    children: item.children.map(child => project(child, tenantId)),
  };
}

export function navigationFromEntitlements(value: UiEntitlementResponse): PermissionAwareNavigationResponse {
  return {
    contractVersion: '3.0',
    workspaceKind: value.workspaceKind,
    tenantId: value.tenantId,
    items: value.navigation.map(item => project(item, value.tenantId)),
    generatedAt: value.generatedAt,
  };
}
