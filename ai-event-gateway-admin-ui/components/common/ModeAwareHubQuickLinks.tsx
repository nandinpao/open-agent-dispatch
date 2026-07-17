'use client';

import Link from 'next/link';
import { canAccessAdminUiMode, type AdminUiMode } from '@/lib/navigation/adminUiMode';
import { useAdminUiMode } from '@/hooks/useAdminUiMode';

export interface ModeAwareHubQuickLinkItem {
  href: string;
  label: string;
  description: string;
  requiredMode?: AdminUiMode;
}

export function ModeAwareHubQuickLinks({
  title,
  description,
  links,
}: Readonly<{
  title: string;
  description: string;
  links: ModeAwareHubQuickLinkItem[];
}>) {
  const { mode } = useAdminUiMode();
  const visibleLinks = links.filter((link) => canAccessAdminUiMode(mode, link.requiredMode));

  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
      <div className="flex flex-col gap-1 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <h2 className="text-sm font-black uppercase tracking-wide text-slate-500">{title}</h2>
          <p className="mt-1 max-w-4xl text-sm leading-6 text-slate-600">{description}</p>
        </div>
        <div className="rounded-xl border border-blue-100 bg-blue-50 px-3 py-2 text-xs font-bold text-blue-800">
          {mode === 'basic' ? 'Basic Mode' : mode === 'advanced' ? 'Advanced Mode' : 'Developer Mode'}
        </div>
      </div>
      <div className="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-3">
        {visibleLinks.map((link) => (
          <Link
            key={`${link.href}:${link.label}`}
            href={link.href}
            className="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 hover:border-blue-200 hover:bg-blue-50"
          >
            <div className="text-sm font-black text-slate-950">{link.label} →</div>
            <p className="mt-1 text-xs leading-5 text-slate-600">{link.description}</p>
          </Link>
        ))}
      </div>
      {!visibleLinks.length ? (
        <p className="mt-4 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-xs font-bold text-amber-800">No links are available for the current UI mode.</p>
      ) : null}
    </section>
  );
}
