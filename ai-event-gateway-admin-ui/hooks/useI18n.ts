'use client';

import { useCallback } from 'react';
import { DEFAULT_ADMIN_LOCALE, normalizeAdminLocale, translate, type AdminLocale, type I18nMessageKey } from '@/lib/i18n';

export function useI18n() {
  // Stage 7 prepares the i18n contract while keeping Admin UI English-first.
  // A locale provider can replace this fixed value in a later localization stage.
  const locale: AdminLocale = normalizeAdminLocale(DEFAULT_ADMIN_LOCALE);
  const t = useCallback((key: I18nMessageKey, params?: Record<string, string | number | undefined>) => translate(key, params, locale), [locale]);
  return { locale, t };
}
