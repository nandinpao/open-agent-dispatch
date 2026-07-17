import Link from 'next/link';
import { PageHeader } from '@/components/common/PageHeader';
import { InformationArchitectureGuide } from '@/components/common/InformationArchitectureGuide';
import { HubQuickLinks } from '@/components/common/HubQuickLinks';
import { TaskTable } from '@/components/tasks/TaskTable';

export default function TasksPage() {
  return (
    <main>
      <PageHeader title="Tasks" description="Track task status, dispatch history, blocking reasons, recovery actions, and issue links from the Core task source of truth." />
      <InformationArchitectureGuide activeLayer="business" compact />
      <HubQuickLinks
        title="Task operations shortcuts"
        description="Tasks show why work can or cannot be dispatched. Agent setup, capabilities, and source catalogs are managed from their own supporting pages."
        links={[
          { href: '/dispatch-flows', label: 'Open Dispatch Flows', description: 'Open the Flow that owns intake and A2A routing for this task.' },
        ]}
      />
      <div className="my-4 flex flex-wrap justify-end gap-2">
        <Link href="/tasks/failure-queue" className="rounded-xl border border-amber-200 px-4 py-2 text-sm font-bold text-amber-700 hover:bg-amber-50">
          Open Failure Queue
        </Link>
      </div>
      <TaskTable />
    </main>
  );
}
