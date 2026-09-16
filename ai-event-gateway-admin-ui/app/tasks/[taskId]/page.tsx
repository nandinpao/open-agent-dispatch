import type { Metadata } from 'next';
import { TaskDetailRscGuard } from '@/components/tasks/TaskDetailRscGuard';
import { loadTaskDetailBootstrap } from '@/lib/server/taskDetailBootstrap';

export const dynamic = 'force-dynamic';
export const revalidate = 0;
export const fetchCache = 'force-no-store';

export async function generateMetadata({ params }: { params: Promise<{ taskId: string }> }): Promise<Metadata> {
  const { taskId } = await params;
  const result = await loadTaskDetailBootstrap(decodeURIComponent(taskId));
  const authorized = result.kind === 'READY';
  return {
    title: authorized ? 'Task details | OpenDispatch' : 'Resource unavailable | OpenDispatch',
    description: authorized ? 'Authorized OpenDispatch task workspace.' : 'Protected OpenDispatch resource.',
    robots: { index: false, follow: false, noarchive: true, nosnippet: true },
    openGraph: { title: authorized ? 'Task details | OpenDispatch' : 'Resource unavailable | OpenDispatch', description: 'Protected OpenDispatch resource.' },
  };
}

export default async function TaskDetailPage({ params }: { params: Promise<{ taskId: string }> }) {
  const { taskId } = await params;
  return <TaskDetailRscGuard taskId={decodeURIComponent(taskId)} />;
}
