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
