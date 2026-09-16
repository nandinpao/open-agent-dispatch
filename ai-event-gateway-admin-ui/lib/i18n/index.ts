import { enMessages, type I18nMessageKey } from './resources/en';

export type AdminLocale = 'en-US';
export const DEFAULT_ADMIN_LOCALE: AdminLocale = 'en-US';

const resources: Record<AdminLocale, Record<string, string>> = {
  'en-US': enMessages,
};

export function normalizeAdminLocale(value: unknown): AdminLocale {
  return value === 'en-US' || value === 'en' ? 'en-US' : DEFAULT_ADMIN_LOCALE;
}

export function translate(
  key: I18nMessageKey,
  params?: Record<string, string | number | undefined>,
  locale: AdminLocale = DEFAULT_ADMIN_LOCALE,
): string {
  const message = resources[locale]?.[key] ?? resources[DEFAULT_ADMIN_LOCALE][key] ?? key;
  if (!params) return message;
  return message.replace(/\{(\w+)\}/g, (_, name: string) => String(params[name] ?? ''));
}

export type { I18nMessageKey };
