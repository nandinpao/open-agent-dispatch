'use client';

import { FormEvent, useCallback, useEffect, useMemo, useState } from 'react';
import { AdminPageHeader } from '@/components/layout/AdminPageHeader';
import { useAuth } from '@/components/auth/AuthProvider';
import { platformGovernanceApi } from '@/lib/api/platformGovernanceApi';
import { PermissionCatalogLifecyclePanel } from '@/components/phase7e/PermissionCatalogLifecyclePanel';
import type {
  PermissionCatalogAlias,
  PermissionCatalogDefinition,
  PermissionCatalogDiff,
  PermissionCatalogPublication,
  PermissionCatalogRevision,
  PermissionCatalogValidation,
} from '@/lib/iam/types';

type Tab = 'overview' | 'definitions' | 'aliases' | 'diff' | 'validation' | 'evidence';
const input = 'w-full rounded-xl border border-slate-300 bg-white px-3 py-2.5 text-sm';
const scopes = ['INSTANCE', 'TENANT', 'DEPARTMENT', 'GROUP'] as const;

function dateTime(value?: string | null) {
  return value ? new Date(value).toLocaleString() : '—';
}
function localInput(value?: string | null) {
  if (!value) return '';
  const date = new Date(value);
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 16);
}
function lifecycleOptions(definition: PermissionCatalogDefinition | null) {
  if (!definition) return ['DRAFT', 'ACTIVE'];
  if (definition.lifecycle === 'DRAFT') return ['DRAFT', 'ACTIVE'];
  if (definition.lifecycle === 'ACTIVE') return ['ACTIVE', 'DEPRECATED'];
  if (definition.lifecycle === 'DEPRECATED') return ['DEPRECATED', 'RETIRED'];
  return ['RETIRED'];
}
function shortHash(value?: string | null) {
  if (!value) return 'Not published';
  return value.length > 30 ? `${value.slice(0, 18)}…${value.slice(-10)}` : value;
}
function nextDraftCode(revisions: PermissionCatalogRevision[]) {
  const next = Math.max(0, ...revisions.map((revision) => revision.revisionNumber)) + 1;
  return `CATALOG-${next}`;
}

