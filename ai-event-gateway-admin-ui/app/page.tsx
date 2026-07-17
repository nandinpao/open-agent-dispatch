import Link from 'next/link';

export default function HomePage() {
  return (
    <main className="space-y-6">
      <section className="rounded-2xl border border-slate-200 bg-white p-8 shadow-sm">
        <p className="text-sm font-semibold uppercase tracking-wide text-blue-600">OpenDispatch</p>
        <h1 className="mt-3 text-3xl font-bold text-slate-950">Admin Console</h1>
        <p className="mt-3 max-w-3xl text-slate-600">
          Manage agents, connect runtimes, assign capabilities, control dispatch rules, and monitor task execution from a simplified operator workflow.
        </p>
        <div className="mt-6 flex flex-wrap gap-3">
          <Link className="rounded-xl bg-blue-600 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-blue-700" href="/agents/setup">
            Create First Agent
          </Link>
          <Link className="rounded-xl border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50" href="/dashboard">
            Open Dashboard
          </Link>
        </div>
      </section>
    </main>
  );
}
