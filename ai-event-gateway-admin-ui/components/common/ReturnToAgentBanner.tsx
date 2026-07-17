import Link from 'next/link';

type SearchParams = Record<string, string | string[] | undefined>;

function firstValue(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}

function safeReturnPath(value: string | undefined): string | null {
  if (!value) return null;
  if (!value.startsWith('/agents/')) return null;
  if (value.includes('://')) return null;
  return value;
}

export function ReturnToAgentBanner({ searchParams }: Readonly<{ searchParams?: SearchParams }>) {
  const returnTo = safeReturnPath(firstValue(searchParams?.returnTo));
  const agentId = firstValue(searchParams?.agentId);
  if (!returnTo) return null;

  return (
    <section className="rounded-3xl border border-blue-200 bg-blue-50/70 p-4">
      <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <div>
          <div className="text-xs font-black uppercase tracking-widest text-blue-700">Opened from Agent Detail</div>
          <p className="mt-1 text-sm font-bold text-blue-950">
            You are maintaining source data for {agentId ? <span className="font-mono">{agentId}</span> : 'the selected agent'}.
          </p>
          <p className="mt-1 text-sm leading-6 text-blue-900">Save your source-data changes here, then return to the agent page to continue setup.</p>
        </div>
        <Link href={returnTo} className="shrink-0 rounded-xl bg-blue-600 px-4 py-2 text-sm font-black text-white hover:bg-blue-700">
          Return to Agent
        </Link>
      </div>
    </section>
  );
}
