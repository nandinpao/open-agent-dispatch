import Link from 'next/link';

interface RouteStateProps {
  title: string;
  description: string;
  actionLabel?: string;
  actionHref?: string;
  onAction?: () => void;
}

export function RouteState({ title, description, actionLabel, actionHref, onAction }: Readonly<RouteStateProps>) {
  return (
    <main className="flex min-h-[60vh] items-center justify-center p-6">
      <div className="max-w-xl rounded-3xl border border-slate-200 bg-white p-8 text-center shadow-sm">
        <div className="text-lg font-bold text-slate-950">{title}</div>
        <p className="mt-3 text-sm leading-6 text-slate-600">{description}</p>
        {actionLabel && actionHref ? (
          <Link href={actionHref} className="mt-6 inline-flex rounded-xl bg-blue-600 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-700">
            {actionLabel}
          </Link>
        ) : null}
        {actionLabel && onAction ? (
          <button type="button" onClick={onAction} className="mt-6 inline-flex rounded-xl bg-blue-600 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-700">
            {actionLabel}
          </button>
        ) : null}
      </div>
    </main>
  );
}
