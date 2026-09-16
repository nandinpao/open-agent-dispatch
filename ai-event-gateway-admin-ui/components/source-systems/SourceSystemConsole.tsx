'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import { useDialogAccessibility } from '@/hooks/useDialogAccessibility';
import { useAuth } from '@/components/auth/AuthProvider';
import { useUiEntitlements } from '@/lib/navigation/useUiEntitlements';
import { actionAllowed, actionAllowedForScope, featureAllowed } from '@/lib/navigation/uiEntitlements';
import { EmptyState } from '@/components/common/EmptyState';
import { LoadingBox } from '@/components/common/LoadingBox';
import { RefreshButton } from '@/components/common/RefreshButton';
import { StatusBadge } from '@/components/common/StatusBadge';
import { Button } from '@/components/ui/Button';
import { BeginnerGuideButton, FieldAssist, OwnershipAccessCard } from '@/components/resource-scope/EnterpriseAccessUi';
import { SearchSelectField } from '@/components/access-management/shared/beginnerUi';
import { SourceRegistrationManager } from '@/components/source-systems/SourceRegistrationManager';
import { SourceSystemOnboardingDialog } from '@/components/source-systems/SourceSystemOnboardingDialog';
import { IssueTrackingContextCard } from '@/components/integrations/IssueTrackingContextCard';
import { sourceSystemsAdminApi } from '@/lib/api/domains/sourceSystemsAdminApi';
import { accessManagementApi } from '@/lib/api/accessManagementApi';
import type { Department, Group } from '@/lib/iam/types';
import type { CoreDispatchFlowView, CoreSourceSystem, CoreSourceSystemCommand } from '@/lib/types/core';
import { formatDateTime } from '@/lib/utils/format';

interface SourceSystemRow extends CoreSourceSystem {
  flowCount: number;
  activeFlowCount: number;
  eventTypes: string[];
  taskTypes: string[];
  lastFlowUpdatedAt?: string;
}

type SourceEditorState = CoreSourceSystemCommand;

const inputClass = 'mt-1 w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm outline-none transition focus:border-purple-400 focus:ring-2 focus:ring-purple-100';
const labelClass = 'text-sm font-black text-slate-800';

function normalizeCode(value: string): string {
  return value.trim().toUpperCase().replace(/[^A-Z0-9_.-]/g, '_').replace(/^_+|_+$/g, '');
}

function isActiveFlow(status?: string | null): boolean {
  const normalized = String(status ?? '').trim().toUpperCase();
  return normalized === 'ACTIVE' || normalized === 'ENABLED';
}