export function PermissionCatalogAdministrationConsole() {
  const { hasPermission } = useAuth();
  const canManage = hasPermission('permission.catalog.manage');
  const canPublish = hasPermission('permission.catalog.publish');
  const [revisions, setRevisions] = useState<PermissionCatalogRevision[]>([]);
  const [active, setActive] = useState<PermissionCatalogRevision | null>(null);
  const [selectedId, setSelectedId] = useState('');
  const [definitions, setDefinitions] = useState<PermissionCatalogDefinition[]>([]);
  const [aliases, setAliases] = useState<PermissionCatalogAlias[]>([]);
  const [diff, setDiff] = useState<PermissionCatalogDiff | null>(null);
  const [validation, setValidation] = useState<PermissionCatalogValidation | null>(null);
  const [publications, setPublications] = useState<PermissionCatalogPublication[]>([]);
  const [tab, setTab] = useState<Tab>('overview');
  const [query, setQuery] = useState('');
  const [selectedPermission, setSelectedPermission] = useState('');
  const [selectedAlias, setSelectedAlias] = useState('');
  const [auditReason, setAuditReason] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [publishConfirmed, setPublishConfirmed] = useState(false);

  const selected = useMemo(
    () => revisions.find((revision) => revision.revisionId === selectedId) ?? null,
    [revisions, selectedId],
  );
  const draft = useMemo(() => revisions.find((revision) => revision.status === 'DRAFT') ?? null, [revisions]);
  const selectedDefinition = useMemo(
    () => definitions.find((definition) => definition.permissionCode === selectedPermission) ?? null,
    [definitions, selectedPermission],
  );
  const selectedAliasValue = useMemo(
    () => aliases.find((alias) => alias.aliasCode === selectedAlias) ?? null,
    [aliases, selectedAlias],
  );
  const editable = selected?.status === 'DRAFT';
  const filteredDefinitions = useMemo(() => {
    const value = query.trim().toLowerCase();
    if (!value) return definitions;
    return definitions.filter((definition) =>
      [definition.permissionCode, definition.ownerModule, definition.resourceType, definition.actionCode, definition.description]
        .join(' ')
        .toLowerCase()
        .includes(value),
    );
  }, [definitions, query]);

  const loadRoot = useCallback(async () => {
    setError('');
    try {
      const [revisionList, activeRevision, publicationList] = await Promise.all([
        platformGovernanceApi.permissionCatalogRevisions(),
        platformGovernanceApi.activePermissionCatalogRevision(),
        platformGovernanceApi.permissionCatalogPublications(),
      ]);
      setRevisions(revisionList);
      setActive(activeRevision);
      setPublications(publicationList);
      setSelectedId((current) => {
        if (current && revisionList.some((revision) => revision.revisionId === current)) return current;
        return revisionList.find((revision) => revision.status === 'DRAFT')?.revisionId ?? activeRevision.revisionId;
      });
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Unable to load Permission Catalog governance.');
    }
  }, []);

  const loadSelected = useCallback(async () => {
    if (!selectedId) return;
    setError('');
    try {
      const selectedRevision = revisions.find((revision) => revision.revisionId === selectedId);
      const [entryList, aliasList, catalogDiff, report] = await Promise.all([
        platformGovernanceApi.permissionCatalogDefinitions(selectedId),
        platformGovernanceApi.permissionCatalogAliases(selectedId),
        platformGovernanceApi.permissionCatalogDiff(selectedId),
        selectedRevision?.status === 'DRAFT' ? platformGovernanceApi.permissionCatalogValidation(selectedId) : Promise.resolve(null),
      ]);
      setDefinitions(entryList);
      setAliases(aliasList);
      setDiff(catalogDiff);
      setValidation(report);
      setSelectedPermission((current) => (current && entryList.some((entry) => entry.permissionCode === current) ? current : ''));
      setSelectedAlias((current) => (current && aliasList.some((entry) => entry.aliasCode === current) ? current : ''));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Unable to load the selected Catalog revision.');
    }
  }, [revisions, selectedId]);

  useEffect(() => { void loadRoot(); }, [loadRoot]);
  useEffect(() => { void loadSelected(); }, [loadSelected]);

  function requireReason() {
    if (auditReason.trim().length < 12) {
      setError('Enter an audit reason of at least 12 characters before changing the Catalog.');
      return false;
    }
    return true;
  }

  async function createDraft(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!requireReason()) return;
    const data = new FormData(event.currentTarget);
    setBusy(true);
    try {
      const created = await platformGovernanceApi.createPermissionCatalogDraft(
        { revisionCode: String(data.get('revisionCode')), description: String(data.get('description') ?? '') },
        auditReason,
      );
      await loadRoot();
      setSelectedId(created.revisionId);
      setTab('definitions');
      event.currentTarget.reset();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Draft creation failed.');
    } finally { setBusy(false); }
  }

  async function saveDefinition(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selected || !editable || !requireReason()) return;
    const data = new FormData(event.currentTarget);
    const permissionCode = String(data.get('permissionCode'));
    const allowedScopes = scopes.filter((scope) => data.get(`scope-${scope}`) === 'on');
    const body = {
      permissionCode,
      ownerModule: String(data.get('ownerModule')),
      resourceType: String(data.get('resourceType')),
      actionCode: String(data.get('actionCode')),
      description: String(data.get('description')),
      riskLevel: String(data.get('riskLevel')),
      riskLane: String(data.get('riskLane')),
      lifecycle: String(data.get('lifecycle')),
      allowedScopes,
      systemManaged: data.get('systemManaged') === 'on',
      replacementPermissionCode: String(data.get('replacementPermissionCode') ?? '') || null,
      deprecatedAt: data.get('deprecatedAt') ? new Date(String(data.get('deprecatedAt'))).toISOString() : null,
      retiredAt: data.get('retiredAt') ? new Date(String(data.get('retiredAt'))).toISOString() : null,
    };
    if (allowedScopes.length === 0) { setError('Select at least one allowed scope.'); return; }
    setBusy(true);
    try {
      if (selectedDefinition) {
        await platformGovernanceApi.updatePermissionCatalogDefinition(selected.revisionId, selectedDefinition.permissionCode, body, selectedDefinition.version, auditReason);
      } else {
        await platformGovernanceApi.createPermissionCatalogDefinition(selected.revisionId, body, auditReason);
      }
      setSelectedPermission(permissionCode);
      await loadSelected();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Permission definition save failed.');
    } finally { setBusy(false); }
  }

  async function removeDefinition() {
    if (!selected || !selectedDefinition || !editable || !requireReason()) return;
    setBusy(true);
    try {
      await platformGovernanceApi.deletePermissionCatalogDefinition(selected.revisionId, selectedDefinition.permissionCode, selectedDefinition.version, auditReason);
      setSelectedPermission('');
      await loadSelected();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Permission definition removal failed. Existing permissions must be retired.');
    } finally { setBusy(false); }
  }

  async function saveAlias(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selected || !editable || !requireReason()) return;
    const data = new FormData(event.currentTarget);
    const aliasCode = String(data.get('aliasCode'));
    const body = {
      aliasCode,
      canonicalPermissionCode: String(data.get('canonicalPermissionCode')),
      aliasType: String(data.get('aliasType')),
      validFrom: data.get('validFrom') ? new Date(String(data.get('validFrom'))).toISOString() : null,
      validUntil: data.get('validUntil') ? new Date(String(data.get('validUntil'))).toISOString() : null,
      reason: String(data.get('reason')),
    };
    setBusy(true);
    try {
      if (selectedAliasValue) {
        await platformGovernanceApi.updatePermissionCatalogAlias(selected.revisionId, selectedAliasValue.aliasCode, body, selectedAliasValue.version, auditReason);
      } else {
        await platformGovernanceApi.createPermissionCatalogAlias(selected.revisionId, body, auditReason);
      }
      setSelectedAlias(aliasCode);
      await loadSelected();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Permission alias save failed.');
    } finally { setBusy(false); }
  }

  async function removeAlias() {
    if (!selected || !selectedAliasValue || !editable || !requireReason()) return;
    setBusy(true);
    try {
      await platformGovernanceApi.deletePermissionCatalogAlias(selected.revisionId, selectedAliasValue.aliasCode, selectedAliasValue.version, auditReason);
      setSelectedAlias('');
      await loadSelected();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Permission alias removal failed.');
    } finally { setBusy(false); }
  }

  async function publish() {
    if (!selected || selected.status !== 'DRAFT' || !validation?.valid || !publishConfirmed || !requireReason()) return;
    setBusy(true);
    try {
      await platformGovernanceApi.publishPermissionCatalog(selected.revisionId, selected.version, auditReason);
      await loadRoot();
      setPublishConfirmed(false);
      setTab('evidence');
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Catalog publication failed.');
    } finally { setBusy(false); }
  }

  return (
    <main className="space-y-5">
      <AdminPageHeader
        title="Permission Catalog"
        eyebrow="Internal engineering · Authorization"
        description="Controlled PostgreSQL Catalog revisions. Published and active revisions are immutable; all changes start in the single Draft."
      />
      <PermissionCatalogLifecyclePanel active={active} selected={selected} validation={validation}/>
      {error ? <div role="alert" className="rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm font-semibold text-rose-800">{error}</div> : null}

      <section className="grid gap-4 md:grid-cols-4">
        <div className="rounded-2xl border border-slate-200 bg-white p-4"><div className="text-xs font-bold uppercase text-slate-500">Active Revision</div><div className="mt-2 text-xl font-black">{active?.revisionCode ?? 'Unavailable'}</div><div className="mt-1 text-xs text-slate-500">Revision {active?.revisionNumber ?? '—'} · {active?.status ?? '—'}</div></div>
        <div className="rounded-2xl border border-slate-200 bg-white p-4"><div className="text-xs font-bold uppercase text-slate-500">Content Hash</div><div className="mt-2 break-all font-mono text-sm font-bold" title={active?.contentHash}>{shortHash(active?.contentHash)}</div><div className="mt-1 text-xs text-slate-500">Immutable publication fingerprint</div></div>
        <div className="rounded-2xl border border-slate-200 bg-white p-4"><div className="text-xs font-bold uppercase text-slate-500">Draft State</div><div className="mt-2 text-xl font-black">{draft ? draft.revisionCode : 'No Draft'}</div><div className="mt-1 text-xs text-slate-500">Only one Draft may exist</div></div>
        <div className="rounded-2xl border border-slate-200 bg-white p-4"><div className="text-xs font-bold uppercase text-slate-500">Latest Evidence</div><div className="mt-2 text-xl font-black">{publications.length}</div><div className="mt-1 text-xs text-slate-500">Last published {dateTime(publications[0]?.publishedAt)}</div></div>
      </section>

      <section className="grid gap-5 xl:grid-cols-[330px_minmax(0,1fr)]">
        <aside className="space-y-4 rounded-2xl border border-slate-200 bg-white p-4">
          <div><h2 className="font-bold">Catalog Revisions</h2><p className="text-xs text-slate-500">Select a revision to inspect its immutable snapshot or edit the Draft.</p></div>
          <div className="max-h-[560px] space-y-2 overflow-y-auto">
            {revisions.map((revision) => (
              <button key={revision.revisionId} onClick={() => setSelectedId(revision.revisionId)} className={`w-full rounded-xl border p-3 text-left ${selectedId === revision.revisionId ? 'border-blue-500 bg-blue-50' : 'border-slate-200'}`}>
                <div className="flex items-center justify-between gap-2"><span className="font-bold">{revision.revisionCode}</span><span className={`rounded-full px-2 py-0.5 text-xs font-bold ${revision.status === 'DRAFT' ? 'bg-amber-100 text-amber-800' : revision.revisionId === active?.revisionId ? 'bg-emerald-100 text-emerald-800' : 'bg-slate-100 text-slate-600'}`}>{revision.revisionId === active?.revisionId ? 'ACTIVE' : revision.status}</span></div>
                <div className="mt-1 text-xs text-slate-500">Revision {revision.revisionNumber} · v{revision.version}</div>
              </button>
            ))}
          </div>
          {!draft && canManage ? (
            <form onSubmit={createDraft} className="space-y-3 border-t pt-4">
              <h3 className="font-bold">Create Draft from Active</h3>
              <input name="revisionCode" defaultValue={nextDraftCode(revisions)} required className={input} />
              <textarea name="description" required minLength={8} placeholder="Purpose of this Catalog revision" className={`${input} min-h-20`} />
              <button disabled={busy} className="w-full rounded-xl bg-slate-950 px-4 py-2.5 font-bold text-white">Create Single Draft</button>
            </form>
          ) : null}
        </aside>

        <section className="min-w-0 rounded-2xl border border-slate-200 bg-white">
          <div className="flex flex-wrap border-b px-4 pt-3">
            {(['overview', 'definitions', 'aliases', 'diff', 'validation', 'evidence'] as Tab[]).map((value) => (
              <button key={value} onClick={() => setTab(value)} className={`border-b-2 px-3 py-3 text-sm font-bold ${tab === value ? 'border-blue-600 text-blue-700' : 'border-transparent text-slate-500'}`}>{value[0].toUpperCase() + value.slice(1)}</button>
            ))}
          </div>
          {!selected ? <div className="p-10 text-center text-slate-500">Select a Catalog revision.</div> : <div className="p-5">
            {tab === 'overview' ? <div className="space-y-5">
              <div className="grid gap-4 md:grid-cols-2"><div><div className="text-xs font-bold uppercase text-slate-500">Revision</div><div className="mt-1 text-xl font-black">{selected.revisionCode}</div><div className="text-sm text-slate-500">#{selected.revisionNumber} · {selected.status} · version {selected.version}</div></div><div><div className="text-xs font-bold uppercase text-slate-500">Publication</div><div className="mt-1 text-sm">Created {dateTime(selected.createdAt)} by {selected.createdBy}</div><div className="text-sm">Published {dateTime(selected.publishedAt)} by {selected.publishedBy || '—'}</div></div></div>
              <div className="rounded-xl bg-slate-50 p-4"><div className="text-xs font-bold uppercase text-slate-500">Description</div><p className="mt-2 text-sm leading-6">{selected.description}</p></div>
              <div className="rounded-xl border border-slate-200 p-4"><div className="text-xs font-bold uppercase text-slate-500">Content Hash</div><div className="mt-2 break-all font-mono text-xs">{selected.contentHash || 'Drafts do not have a publication hash.'}</div></div>
              {selected.status !== 'DRAFT' ? <div className="rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-900"><strong>Immutable revision.</strong> Published and active revisions cannot be edited. Create a new Draft to change Permission semantics.</div> : <div className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900"><strong>Draft revision.</strong> Changes are isolated until validation passes and a Platform Publisher explicitly publishes this revision.</div>}
            </div> : null}

            {tab === 'definitions' ? <div className="grid gap-5 lg:grid-cols-[330px_minmax(0,1fr)]">
              <div><div className="flex gap-2"><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search Permission definitions" className={input} /><button onClick={() => setSelectedPermission('')} className="rounded-xl border px-3 text-sm font-bold">New</button></div><div className="mt-3 max-h-[580px] space-y-2 overflow-y-auto">{filteredDefinitions.map((definition) => <button key={definition.permissionCode} onClick={() => setSelectedPermission(definition.permissionCode)} className={`w-full rounded-xl border p-3 text-left ${selectedPermission === definition.permissionCode ? 'border-blue-500 bg-blue-50' : 'border-slate-200'}`}><div className="font-mono text-sm font-bold">{definition.permissionCode}</div><div className="mt-1 text-xs text-slate-500">{definition.ownerModule} · {definition.riskLane} · {definition.lifecycle}</div></button>)}</div></div>
              <DefinitionForm key={`${selected.revisionId}:${selectedDefinition?.permissionCode ?? 'new'}:${selectedDefinition?.version ?? 0}`} definition={selectedDefinition} editable={editable && canManage} busy={busy} onSubmit={saveDefinition} onDelete={removeDefinition} />
            </div> : null}

            {tab === 'aliases' ? <div className="grid gap-5 lg:grid-cols-[330px_minmax(0,1fr)]">
              <div><button onClick={() => setSelectedAlias('')} className="mb-3 rounded-xl border px-3 py-2 text-sm font-bold">New Alias</button><div className="max-h-[560px] space-y-2 overflow-y-auto">{aliases.map((alias) => <button key={alias.aliasCode} onClick={() => setSelectedAlias(alias.aliasCode)} className={`w-full rounded-xl border p-3 text-left ${selectedAlias === alias.aliasCode ? 'border-blue-500 bg-blue-50' : 'border-slate-200'}`}><div className="font-mono text-sm font-bold">{alias.aliasCode}</div><div className="mt-1 text-xs text-slate-500">→ {alias.canonicalPermissionCode}</div></button>)}</div></div>
              <AliasForm key={`${selected.revisionId}:${selectedAliasValue?.aliasCode ?? 'new'}:${selectedAliasValue?.version ?? 0}`} alias={selectedAliasValue} definitions={definitions} editable={editable && canManage} busy={busy} onSubmit={saveAlias} onDelete={removeAlias} />
            </div> : null}

            {tab === 'diff' ? <div className="space-y-4"><div><h2 className="font-bold">Revision Diff</h2><p className="text-sm text-slate-500">Compared with the revision this Draft supersedes. Published historical snapshots remain read-only.</p></div>{!diff?.items.length ? <div className="rounded-xl border border-slate-200 p-8 text-center text-slate-500">No Permission definition changes.</div> : <div className="space-y-2">{diff.items.map((item) => <div key={`${item.type}:${item.permissionCode}`} className="rounded-xl border border-slate-200 p-4"><div className="flex flex-wrap items-center justify-between gap-2"><span className="font-mono text-sm font-bold">{item.permissionCode}</span><span className={`rounded-full px-2 py-1 text-xs font-bold ${item.type === 'ADDED' ? 'bg-emerald-100 text-emerald-800' : item.type === 'REMOVED' ? 'bg-rose-100 text-rose-800' : 'bg-amber-100 text-amber-800'}`}>{item.type}</span></div><div className="mt-2 text-xs text-slate-500">Changed fields: {item.changedFields.join(', ') || 'Definition added or removed'}</div></div>)}</div>}</div> : null}

            {tab === 'validation' ? <div className="space-y-5">
              {selected.status !== 'DRAFT' ? <div className="rounded-xl border border-slate-200 bg-slate-50 p-5"><div className="text-xl font-black">Immutable publication snapshot</div><p className="mt-2 text-sm text-slate-600">Publication validation runs only against the single Draft. This revision is already {selected.status.toLowerCase()} and cannot be modified or re-published.</p></div> : <>
              <div className={`rounded-xl border p-4 ${validation?.valid ? 'border-emerald-200 bg-emerald-50' : 'border-rose-200 bg-rose-50'}`}><div className="text-xl font-black">{validation?.valid ? 'Ready for publication review' : 'Publication blocked'}</div><div className="mt-1 text-sm">{validation?.entryCount ?? 0} definitions · {validation?.aliasCount ?? 0} aliases · {validation?.issues.length ?? 0} findings</div></div>
              <div className="space-y-2">{validation?.issues.map((issue, index) => <div key={`${issue.code}:${issue.permissionCode}:${index}`} className={`rounded-xl border p-4 ${issue.severity === 'ERROR' ? 'border-rose-200' : 'border-amber-200'}`}><div className="flex items-center justify-between gap-2"><span className="font-bold">{issue.code}</span><span className="text-xs font-bold">{issue.severity}</span></div><div className="mt-1 font-mono text-xs">{issue.permissionCode}</div><p className="mt-2 text-sm text-slate-600">{issue.message}</p></div>)}</div>
              <div className="rounded-2xl border border-slate-300 p-5"><h3 className="font-bold">Publish Review</h3><p className="mt-1 text-sm text-slate-600">Publication atomically hashes the Catalog, marks the previous revision superseded, materializes the active PostgreSQL projection, switches the active pointer, and appends immutable evidence.</p><label className="mt-4 flex items-start gap-3 rounded-xl border border-amber-200 bg-amber-50 p-3 text-sm"><input type="checkbox" checked={publishConfirmed} onChange={(event) => setPublishConfirmed(event.target.checked)} /><span>I reviewed the Diff and Validation findings and confirm this Draft should become the active runtime Permission Authority.</span></label><button onClick={() => void publish()} disabled={busy || !validation?.valid || !canPublish || !publishConfirmed} className="mt-4 rounded-xl bg-emerald-700 px-4 py-2.5 font-bold text-white disabled:cursor-not-allowed disabled:opacity-40">Publish and Activate Revision</button>{!canPublish ? <p className="mt-2 text-xs text-slate-500">Requires <code>permission.catalog.publish</code>.</p> : null}</div>
              </>}
            </div> : null}

            {tab === 'evidence' ? <div className="space-y-4"><div><h2 className="font-bold">Publication Evidence</h2><p className="text-sm text-slate-500">Append-only evidence from PostgreSQL <code>permission_catalog_publication_events</code>.</p></div><div className="overflow-x-auto"><table className="min-w-full text-sm"><thead><tr className="text-left text-xs uppercase text-slate-500"><th className="p-2">Published</th><th>Revision</th><th>Entries</th><th>Actor</th><th>Reason</th><th>Hash</th></tr></thead><tbody>{publications.map((publication) => <tr key={publication.publicationId} className="border-t align-top"><td className="p-2 whitespace-nowrap">{dateTime(publication.publishedAt)}</td><td className="font-mono text-xs">{publication.revisionId}</td><td>{publication.entryCount} / {publication.aliasCount}</td><td>{publication.actorId}<div className="text-xs text-slate-500">{publication.correlationId || 'No correlation ID'}</div></td><td className="max-w-xs whitespace-normal">{publication.auditReason}</td><td className="max-w-xs break-all font-mono text-xs" title={publication.contentHash}>{shortHash(publication.contentHash)}</td></tr>)}</tbody></table></div></div> : null}
          </div>}
        </section>
      </section>

      {(canManage || canPublish) ? <section className="rounded-2xl border border-slate-200 bg-white p-5"><label className="text-sm font-bold" htmlFor="catalog-audit-reason">Catalog mutation audit reason</label><textarea id="catalog-audit-reason" value={auditReason} onChange={(event) => setAuditReason(event.target.value)} minLength={12} placeholder="Explain why this Catalog change or publication is required (minimum 12 characters)." className={`${input} mt-2 min-h-24`} /><p className="mt-2 text-xs text-slate-500">The same reason is attached to idempotent mutation evidence and publication evidence.</p></section> : <div className="rounded-xl border bg-white p-4 text-sm text-slate-600">Read-only. Catalog changes require <code>permission.catalog.manage</code>; publication requires <code>permission.catalog.publish</code>.</div>}
    </main>
  );
}

