import type { AdminUiMode } from '@/lib/navigation/adminUiMode';
import { translate as t } from '@/lib/i18n';

export type AdminInformationLayerId = 'business' | 'dispatch' | 'runtime' | 'operations' | 'advanced' | 'system';

export const adminInformationArchitecturePrinciples = [
  { layer: 'Business Truth', authority: 'CORE_AUTHORITY', sourceOfTruth: 'Core PostgreSQL / Core Admin API' },
  { layer: 'Dispatch Truth', authority: 'DISPATCH_FLOW', sourceOfTruth: 'Dispatch Flow / Task / Assignment' },
] as const;

export interface AdminNavigationItem {
  href: string;
  label: string;
  purpose: string;
  requiredMode?: AdminUiMode;
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
 * Current setup navigation.
 *
 * Normal administrators use one fixed product navigation. Historical catalogs, migration tools, raw runtime pages, simulators, and parallel dispatch models are absent from the sidebar. Normal administrators configure dispatch through the Current setup path: Source System -> Dispatch Setup -> Source Flow -> Agent Pool -> Pool Member Agent.
 */
export const adminPrimaryNavigation: AdminNavigationItem[] = [
  { href: '/dashboard', label: '總覽', purpose: '查看派工健康度、系統狀態與新手下一步。' },
  { href: '/source-systems', label: '來源系統', purpose: '先建立事件來源主檔，供 Source Flow 使用。' },
  { href: '/dispatch-flows', label: '派工設定', purpose: 'Current 唯一設定入口：Source Flow、預設 Agent Pool、Rule override 與 Pool Member Agent。' },
  { href: '/agents', label: 'Agent', purpose: '建立、核准及查看 Agent 連線；加入 Pool 請回到派工設定。' },
  { href: '/tasks', label: 'Task', purpose: '依 Source Flow -> Agent Pool -> Pool Member Agent 標準鏈追蹤失敗原因與人工處置。' },
  { href: '/issues-events', label: '問題與事件', purpose: '查看問題追蹤、失敗事件、安全事件與事件紀錄。' },
  { href: '/settings', label: '系統設定', purpose: '設定環境、整合、安全與權限。' },
];

// Kept as exported empty arrays for compatibility with existing imports.
// Stage 4 no longer renders these as normal operator navigation.
export const adminAdvancedNavigation: AdminNavigationItem[] = [];
export const adminDeveloperNavigation: AdminNavigationItem[] = [];

export const adminInformationLayers: AdminInformationLayer[] = [
  {
    id: 'operations',
    badge: 'OPERATIONS',
    title: '日常操作',
    shortTitle: '操作',
    requiredMode: 'basic',
    sourceOfTruth: 'Core Agent, Dispatch Flow, Task, and Issue state',
    description: '一般管理員只需要來源系統、派工設定、Agent、Task 與問題事件。',
    operatorQuestion: '我要建立來源、設定派工或處理失敗 Task，應從哪裡開始？',
    warning: '派工設定是唯一 Current setup 入口：Source Flow -> Agent Pool -> Pool Member Agent。',
    primaryLinks: adminPrimaryNavigation.filter((link) => link.href !== '/settings'),
  },
  {
    id: 'system',
    badge: 'SYSTEM',
    title: '系統設定',
    shortTitle: '設定',
    requiredMode: 'basic',
    sourceOfTruth: 'Authentication, environment, integrations, and permissions',
    description: '初始化環境、整合與安全設定。日常派工一律從派工流程設定。',
    operatorQuestion: '我要設定環境、外部整合或權限，應從哪裡開始？',
    warning: '系統設定不建立第二套派工模型；Current setup 仍回到派工設定。',
    primaryLinks: [
      { href: '/settings', label: t('nav.environmentSettings'), purpose: t('nav.environmentSettings.purpose') },
      { href: '/settings/issue-tracking', label: t('nav.issueTrackingIntegrations'), purpose: t('nav.issueTrackingIntegrations.purpose') },
      { href: '/security-events', label: t('nav.securityEvents'), purpose: t('nav.securityEvents.purpose') },
      { href: '/agent-enrollments', label: t('nav.agentEnrollmentReview'), purpose: t('nav.agentEnrollmentReview.purpose') },
    ],
  },
];

export function getAdminInformationLayer(layerId: AdminInformationLayerId): AdminInformationLayer {
  const resolvedLayerId = layerId === 'system' ? 'system' : 'operations';
  return adminInformationLayers.find((layer) => layer.id === resolvedLayerId) ?? adminInformationLayers[0];
}

