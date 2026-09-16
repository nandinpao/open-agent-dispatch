import { EmptyState } from '@/components/ui/EmptyState';
import type { WorkspaceSectionState } from './dispatchWorkspaceModel';

export function WorkspaceStateBlock({
  state,
  title,
  description,
  error,
}: Readonly<{
  state: WorkspaceSectionState;
  title: string;
  description: string;
  error?: string | null;
}>) {
  if (state === 'loading') {
    return <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4 text-sm font-bold text-slate-600">Loading…</div>;
  }
  if (state === 'error') {
    return <div className="rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm font-bold text-rose-900">{error ?? 'Loading failed.'}</div>;
  }
  if (state === 'empty') {
    return <EmptyState title={title} description={description} compact />;
  }
  return null;
}
