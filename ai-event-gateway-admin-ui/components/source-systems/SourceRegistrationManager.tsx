'use client';

import Link from 'next/link';
import { useState } from 'react';
import { Button } from '@/components/ui/Button';
import { StatusBadge } from '@/components/common/StatusBadge';
import { sourceSystemsAdminApi } from '@/lib/api/domains/sourceSystemsAdminApi';
import type { CoreSourceSystem, CoreWorkloadSourceRegistration, CoreWorkloadSourceRegistrationCommand } from '@/lib/types/core';

const inputClass = 'mt-1 w-full rounded-xl border border-slate-300 bg-white px-3 py-2 text-sm outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100';

function csv(value?: string[]): string { return (value ?? []).join(', '); }
function split(value: string): string[] { return Array.from(new Set(value.split(',').map((item) => item.trim().toUpperCase()).filter(Boolean))); }

type AuthPreset = 'MACHINE' | 'HUMAN' | 'MACHINE_OR_HUMAN';
function authPreset(types?: string[]): AuthPreset {
  const normalized = new Set((types ?? []).map((value) => value.toUpperCase()));
  if (normalized.has('SERVICE_ACCOUNT') && normalized.has('HUMAN')) return 'MACHINE_OR_HUMAN';
  if (normalized.has('HUMAN')) return 'HUMAN';
  return 'MACHINE';
}
function principalTypes(value: AuthPreset): string[] {
  if (value === 'HUMAN') return ['HUMAN'];
  if (value === 'MACHINE_OR_HUMAN') return ['SERVICE_ACCOUNT', 'HUMAN'];
  return ['SERVICE_ACCOUNT'];
}

function blankCommand(source: CoreSourceSystem): CoreWorkloadSourceRegistrationCommand {
  return {
    registrationName: `${source.displayName || source.sourceSystemId} Event Intake`,
    channelType: 'API',
    principalBindingMode: 'DYNAMIC',
    allowedPrincipalTypes: ['SERVICE_ACCOUNT'],
    idempotencyStrategy: 'OPTIONAL_KEY',
    idempotencyRetentionSeconds: 86400,
    orderingStrategy: 'NONE',
    acknowledgementMode: 'SYNC_RESPONSE',
    status: 'ACTIVE',
    defaultRegistration: false,
  };
}

