import { InterceptedRouteModal } from '@/components/ui-capability/InterceptedRouteModal';
export default function InterceptedTaskLoading() { return <InterceptedRouteModal><section aria-busy="true" className="rounded-2xl bg-white p-6 text-sm text-slate-600">Verifying protected task access…</section></InterceptedRouteModal>; }
