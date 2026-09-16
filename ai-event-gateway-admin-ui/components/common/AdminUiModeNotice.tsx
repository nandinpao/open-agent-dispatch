'use client';

import { canAccessAdminUiMode, getAdminUiModeOption, type AdminUiMode } from '@/lib/navigation/adminUiMode';
import { useAdminUiMode } from '@/hooks/useAdminUiMode';

export function AdminUiModeNotice({
  requiredMode,
  title,
  description,
}: Readonly<{
  requiredMode: AdminUiMode;
  title: string;
  description: string;
}>) {
  const { mode, setMode } = useAdminUiMode();
  const requiredOption = getAdminUiModeOption(requiredMode);

  if (canAccessAdminUiMode(mode, requiredMode)) {
    return (
      <section className="rounded-2xl border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600 shadow-sm">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <div className="font-black text-slate-900">{title}</div>
            <p className="mt-1 leading-6">{description}</p>
            <p className="mt-2 text-xs font-semibold text-slate-500">UI mode controls navigation and information density; it does not grant API permissions. Backend RBAC still authorizes every read and mutation.</p>
          </div>
          <div className="shrink-0 rounded-full border border-slate-200 bg-white px-3 py-1 text-xs font-black text-slate-600">
            Visible in {requiredOption.shortLabel} Mode
          </div>
        </div>
      </section>
    );
  }

  return (
    <section className="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-950 shadow-sm">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <div className="font-black">{title}</div>
          <p className="mt-1 leading-6">{description}</p>
          <p className="mt-2 text-xs font-bold text-amber-800">This page is hidden in the current mode. Changing modes does not grant permissions; backend RBAC still authorizes access.</p>
        </div>
        <button
          type="button"
          onClick={() => setMode(requiredMode)}
          className="shrink-0 rounded-xl border border-amber-300 bg-white px-4 py-2 text-xs font-black text-amber-800 hover:bg-amber-100"
        >
          Switch to {requiredOption.shortLabel} Mode
        </button>
      </div>
    </section>
  );
}
