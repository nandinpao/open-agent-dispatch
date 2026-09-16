import type { AdminUiMode } from '@/lib/navigation/adminUiMode';
import { translate as t } from '@/lib/i18n';

export type AdminInformationLayerId = 'business' | 'dispatch' | 'runtime' | 'operations' | 'advanced' | 'system';

export const adminInformationArchitecturePrinciples = [
  { layer: 'Business Truth', authority: 'CORE_AUTHORITY', sourceOfTruth: 'Core PostgreSQL / Core Admin API' },
  { layer: 'Dispatch Truth', authority: 'DISPATCH_FLOW', sourceOfTruth: 'Dispatch Flow / Task / Assignment' },
] as const;

export interface AdminNavigationItem {
  navigationId?: string;
  href: string;
  label: string;
  purpose: string;
  requiredMode?: AdminUiMode;
  requiredPermission?: string;
  iamOnly?: boolean;
  rootOnly?: boolean;
  tenantOnly?: boolean;
  resourceAccessOnly?: boolean;
  workspaceUniversal?: boolean;
  accountManagementOnly?: boolean;
}

export interface AdminInformationLayer {
  id: AdminInformationLayerId;
  badge: string;
  title: string;
  shortTitle: string;
  sourceOfTruth: string;
  description: string;
  operatorQuestion: string;
  warning: string;
  requiredMode: AdminUiMode;
  primaryLinks: AdminNavigationItem[];
}

/**
 * Phase 0I product navigation.
 *
 * Normal operators use a single English navigation model. Historical catalogs,
 * migration tools, raw runtime pages, simulators, and compatibility dispatch
 * models remain outside primary navigation.
 */
export const adminPrimaryNavigation: AdminNavigationItem[] = [
  // Documentation/onboarding links only. Runtime Menu/Page/Action authorization is
  // supplied by Core /api/session/entitlements and must not be inferred from this list.
  { href: '/dashboard', label: t('nav.dashboard'), purpose: t('nav.dashboard.purpose') },
  { href: '/source-systems', label: t('nav.sourceSystems'), purpose: t('nav.sourceSystems.purpose') },
  { href: '/dispatch-flows', label: t('nav.dispatch'), purpose: t('nav.dispatch.purpose') },
  { href: '/agents', label: t('nav.agents'), purpose: t('nav.agents.purpose') },
  { href: '/tasks', label: t('nav.tasks'), purpose: t('nav.tasks.purpose') },
  { href: '/operations/observability', label: 'Observability', purpose: 'Trace execution decisions, review economics, and inspect executor-level memory.' },
  { href: '/a2a-operations', label: 'Delegations', purpose: 'Review governed delegated work, blockers and results.' },
  { href: '/issues-events', label: t('nav.incidents'), purpose: t('nav.incidents.purpose') },
  { href: '/settings/integrations', label: t('nav.integrations'), purpose: t('nav.integrations.purpose') },
  { href: '/settings', label: t('nav.configurationGovernance'), purpose: t('nav.configurationGovernance.purpose') },
  { href: '/admin/tenants', label: 'People & Access', purpose: 'Manage people, organization, responsibilities, sign-in security and audit from one workspace.' },
];

export const adminAdvancedNavigation: AdminNavigationItem[] = [];
export const adminDeveloperNavigation: AdminNavigationItem[] = [];

export const adminInformationLayers: AdminInformationLayer[] = [
  {
    id: 'operations',
    badge: 'OPERATIONS',
    title: t('nav.group.daily'),
    shortTitle: 'Operations',
    requiredMode: 'basic',
    sourceOfTruth: 'Core Agent, Dispatch Flow, Task, and Issue state',
    description: 'Operate Tasks, Agents, delegated work and incidents from one canonical workflow; configuration stays separate.',
    operatorQuestion: 'Where should I monitor work, inspect delegated execution, or resolve a blocked Task?',
    warning: 'Dispatch is the only current setup entry: Source Flow → Agent Pool → Pool Member Agent.',
    primaryLinks: adminPrimaryNavigation.filter((link) => !['/settings', '/settings/integrations'].includes(link.href)),
  },
  {
    id: 'system',
    badge: 'ADMINISTRATION',
    title: t('nav.group.configuration'),
    shortTitle: 'Admin',
    requiredMode: 'basic',
    sourceOfTruth: 'Authentication, environment, integrations, and permissions',
    description: 'Manage integrations, identity, environment, security, and governance settings.',
    operatorQuestion: 'Where should I manage Provider connections, security, or platform settings?',
    warning: 'Administration does not create a second dispatch model; dispatch configuration remains under Dispatch.',
    primaryLinks: [
      { href: '/settings/integrations', label: t('nav.integrations'), purpose: t('nav.integrations.purpose') },
      { href: '/settings', label: t('nav.configurationGovernance'), purpose: t('nav.configurationGovernance.purpose') },
      { href: '/security-events', label: t('nav.securityEvents'), purpose: t('nav.securityEvents.purpose') },
      { href: '/admin/tenants', label: 'People & Access', purpose: 'Manage people, organization, access, sign-in security and audit from one workspace.', iamOnly: true, workspaceUniversal: true, accountManagementOnly: true },
      { href: '/agent-enrollments', label: t('nav.agentEnrollmentReview'), purpose: t('nav.agentEnrollmentReview.purpose') },
    ],
  },
];

export function getAdminInformationLayer(layerId: AdminInformationLayerId): AdminInformationLayer {
  return adminInformationLayers.find((layer) => layer.id === layerId) ?? adminInformationLayers[0];
}
