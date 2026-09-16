import { InterceptedRouteModal } from '@/components/ui-capability/InterceptedRouteModal';
import { TaskDetailRscGuard } from '@/components/tasks/TaskDetailRscGuard';

export const dynamic = 'force-dynamic';
export const revalidate = 0;
export const fetchCache = 'force-no-store';

export default async function InterceptedTaskDetailPage({ params }: { params: Promise<{ taskId: string }> }) {
  const { taskId } = await params;
  return <InterceptedRouteModal><TaskDetailRscGuard taskId={decodeURIComponent(taskId)} modal /></InterceptedRouteModal>;
}
