/**
 * Presentation-route entitlement contract.
 *
 * These feature IDs are projected by Core UiFeatureEntitlementService. This file
 * does not authorize anything; it only keeps App Router guards aligned with the
 * canonical Core feature projection so nested pages cannot silently lose UX gates.
 */
export const ROUTE_FAMILY_FEATURES = {
  agents: 'agents',
  delegations: 'a2a-operations',
  resourceAccess: 'resource-access',
  settings: 'administration',
  integrations: 'integrations',
} as const;

export function settingsFeatureForPath(pathname: string): string {
  return pathname === '/settings/integrations' || pathname.startsWith('/settings/integrations/')
    ? ROUTE_FAMILY_FEATURES.integrations
    : ROUTE_FAMILY_FEATURES.settings;
}
