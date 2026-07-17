'use client';

import { adminUiModeOptions, getAdminUiModeOption } from '@/lib/navigation/adminUiMode';
import { useAdminUiMode } from '@/hooks/useAdminUiMode';
import { useI18n } from '@/hooks/useI18n';

export function AdminUiModeSwitcher() {
  const { mode, setMode } = useAdminUiMode();
  const { t } = useI18n();
  const option = getAdminUiModeOption(mode);

  return (
    <label className="hidden items-center gap-2 rounded-full border border-slate-200 bg-white px-3 py-1 text-xs font-semibold text-slate-600 md:flex" title={option.description}>
      <span className="whitespace-nowrap">{t('mode.switcher.label')}</span>
      <select
        value={mode}
        onChange={(event) => setMode(event.target.value as typeof mode)}
        className="rounded-full border border-slate-200 bg-slate-50 px-2 py-1 text-xs font-black text-slate-800 outline-none focus:border-blue-300 focus:ring-2 focus:ring-blue-100"
        aria-label={t('mode.switcher.aria')}
      >
        {adminUiModeOptions.map((item) => (
          <option key={item.value} value={item.value}>{item.shortLabel}</option>
        ))}
      </select>
    </label>
  );
}
