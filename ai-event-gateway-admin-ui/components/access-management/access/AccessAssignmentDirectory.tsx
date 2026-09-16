'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { accessManagementApi } from '@/lib/api/accessManagementApi';
import { formatIamError } from '@/lib/iam/errorPresentation';
import type { AccessAssignment, RoleBindingRevocationPreview } from '@/lib/iam/types';
import { ErrorNotice, SuccessNotice } from '../ui';
import { AuditReasonSelector, ContextLink, HumanStatus, SearchField, SelectField, isAuditReasonValid } from '../shared/beginnerUi';
import { WorkspaceModal } from '../shared/workspaceUi';
import { GuidedEmptyState, ReadOnlyBoundary, TechnicalDetails } from '../shared/acceptanceUi';

interface Filters { text: string; lifecycle: string; principalType: string; scopeType: string; }
const EMPTY_FILTERS: Filters = { text: '', lifecycle: '', principalType: '', scopeType: '' };

export function AccessAssignmentDirectory({
  tenantId,
  highlightedBindingId,
  canManage,
  refreshKey,
  onChanged,
  onAssign,
}: Readonly<{
  tenantId: string;
  highlightedBindingId?: string;
  canManage: boolean;
  refreshKey: number;
  onChanged: () => void;
  onAssign?: () => void;
}>) {
  const [draft, setDraft] = useState<Filters>(() => ({ ...EMPTY_FILTERS, text: highlightedBindingId ?? '' }));
  const [applied, setApplied] = useState<Filters>(() => ({ ...EMPTY_FILTERS, text: highlightedBindingId ?? '' }));
  const [items, setItems] = useState<AccessAssignment[]>([]);
  const [nextCursor, setNextCursor] = useState('');
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [revoke, setRevoke] = useState<AccessAssignment | null>(null);
  const [preview, setPreview] = useState<RoleBindingRevocationPreview | null>(null);
  const [reason, setReason] = useState('Access review correction');

  const load = useCallback(async (cursor = '', append = false) => {
    setLoading(true);
    setError('');
    try {
      const page = await accessManagementApi.accessAssignments(tenantId, 50, cursor, applied.text, '', applied.principalType, applied.scopeType, applied.lifecycle);
      setItems((current) => append ? [...current, ...page.items] : page.items);
      setNextCursor(page.nextCursor);
      setHasMore(page.hasMore);
    } catch (cause) {
      setError(formatIamError(cause, 'Unable to load access assignments.'));
    } finally {
      setLoading(false);
    }
  }, [applied, tenantId]);

  useEffect(() => { void load(); }, [load, refreshKey]);

  const selectedExists = useMemo(() => items.some((item) => item.bindingId === highlightedBindingId), [highlightedBindingId, items]);
  const hasAppliedFilters = useMemo(() => Object.values(applied).some((value) => value.trim().length > 0), [applied]);

  async function openRevoke(item: AccessAssignment) {
    setRevoke(item);
    setReason('');
    setPreview(null);
    setError('');
    if (item.principalType !== 'USER') return;
    try {
      setPreview(await accessManagementApi.previewRoleBindingRevocation(tenantId, item.bindingId, item.principalId));
    } catch (cause) {
      setError(formatIamError(cause, 'Unable to preview the user’s access after revocation.'));
    }
  }

  async function confirmRevoke() {
    if (!revoke) return;
    if (!isAuditReasonValid(reason, 'HIGH_RISK')) { setError('Enter the specific access-revocation justification.'); return; }
    setBusy(true); setError(''); setNotice('');
    try {
      await accessManagementApi.revokeRoleBinding(tenantId, revoke.bindingId, revoke.version, reason);
      setNotice(`${revoke.roleName} was revoked from ${revoke.principalName}. Effective access will be recalculated immediately.`);
      setRevoke(null); setPreview(null);
      await load(); onChanged();
    } catch (cause) {
      setError(formatIamError(cause, 'Unable to revoke the access assignment.'));
    } finally { setBusy(false); }
  }

  return (
    <div className="space-y-5">
      <ErrorNotice message={error} />
      <SuccessNotice message={notice} />
      <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
        <form onSubmit={(event) => { event.preventDefault(); setApplied(draft); }} className="grid gap-3 lg:grid-cols-[minmax(240px,1fr)_180px_180px_180px_auto] lg:items-end">
          <div><label htmlFor="assignment-text" className="mb-1.5 block text-sm font-black text-slate-800">Search assignments</label><SearchField id="assignment-text" value={draft.text} onChange={(text) => setDraft((value) => ({ ...value, text }))} placeholder="Person, Department, Group, responsibility or scope" /></div>
          <div><label htmlFor="assignment-lifecycle" className="mb-1.5 block text-sm font-black text-slate-800">Lifecycle</label><SelectField id="assignment-lifecycle" value={draft.lifecycle} onChange={(lifecycle) => setDraft((value) => ({ ...value, lifecycle }))} options={[{ value: 'ACTIVE', label: 'Active' }, { value: 'EXPIRING', label: 'Expiring in 30 days' }, { value: 'EXPIRED', label: 'Expired' }, { value: 'SCHEDULED', label: 'Scheduled' }, { value: 'REVOKED', label: 'Revoked' }]} placeholder="All lifecycle states" /></div>
          <div><label htmlFor="assignment-principal" className="mb-1.5 block text-sm font-black text-slate-800">Assigned to</label><SelectField id="assignment-principal" value={draft.principalType} onChange={(principalType) => setDraft((value) => ({ ...value, principalType }))} options={[{ value: 'USER', label: 'People' }, { value: 'DEPARTMENT', label: 'Departments' }, { value: 'GROUP', label: 'Groups' }, { value: 'SERVICE_ACCOUNT', label: 'Service Accounts' }]} placeholder="All recipient types" /></div>
          <div><label htmlFor="assignment-scope" className="mb-1.5 block text-sm font-black text-slate-800">Applies to</label><SelectField id="assignment-scope" value={draft.scopeType} onChange={(scopeType) => setDraft((value) => ({ ...value, scopeType }))} options={[{ value: 'TENANT', label: 'Entire Tenant' }, { value: 'DEPARTMENT', label: 'One Department' }, { value: 'DEPARTMENT_SUBTREE', label: 'Department and children' }, { value: 'GROUP', label: 'One Group' }]} placeholder="All scopes" /></div>
          <div className="flex gap-2"><button type="submit" className="rounded-xl bg-slate-950 px-4 py-2.5 text-sm font-black text-white">Apply</button><button type="button" onClick={() => { setDraft(EMPTY_FILTERS); setApplied(EMPTY_FILTERS); }} className="rounded-xl border border-slate-300 px-4 py-2.5 text-sm font-black text-slate-700">Clear</button></div>
        </form>
      </section>

      {!canManage ? <ReadOnlyBoundary description="You can review responsibility assignments in your effective scope. Creating, changing or revoking assignments requires access-management authority for the target scope." /> : null}
      {highlightedBindingId && !loading && !selectedExists ? <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">The requested assignment was not returned by the current filters. Clear filters or search the assignment ID.</div> : null}
      {!loading && !items.length ? hasAppliedFilters ? <GuidedEmptyState title="No assignments match these filters" description="No responsibility assignment in your current Tenant scope matches all selected filters." nextStep="Clear filters, then search by Person, Department, Group or responsibility name." primaryAction={<button type="button" onClick={() => { setDraft(EMPTY_FILTERS); setApplied(EMPTY_FILTERS); }} className="rounded-xl bg-slate-950 px-4 py-2.5 text-sm font-black text-white">Clear filters</button>} /> : <GuidedEmptyState title="No responsibilities assigned yet" description="Responsibilities connect a Person, Department, Group or Service Account to an approved role and business scope." nextStep={canManage ? 'Assign the first responsibility, then use Effective Access to confirm what the recipient can actually do.' : 'An access administrator must create the first assignment before this directory contains records.'} primaryAction={canManage && onAssign ? <button type="button" onClick={onAssign} className="rounded-xl bg-blue-700 px-4 py-2.5 text-sm font-black text-white">Assign first responsibility</button> : undefined} /> : null}
      <div className="space-y-3">
        {items.map((item) => (
          <article key={item.bindingId} className={`rounded-3xl border bg-white p-5 shadow-sm ${item.bindingId === highlightedBindingId ? 'border-blue-500 ring-2 ring-blue-100' : 'border-slate-200'}`}>
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <p className="font-black text-slate-950">{item.roleName}</p>
                <p className="mt-1 text-sm text-slate-700">{item.principalName}</p>
                <p className="mt-1 text-xs text-slate-500">Applies to {item.scopeName} · {item.expiresAt ? `until ${new Date(item.expiresAt).toLocaleDateString()}` : 'no planned expiry'}</p>
              </div>
              <div className="flex flex-wrap gap-2"><HumanStatus value={item.lifecycleStatus} /><RiskPill value={item.riskLevel} /></div>
            </div>
            <div className="mt-4 flex flex-wrap gap-2">
              {item.principalType === 'USER' ? <ContextLink href={`/admin/tenants/${encodeURIComponent(tenantId)}/people?userId=${encodeURIComponent(item.principalId)}`}>Open person</ContextLink> : null}
              {item.principalType === 'DEPARTMENT' ? <ContextLink href={`/admin/tenants/${encodeURIComponent(tenantId)}/organization?type=DEPARTMENT&id=${encodeURIComponent(item.principalId)}`}>Open Department</ContextLink> : null}
              {item.principalType === 'GROUP' ? <ContextLink href={`/admin/tenants/${encodeURIComponent(tenantId)}/organization?type=GROUP&id=${encodeURIComponent(item.principalId)}`}>Open Group</ContextLink> : null}
              {item.scopeType === 'DEPARTMENT' || item.scopeType === 'DEPARTMENT_SUBTREE' ? <ContextLink href={`/admin/tenants/${encodeURIComponent(tenantId)}/organization?type=DEPARTMENT&id=${encodeURIComponent(item.scopeId)}`}>{item.scopeType === 'DEPARTMENT_SUBTREE' ? 'Open Department subtree' : 'Open Department scope'}</ContextLink> : null}
              {item.scopeType === 'GROUP' ? <ContextLink href={`/admin/tenants/${encodeURIComponent(tenantId)}/organization?type=GROUP&id=${encodeURIComponent(item.scopeId)}`}>Open scope Group</ContextLink> : null}
              {canManage && item.status === 'ACTIVE' ? <button type="button" onClick={() => { void openRevoke(item); }} className="rounded-lg border border-rose-300 px-3 py-1.5 text-xs font-black text-rose-800">Review and revoke</button> : null}
            </div>
            <TechnicalDetails summary="Technical evidence" className="mt-4"><dl className="grid gap-1 sm:grid-cols-2"><div><dt className="font-bold">Assignment ID</dt><dd className="break-all">{item.bindingId}</dd></div><div><dt className="font-bold">Role code</dt><dd>{item.roleCode}</dd></div><div><dt className="font-bold">Principal</dt><dd>{item.principalType}:{item.principalId}</dd></div><div><dt className="font-bold">Scope</dt><dd>{item.scopeType}:{item.scopeId}</dd></div></dl></TechnicalDetails>
          </article>
        ))}
      </div>
      {loading ? <div className="rounded-2xl bg-slate-50 p-5 text-sm text-slate-500">Loading access assignments…</div> : null}
      {hasMore ? <button type="button" disabled={loading} onClick={() => { void load(nextCursor, true); }} className="w-full rounded-xl border border-slate-300 bg-white px-4 py-3 text-sm font-black text-slate-800">Load more</button> : null}

      {revoke ? (
        <WorkspaceModal open title="Review access revocation" onClose={() => { setRevoke(null); setPreview(null); }}>
          <div className="space-y-4">
            <div className="rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-900"><b>{revoke.principalName}</b> will lose the <b>{revoke.roleName}</b> assignment for <b>{revoke.scopeName}</b>.</div>
            {preview ? <div className="grid gap-3 sm:grid-cols-2"><Impact title="Capabilities removed" values={preview.permissionsLost} empty="No capability is expected to be removed." /><Impact title="Capabilities retained" values={preview.permissionsRetained} empty="No capability is retained through another source." /></div> : <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">For Department, Group and Service Account assignments, review the affected members or consumers after revocation. The current canonical preview is user-specific.</div>}
            <AuditReasonSelector idPrefix="assignment-revoke" value={reason} onChange={setReason} tier="HIGH_RISK" />
            <button type="button" disabled={busy || !isAuditReasonValid(reason, 'HIGH_RISK')} onClick={() => { void confirmRevoke(); }} className="w-full rounded-xl bg-rose-700 px-4 py-3 text-sm font-black text-white disabled:bg-slate-300">Revoke assignment</button>
          </div>
        </WorkspaceModal>
      ) : null}
    </div>
  );
}

function Impact({ title, values, empty }: Readonly<{ title: string; values: string[]; empty: string }>) {
  return <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4"><p className="text-sm font-black text-slate-900">{title}</p><ul className="mt-2 space-y-1 text-xs text-slate-600">{values.map((value) => <li key={value}>• {value}</li>)}{!values.length ? <li>{empty}</li> : null}</ul></div>;
}
function RiskPill({ value }: Readonly<{ value: string }>) { const critical = value === 'CRITICAL'; const high = value === 'HIGH'; return <span className={`rounded-full border px-2.5 py-1 text-xs font-black ${critical ? 'border-rose-200 bg-rose-50 text-rose-800' : high ? 'border-amber-200 bg-amber-50 text-amber-900' : 'border-slate-200 bg-slate-100 text-slate-700'}`}>{value || 'STANDARD'} risk</span>; }
