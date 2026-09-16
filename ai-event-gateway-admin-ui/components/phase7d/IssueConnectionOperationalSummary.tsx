import type { IntegrationConnection, IntegrationCredentialMetadata, IntegrationPrincipal, PermissionProbeResult } from '@/lib/api/domains/integrationIdentityApi';

export function IssueConnectionOperationalSummary({ connection, principal, credentials, probe }: Readonly<{ connection?: IntegrationConnection; principal?: IntegrationPrincipal; credentials: IntegrationCredentialMetadata[]; probe?: PermissionProbeResult | null }>) {
  const requiredProbeFailures = Object.entries(probe?.capabilityResults ?? {}).filter(([, value]) => !['GRANTED', 'PASS', 'SUPPORTED'].includes(String(value).toUpperCase())).length;
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm" aria-labelledby="issue-connection-summary-title">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
        <div><h3 id="issue-connection-summary-title" className="font-black text-slate-950">Issue Tracking connection workspace</h3><p className="mt-1 text-sm leading-6 text-slate-600">Connection, scoped principal, credential metadata, project mapping, permission probe, and sync recovery remain separate governed concerns.</p></div>
        <span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-black text-slate-700">{connection?.providerType ?? 'No provider selected'}</span>
      </div>
      <div className="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
        <Metric label="Connection" value={connection?.status ?? 'NOT SELECTED'} />
        <Metric label="Principal" value={principal?.status ?? 'NOT SELECTED'} />
        <Metric label="Credential refs" value={String(credentials.length)} />
        <Metric label="Probe" value={probe?.overallStatus ?? 'NOT TESTED'} />
        <Metric label="Probe issues" value={String(requiredProbeFailures)} />
      </div>
    </section>
  );
}
function Metric({ label, value }: Readonly<{ label: string; value: string }>) { return <div className="rounded-xl border border-slate-200 bg-slate-50 p-3"><div className="text-xs font-black uppercase tracking-wide text-slate-500">{label}</div><div className="mt-1 break-all text-sm font-black text-slate-900">{value}</div></div>; }
