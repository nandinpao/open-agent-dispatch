'use client';

import type { ReactNode } from 'react';
import { usePathname } from 'next/navigation';
import { useAdminUiMode } from '@/hooks/useAdminUiMode';
import { canAccessAdminUiMode, getAdminUiModeOption, requiredAdminUiModeForPath } from '@/lib/navigation/adminUiMode';

export function AdminUiModeRouteBoundary({ children }: Readonly<{ children: ReactNode }>) {
  const pathname = usePathname();
  const { mode, setMode } = useAdminUiMode();
  const requiredMode = requiredAdminUiModeForPath(pathname);

  if (canAccessAdminUiMode(mode, requiredMode)) return children;

  const current = getAdminUiModeOption(mode);
  const required = getAdminUiModeOption(requiredMode);
  return (
    <section className="mx-auto max-w-4xl rounded-3xl border border-amber-200 bg-amber-50 p-6 shadow-sm" aria-labelledby="ui-level-boundary-title">
      <div className="text-xs font-black uppercase tracking-[0.16em] text-amber-700">Information level</div>
      <h1 id="ui-level-boundary-title" className="mt-2 text-2xl font-black text-amber-950">This page belongs to {required.label}.</h1>
      <p className="mt-3 max-w-3xl text-sm leading-6 text-amber-900">
        You opened a deeper administration or diagnostic route while the console is using {current.label}. The page is hidden to keep normal operations focused; it has not been denied by this UI setting.
      </p>
      <div className="mt-4 rounded-2xl border border-amber-200 bg-white/80 p-4 text-sm text-slate-700">
        <div className="font-black text-slate-950">What changes when you continue?</div>
        <p className="mt-1 leading-6">Only navigation and information density change. Backend RBAC, tenant scope, action permissions and production authority remain exactly the same.</p>
      </div>
      <div className="mt-5 flex flex-wrap gap-2">
        <button type="button" onClick={() => setMode(requiredMode)} className="rounded-xl bg-amber-800 px-4 py-2.5 text-sm font-black text-white hover:bg-amber-900">
          Switch to {required.shortLabel} and open page
        </button>
        <button type="button" onClick={() => window.history.back()} className="rounded-xl border border-amber-300 bg-white px-4 py-2.5 text-sm font-black text-amber-900 hover:bg-amber-100">
          Go back
        </button>
      </div>
    </section>
  );
}
