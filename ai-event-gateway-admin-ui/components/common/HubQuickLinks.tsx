import Link from 'next/link';

export interface HubQuickLinkItem {
  href: string;
  label: string;
  description: string;
}

export function HubQuickLinks({
  title,
  description,
  links,
}: Readonly<{
  title: string;
  description: string;
  links: HubQuickLinkItem[];
}>) {
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
      <div className="flex flex-col gap-1 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <h2 className="text-sm font-black uppercase tracking-wide text-slate-500">{title}</h2>
          <p className="mt-1 max-w-4xl text-sm leading-6 text-slate-600">{description}</p>
        </div>
        <div className="rounded-xl border border-blue-100 bg-blue-50 px-3 py-2 text-xs font-bold text-blue-800">
          Hub-and-Spoke
        </div>
      </div>
      <div className="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-3">
        {links.map((link) => (
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
    </section>
  );
}