function DefinitionForm({ definition, editable, busy, onSubmit, onDelete }: { definition: PermissionCatalogDefinition | null; editable: boolean; busy: boolean; onSubmit: (event: FormEvent<HTMLFormElement>) => void; onDelete: () => void }) {
  const selectedScopes = new Set(definition?.allowedScopes ?? ['TENANT']);
  return <form onSubmit={onSubmit} className="space-y-3 rounded-xl border border-slate-200 p-4"><div className="flex items-center justify-between"><div><h2 className="font-bold">{definition ? 'Permission Definition' : 'New Permission Definition'}</h2><p className="text-xs text-slate-500">Version {definition?.version ?? 0}</p></div>{definition && editable && definition.lifecycle === 'DRAFT' ? <button type="button" onClick={onDelete} className="text-sm font-bold text-rose-700">Delete Draft-only Definition</button> : null}</div><input name="permissionCode" defaultValue={definition?.permissionCode ?? ''} readOnly={Boolean(definition)} required placeholder="task.read" className={input} /><div className="grid gap-3 md:grid-cols-2"><input name="ownerModule" defaultValue={definition?.ownerModule ?? ''} required placeholder="Owner module" disabled={!editable} className={input} /><input name="resourceType" defaultValue={definition?.resourceType ?? ''} required placeholder="Resource type" disabled={!editable} className={input} /><input name="actionCode" defaultValue={definition?.actionCode ?? ''} required placeholder="Action code" disabled={!editable} className={input} /><input name="riskLevel" defaultValue={definition?.riskLevel ?? 'MEDIUM'} required placeholder="Risk level" disabled={!editable} className={input} /><select name="riskLane" defaultValue={definition?.riskLane ?? 'READ'} disabled={!editable} className={input}>{['READ', 'WRITE', 'EXPORT', 'ADMIN', 'CRITICAL'].map((value) => <option key={value}>{value}</option>)}</select><select name="lifecycle" defaultValue={definition?.lifecycle ?? 'DRAFT'} disabled={!editable} className={input}>{lifecycleOptions(definition).map((value) => <option key={value}>{value}</option>)}</select></div><textarea name="description" defaultValue={definition?.description ?? ''} required minLength={4} disabled={!editable} placeholder="Stable Permission semantics" className={`${input} min-h-24`} /><fieldset disabled={!editable} className="rounded-xl border p-3"><legend className="px-2 text-xs font-bold uppercase text-slate-500">Allowed Scopes</legend><div className="flex flex-wrap gap-4">{scopes.map((scope) => <label key={scope} className="flex items-center gap-2 text-sm"><input name={`scope-${scope}`} type="checkbox" defaultChecked={selectedScopes.has(scope)} />{scope}</label>)}</div></fieldset><input name="replacementPermissionCode" defaultValue={definition?.replacementPermissionCode ?? ''} disabled={!editable} placeholder="Replacement Permission when deprecated or retired" className={input} /><div className="grid gap-3 md:grid-cols-2"><input name="deprecatedAt" type="datetime-local" defaultValue={localInput(definition?.deprecatedAt)} disabled={!editable} className={input} /><input name="retiredAt" type="datetime-local" defaultValue={localInput(definition?.retiredAt)} disabled={!editable} className={input} /></div><label className="flex items-center gap-2 text-sm"><input name="systemManaged" type="checkbox" defaultChecked={definition?.systemManaged ?? false} disabled={!editable} />System managed</label>{editable ? <button disabled={busy} className="rounded-xl bg-blue-600 px-4 py-2.5 font-bold text-white">{definition ? 'Save Definition' : 'Create Definition'}</button> : <div className="rounded-xl bg-slate-50 p-3 text-sm text-slate-600">Published snapshots are immutable.</div>}</form>;
}

