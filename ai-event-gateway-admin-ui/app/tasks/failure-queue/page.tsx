import { InformationArchitectureGuide } from '@/components/common/InformationArchitectureGuide';
import { PageHeader } from '@/components/common/PageHeader';
import { TaskFailureQueuePanel } from '@/components/tasks/TaskFailureQueuePanel';

export default function TaskFailureQueuePage() {
  return (
    <main className="space-y-6">
      <PageHeader title="Task Failure Queue" description="Review retrying, failed, escalated, dead-lettered, orphaned, and reconciling Tasks, with controlled retry, escalation, and dead-letter actions." />
      <InformationArchitectureGuide activeLayer="dispatch" compact />
      <TaskFailureQueuePanel />
    </main>
  );
}
