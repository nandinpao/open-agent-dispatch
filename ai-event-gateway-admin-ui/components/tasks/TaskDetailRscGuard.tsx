import { TaskDetailView } from '@/components/tasks/TaskDetailView';
import { BootstrapUnavailableShell, CapabilityDegradedNotice, SafePageShell } from '@/components/ui-capability/SafePageShell';
import { UiPageBootstrapProvider } from '@/components/ui-capability/UiPageBootstrapProvider';
import { StaleResourceDialog } from '@/components/ui-capability/StaleResourceDialog';
import { loadTaskDetailBootstrap } from '@/lib/server/taskDetailBootstrap';

export async function TaskDetailRscGuard({ taskId, modal = false }: Readonly<{ taskId: string; modal?: boolean }>) {
  const result = await loadTaskDetailBootstrap(taskId);
  if (result.kind === 'UNAVAILABLE') return <BootstrapUnavailableShell diagnostic={result.diagnostic} modal={modal} />;
  if (result.kind === 'SAFE_SHELL') return <SafePageShell outcome={result.bootstrap.outcome} diagnostic={result.diagnostic} modal={modal} />;
  return (
    <UiPageBootstrapProvider bootstrap={result.bootstrap} resourceId={taskId}>
      <div data-ui-bootstrap="task.detail" data-ui-bootstrap-outcome="PAGE" data-hydration-nonce={result.bootstrap.hydrationNonce}>
        <CapabilityDegradedNotice diagnostic={result.diagnostic} />
        <TaskDetailView taskId={taskId} />
        <StaleResourceDialog contextId={result.bootstrap.routeContext} />
      </div>
    </UiPageBootstrapProvider>
  );
}
