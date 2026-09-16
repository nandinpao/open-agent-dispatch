'use client';

import { actionAllowed } from '@/lib/navigation/uiEntitlements';
import type { UiEntitlementResponse } from '@/lib/navigation/uiEntitlements';
import { EnterpriseAuthenticationPanel } from './EnterpriseAuthenticationPanel';
import { SecurityPolicyForms } from './SecurityPolicyForms';
import { SessionManagementPanel } from './SessionManagementPanel';

type Section = 'SESSIONS' | 'POLICIES' | 'FEDERATION';

const SECTIONS: Array<{ value: Section; label: string; description: string; viewAction: string }> = [
  { value: 'SESSIONS', label: 'Sessions', description: 'See who is signed in and revoke sessions when needed.', viewAction: 'access.security-session.view' },
  { value: 'POLICIES', label: 'Protection rules', description: 'Password, MFA, session and token safeguards.', viewAction: 'access.security-policy.view' },
  { value: 'FEDERATION', label: 'Enterprise sign-in', description: 'Company OIDC providers and sign-in policy.', viewAction: 'access.federation.view' },
];

export function IdentityProtectionWorkspace({ tenantId, section, entitlements, onSectionChange, onChanged }: Readonly<{ tenantId: string; section: Section; entitlements: UiEntitlementResponse | undefined; onSectionChange: (section: Section) => void; onChanged: () => void }>) {
  const visible = SECTIONS.filter((item) => actionAllowed(entitlements, item.viewAction));
  const selected = visible.some((item) => item.value === section) ? section : visible[0]?.value ?? 'SESSIONS';
  return <div className="space-y-4">
    <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
      <p className="text-xs font-black uppercase tracking-[.16em] text-blue-700">Sign-in & Protection</p>
      <h2 className="mt-1 text-xl font-black text-slate-950">Keep sign-in security in one workspace</h2>
      <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-600">Switch between sessions, protection rules and enterprise sign-in here. These are sections of the same security task, not separate administration pages.</p>
      <div className="mt-4 grid gap-2 md:grid-cols-3">{visible.map((item) => <button key={item.value} type="button" onClick={() => onSectionChange(item.value)} className={`rounded-2xl border p-3 text-left ${selected === item.value ? 'border-blue-400 bg-blue-50' : 'border-slate-200 hover:bg-slate-50'}`}><span className="block text-sm font-black text-slate-950">{item.label}</span><span className="mt-1 block text-xs leading-5 text-slate-500">{item.description}</span></button>)}</div>
    </section>
    {selected === 'SESSIONS' ? <SessionManagementPanel tenantId={tenantId} canManage={actionAllowed(entitlements, 'access.security-session.revoke')} onChanged={onChanged}/> : null}
    {selected === 'POLICIES' ? <SecurityPolicyForms tenantId={tenantId} canManage={actionAllowed(entitlements, 'access.security-policy.manage')} onChanged={onChanged}/> : null}
    {selected === 'FEDERATION' ? <EnterpriseAuthenticationPanel tenantId={tenantId} canManage={actionAllowed(entitlements, 'access.federation.manage')} onChanged={onChanged}/> : null}
  </div>;
}

export function normalizeIdentitySection(value: string | null): Section {
  return value === 'POLICIES' || value === 'FEDERATION' ? value : 'SESSIONS';
}
