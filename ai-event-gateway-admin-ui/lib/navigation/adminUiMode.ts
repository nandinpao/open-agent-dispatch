import { translate as t } from '@/lib/i18n';
export type AdminUiMode = 'basic' | 'advanced' | 'developer';

export const ADMIN_UI_MODE_STORAGE_KEY = 'opendispatch.adminUiMode';

export interface AdminUiModeOption {
  value: AdminUiMode;
  label: string;
  shortLabel: string;
  description: string;
}

export const adminUiModeOptions: AdminUiModeOption[] = [
  {
    value: 'basic',
    label: t('mode.basic.label'),
    shortLabel: t('mode.basic.short'),
    description: t('mode.basic.description'),
  },
  {
    value: 'advanced',
    label: t('mode.advanced.label'),
    shortLabel: t('mode.advanced.short'),
    description: t('mode.advanced.description'),
  },
  {
    value: 'developer',
    label: t('mode.developer.label'),
    shortLabel: t('mode.developer.short'),
    description: t('mode.developer.description'),
  },
];

const modeRank: Record<AdminUiMode, number> = {
  basic: 0,
  advanced: 1,
  developer: 2,
};

export function normalizeAdminUiMode(value: unknown): AdminUiMode {
  return value === 'advanced' || value === 'developer' || value === 'basic' ? value : 'basic';
}

export function canAccessAdminUiMode(currentMode: AdminUiMode, requiredMode: AdminUiMode = 'basic'): boolean {
  return modeRank[currentMode] >= modeRank[requiredMode];
}

export function getAdminUiModeOption(mode: AdminUiMode): AdminUiModeOption {
  return adminUiModeOptions.find((option) => option.value === mode) ?? adminUiModeOptions[0];
}

const DEVELOPER_EXACT_ROUTES = ['/operations'] as const;

const DEVELOPER_ROUTE_PREFIXES = [
  '/engineering-tools',
  '/events',
  '/runtime/rejected-connections',
  '/cluster',
  '/websocket',
  '/resource-access',
  '/settings/runtime-features',
  '/settings/runtime-resources',
  '/settings/fast-path-runtime',
  '/settings/routing-authority',
  '/settings/migration-readiness',
  '/settings/enforce-observability',
  '/settings/quality-metrics',
] as const;

const ADVANCED_ROUTE_PREFIXES = [
  '/a2a-operations',
  '/a2a-governance',
  '/agents/runtime',
  '/operations/integration-sync',
  '/platform-administration',
  '/testing/dispatch-simulator',
  '/settings/capabilities',
  '/settings/delegation-governance',
  '/settings/provider-routing',
  '/settings/execution-adapters',
  '/settings/execution-safety',
  '/settings/semantic-triage',
  '/settings/execution-plans',
  '/settings/plan-executions',
  '/settings/runtime-step-authority',
  '/settings/learning-governance',
  '/settings/learning-fast-path',
  '/settings/case-convergence',
  '/settings/runtime-configuration',
  '/settings/release-certification',
] as const;

function matchesRoutePrefix(pathname: string, prefix: string): boolean {
  return pathname === prefix || pathname.startsWith(`${prefix}/`);
}

/**
 * Presentation-only route depth. This never grants or removes backend authority.
 * It prevents a copied deep link from unexpectedly dumping engineering surfaces
 * into Basic mode, while keeping a one-click path to reveal the requested layer.
 */
export function requiredAdminUiModeForPath(pathname: string): AdminUiMode {
  if (DEVELOPER_EXACT_ROUTES.includes(pathname as (typeof DEVELOPER_EXACT_ROUTES)[number])) return 'developer';
  if (DEVELOPER_ROUTE_PREFIXES.some((prefix) => matchesRoutePrefix(pathname, prefix))) return 'developer';
  if (ADVANCED_ROUTE_PREFIXES.some((prefix) => matchesRoutePrefix(pathname, prefix))) return 'advanced';
  return 'basic';
}
