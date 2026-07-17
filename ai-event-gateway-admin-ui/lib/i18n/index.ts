import { enMessages, type I18nMessageKey } from './resources/en';
import { zhTWMessages } from './resources/zh-TW';

export type AdminLocale = 'en' | 'zh-TW';

export const DEFAULT_ADMIN_LOCALE: AdminLocale = 'en';

const resources: Record<AdminLocale, Record<string, string>> = {
  en: enMessages,
  'zh-TW': zhTWMessages,
};

export function normalizeAdminLocale(value: unknown): AdminLocale {
  return value === 'zh-TW' || value === 'en' ? value : DEFAULT_ADMIN_LOCALE;
}

export function translate(key: I18nMessageKey, params?: Record<string, string | number | undefined>, locale: AdminLocale = DEFAULT_ADMIN_LOCALE): string {
  const message = resources[locale]?.[key] ?? resources[DEFAULT_ADMIN_LOCALE][key] ?? key;
  if (!params) {
    return message;
  }
  return message.replace(/\{(\w+)\}/g, (_, name: string) => String(params[name] ?? ''));
}

export type { I18nMessageKey };
