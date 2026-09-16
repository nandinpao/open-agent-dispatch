import { translate as t } from '@/lib/i18n';
import type { AdminInformationLayer, AdminInformationLayerId, AdminNavigationItem } from '@/lib/navigation/adminInformationArchitecture';

const dailyLinks: AdminNavigationItem[] = [
  { href: '/dashboard', label: t('nav.dashboard'), purpose: t('nav.dashboard.purpose') },
  { href: '/source-systems', label: t('nav.sourceSystems'), purpose: t('nav.sourceSystems.purpose') },
  { href: '/dispatch-flows', label: t('nav.dispatch'), purpose: t('nav.dispatch.purpose') },
  { href: '/agents', label: t('nav.agents'), purpose: t('nav.agents.purpose') },
  { href: '/tasks', label: t('nav.tasks'), purpose: t('nav.tasks.purpose') },
  { href: '/operations/observability', label: 'Observability', purpose: 'Trace execution decisions, review economics, and inspect executor-level memory.' },
  { href: '/a2a-operations', label: 'Delegations', purpose: 'Review governed delegated work, blockers and results.' },
  { href: '/issues-events', label: t('nav.incidents'), purpose: t('nav.incidents.purpose') },
  { href: '/operations', label: 'Operations', purpose: 'Review governed exports, attachments, background jobs, bulk/import safety, audit and diagnostics.' },
];

const administrationLinks: AdminNavigationItem[] = [
  { href: '/settings/integrations', label: t('nav.integrations'), purpose: t('nav.integrations.purpose') },
  { href: '/settings', label: t('nav.configurationGovernance'), purpose: t('nav.configurationGovernance.purpose') },
  { href: '/security-events', label: t('nav.securityEvents'), purpose: t('nav.securityEvents.purpose') },
];

/** Public information architecture contains no role or permission metadata. */
export const adminPublicInformationLayers: AdminInformationLayer[] = [
  {
    id: 'operations', badge: 'OPERATIONS', title: t('nav.group.daily'), shortTitle: 'Operations', requiredMode: 'basic',
    sourceOfTruth: 'Core Agent, Dispatch Flow, Task, and Issue state',
    description: 'Operate Tasks, Agents, delegated work and incidents from one canonical workflow; configuration stays separate.',
    operatorQuestion: 'Where should I configure dispatch or resolve a blocked Task?',
    warning: 'Core product links are public-safe navigation. Permission-sensitive administration links are added only by the server projection.',
    primaryLinks: dailyLinks,
  },
  {
    id: 'system', badge: 'ADMINISTRATION', title: t('nav.group.configuration'), shortTitle: 'Admin', requiredMode: 'basic',
    sourceOfTruth: 'Authentication, environment, integrations, and governance',
    description: 'Manage integrations, environment, security, and governance settings.',
    operatorQuestion: 'Where should I manage Provider connections or platform settings?',
    warning: 'Permission-sensitive administration links are supplied only by the server navigation projection.',
    primaryLinks: administrationLinks,
  },
];

export function getAdminPublicInformationLayer(layerId: AdminInformationLayerId): AdminInformationLayer {
  return adminPublicInformationLayers.find((layer) => layer.id === layerId) ?? adminPublicInformationLayers[0];
}
