import type { IntegrationCredentialMetadata } from '@/lib/api/domains/integrationIdentityApi';
import { credentialRotationImpact } from '@/lib/phase7d/issueAgentUx';

function format(value?: string | null) { return value ? new Date(value).toLocaleString() : 'Not recorded'; }

export function CredentialMetadataImpactPanel({ credentials, mappingCount = 0, principalCount = 1 }: Readonly<{ credentials: IntegrationCredentialMetadata[]; mappingCount?: number; principalCount?: number }>) {
  const active = credentials.find((item) => String(item.status ?? '').toUpperCase() === 'ACTIVE') ?? credentials[0];
  const impact = credentialRotationImpact({ status: active?.status, expiresAt: active?.expiresAt, lastUsedAt: active?.lastUsedAt, rotatedAt: active?.rotatedAt, mappingCount, principalCount });
  return (
    <section className="rounded-2xl border border-amber-200 bg-amber-50 p-4" aria-labelledby="credential-impact-title">
      <h4 id="credential-impact-title" className="font-black text-amber-950">Credential metadata and rotation impact</h4>
      <p className="mt-1 text-sm leading-6 text-amber-900">Only reference metadata is displayed. Secret values, tokens, and private keys are never returned to the browser.</p>
      <dl className="mt-3 grid gap-3 text-sm sm:grid-cols-2 xl:grid-cols-4">
        <div><dt className="text-xs font-black uppercase text-amber-700">Status</dt><dd className="mt-1 font-bold">{active?.status ?? 'MISSING'}</dd></div>
        <div><dt className="text-xs font-black uppercase text-amber-700">Expires</dt><dd className="mt-1 font-bold">{format(active?.expiresAt)}</dd></div>
        <div><dt className="text-xs font-black uppercase text-amber-700">Last used</dt><dd className="mt-1 font-bold">{format(active?.lastUsedAt)}</dd></div>
        <div><dt className="text-xs font-black uppercase text-amber-700">Rotation urgency</dt><dd className="mt-1 font-bold">{impact.urgency}</dd></div>
      </dl>
      <div className="mt-3 rounded-xl border border-amber-200 bg-white p-3 text-sm text-slate-700">
        <div className="font-black text-slate-900">Impact preview</div>
        <p className="mt-1">Estimated governed references: {impact.blastRadius}. Runtime reconnect required: {impact.reconnectRequired ? 'likely' : 'not currently indicated'}.</p>
        <p className="mt-1 font-semibold">Safest next action: {impact.safestNextAction}</p>
      </div>
    </section>
  );
}
