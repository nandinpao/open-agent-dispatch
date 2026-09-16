import Link from 'next/link';
import { ReturnToAgentBanner } from '@/components/common/ReturnToAgentBanner';
import { RuntimeResourceConsole } from '@/components/runtime-resources/RuntimeResourceConsole';

type PageProps = { searchParams?: Promise<Record<string, string | string[] | undefined>> };

export default async function AgentRuntimePage({ searchParams }: Readonly<PageProps>) {
  const resolvedSearchParams = await searchParams;
  return (
    <main className="space-y-5">
      <ReturnToAgentBanner searchParams={resolvedSearchParams} />
      <section className="rounded-3xl border border-blue-200 bg-blue-50/60 p-5">
        <div className="text-xs font-black uppercase tracking-[.16em] text-blue-700">Agent Runtime Journey</div>
        <h1 className="mt-2 text-xl font-black text-blue-950">Runtime resources stay inside the Agent workflow</h1>
        <p className="mt-2 max-w-4xl text-sm leading-6 text-blue-900">Register or inspect gateway and execution resources here, then return to the Agent detail to bind and verify the runtime. Runtime observation does not create dispatch eligibility by itself.</p>
        <div className="mt-3 flex flex-wrap gap-2">
          <Link href="/agents" className="rounded-xl border border-blue-300 bg-white px-3 py-2 text-xs font-black text-blue-800 hover:bg-blue-100">Back to Agents</Link>
          <Link href="/dispatch-flows" className="rounded-xl border border-blue-300 bg-white px-3 py-2 text-xs font-black text-blue-800 hover:bg-blue-100">Open Dispatch</Link>
        </div>
      </section>
      <RuntimeResourceConsole />
    </main>
  );
}
