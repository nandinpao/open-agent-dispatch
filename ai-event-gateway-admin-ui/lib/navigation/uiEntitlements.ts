export type UiWorkspaceKind = 'TENANT' | 'INSTANCE_ROOT';
export type UiEntitlementSurface = 'TENANT' | 'PLATFORM' | 'INTERNAL_ENGINEERING';
export type UiDisplayMode = 'HIDDEN' | 'READ_ONLY' | 'ENABLED';

export interface UiNavigationItem {
  featureId: string;
  parentFeatureId?: string | null;
  section: string;
  order: number;
  route: string;
  label: string;
  purpose: string;
  displayMode: UiDisplayMode;
  children: UiNavigationItem[];
}

export interface UiPageEntitlement {
  featureId: string;
  route: string;
  displayMode: UiDisplayMode;
  /** @deprecated Contract 3.0 compatibility projection. Use displayMode. */
  allowed: boolean;
  denialReason: string;
  surface: UiEntitlementSurface;
}

export interface UiActionEntitlement {
  actionId: string;
  displayMode: 'HIDDEN' | 'ENABLED';
  scopes: string[];
}

export interface UiEntitlementResponse {
  contractVersion: '3.0';
  workspaceKind: UiWorkspaceKind;
  tenantId: string;
  navigation: UiNavigationItem[];
  pages: Record<string, UiPageEntitlement>;
  /** @deprecated Contract 2.x compatibility projection. Use actionEntitlements. */
  actions: string[];
  actionEntitlements: Record<string, UiActionEntitlement>;
  /** @deprecated Contract 2.x compatibility projection. Use actionEntitlements[actionId].scopes. */
  actionScopes?: Record<string, string[]>;
  generatedAt: string;
}

export function featureDisplayMode(entitlements: UiEntitlementResponse | undefined, featureId: string): UiDisplayMode {
  const page = entitlements?.pages?.[featureId];
  if (page?.displayMode) return page.displayMode;
  return page?.allowed === true ? 'READ_ONLY' : 'HIDDEN';
}

export function featureAllowed(entitlements: UiEntitlementResponse | undefined, featureId: string): boolean {
  return featureDisplayMode(entitlements, featureId) !== 'HIDDEN';
}

export function featureWritable(entitlements: UiEntitlementResponse | undefined, featureId: string): boolean {
  return featureDisplayMode(entitlements, featureId) === 'ENABLED';
}

export function actionAllowed(entitlements: UiEntitlementResponse | undefined, actionId: string): boolean {
  const projected = entitlements?.actionEntitlements?.[actionId];
  if (projected) return projected.displayMode === 'ENABLED';
  return entitlements?.actions?.includes(actionId) === true;
}

export function actionScopes(entitlements: UiEntitlementResponse | undefined, actionId: string): string[] {
  return entitlements?.actionEntitlements?.[actionId]?.scopes ?? entitlements?.actionScopes?.[actionId] ?? [];
}

export function actionAllowedForScope(
  entitlements: UiEntitlementResponse | undefined,
  actionId: string,
  scopeType: 'TENANT' | 'DEPARTMENT' | 'GROUP',
  scopeId: string,
  departmentAncestorIds: string[] = [],
): boolean {
  if (!actionAllowed(entitlements, actionId)) return false;
  const scopes = actionScopes(entitlements, actionId);
  if (scopes.includes('*')) return true;
  const tenantId = entitlements?.tenantId ?? '';
  if (tenantId && scopes.includes(`TENANT:${tenantId}`)) return true;
  if (scopeType === 'TENANT') return Boolean(scopeId) && scopes.includes(`TENANT:${scopeId}`);
  if (scopeType === 'GROUP') return scopes.includes(`GROUP:${scopeId}`);
  if (scopes.includes(`DEPARTMENT:${scopeId}`)) return true;
  const ancestors = new Set([scopeId, ...departmentAncestorIds]);
  return scopes.some((scope) => scope.startsWith('DEPARTMENT_SUBTREE:') && ancestors.has(scope.slice('DEPARTMENT_SUBTREE:'.length)));
}

export function actionHasAnyScopedAuthority(
  entitlements: UiEntitlementResponse | undefined,
  actionId: string,
): boolean {
  if (!actionAllowed(entitlements, actionId)) return false;
  const scopes = actionScopes(entitlements, actionId);
  return scopes.includes('*') || scopes.some((scope) => /^(TENANT|DEPARTMENT|DEPARTMENT_SUBTREE|GROUP):/.test(scope));
}

export function resolveEntitlementRoute(route: string, tenantId?: string): string {
  if (!route.includes('{tenantId}')) return route;
  const normalized = tenantId?.trim();
  if (!normalized) return route.replace('/{tenantId}', '').replace('{tenantId}', '');
  return route.replace('{tenantId}', encodeURIComponent(normalized));
}

export function navigationChildren(entitlements: UiEntitlementResponse | undefined, parentFeatureId: string): UiNavigationItem[] {
  return entitlements?.navigation?.find((item) => item.featureId === parentFeatureId)?.children ?? [];
}
