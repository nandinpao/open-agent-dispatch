import Link from 'next/link';
import { DashboardSummary } from '@/components/dashboard/DashboardSummary';
import { PageHeader } from '@/components/common/PageHeader';
import { InformationArchitectureGuide } from '@/components/common/InformationArchitectureGuide';

export default function DashboardPage() {
  return (
    <main>
      <PageHeader
        title="Dashboard"
        description="Start here to check dispatch readiness, agent health, blocked work, and first-time setup progress. Use Agents as the primary entry for creating and configuring agents."
      />
      <section className="mb-6 rounded-2xl border border-blue-100 bg-blue-50 p-5 shadow-sm">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <h2 className="text-base font-black text-blue-950">New environment?</h2>
            <p className="mt-1 max-w-3xl text-sm leading-6 text-blue-900">
              If the database is empty, create the first agent first. The setup flow creates an enrollment draft, optional approved agent profile, optional capabilities, and a start command. Dispatch Flow decides when the Agent is used.
            </p>
          </div>
          <Link href="/agents/setup" className="shrink-0 rounded-xl bg-blue-600 px-4 py-2 text-sm font-bold text-white shadow-sm hover:bg-blue-700">
            Create First Agent
          </Link>
        </div>
      </section>
      <InformationArchitectureGuide />
      <div className="mt-6">
        <DashboardSummary />
      </div>
    </main>
  );
}
