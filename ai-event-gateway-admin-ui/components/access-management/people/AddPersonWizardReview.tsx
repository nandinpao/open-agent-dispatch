import type { ResponsibilityUiAccessPreview } from '@/lib/iam/types';

export function AccessPreviewPanel({ preview, loading, error, title = 'Access preview' }: Readonly<{ preview: ResponsibilityUiAccessPreview | null; loading: boolean; error: string; title?: string }>) {
  const navigation = preview?.uiAccess.navigation ?? [];
  const pages = Object.values(preview?.uiAccess.pages ?? {}).filter((page) => page.displayMode !== 'HIDDEN' && page.featureId !== 'my-account');
  const functions = Object.values(preview?.uiAccess.actionEntitlements ?? {}).filter((action) => action.displayMode === 'ENABLED');
  if (loading) return <div className="rounded-2xl border border-blue-200 bg-blue-50 p-4 text-sm text-blue-900">Loading the canonical Navigator and function preview…</div>;
  if (error) return <div className="rounded-2xl border border-amber-300 bg-amber-50 p-4 text-sm leading-6 text-amber-950"><b className="block">Access preview unavailable</b>{error}</div>;
  if (!preview) return null;
  return <section className="rounded-2xl border border-emerald-200 bg-emerald-50/50 p-4"><p className="text-xs font-black uppercase tracking-wide text-emerald-800">{title}</p><div className="mt-3 grid gap-4 lg:grid-cols-3"><div><p className="text-xs font-black uppercase tracking-wide text-slate-500">Navigator</p><div className="mt-2 space-y-2">{navigation.length ? navigation.map((item) => <div key={item.featureId} className="rounded-xl bg-white p-3 text-sm"><b>{item.label}</b>{item.children.length ? <p className="mt-1 text-xs text-slate-600">{item.children.map((child) => `${child.label}${child.displayMode === 'READ_ONLY' ? ' (Read)' : ''}`).join(' · ')}</p> : null}</div>) : <p className="rounded-xl bg-white p-3 text-xs text-amber-800">No business Navigator item is produced by this Responsibility.</p>}</div></div><div><p className="text-xs font-black uppercase tracking-wide text-slate-500">Pages</p><p className="mt-2 text-sm font-black text-slate-900">{pages.length}</p><p className="mt-1 text-xs leading-5 text-slate-600">{pages.slice(0, 6).map((page) => `${page.featureId.replaceAll('-', ' ')}${page.displayMode === 'READ_ONLY' ? ' (Read)' : ''}`).join(' · ') || 'None'}</p></div><div><p className="text-xs font-black uppercase tracking-wide text-slate-500">Enabled functions</p><p className="mt-2 text-sm font-black text-slate-900">{functions.length}</p><p className="mt-1 text-xs leading-5 text-slate-600">Scope remains limited by the selected workspace, Department or Group assignment.</p></div></div></section>;
}

export function ReviewCard({ label, value }: Readonly<{ label: string; value: string }>) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-4">
      <p className="text-xs font-black uppercase tracking-wide text-slate-500">{label}</p>
      <p className="mt-2 text-sm font-black text-slate-950">{value}</p>
    </div>
  );
}

export function AddPersonWizardHeader({ tenantName, onClose }: Readonly<{ tenantName: string; onClose: () => void }>) {
  return <div className="flex flex-col gap-3 border-b border-slate-200 pb-4 sm:flex-row sm:items-start sm:justify-between"><div><p className="text-xs font-black uppercase tracking-[.18em] text-blue-700">Guided onboarding</p><h2 id="add-person-title" className="mt-1 text-xl font-black text-slate-950">Add a person to {tenantName}</h2><p className="mt-2 max-w-3xl text-sm leading-6 text-slate-600">Choose the person, where they work, what they are responsible for, and how they will sign in. OpenDispatch commits the result as one governed onboarding operation.</p></div><button type="button" onClick={onClose} className="rounded-xl border border-slate-300 px-3 py-2 text-sm font-black text-slate-700">Close</button></div>;
}
