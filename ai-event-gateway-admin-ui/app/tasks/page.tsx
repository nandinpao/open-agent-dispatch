import { EntitlementPageGuard } from '@/components/auth/EntitlementPageGuard';
import Link from 'next/link';
import { AdminListPage } from '@/components/layout/AdminListPage';
import { TaskTable } from '@/components/tasks/TaskTable';

export const dynamic = 'force-dynamic';
export const revalidate = 0;

export default function TasksPage() {
  return (
    <EntitlementPageGuard featureId="tasks">
    <AdminListPage
      eyebrow="Operational workspace"
      title="Tasks"
      description="Find blocked work, understand the safest next action, and open one authoritative Task detail without losing list context."
      actions={(
        <div className="flex flex-wrap gap-2">
          <Link href="/a2a-operations" className="rounded-xl border border-indigo-200 px-4 py-2 text-sm font-bold text-indigo-700 hover:bg-indigo-50">Delegations</Link>
          <Link href="/tasks/failure-queue" className="rounded-xl border border-amber-200 px-4 py-2 text-sm font-bold text-amber-700 hover:bg-amber-50">Failure Queue</Link>
        </div>
      )}
    >
      <div className="p-4 sm:p-5"><TaskTable /></div>
    </AdminListPage>
    </EntitlementPageGuard>
  );
}
