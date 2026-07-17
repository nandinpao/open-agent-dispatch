import { InformationArchitectureGuide } from '@/components/common/InformationArchitectureGuide';
import { PageHeader } from '@/components/common/PageHeader';
import { TaskFailureQueuePanel } from '@/components/tasks/TaskFailureQueuePanel';

export default function TaskFailureQueuePage() {
  return (
    <main className="space-y-6">
      <PageHeader title="Task Failure Queue" description="集中檢視 retry wait、failed、escalated、dead-letter、orphaned 與 reconciling 任務，並提供 manual retry / escalation / DLQ 操作。" />
      <InformationArchitectureGuide activeLayer="dispatch" compact />
      <TaskFailureQueuePanel />
    </main>
  );
}
