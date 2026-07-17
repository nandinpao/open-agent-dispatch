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
 * Stage 4 beginner navigation.
 *
 * Normal administrators use one fixed product navigation. Historical catalogs, migration tools, raw runtime pages, simulators, and parallel dispatch models are absent from the sidebar. Normal administrators configure dispatch only through Dispatch Flows.
 */
export const adminPrimaryNavigation: AdminNavigationItem[] = [
  { href: '/dashboard', label: '總覽', purpose: '查看派工健康度、系統狀態與新手下一步。' },
  { href: '/source-systems', label: '來源系統', purpose: '管理企業自行定義的事件來源，供派工流程使用。' },
  { href: '/agents', label: 'Agent', purpose: '建立、核准及查看 Agent 連線與可處理能力。' },
  { href: '/dispatch-flows', label: '派工流程', purpose: '唯一的派工設定入口：事件條件、處理 Agent 與選填特殊能力。' },
  { href: '/tasks', label: 'Task', purpose: '追蹤 Task、失敗原因、人工處置與執行紀錄。' },
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
    description: '一般管理員只需要 Agent、派工流程、Task 與問題事件。',
    operatorQuestion: '我要建立 Agent、設定派工或處理失敗 Task，應從哪裡開始？',
    warning: '派工流程是唯一派工設定入口。',
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
    warning: '系統設定不建立第二套派工模型。',
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

