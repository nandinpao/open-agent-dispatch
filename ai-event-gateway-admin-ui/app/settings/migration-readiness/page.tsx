import Link from 'next/link';
import { AdminUiModeNotice } from '@/components/common/AdminUiModeNotice';

export default function MigrationReadinessCompatibilityPage() {
  return <main className="space-y-6"><AdminUiModeNotice requiredMode="advanced" title="Migration Readiness moved" description="The legacy dispatch readiness route is retained to prevent broken runbook links. Current permission and entry-point readiness evidence is managed under Platform Administration." /><section className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm"><h1 className="text-xl font-black text-slate-950">Readiness evidence</h1><p className="mt-2 max-w-3xl text-sm leading-6 text-slate-600">Use Permission Readiness for Phase 5 evidence. Phase 6B will bind immutable evidence references to cutover-plan revisions; this compatibility page does not publish authority changes.</p><Link href="/platform-administration/permission-readiness" className="mt-5 inline-flex rounded-xl bg-slate-950 px-4 py-3 text-sm font-black text-white">Open Permission Readiness →</Link></section></main>;
}