export function SourceRegistrationManager({ tenantId, source, editable }: Readonly<{ tenantId: string; source: CoreSourceSystem; editable: boolean }>) {
  const [open, setOpen] = useState(false);
  const [rows, setRows] = useState<CoreWorkloadSourceRegistration[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editor, setEditor] = useState<CoreWorkloadSourceRegistrationCommand>(() => blankCommand(source));
  const [eventTypes, setEventTypes] = useState('');
  const [objectTypes, setObjectTypes] = useState('');
  const [inputSchemas, setInputSchemas] = useState('');
  const [advancedOpen, setAdvancedOpen] = useState(false);
  const [saving, setSaving] = useState(false);

  async function reload() {
    setLoading(true); setError(null);
    try { setRows(await sourceSystemsAdminApi.getSourceRegistrations(tenantId, source.sourceSystemId)); }
    catch (caught) { setError(caught instanceof Error ? caught.message : 'Unable to load Intake registrations.'); }
    finally { setLoading(false); }
  }

  async function toggle() {
    const next = !open; setOpen(next);
    if (next && rows.length === 0) await reload();
  }

  function startCreate() {
    const next = blankCommand(source);
    setEditingId(''); setEditor(next); setEventTypes(''); setObjectTypes(''); setInputSchemas(''); setAdvancedOpen(false);
  }

  function startEdit(row: CoreWorkloadSourceRegistration) {
    setEditingId(row.sourceRegistrationId);
    setEditor({
      sourceRegistrationId: row.sourceRegistrationId,
      registrationName: row.registrationName,
      channelType: row.channelType,
      principalBindingMode: row.principalBindingMode,
      allowedPrincipalTypes: row.allowedPrincipalTypes,
      staticPrincipalRef: row.staticPrincipalRef,
      allowedEventTypes: row.allowedEventTypes,
      allowedObjectTypes: row.allowedObjectTypes,
      inputSchemas: row.inputSchemas,
      idempotencyStrategy: row.idempotencyStrategy,
      idempotencyRetentionSeconds: row.idempotencyRetentionSeconds,
      orderingStrategy: row.orderingStrategy,
      acknowledgementMode: row.acknowledgementMode,
      rateLimitPerMinute: row.rateLimitPerMinute,
      quotaPerDay: row.quotaPerDay,
      dataClassificationProfile: row.dataClassificationProfile,
      residencyProfile: row.residencyProfile,
      status: row.status,
      effectiveFrom: row.effectiveFrom,
      effectiveTo: row.effectiveTo,
      defaultRegistration: row.defaultRegistration,
    });
    setEventTypes(csv(row.allowedEventTypes)); setObjectTypes(csv(row.allowedObjectTypes)); setInputSchemas(csv(row.inputSchemas)); setAdvancedOpen(false);
  }

  async function save() {
    if (!editor.registrationName?.trim()) { setError('Registration Name is required.'); return; }
    setSaving(true); setError(null);
    const body: CoreWorkloadSourceRegistrationCommand = {
      ...editor,
      registrationName: editor.registrationName.trim(),
      allowedPrincipalTypes: editor.allowedPrincipalTypes ?? ['SERVICE_ACCOUNT'],
      allowedEventTypes: split(eventTypes),
      allowedObjectTypes: split(objectTypes),
      inputSchemas: split(inputSchemas),
    };
    try {
      if (editingId) await sourceSystemsAdminApi.updateSourceRegistration(tenantId, source.sourceSystemId, editingId, body);
      else await sourceSystemsAdminApi.createSourceRegistration(tenantId, source.sourceSystemId, body);
      setEditingId(null); await reload();
    } catch (caught) { setError(caught instanceof Error ? caught.message : 'Unable to save Intake registration.'); }
    finally { setSaving(false); }
  }

  async function retire(row: CoreWorkloadSourceRegistration) {
    if (!window.confirm(`Retire Intake registration ${row.registrationName}?`)) return;
    setSaving(true); setError(null);
    try { await sourceSystemsAdminApi.retireSourceRegistration(tenantId, source.sourceSystemId, row.sourceRegistrationId); await reload(); }
    catch (caught) { setError(caught instanceof Error ? caught.message : 'Unable to retire Intake registration.'); }
    finally { setSaving(false); }
  }

  const currentAuth = authPreset(editor.allowedPrincipalTypes);

  return (
    <div className="mt-4 rounded-2xl border border-cyan-200 bg-cyan-50/60 p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="text-sm font-black text-slate-950">Intake</div>
          <p className="mt-1 max-w-3xl text-xs leading-5 text-slate-600">Intake controls how this Source System may submit workload. Most administrators only need the channel and caller type; idempotency, ordering, schemas, quotas and residency are Advanced Intake Policy.</p>
        </div>
        <div className="flex gap-2">
          <button type="button" onClick={() => void toggle()} className="rounded-xl border border-slate-200 bg-white px-3 py-2 text-xs font-black text-slate-700 hover:bg-slate-50">{open ? 'Hide Intake' : 'Manage Intake'}</button>
          {open && editable ? <Button size="sm" tone="secondary" onClick={startCreate}>Add Intake</Button> : null}
        </div>
      </div>

      {open ? (
        <div className="mt-4 space-y-3">
          <div className="rounded-xl border border-blue-200 bg-blue-50 p-3 text-xs leading-5 text-blue-950"><b>Ingress endpoint:</b> <span className="font-mono font-black">POST /api/events/intake</span>. Machine credentials are governed separately by IAM. <Link href={`/admin/tenants/${encodeURIComponent(tenantId)}/security?view=MACHINE`} className="font-black underline">Machine Access</Link></div>
          {error ? <div className="rounded-xl border border-rose-200 bg-rose-50 p-3 text-xs font-bold text-rose-900">{error}</div> : null}
          {loading ? <div className="text-sm font-bold text-slate-500">Loading Intake…</div> : null}
          {!loading && rows.length === 0 ? <div className="rounded-xl border border-dashed border-slate-300 bg-white p-4 text-sm text-slate-600">No Intake registration is visible. Add one before expecting workload to enter from this Source System.</div> : null}
          {rows.map((row) => (
            <div key={row.sourceRegistrationId} className="rounded-xl border border-slate-200 bg-white p-4">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <div className="flex flex-wrap items-center gap-2"><span className="font-black text-slate-950">{row.registrationName}</span><StatusBadge status={row.status} />{row.defaultRegistration ? <span className="rounded-full bg-blue-50 px-2.5 py-1 text-xs font-black text-blue-800">DEFAULT</span> : null}</div>
                  <div className="mt-2 flex flex-wrap gap-2 text-xs font-bold text-slate-600">
                    <span className="rounded-full bg-slate-100 px-2.5 py-1">{row.channelType}</span>
                    <span className="rounded-full bg-slate-100 px-2.5 py-1">Caller: {authPreset(row.allowedPrincipalTypes) === 'MACHINE' ? 'Machine' : authPreset(row.allowedPrincipalTypes) === 'HUMAN' ? 'Human' : 'Machine / Human'}</span>
                    <span className="rounded-full bg-slate-100 px-2.5 py-1">ACK: {row.acknowledgementMode}</span>
                    {row.dataClassificationProfile ? <span className="rounded-full bg-slate-100 px-2.5 py-1">Data: {row.dataClassificationProfile}</span> : null}
                    <span className="rounded-full bg-cyan-50 px-2.5 py-1 text-cyan-800">Ownership inherited</span>
                  </div>
                  <div className="mt-2 break-all font-mono text-[11px] text-slate-400">{row.sourceRegistrationId}</div>
                </div>
                {editable && row.status !== 'RETIRED' ? <div className="flex gap-2"><button type="button" onClick={() => startEdit(row)} className="rounded-lg border border-slate-200 px-3 py-1.5 text-xs font-black">Edit</button><button type="button" onClick={() => void retire(row)} className="rounded-lg border border-rose-200 px-3 py-1.5 text-xs font-black text-rose-700">Retire</button></div> : null}
              </div>
            </div>
          ))}

          {editingId !== null ? (
            <div className="rounded-2xl border border-blue-200 bg-white p-4 shadow-sm">
              <div className="text-sm font-black text-slate-950">{editingId ? 'Edit Intake' : 'Add Intake'}</div>
              <p className="mt-1 text-xs leading-5 text-slate-500">Start with the business-facing choices. Open Advanced Intake Policy only when the integration requires a specific transport contract.</p>
              <div className="mt-4 grid gap-3 md:grid-cols-2">
                <label className="text-xs font-black text-slate-700">Intake name<input className={inputClass} value={editor.registrationName ?? ''} onChange={(e) => setEditor((v) => ({ ...v, registrationName: e.target.value }))} /></label>
                <label className="text-xs font-black text-slate-700">Intake type<select className={inputClass} value={editor.channelType ?? 'API'} onChange={(e) => setEditor((v) => ({ ...v, channelType: e.target.value }))}><option value="API">REST API</option><option value="EVENT">Event gateway</option><option value="HUMAN">Human initiated</option><option value="SCHEDULE">Scheduled workload</option><option value="A2A_INBOUND">Inbound A2A</option><option value="REPLAY">Replay / recovery</option></select></label>
                <label className="text-xs font-black text-slate-700">Authentication<select className={inputClass} value={currentAuth} onChange={(e) => setEditor((v) => ({ ...v, principalBindingMode: 'DYNAMIC', staticPrincipalRef: undefined, allowedPrincipalTypes: principalTypes(e.target.value as AuthPreset) }))}><option value="MACHINE">Machine credential</option><option value="HUMAN">Signed-in human</option><option value="MACHINE_OR_HUMAN">Machine or signed-in human</option></select></label>
                <label className="text-xs font-black text-slate-700">Status<select className={inputClass} value={editor.status ?? 'ACTIVE'} onChange={(e) => setEditor((v) => ({ ...v, status: e.target.value, defaultRegistration: e.target.value === 'ACTIVE' ? v.defaultRegistration : false }))}><option>ACTIVE</option><option>DISABLED</option></select></label>
              </div>
              <label className="mt-3 flex items-center gap-2 rounded-xl border border-slate-200 bg-slate-50 p-3 text-xs font-black text-slate-700"><input type="checkbox" disabled={(editor.status ?? 'ACTIVE') !== 'ACTIVE'} checked={Boolean(editor.defaultRegistration)} onChange={(e) => setEditor((v) => ({ ...v, defaultRegistration: e.target.checked }))} />Use as the default Intake when a compatible caller does not send sourceRegistrationId</label>

              <details open={advancedOpen} onToggle={(event) => setAdvancedOpen((event.currentTarget as HTMLDetailsElement).open)} className="mt-4 rounded-2xl border border-slate-200 bg-slate-50 p-4">
                <summary className="cursor-pointer text-xs font-black text-slate-800">Advanced Intake Policy</summary>
                <div className="mt-4 grid gap-3 md:grid-cols-2">
                  {!editingId ? <label className="text-xs font-black text-slate-700">Registration ID<input className={inputClass} value={editor.sourceRegistrationId ?? ''} onChange={(e) => setEditor((v) => ({ ...v, sourceRegistrationId: e.target.value }))} placeholder="Generated when empty" /></label> : null}
                  <label className="text-xs font-black text-slate-700">Principal Binding<select className={inputClass} value={editor.principalBindingMode ?? 'DYNAMIC'} onChange={(e) => setEditor((v) => ({ ...v, principalBindingMode: e.target.value }))}><option>DYNAMIC</option><option>STATIC</option></select></label>
                  {editor.principalBindingMode === 'STATIC' ? <label className="text-xs font-black text-slate-700">Static Principal Ref<input className={inputClass} value={editor.staticPrincipalRef ?? ''} onChange={(e) => setEditor((v) => ({ ...v, staticPrincipalRef: e.target.value }))} /></label> : null}
                  <label className="text-xs font-black text-slate-700">Allowed Event Types<input className={inputClass} value={eventTypes} onChange={(e) => setEventTypes(e.target.value)} placeholder="Empty = any" /></label>
                  <label className="text-xs font-black text-slate-700">Allowed Object Types<input className={inputClass} value={objectTypes} onChange={(e) => setObjectTypes(e.target.value)} placeholder="Empty = any" /></label>
                  <label className="text-xs font-black text-slate-700">Input Schemas<input className={inputClass} value={inputSchemas} onChange={(e) => setInputSchemas(e.target.value)} placeholder="Optional governed schema identifiers" /></label>
                  <label className="text-xs font-black text-slate-700">Ordering<select className={inputClass} value={editor.orderingStrategy ?? 'NONE'} onChange={(e) => setEditor((v) => ({ ...v, orderingStrategy: e.target.value }))}><option>NONE</option><option>SOURCE_SEQUENCE</option><option>AGGREGATE_SEQUENCE</option></select></label>
                  <label className="text-xs font-black text-slate-700">Idempotency<select className={inputClass} value={editor.idempotencyStrategy ?? 'OPTIONAL_KEY'} onChange={(e) => setEditor((v) => ({ ...v, idempotencyStrategy: e.target.value }))}><option>NONE</option><option>OPTIONAL_KEY</option><option>REQUIRED_KEY</option><option>SOURCE_EVENT_ID</option></select></label>
                  <label className="text-xs font-black text-slate-700">Idempotency Retention<select className={inputClass} value={String(editor.idempotencyRetentionSeconds ?? 86400)} onChange={(e) => setEditor((v) => ({ ...v, idempotencyRetentionSeconds: Number(e.target.value) }))}><option value="3600">1 hour</option><option value="21600">6 hours</option><option value="86400">24 hours (recommended)</option><option value="604800">7 days</option></select></label>
                  <label className="text-xs font-black text-slate-700">Acknowledgement<select className={inputClass} value={editor.acknowledgementMode ?? 'SYNC_RESPONSE'} onChange={(e) => setEditor((v) => ({ ...v, acknowledgementMode: e.target.value }))}><option>SYNC_RESPONSE</option><option>CALLBACK</option><option>OUTBOX_EVENT</option><option>POLL</option><option>NONE</option></select></label>
                  <label className="text-xs font-black text-slate-700">Rate Limit / minute<input type="number" min={1} className={inputClass} value={editor.rateLimitPerMinute ?? ''} onChange={(e) => setEditor((v) => ({ ...v, rateLimitPerMinute: e.target.value === '' ? undefined : Number(e.target.value) }))} placeholder="Unlimited" /></label>
                  <label className="text-xs font-black text-slate-700">Daily Quota<input type="number" min={1} className={inputClass} value={editor.quotaPerDay ?? ''} onChange={(e) => setEditor((v) => ({ ...v, quotaPerDay: e.target.value === '' ? undefined : Number(e.target.value) }))} placeholder="Unlimited" /></label>
                  <label className="text-xs font-black text-slate-700">Data Classification<select className={inputClass} value={editor.dataClassificationProfile ?? ''} onChange={(e) => setEditor((v) => ({ ...v, dataClassificationProfile: e.target.value || undefined }))}><option value="">Not constrained here</option><option>PUBLIC</option><option>INTERNAL</option><option>CONFIDENTIAL</option><option>RESTRICTED</option></select></label>
                  <label className="text-xs font-black text-slate-700">Residency Profile<input className={inputClass} value={editor.residencyProfile ?? ''} onChange={(e) => setEditor((v) => ({ ...v, residencyProfile: e.target.value }))} placeholder="Optional governed profile code" /></label>
                </div>
              </details>
              <div className="mt-4 flex justify-end gap-2"><Button size="sm" tone="secondary" onClick={() => setEditingId(null)} disabled={saving}>Cancel</Button><Button size="sm" onClick={() => void save()} disabled={saving}>{saving ? 'Saving…' : 'Save Intake'}</Button></div>
            </div>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