function AliasForm({ alias, definitions, editable, busy, onSubmit, onDelete }: { alias: PermissionCatalogAlias | null; definitions: PermissionCatalogDefinition[]; editable: boolean; busy: boolean; onSubmit: (event: FormEvent<HTMLFormElement>) => void; onDelete: () => void }) {
  return <form onSubmit={onSubmit} className="space-y-3 rounded-xl border border-slate-200 p-4"><div className="flex items-center justify-between"><div><h2 className="font-bold">{alias ? 'Permission Alias' : 'New Permission Alias'}</h2><p className="text-xs text-slate-500">Aliases are temporary migration mappings, not a second Permission definition.</p></div>{alias && editable ? <button type="button" onClick={onDelete} className="text-sm font-bold text-rose-700">Delete Alias</button> : null}</div><input name="aliasCode" defaultValue={alias?.aliasCode ?? ''} readOnly={Boolean(alias)} required placeholder="legacy.task.view" className={input} /><select name="canonicalPermissionCode" defaultValue={alias?.canonicalPermissionCode ?? ''} required disabled={!editable} className={input}><option value="">Select canonical Permission</option>{definitions.filter((definition) => definition.lifecycle !== 'RETIRED').map((definition) => <option key={definition.permissionCode} value={definition.permissionCode}>{definition.permissionCode}</option>)}</select><select name="aliasType" defaultValue={alias?.aliasType ?? 'LEGACY'} disabled={!editable} className={input}><option>LEGACY</option><option>RENAMED</option><option>COMPATIBILITY</option></select><div className="grid gap-3 md:grid-cols-2"><input name="validFrom" type="datetime-local" defaultValue={localInput(alias?.validFrom)} disabled={!editable} className={input} /><input name="validUntil" type="datetime-local" defaultValue={localInput(alias?.validUntil)} disabled={!editable} className={input} /></div><textarea name="reason" defaultValue={alias?.reason ?? ''} required minLength={4} disabled={!editable} placeholder="Why this alias is required and when it will be removed" className={`${input} min-h-24`} />{editable ? <button disabled={busy} className="rounded-xl bg-blue-600 px-4 py-2.5 font-bold text-white">{alias ? 'Save Alias' : 'Create Alias'}</button> : <div className="rounded-xl bg-slate-50 p-3 text-sm text-slate-600">Published aliases are immutable.</div>}</form>;
}
