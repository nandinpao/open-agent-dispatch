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
  delegationGovernance: 'delegation-governance',
  providerRouting: 'provider-routing',
  releaseCertification: 'release-certification',
  runtimeConfiguration: 'runtime-configuration',
} as const;

/**
 * Settings contains multiple authority families. Longest/specific routes must be
 * matched before the generic Settings fallback so a direct URL receives the same
 * Core feature projection as the Settings Hub and page actions.
 */
export const SETTINGS_ROUTE_FEATURES: ReadonlyArray<readonly [string, string]> = [
  ['/settings/runtime-configuration', ROUTE_FAMILY_FEATURES.runtimeConfiguration],
  ['/settings/integrations', ROUTE_FAMILY_FEATURES.integrations],
  ['/settings/issue-tracking', ROUTE_FAMILY_FEATURES.integrations],
  ['/settings/delegation-governance', ROUTE_FAMILY_FEATURES.delegationGovernance],
  ['/settings/provider-routing', ROUTE_FAMILY_FEATURES.providerRouting],
  ['/settings/release-certification', ROUTE_FAMILY_FEATURES.releaseCertification],
  ['/settings/runtime-acceptance', ROUTE_FAMILY_FEATURES.releaseCertification],
  ['/settings/production-foundation', ROUTE_FAMILY_FEATURES.releaseCertification],
  ['/settings/release-cutover', ROUTE_FAMILY_FEATURES.releaseCertification],
];

export function settingsFeatureForPath(pathname: string): string {
  const normalized = pathname.split('?')[0].replace(/\/$/, '') || '/';
  const matched = SETTINGS_ROUTE_FEATURES.find(([route]) => normalized === route || normalized.startsWith(`${route}/`));
  return matched?.[1] ?? ROUTE_FAMILY_FEATURES.settings;
}
