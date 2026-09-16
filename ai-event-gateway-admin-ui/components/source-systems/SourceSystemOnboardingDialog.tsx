'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { useDialogAccessibility } from '@/hooks/useDialogAccessibility';
import { useUiEntitlements } from '@/lib/navigation/useUiEntitlements';
import { actionAllowedForScope } from '@/lib/navigation/uiEntitlements';
import { accessManagementApi } from '@/lib/api/accessManagementApi';
import { sourceSystemsAdminApi } from '@/lib/api/domains/sourceSystemsAdminApi';
import { Button } from '@/components/ui/Button';
import { AdvancedSection, EntityPicker, FormField, SelectField, TextAreaField, TextField } from '@/components/forms';
import type { Department, Group } from '@/lib/iam/types';
import type { CoreSourceSystem, CoreWorkloadSourceRegistration } from '@/lib/types/core';


export interface SourceSystemOnboardingResult {
  source: CoreSourceSystem;
  registration: CoreWorkloadSourceRegistration;
}

type IntakePreset = 'REST_API' | 'EVENT_GATEWAY' | 'SCHEDULE' | 'A2A_INBOUND' | 'HUMAN' | 'REPLAY';
type AuthPreset = 'MACHINE' | 'HUMAN' | 'MACHINE_OR_HUMAN';
type OwnerMode = 'TENANT' | 'DEPARTMENT' | 'GROUP';

function normalizeCode(value: string): string {
  return value.trim().toUpperCase().replace(/[^A-Z0-9_.-]/g, '_').replace(/^_+|_+$/g, '');
}

function departmentAncestorIds(departmentId: string, departments: Department[]): string[] {
  const byId = new Map(departments.map((item) => [item.departmentId, item] as const));
  const result: string[] = [];
  const visited = new Set<string>();
  let current = byId.get(departmentId)?.parentDepartmentId ?? '';
  while (current && !visited.has(current)) {
    visited.add(current);
    result.push(current);
    current = byId.get(current)?.parentDepartmentId ?? '';
  }
  return result;
}

function channelType(value: IntakePreset): string {
  switch (value) {
    case 'REST_API': return 'API';
    case 'EVENT_GATEWAY': return 'EVENT';
    case 'SCHEDULE': return 'SCHEDULE';
    case 'A2A_INBOUND': return 'A2A_INBOUND';
    case 'HUMAN': return 'HUMAN';
    case 'REPLAY': return 'REPLAY';
  }
}

function principalTypes(value: AuthPreset): string[] {
  if (value === 'HUMAN') return ['HUMAN'];
  if (value === 'MACHINE_OR_HUMAN') return ['SERVICE_ACCOUNT', 'HUMAN'];
  return ['SERVICE_ACCOUNT'];
}

function authLabel(value: AuthPreset): string {
  if (value === 'HUMAN') return 'Signed-in human';
  if (value === 'MACHINE_OR_HUMAN') return 'Machine or signed-in human';
  return 'Machine credential';
}

function intakeLabel(value: IntakePreset): string {
  const labels: Record<IntakePreset, string> = {
    REST_API: 'REST API',
    EVENT_GATEWAY: 'Event gateway',
    SCHEDULE: 'Scheduled workload',
    A2A_INBOUND: 'Inbound A2A',
    HUMAN: 'Human initiated',
    REPLAY: 'Replay / recovery',
  };
  return labels[value];
}

