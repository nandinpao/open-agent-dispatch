import type { ReactNode } from 'react';

export function AdminMasterDetailLayout({
  list,
  detail,
  listLabel,
  detailLabel,
}: Readonly<{ list: ReactNode; detail: ReactNode; listLabel: string; detailLabel: string }>) {
  return (
    <section className="grid min-h-[38rem] grid-cols-1 gap-5 xl:grid-cols-[minmax(24rem,2fr)_minmax(22rem,1fr)]">
      <div aria-label={listLabel} className="min-w-0">{list}</div>
      <aside aria-label={detailLabel} className="min-w-0 xl:sticky xl:top-24 xl:h-fit">{detail}</aside>
    </section>
  );
}