function unique(values: Array<string | undefined | null>): string[] {
  return Array.from(new Set(values.map((value) => String(value ?? '').trim()).filter(Boolean))).sort();
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

function sourceLabel(source: CoreSourceSystem): string {
  return source.displayName && source.displayName !== source.sourceSystemId
    ? `${source.displayName} (${source.sourceSystemId})`
    : source.sourceSystemId;
}

function mergeSourceRows(sources: CoreSourceSystem[], flows: CoreDispatchFlowView[]): SourceSystemRow[] {
  const byCode = new Map<string, SourceSystemRow>();
  for (const source of sources) {
    byCode.set(source.sourceSystemId, {
      ...source,
      flowCount: 0,
      activeFlowCount: 0,
      eventTypes: [],
      taskTypes: [],
    });
  }
  for (const flow of flows) {
    const code = String(flow.sourceSystem ?? '').trim();
    const current = code ? byCode.get(code) : undefined;
    if (!current) continue;
    byCode.set(code, {
      ...current,
      flowCount: current.flowCount + 1,
      activeFlowCount: current.activeFlowCount + (isActiveFlow(flow.status) ? 1 : 0),
      eventTypes: unique([...(current.eventTypes ?? []), ...(flow.rules ?? []).map((rule) => rule.eventType)]),
      taskTypes: unique([...(current.taskTypes ?? []), ...(flow.rules ?? []).map((rule) => rule.serviceCode)]),
      lastFlowUpdatedAt: flow.updatedAt ?? current.lastFlowUpdatedAt,
    });
  }
  return Array.from(byCode.values()).sort((left, right) => left.sourceSystemId.localeCompare(right.sourceSystemId));
}

function createFlowHref(sourceSystemId?: string): string {
  const query = new URLSearchParams({ create: '1' });
  if (sourceSystemId) query.set('sourceSystem', sourceSystemId);
  return `/dispatch-flows?${query.toString()}`;
}

function SourceSystemEditor({
  open,
  editor,
  editing,
  busy,
  error,
  onChange,
  onClose,
  onSave,
  departments,
  groups,
  allowTenantWideOwnership,
}: Readonly<{
  open: boolean;
  editor: SourceEditorState;
  editing: boolean;
  busy: boolean;
  error?: string | null;
  onChange: (patch: Partial<SourceEditorState>) => void;
  onClose: () => void;
  onSave: () => void;
  departments: Department[];
  groups: Group[];
  allowTenantWideOwnership: boolean;
}>) {
  const dialogRef = useDialogAccessibility(open, onClose);
  if (!open) return null;
  return (
    <div ref={dialogRef} tabIndex={-1} className="fixed inset-0 z-[80] flex items-start justify-center overflow-y-auto bg-slate-950/60 p-4 outline-none sm:p-8" role="dialog" aria-modal="true" aria-label="Source System editor">
      <div className="w-full max-w-2xl rounded-3xl bg-white shadow-2xl">
        <div className="flex items-start justify-between gap-4 rounded-t-3xl border-b border-slate-200 bg-white px-6 py-5">
          <div>
            <div className="text-xs font-black uppercase tracking-wide text-blue-700">Source System Master</div>
            <h2 className="mt-1 text-xl font-black text-slate-950">{editing ? 'Edit Source System' : 'Add Source System'}</h2>
            <p className="mt-1 text-sm leading-6 text-slate-600">Define the source identity and description used by Dispatch Flows and operational diagnostics.</p>
          </div>
          <button type="button" onClick={onClose} className="rounded-xl border border-slate-200 px-3 py-2 text-sm font-black text-slate-600 hover:bg-slate-50" aria-label="Close">×</button>
        </div>
        <div className="space-y-4 p-6">
          {error ? <div className="rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm font-bold text-rose-900">{error}</div> : null}
          <label className={labelClass}>Source System Code<span className="text-rose-600"> *</span>
            <input className={`${inputClass} ${editing ? 'cursor-not-allowed bg-slate-100 text-slate-500' : ''}`} value={editor.sourceSystemId} onChange={(event) => onChange({ sourceSystemId: normalizeCode(event.target.value) })} placeholder="For example: SRC_E2E_7F28 or FACTORY_IOT_01" readOnly={editing} aria-readonly={editing} />
            {editing ? <span className="mt-1 block text-xs font-medium leading-5 text-slate-500">Source System Code is the stable identity used by Source Flows and historical evidence. It cannot be changed after creation.</span> : null}
          </label>
          <label className={labelClass}>Display Name<span className="text-rose-600"> *</span>
            <input className={inputClass} value={editor.displayName} onChange={(event) => onChange({ displayName: event.target.value })} placeholder="For example: Factory IoT Events" />
          </label>
          <label className={labelClass}>Status
            <select className={inputClass} value={editor.status ?? 'ACTIVE'} onChange={(event) => onChange({ status: event.target.value })}>
              <option value="ACTIVE">Enabled</option>
              <option value="DISABLED">Disabled</option>
            </select>
          </label>
          <div className="rounded-2xl border border-violet-200 bg-violet-50 p-4">
            <div className="text-sm font-black text-violet-950">Data ownership</div>
            <p className="mt-1 text-xs leading-5 text-violet-800">
              Optional. Leave both fields empty for Tenant-wide data. Department / Group ownership is enforced by backend list and object authorization; it is not a UI-only filter. Dispatch Flows and Agent Pools created under this Source System inherit the same owner.
            </p>
            <div className="mt-3 grid gap-3 sm:grid-cols-2">
              <label className={labelClass}>
                <span className="flex flex-wrap items-center justify-between gap-2">Owner Department <FieldAssist help="Ownership controls the default data scope inherited by Events and Dispatch resources. It does not grant access by itself; a Responsibility is still required." href="/access-management?workspace=organization" linkLabel="Manage organization" /></span>
                <div className="mt-1">
                  <SearchSelectField
                    id="source-owner-department"
                    value={editor.ownerDepartmentId ?? ''}
                    onChange={(value) => onChange({ ownerDepartmentId: value || undefined })}
                    placeholder={allowTenantWideOwnership ? 'Tenant-wide (no Department owner)' : 'Select an authorized Department'}
                    options={[
                      ...(editor.ownerDepartmentId && !departments.some((item) => item.departmentId === editor.ownerDepartmentId) ? [{ value: editor.ownerDepartmentId, label: 'Current Department', description: 'Retained; re-scope is not available with your current access.' }] : []),
                      ...departments.filter((item) => item.status !== 'DELETED').map((item) => ({ value: item.departmentId, label: item.name, description: item.code })),
                    ]}
                  />
                </div>
              </label>
              <label className={labelClass}>
                <span className="flex flex-wrap items-center justify-between gap-2">Owner Group <FieldAssist help="Use a Group when this Source System is governed by a cross-functional team. Group membership alone does not grant Source access." href="/access-management?workspace=organization" linkLabel="Manage groups" /></span>
                <div className="mt-1">
                  <SearchSelectField
                    id="source-owner-group"
                    value={editor.ownerGroupId ?? ''}
                    onChange={(value) => onChange({ ownerGroupId: value || undefined })}
                    placeholder="No Group owner"
                    options={[
                      ...(editor.ownerGroupId && !groups.some((item) => item.groupId === editor.ownerGroupId) ? [{ value: editor.ownerGroupId, label: 'Current Group', description: 'Retained; re-scope is not available with your current access.' }] : []),
                      ...groups.filter((item) => item.status !== 'DELETED').map((item) => ({ value: item.groupId, label: item.name, description: item.code })),
                    ]}
                  />
                </div>
              </label>
            </div>
          </div>
          <label className={labelClass}>Description
            <textarea className={`${inputClass} min-h-28`} value={editor.description ?? ''} onChange={(event) => onChange({ description: event.target.value })} placeholder="Describe the business system and the events it sends to OpenDispatch." />
          </label>
          <div className="rounded-2xl border border-blue-100 bg-blue-50 p-4 text-sm leading-6 text-blue-900">
            After saving the Source System, create a Source Flow in Dispatch, select a Default Agent Pool, and add approved Agents to that Pool.
          </div>
        </div>
        <div className="flex justify-end gap-2 rounded-b-3xl border-t border-slate-200 bg-slate-50 px-6 py-4">
          <Button tone="secondary" onClick={onClose} disabled={busy}>Cancel</Button>
          <Button onClick={onSave} disabled={busy}>{busy ? 'Saving…' : 'Save Source System'}</Button>
        </div>
      </div>
    </div>
  );
}

export function SourceSystemConsole() {
  const { activeTenantId: selectedTenantId } = useAuth();
  const entitlements = useUiEntitlements();
  const canCreateSource = actionAllowed(entitlements.value, 'source-systems.create');
  const canUpdateSource = actionAllowed(entitlements.value, 'source-systems.update');
  const canCreateFlow = actionAllowed(entitlements.value, 'dispatch.create');
  const canViewDispatch = featureAllowed(entitlements.value, 'dispatch');
  const [sources, setSources] = useState<CoreSourceSystem[]>([]);
  const [flows, setFlows] = useState<CoreDispatchFlowView[]>([]);
  const [departments, setDepartments] = useState<Department[]>([]);
  const [groups, setGroups] = useState<Group[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [editorError, setEditorError] = useState<string | null>(null);
  const [lastUpdatedAt, setLastUpdatedAt] = useState<string | null>(null);
  const [editorOpen, setEditorOpen] = useState(false);
  const [onboardingOpen, setOnboardingOpen] = useState(false);
  const [editingSourceSystemId, setEditingSourceSystemId] = useState<string | null>(null);
  const [editor, setEditor] = useState<SourceEditorState>({ sourceSystemId: '', displayName: '', description: '', status: 'ACTIVE' });

  const rows = useMemo(() => mergeSourceRows(sources, flows), [sources, flows]);
  const ownershipActionId = editingSourceSystemId ? 'source-systems.update' : 'source-systems.create';
  const allowTenantWideOwnership = actionAllowedForScope(entitlements.value, ownershipActionId, 'TENANT', selectedTenantId);
  const manageableDepartments = useMemo(() => departments.filter((item) => actionAllowedForScope(
    entitlements.value, ownershipActionId, 'DEPARTMENT', item.departmentId, departmentAncestorIds(item.departmentId, departments),
  )), [departments, entitlements.value, ownershipActionId]);
  const manageableGroups = useMemo(() => groups.filter((item) => actionAllowedForScope(
    entitlements.value, ownershipActionId, 'GROUP', item.groupId,
  )), [groups, entitlements.value, ownershipActionId]);

  async function reload() {
    const tenantId = selectedTenantId.trim();
    if (!tenantId) {
      setSources([]);
      setFlows([]);
      setError('No active workspace is available. Sign in again or ask an administrator to review your Tenant membership.');
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const [sourceRows, flowRows, departmentPage, groupPage] = await Promise.all([
        sourceSystemsAdminApi.getSourceSystems(tenantId),
        canViewDispatch ? sourceSystemsAdminApi.getDispatchFlows(tenantId) : Promise.resolve([] as CoreDispatchFlowView[]),
        accessManagementApi.departments(tenantId, 0, 100, '', 'ACTIVE'),
        accessManagementApi.groups(tenantId, 0, 100, '', '', 'ACTIVE'),
      ]);
      setSources(sourceRows);
      setFlows(flowRows);
      setDepartments(departmentPage.items);
      setGroups(groupPage.items);
      setLastUpdatedAt(new Date().toISOString());
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Source Systems.');
    } finally {
      setLoading(false);
    }
  }

  function openCreate() {
    setOnboardingOpen(true);
  }

  function openEdit(source: CoreSourceSystem) {
    setEditingSourceSystemId(source.sourceSystemId);
    setEditor({
      sourceSystemId: source.sourceSystemId,
      displayName: source.displayName,
      description: source.description ?? '',
      status: source.status ?? 'ACTIVE',
      ownerDepartmentId: source.ownerDepartmentId,
      ownerGroupId: source.ownerGroupId,
    });
    setEditorError(null);
    setEditorOpen(true);
  }

  async function saveEditor() {
    const tenantId = selectedTenantId.trim();
    if (!tenantId) {
      setEditorError('No active workspace is available. Sign in again or ask an administrator to review your Tenant membership.');
      return;
    }
    if (!editor.sourceSystemId.trim() || !editor.displayName.trim()) {
      setEditorError('Source System Code and Display Name are required.');
      return;
    }
    if (!allowTenantWideOwnership && !editor.ownerDepartmentId && !editor.ownerGroupId) {
      setEditorError('Choose an authorized Department or Group. Tenant-wide ownership requires Tenant-scoped Source System authority.');
      return;
    }
    setSaving(true);
    setEditorError(null);
    try {
      if (editingSourceSystemId) {
        await sourceSystemsAdminApi.updateSourceSystem(tenantId, editingSourceSystemId, { ...editor, sourceSystemId: editingSourceSystemId });
      } else {
        const duplicate = sources.some((source) => source.sourceSystemId.toUpperCase() === editor.sourceSystemId.toUpperCase());
        if (duplicate) throw new Error(`Source System Code ${editor.sourceSystemId} already exists.`);
        await sourceSystemsAdminApi.createSourceSystem(tenantId, editor);
      }
      setEditorOpen(false);
      setEditingSourceSystemId(null);
      await reload();
    } catch (caught) {
      setEditorError(caught instanceof Error ? caught.message : 'Failed to save the Source System.');
    } finally {
      setSaving(false);
    }
  }

  useEffect(() => { void reload(); }, [selectedTenantId, canViewDispatch]); // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <main className="space-y-5">
      <SourceSystemOnboardingDialog
        open={onboardingOpen}
        tenantId={selectedTenantId}
        existingSources={sources}
        onClose={() => setOnboardingOpen(false)}
        onCreated={() => { void reload(); }}
      />
      <SourceSystemEditor
        open={editorOpen}
        editor={editor}
        editing={Boolean(editingSourceSystemId)}
        busy={saving}
        error={editorError}
        onChange={(patch) => setEditor((current) => ({ ...current, ...patch }))}
        onClose={() => { setEditorOpen(false); setEditingSourceSystemId(null); }}
        onSave={() => void saveEditor()}
        departments={manageableDepartments}
        groups={manageableGroups}
        allowTenantWideOwnership={allowTenantWideOwnership}
      />

      <section className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <div className="text-xs font-black uppercase tracking-wide text-blue-700">Source Systems</div>
            <h1 className="mt-1 text-2xl font-black text-slate-950">Source Systems</h1>
            <p className="mt-2 max-w-4xl text-sm leading-6 text-slate-600">
              Create and maintain the systems that send events to OpenDispatch. A Source System can be Tenant-owned or assigned to a Department / Group data scope; child Dispatch Flows and Agent Pools inherit that owner.
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <BeginnerGuideButton title="Source Systems in three steps" description="Most administrators can finish setup from this page without navigating through multiple consoles." steps={[
              { title: 'Add the business system', description: 'Create a Source System and choose its business owner using the searchable Department or Group selectors.' },
              { title: 'Create its Dispatch Flow', description: 'Use the nearby Create Source Flow action. OpenDispatch carries the Source ownership into downstream Event and Dispatch scope.' },
              { title: 'Test from Dispatch', description: 'Use the linked Dispatch workspace to test routing. Advanced governance remains available nearby instead of becoming another required setup page.' },
            ]} />
            <RefreshButton refreshing={loading} lastUpdatedAt={lastUpdatedAt} onRefresh={() => void reload()} />
            {canCreateSource ? <Button onClick={openCreate}>Add Source System</Button> : null}
          </div>
        </div>
        <div className={`mt-5 grid gap-3 ${canViewDispatch ? 'sm:grid-cols-3' : 'sm:grid-cols-2'}`}>
          <div className="rounded-2xl bg-slate-50 p-4"><div className="text-xs font-black text-slate-500">Total Sources</div><div className="mt-1 text-2xl font-black">{rows.length}</div></div>
          <div className="rounded-2xl bg-emerald-50 p-4"><div className="text-xs font-black text-emerald-700">Active sources</div><div className="mt-1 text-2xl font-black text-emerald-950">{rows.filter((row) => String(row.status).toUpperCase() === 'ACTIVE').length}</div></div>
          {canViewDispatch ? <div className="rounded-2xl bg-blue-50 p-4"><div className="text-xs font-black text-blue-700">Sources with flows</div><div className="mt-1 text-2xl font-black text-blue-950">{rows.filter((row) => row.flowCount > 0).length}</div></div> : null}
        </div>
      </section>

      {error ? <div className="rounded-2xl border border-rose-200 bg-rose-50 p-4 text-sm font-bold text-rose-900">{error}</div> : null}
      {!selectedTenantId.trim() ? <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4 text-sm font-bold text-amber-900">No active workspace is available. Source Systems use the Tenant resolved by your authenticated session.</div> : null}

      <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <h2 className="text-lg font-black text-slate-950">Source Systems</h2>
            <p className="mt-1 text-sm leading-6 text-slate-600">Configure Agent Pools, rule overrides, and Pool Member Agents in Dispatch.</p>
          </div>
          {canCreateSource ? <Button size="sm" onClick={openCreate}>Add Source System</Button> : null}
        </div>

        {loading ? <div className="mt-5"><LoadingBox label="Loading Source Systems..." /></div> : null}
        {!loading && !rows.length ? (
          <div className="mt-5">
            <EmptyState title="No Source Systems" description="A Source System is the governed business system that sends events to OpenDispatch." nextAction={canCreateSource ? "Create the first Source System, choose its Tenant / Department / Group data ownership, then continue to Dispatch for Source Flow and Agent Pool setup." : "A Source System administrator must create the first source in a scope you can access."} action={canCreateSource ? <Button onClick={openCreate}>Add first Source System</Button> : undefined} />
          </div>
        ) : null}

        <div className="mt-5 grid gap-3">
          {rows.map((source) => (
            <article key={source.sourceSystemId} className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
              <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <h3 className="break-all text-base font-black text-slate-950">{sourceLabel(source)}</h3>
                    <StatusBadge status={source.status ?? 'ACTIVE'} />
                  </div>
                  <p className="mt-2 text-sm leading-6 text-slate-600">{source.description || 'No description provided.'}</p>
                  <div className="mt-3 flex flex-wrap gap-2 text-xs font-bold text-slate-600">
                    {canViewDispatch ? <span className="rounded-full bg-white px-3 py-1">Flows: {source.flowCount}</span> : null}
                    {canViewDispatch ? <span className="rounded-full bg-white px-3 py-1">Active Flows: {source.activeFlowCount}</span> : null}
                    <span className="rounded-full bg-violet-50 px-3 py-1 text-violet-800">Data scope: {source.ownerDepartmentId || source.ownerGroupId ? 'Organization-scoped' : 'Tenant-wide'}</span>
                    {source.ownerDepartmentId ? <span className="rounded-full bg-white px-3 py-1">Department: {departments.find((item) => item.departmentId === source.ownerDepartmentId)?.name ?? source.ownerDepartmentId}</span> : null}
                    {source.ownerGroupId ? <span className="rounded-full bg-white px-3 py-1">Group: {groups.find((item) => item.groupId === source.ownerGroupId)?.name ?? source.ownerGroupId}</span> : null}
                    {canViewDispatch ? source.eventTypes.slice(0, 4).map((eventType) => <span key={eventType} className="rounded-full bg-blue-50 px-3 py-1 text-blue-800">{eventType}</span>) : null}
                    {canViewDispatch && source.eventTypes.length > 4 ? <span className="rounded-full bg-white px-3 py-1">+{source.eventTypes.length - 4}</span> : null}
                    {source.updatedAt ? <span className="rounded-full bg-white px-3 py-1">{formatDateTime(source.updatedAt)}</span> : null}
                    {canViewDispatch && source.lastFlowUpdatedAt ? <span className="rounded-full bg-white px-3 py-1">Flow updated: {formatDateTime(source.lastFlowUpdatedAt)}</span> : null}
                  </div>
                  <div className="mt-3">
                    <OwnershipAccessCard
                      compact
                      resourceType="SOURCE_SYSTEM"
                      resourceId={source.sourceSystemId}
                      permissionCode="admin.source-system.read"
                      purpose="Explain Source System access from the Source Systems workspace."
                      primaryOwner={source.ownerDepartmentId ? `Department · ${departments.find((item) => item.departmentId === source.ownerDepartmentId)?.name ?? source.ownerDepartmentId}` : source.ownerGroupId ? `Group · ${groups.find((item) => item.groupId === source.ownerGroupId)?.name ?? source.ownerGroupId}` : 'Tenant-wide'}
                      provenance="Events and Dispatch resources inherit this Source System's canonical owner unless a governed downstream rule narrows or adds operational collaboration."
                      managementHref="/access-management?workspace=access"
                      managementLabel="Review access"
                    />
                  </div>
                </div>
                <div className="flex shrink-0 flex-wrap gap-2">
                  {canUpdateSource ? <button type="button" onClick={() => openEdit(source)} className="rounded-xl border border-slate-200 bg-white px-4 py-2 text-sm font-black text-slate-700 hover:bg-slate-100">Edit Source</button> : null}
                  {canViewDispatch ? <Link href={`/dispatch-flows?sourceSystem=${encodeURIComponent(source.sourceSystemId)}`} className="rounded-xl border border-slate-200 bg-white px-4 py-2 text-sm font-black text-slate-700 hover:bg-slate-100">View Flows</Link> : null}
                  {canCreateFlow ? <Link href={createFlowHref(source.sourceSystemId)} className="rounded-xl bg-blue-700 px-4 py-2 text-sm font-black text-white hover:bg-blue-800">Create Source Flow</Link> : null}
                </div>
              </div>
              <div className="mt-4">
                <IssueTrackingContextCard
                  compact
                  title="Issue Tracking"
                  contexts={[{ sourceSystemId: source.sourceSystemId, taskType: null }]}
                />
              </div>
              <SourceRegistrationManager tenantId={selectedTenantId} source={source} editable={canUpdateSource} />
            </article>
          ))}
        </div>
      </section>
    </main>
  );
}