export function SourceSystemOnboardingDialog({
  open,
  tenantId,
  existingSources,
  onClose,
  onCreated,
}: Readonly<{
  open: boolean;
  tenantId: string;
  existingSources: CoreSourceSystem[];
  onClose: () => void;
  onCreated: (result: SourceSystemOnboardingResult) => void;
}>) {
  const entitlements = useUiEntitlements();
  const [departments, setDepartments] = useState<Department[]>([]);
  const [groups, setGroups] = useState<Group[]>([]);
  const [ownershipLoading, setOwnershipLoading] = useState(false);
  const [displayName, setDisplayName] = useState('');
  const [sourceSystemId, setSourceSystemId] = useState('');
  const [codeTouched, setCodeTouched] = useState(false);
  const [intake, setIntake] = useState<IntakePreset>('REST_API');
  const [authentication, setAuthentication] = useState<AuthPreset>('MACHINE');
  const [ownerMode, setOwnerMode] = useState<OwnerMode>('TENANT');
  const [ownerDepartmentId, setOwnerDepartmentId] = useState('');
  const [ownerGroupId, setOwnerGroupId] = useState('');
  const [description, setDescription] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [createdSource, setCreatedSource] = useState<CoreSourceSystem | null>(null);
  const [result, setResult] = useState<SourceSystemOnboardingResult | null>(null);
  const dialogRef = useDialogAccessibility(open, onClose);

  const allowTenantWide = actionAllowedForScope(entitlements.value, 'source-systems.create', 'TENANT', tenantId);
  const manageableDepartments = useMemo(() => departments.filter((item) => actionAllowedForScope(
    entitlements.value, 'source-systems.create', 'DEPARTMENT', item.departmentId, departmentAncestorIds(item.departmentId, departments),
  )), [departments, entitlements.value]);
  const manageableGroups = useMemo(() => groups.filter((item) => actionAllowedForScope(
    entitlements.value, 'source-systems.create', 'GROUP', item.groupId,
  )), [groups, entitlements.value]);

  useEffect(() => {
    if (!open) return;
    setDisplayName('');
    setSourceSystemId('');
    setCodeTouched(false);
    setIntake('REST_API');
    setAuthentication('MACHINE');
    setOwnerDepartmentId('');
    setOwnerGroupId('');
    setDescription('');
    setError(null);
    setCreatedSource(null);
    setResult(null);
    setOwnershipLoading(true);
    void Promise.all([
      accessManagementApi.departments(tenantId, 0, 100, '', 'ACTIVE'),
      accessManagementApi.groups(tenantId, 0, 100, '', '', 'ACTIVE'),
    ]).then(([departmentPage, groupPage]) => {
      setDepartments(departmentPage.items);
      setGroups(groupPage.items);
    }).catch((cause) => {
      setError(cause instanceof Error ? cause.message : 'Unable to load ownership choices.');
    }).finally(() => setOwnershipLoading(false));
  }, [open, tenantId]);

  useEffect(() => {
    if (!open || ownershipLoading) return;
    if (allowTenantWide) setOwnerMode('TENANT');
    else if (manageableDepartments.length) setOwnerMode('DEPARTMENT');
    else if (manageableGroups.length) setOwnerMode('GROUP');
  }, [allowTenantWide, manageableDepartments.length, manageableGroups.length, open, ownershipLoading]);

  useEffect(() => {
    if (!codeTouched) setSourceSystemId(normalizeCode(displayName));
  }, [codeTouched, displayName]);

  if (!open) return null;

  async function submit() {
    const scopedTenantId = tenantId.trim();
    const code = normalizeCode(sourceSystemId || displayName);
    if (!scopedTenantId) { setError('No active workspace is available.'); return; }
    if (!displayName.trim()) { setError('Source System name is required.'); return; }
    if (!code) { setError('OpenDispatch could not derive a Source System code.'); return; }
    if (existingSources.some((source) => source.sourceSystemId.toUpperCase() === code.toUpperCase()) && !createdSource) {
      setError(`Source System Code ${code} already exists.`); return;
    }
    if (ownerMode === 'TENANT' && !allowTenantWide) { setError('Choose an authorized Department or Group owner.'); return; }
    if (ownerMode === 'DEPARTMENT' && !ownerDepartmentId) { setError('Select an Owner Department.'); return; }
    if (ownerMode === 'GROUP' && !ownerGroupId) { setError('Select an Owner Group.'); return; }

    setBusy(true); setError(null);
    try {
      const source = createdSource ?? await sourceSystemsAdminApi.createSourceSystem(scopedTenantId, {
        sourceSystemId: code,
        displayName: displayName.trim(),
        description: description.trim() || undefined,
        status: 'ACTIVE',
        ownerDepartmentId: ownerMode === 'DEPARTMENT' ? ownerDepartmentId : undefined,
        ownerGroupId: ownerMode === 'GROUP' ? ownerGroupId : undefined,
      });
      if (!createdSource) setCreatedSource(source);
      const registration = await sourceSystemsAdminApi.createSourceRegistration(scopedTenantId, source.sourceSystemId, {
        registrationName: `${displayName.trim()} ${intakeLabel(intake)} Intake`,
        channelType: channelType(intake),
        principalBindingMode: 'DYNAMIC',
        allowedPrincipalTypes: principalTypes(authentication),
        idempotencyStrategy: 'OPTIONAL_KEY',
        idempotencyRetentionSeconds: 86400,
        orderingStrategy: 'NONE',
        acknowledgementMode: 'SYNC_RESPONSE',
        status: 'ACTIVE',
        defaultRegistration: true,
      });
      const completed = { source, registration };
      setResult(completed);
      onCreated(completed);
    } catch (cause) {
      const prefix = createdSource ? 'Source System exists, but the Intake registration could not be completed. Retry to finish the registration. ' : '';
      setError(prefix + (cause instanceof Error ? cause.message : 'Unable to create the Source System onboarding configuration.'));
    } finally { setBusy(false); }
  }

  const noOwnershipOption = !ownershipLoading && !allowTenantWide && manageableDepartments.length === 0 && manageableGroups.length === 0;
  const machineAuth = authentication === 'MACHINE' || authentication === 'MACHINE_OR_HUMAN';

  return (
    <div ref={dialogRef} tabIndex={-1} className="fixed inset-0 z-[100] flex items-start justify-center overflow-y-auto bg-slate-950/60 p-4 outline-none sm:p-8" role="dialog" aria-modal="true" aria-label="Create Source System">
      <div className="w-full max-w-3xl rounded-3xl bg-white shadow-2xl">
        <div className="flex items-start justify-between gap-4 rounded-t-3xl border-b border-slate-200 px-6 py-5">
          <div>
            <div className="text-xs font-black uppercase tracking-wide text-blue-700">Source onboarding</div>
            <h2 className="mt-1 text-xl font-black text-slate-950">{result ? 'Source System is ready for configuration' : 'Create Source System'}</h2>
            <p className="mt-1 max-w-2xl text-sm leading-6 text-slate-600">Create the business source and its default Intake registration in one guided popup. Advanced intake policy remains available on the Source Systems workspace.</p>
          </div>
          <button type="button" onClick={onClose} className="rounded-xl border border-slate-200 px-3 py-2 text-sm font-black text-slate-600 hover:bg-slate-50" aria-label="Close">×</button>
        </div>

        {result ? (
          <div className="space-y-4 p-6">
            <div className="rounded-2xl border border-emerald-200 bg-emerald-50 p-4 text-sm leading-6 text-emerald-950">
              <b className="block">{result.source.displayName} is created.</b>
              The default Intake registration is ACTIVE and can now be selected by Dispatch configuration.
            </div>
            <div className="grid gap-3 md:grid-cols-2">
              <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4"><div className="text-xs font-black uppercase text-slate-500">Source System</div><div className="mt-1 font-black text-slate-950">{result.source.sourceSystemId}</div></div>
              <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4"><div className="text-xs font-black uppercase text-slate-500">Intake type</div><div className="mt-1 font-black text-slate-950">{intakeLabel(intake)}</div></div>
              <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4"><div className="text-xs font-black uppercase text-slate-500">Authentication</div><div className="mt-1 font-black text-slate-950">{authLabel(authentication)}</div></div>
              <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4"><div className="text-xs font-black uppercase text-slate-500">Intake endpoint</div><div className="mt-1 font-mono text-sm font-black text-slate-950">POST /api/events/intake</div></div>
            </div>
            {machineAuth ? <div className="rounded-2xl border border-blue-200 bg-blue-50 p-4 text-sm leading-6 text-blue-950"><b className="block">Machine credential is a separate IAM identity.</b>This Intake registration allows Service Accounts; it does not create or expose a secret. Issue the caller credential from the existing Machine Access workspace when the integration is ready. <Link className="font-black underline" href={`/admin/tenants/${encodeURIComponent(tenantId)}/security?view=MACHINE`}>Open Machine Access</Link></div> : null}
            <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm leading-6 text-amber-950"><b className="block">Next: create the Source Flow.</b>The real Test Event is intentionally performed in Dispatch after a Flow, Pool and runtime readiness are configured, so a test cannot create misleading routing evidence.</div>
          </div>
        ) : (
          <div className="space-y-5 p-6">
            {error ? <div className="rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm font-bold text-rose-900">{error}</div> : null}
            {noOwnershipOption ? <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm font-bold text-amber-950">Your current access cannot create a Tenant-wide, Department-owned, or Group-owned Source System.</div> : null}

            <div className="grid gap-4 md:grid-cols-2">
              <FormField id="source-onboarding-name" label="Source System name" required>
                <TextField id="source-onboarding-name" value={displayName} onChange={setDisplayName} placeholder="ERP Production" disabled={busy} />
              </FormField>
              <FormField id="source-onboarding-intake" label="Intake type">
                <SelectField id="source-onboarding-intake" value={intake} onChange={(value) => setIntake(value as IntakePreset)} disabled={busy} options={[
                  { value:'REST_API', label:'REST API' }, { value:'EVENT_GATEWAY', label:'Event gateway' }, { value:'SCHEDULE', label:'Scheduled workload' },
                  { value:'A2A_INBOUND', label:'Inbound A2A' }, { value:'HUMAN', label:'Human initiated' }, { value:'REPLAY', label:'Replay / recovery' },
                ]} />
              </FormField>
              <FormField id="source-onboarding-auth" label="Authentication" help="This defines who may present workload to the registration. Actual Service Account secrets remain in Machine Access.">
                <SelectField id="source-onboarding-auth" value={authentication} onChange={(value) => setAuthentication(value as AuthPreset)} disabled={busy} options={[
                  { value:'MACHINE', label:'Machine credential (recommended for ERP/MES)' },
                  { value:'HUMAN', label:'Signed-in human' },
                  { value:'MACHINE_OR_HUMAN', label:'Machine or signed-in human' },
                ]} />
              </FormField>
              <FormField id="source-onboarding-owner-mode" label="Data owner">
                <SelectField id="source-onboarding-owner-mode" value={ownerMode} onChange={(value) => { setOwnerMode(value as OwnerMode); setOwnerDepartmentId(''); setOwnerGroupId(''); }} disabled={busy || ownershipLoading} options={[
                  ...(allowTenantWide ? [{ value:'TENANT', label:'Tenant-wide' }] : []),
                  ...(manageableDepartments.length ? [{ value:'DEPARTMENT', label:'Department' }] : []),
                  ...(manageableGroups.length ? [{ value:'GROUP', label:'Group' }] : []),
                ]} />
              </FormField>
            </div>

            {ownerMode === 'DEPARTMENT' ? <FormField id="onboarding-source-owner-department" label="Owner Department" required><EntityPicker id="onboarding-source-owner-department" value={ownerDepartmentId} onChange={setOwnerDepartmentId} disabled={busy} placeholder="Select an authorized Department" options={manageableDepartments.map((item) => ({ value: item.departmentId, label: item.name, description: item.code }))} /></FormField> : null}
            {ownerMode === 'GROUP' ? <FormField id="onboarding-source-owner-group" label="Owner Group" required><EntityPicker id="onboarding-source-owner-group" value={ownerGroupId} onChange={setOwnerGroupId} disabled={busy} placeholder="Select an authorized Group" options={manageableGroups.map((item) => ({ value: item.groupId, label: item.name, description: item.code }))} /></FormField> : null}

            <div className="rounded-2xl border border-blue-100 bg-blue-50 p-4 text-sm leading-6 text-blue-950"><b className="block">What OpenDispatch creates now</b>One ACTIVE Source System plus one ACTIVE default Intake registration. The endpoint is <span className="font-mono font-black">POST /api/events/intake</span>. Dispatch Flow and real Test Event remain the next guided steps.</div>

            <AdvancedSection title="Advanced source identity" description="technical code and optional description">
              <div className="space-y-4">
                <FormField id="source-onboarding-code" label="Source System code" help="Generated from the name. Change only when an established enterprise source code must be preserved.">
                  <TextField id="source-onboarding-code" value={sourceSystemId} onChange={(value) => { setCodeTouched(true); setSourceSystemId(normalizeCode(value)); }} placeholder="ERP_PRODUCTION" disabled={busy} />
                </FormField>
                <FormField id="source-onboarding-description" label="Description">
                  <TextAreaField id="source-onboarding-description" value={description} onChange={setDescription} placeholder="Optional business description" disabled={busy} rows={4} />
                </FormField>
              </div>
            </AdvancedSection>
          </div>
        )}

        <div className="flex justify-end gap-2 rounded-b-3xl border-t border-slate-200 bg-slate-50 px-6 py-4">
          {result ? <Button tone="primary" onClick={onClose}>Continue</Button> : <><Button tone="secondary" onClick={onClose} disabled={busy}>Cancel</Button><Button tone="primary" onClick={() => void submit()} disabled={busy || ownershipLoading || noOwnershipOption}>{busy ? (createdSource ? 'Finishing Intake…' : 'Creating…') : 'Create Source & Intake'}</Button></>}
        </div>
      </div>
    </div>
  );
}
