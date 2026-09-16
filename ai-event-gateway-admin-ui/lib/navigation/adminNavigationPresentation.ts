import { translate as t } from '@/lib/i18n';
import type { UiNavigationItem } from '@/lib/navigation/uiEntitlements';

export type AdminNavigationGroupId = 'daily' | 'configuration' | 'engineering' | 'platform' | 'account';

export interface AdminNavigationGroup {
  id: AdminNavigationGroupId;
  label: string;
  description: string;
  collapsible: boolean;
  items: PresentedNavigationItem[];
}

export interface PresentedNavigationItem {
  item: UiNavigationItem;
  label: string;
  purpose: string;
  groupId: AdminNavigationGroupId;
}

const GROUP_ORDER: AdminNavigationGroupId[] = ['daily', 'configuration', 'engineering', 'platform', 'account'];

const GROUPS: Record<AdminNavigationGroupId, Omit<AdminNavigationGroup, 'items'>> = {
  daily: {
    id: 'daily',
    label: t('nav.group.daily'),
    description: 'Monitor work, Agents, delegated work and operational issues.',
    collapsible: false,
  },
  configuration: {
    id: 'configuration',
    label: t('nav.group.configuration'),
    description: 'Configure sources, routing, integrations, people and governed product settings.',
    collapsible: false,
  },
  engineering: {
    id: 'engineering',
    label: t('nav.group.engineering'),
    description: 'Open diagnostics, recovery and governance surfaces only when deeper investigation is required.',
    collapsible: true,
  },
  platform: {
    id: 'platform',
    label: t('nav.group.platform'),
    description: 'Manage instance-wide administration and controlled platform operations.',
    collapsible: false,
  },
  account: {
    id: 'account',
    label: t('nav.group.account'),
    description: 'Review your own sign-in identity and account security.',
    collapsible: true,
  },
};

const FEATURE_PRESENTATION: Record<string, Partial<Pick<PresentedNavigationItem, 'label' | 'purpose' | 'groupId'>>> = {
  dashboard: { groupId: 'daily' },
  agents: { groupId: 'daily' },
  tasks: { groupId: 'daily' },
  'a2a-operations': {
    groupId: 'daily',
    label: 'Delegations',
    purpose: 'Review governed delegated work, blockers, results and recovery actions.',
  },
  'issues-events': { groupId: 'daily' },

  'source-systems': { groupId: 'configuration' },
  dispatch: { groupId: 'configuration' },
  integrations: { groupId: 'configuration' },
  administration: { groupId: 'configuration', label: 'Configuration & Governance', purpose: 'Configure governed product behavior, runtime safety and administration without creating a second dispatch authority.' },
  'access-management': { groupId: 'configuration' },
  'agent-enrollments': { groupId: 'configuration' },

  operations: {
    groupId: 'engineering',
    label: 'Operational Diagnostics',
    purpose: 'Review governed exports, background authorization and audit diagnostics.',
  },
  'sync-operations': { groupId: 'engineering' },
  'a2a-governance': { groupId: 'engineering' },
  'resource-access': { groupId: 'engineering' },

  'instance-administration': { groupId: 'platform' },
  'my-account': { groupId: 'account' },
};

function defaultGroup(section: string): AdminNavigationGroupId {
  switch (section) {
    case 'DISPATCH':
    case 'ADMINISTRATION':
      return 'configuration';
    case 'PLATFORM':
      return 'platform';
    case 'ACCOUNT':
      return 'account';
    case 'INTERNAL':
    case 'INTERNAL_ENGINEERING':
      return 'engineering';
    case 'OPERATIONS':
    default:
      return 'daily';
  }
}

export function presentNavigationItem(item: UiNavigationItem): PresentedNavigationItem {
  const presentation = FEATURE_PRESENTATION[item.featureId] ?? {};
  return {
    item,
    label: presentation.label ?? item.label,
    purpose: presentation.purpose ?? item.purpose,
    groupId: presentation.groupId ?? defaultGroup(item.section),
  };
}

/**
 * Presentation-only grouping of Core-projected navigation.
 *
 * This function cannot add an entitlement, route, action or page. It receives only
 * already-authorized Core navigation items and preserves the original item object,
 * displayMode, featureId, route, children and ordering. The mapping changes only
 * user-facing grouping, labels and purpose text.
 */
export function presentNavigation(items: UiNavigationItem[]): AdminNavigationGroup[] {
  const presented = items.map(presentNavigationItem);
  return GROUP_ORDER.map((id) => ({
    ...GROUPS[id],
    items: presented.filter((entry) => entry.groupId === id),
  })).filter((group) => group.items.length > 0);
}
