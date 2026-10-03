'use client';

import { Button } from '@/components/ui/Button';
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ConfigurationImpactPreview,
  ConfigurationLifecyclePanel,
  ConfigurationOperationOutcome,
  ConfigurationValidationPanel,
  type ConfigurationLifecycleStep,
  type ConfigurationRisk,
  type GovernedConfigurationState,
} from '@/components/configuration/ConfigurationConfidence';
import {
  deprecateProjectMapping,
  diffProjectMapping,
  forkProjectMapping,
  listPrincipals,
  listProjectMappings,
  listProjectMappingVersions,
  listProviderMetadataSnapshots,
  previewProjectMapping,
  probeProviderMetadata,
  probeSourceIssueTrackingReadiness,
  publishProjectMapping,
  rollbackProjectMapping,
  saveProjectMapping,
  validateProjectMapping,
  type IntegrationPrincipal,
  type IntegrationProjectMapping,
  type IntegrationProjectMappingVersion,
  type IssueTrackingRuntimeReadiness,
  type ProjectMappingDiff,
  type ProjectMappingPreview,
  type ProjectMappingValidationResult,
  type ProviderMetadataSnapshot,
  type ProviderType,
} from '@/lib/api/domains/integrationIdentityApi';
import { useAuth } from '@/components/auth/AuthProvider';
import { coreAdminApi } from '@/lib/api/coreAdminApi';
import { taskContractAdminApi } from '@/lib/api/domains/taskContractAdminApi';
import type { CoreDispatchTaskDefinition, CoreSourceSystem } from '@/lib/types/core';

const emptyMapping = (connectionId = '', sourceSystemId?: string, taskType?: string): IntegrationProjectMapping => ({
  mappingId: '',
  connectionId,
  sourceSystemId: sourceSystemId || null,
  taskType: taskType || null,
  externalProjectId: '',
  externalProjectKey: '',
  externalIssueType: '',
  mappingStatus: 'DRAFT',
  lifecycleStatus: 'DRAFT',
  mappingVersion: 1,
  summaryTemplate: '{{task.title}}',
  descriptionTemplate: '{{task.description}}',
  requiredFields: [],
  customFieldMappings: {},
  transitionMappings: {},
  commentPolicy: 'APPEND_ONLY',
  linkPolicy: 'CANONICAL_ONLY',
  resolutionPriority: 1000,
  defaultMapping: false,
  enabled: false,
});

function split(value: string) { return value.split(',').map((item) => item.trim()).filter(Boolean); }
function jsonMap(value: string) {
  if (!value.trim()) return {};
  const parsed = JSON.parse(value);
  if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') throw new Error('Expected a JSON object.');
  return parsed as Record<string, string>;
}
function errorText(error: unknown) { return error instanceof Error ? error.message : 'The operation failed.'; }
function providerName(providerType?: ProviderType) {
  return providerType === 'JIRA' ? 'Jira' : providerType === 'GITLAB_ISSUES' ? 'GitLab Issues' : providerType === 'REDMINE' ? 'Redmine' : 'Issue Tracking provider';
}
function mappingFingerprint(value: IntegrationProjectMapping, customText: string, transitionText: string) {
  return JSON.stringify({
    mappingId: value.mappingId,
    connectionId: value.connectionId,
    sourceSystemId: value.sourceSystemId ?? null,
    taskType: value.taskType ?? null,
    externalProjectId: value.externalProjectId,
    externalProjectKey: value.externalProjectKey ?? null,
    externalIssueType: value.externalIssueType ?? null,
    externalTrackerId: value.externalTrackerId ?? null,
    readPrincipalId: value.readPrincipalId ?? null,
    requiredFields: value.requiredFields ?? [],
    summaryTemplate: value.summaryTemplate ?? '',
    descriptionTemplate: value.descriptionTemplate ?? '',
    customText,
    transitionText,
    commentPolicy: value.commentPolicy ?? 'APPEND_ONLY',
    linkPolicy: value.linkPolicy ?? 'CANONICAL_ONLY',
  });
}

type OperationOutcome = {
  outcome: 'VALIDATION_FAILED' | 'APPLY_FAILED' | 'APPLIED' | 'ACTIVE_NEEDS_ATTENTION' | 'INFO';
  title: string;
  message: string;
  safetyNote: string;
};

export function ProjectMappingGovernancePanel({
  connectionId,
  providerType,
  initialSourceSystemId,
  initialTaskType,
  initialMappingId,
  onMappingsChanged,
}: {
  connectionId: string;
  providerType?: ProviderType;
  initialSourceSystemId?: string;
  initialTaskType?: string;
  initialMappingId?: string;
  onMappingsChanged?: () => void | Promise<void>;
}) {
  const { activeTenantId } = useAuth();
  const [mappings, setMappings] = useState<IntegrationProjectMapping[]>([]);
  const [selectedId, setSelectedId] = useState('');
  const [draft, setDraft] = useState<IntegrationProjectMapping>(emptyMapping(connectionId, initialSourceSystemId, initialTaskType));
  const [snapshots, setSnapshots] = useState<ProviderMetadataSnapshot[]>([]);
  const [versions, setVersions] = useState<IntegrationProjectMappingVersion[]>([]);
  const [validation, setValidation] = useState<ProjectMappingValidationResult | null>(null);
  const [preview, setPreview] = useState<ProjectMappingPreview | null>(null);
  const [diff, setDiff] = useState<ProjectMappingDiff | null>(null);
  const [runtimeReadiness, setRuntimeReadiness] = useState<IssueTrackingRuntimeReadiness | null>(null);
  const [contextText, setContextText] = useState('{"task.title":"Example incident","task.description":"Generated preview context"}');
  const [customText, setCustomText] = useState('{}');
  const [transitionText, setTransitionText] = useState('{}');
  const [persistedFingerprint, setPersistedFingerprint] = useState('');
  const [newMappingId, setNewMappingId] = useState('');
  const [fromVersion, setFromVersion] = useState(1);
  const [toVersion, setToVersion] = useState(1);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [operationOutcome, setOperationOutcome] = useState<OperationOutcome | null>(null);
  const [publishAcknowledged, setPublishAcknowledged] = useState(false);
  const [deprecateAcknowledged, setDeprecateAcknowledged] = useState(false);
  const [principals, setPrincipals] = useState<IntegrationPrincipal[]>([]);
  const [sourceSystems, setSourceSystems] = useState<CoreSourceSystem[]>([]);
  const [taskDefinitions, setTaskDefinitions] = useState<CoreDispatchTaskDefinition[]>([]);

  const activeSnapshot = snapshots[0];
  const serviceAccounts = useMemo(() => principals.filter((value) => value.principalType === 'SERVICE_ACCOUNT'), [principals]);
  const taskOptions = useMemo(
    () => taskDefinitions.filter((value) => !draft.sourceSystemId || String(value.sourceSystem ?? '').toUpperCase() === String(draft.sourceSystemId).toUpperCase()),
    [taskDefinitions, draft.sourceSystemId],
  );
  const selectedPrincipalId = draft.readPrincipalId ?? draft.createPrincipalId ?? draft.commentPrincipalId ?? draft.updatePrincipalId ?? '';
  const provider = providerName(providerType);
  const currentFingerprint = useMemo(() => mappingFingerprint(draft, customText, transitionText), [draft, customText, transitionText]);
  const isDirty = selectedId ? currentFingerprint !== persistedFingerprint : true;
  const immutable = draft.lifecycleStatus === 'ACTIVE' || draft.lifecycleStatus === 'DEPRECATED';
  const overlappingActiveMappings = useMemo(
    () => mappings.filter((value) => value.mappingId !== draft.mappingId
      && value.lifecycleStatus === 'ACTIVE'
      && String(value.sourceSystemId ?? '').toUpperCase() === String(draft.sourceSystemId ?? '').toUpperCase()
      && String(value.taskType ?? '').toUpperCase() === String(draft.taskType ?? '').toUpperCase()),
    [mappings, draft.mappingId, draft.sourceSystemId, draft.taskType],
  );
  const mappingPublishRisk: ConfigurationRisk = overlappingActiveMappings.length > 0 ? 'HIGH' : 'MEDIUM';
  const scopeLabel = `${draft.sourceSystemId || 'Any Source'} · ${draft.taskType || 'All task types'}`;
  const visibleMappings = useMemo(
    () => initialSourceSystemId
      ? mappings.filter((value) => String(value.sourceSystemId ?? '').toUpperCase() === initialSourceSystemId.toUpperCase()
        && (!initialTaskType || String(value.taskType ?? '').toUpperCase() === initialTaskType.toUpperCase()))
      : mappings,
    [mappings, initialSourceSystemId, initialTaskType],
  );
  const activeVersion = useMemo(() => versions.find((value) => value.lifecycle === 'ACTIVE'), [versions]);

  const governedState: GovernedConfigurationState = validation && !validation.valid
    ? 'FAILED'
    : isDirty
      ? 'UNSAVED'
      : draft.lifecycleStatus === 'VALIDATING'
        ? 'VALIDATING'
        : draft.lifecycleStatus === 'VALID'
          ? 'VALIDATED'
          : draft.lifecycleStatus === 'ACTIVE'
            ? 'ACTIVE'
            : draft.lifecycleStatus === 'DEPRECATED'
              ? 'SUPERSEDED'
              : 'DRAFT';

  const lifecycleSteps: ConfigurationLifecycleStep[] = useMemo(() => {
    const saved = Boolean(selectedId) && !isDirty;
    const validated = !isDirty && ['VALID', 'ACTIVE', 'DEPRECATED'].includes(String(draft.lifecycleStatus));
    const published = ['ACTIVE', 'DEPRECATED'].includes(String(draft.lifecycleStatus));
    const verified = draft.lifecycleStatus === 'ACTIVE' && (runtimeReadiness ? runtimeReadiness.runtimeReady : !draft.sourceSystemId);
    return [
      { key: 'UNSAVED', label: 'Edit', detail: isDirty ? 'Unsaved changes exist. Production is unchanged.' : 'Edit a new or forked working copy.', state: isDirty ? 'current' : saved ? 'complete' : 'upcoming' },
      { key: 'DRAFT', label: 'Save draft', detail: 'Persist the working copy without changing runtime routing.', state: saved ? 'complete' : isDirty ? 'upcoming' : 'current' },
      { key: 'VALIDATED', label: 'Validate', detail: 'Check provider metadata and required fields against the saved draft.', state: validation && !validation.valid ? 'blocked' : validated ? 'complete' : saved ? 'current' : 'upcoming' },
      { key: 'ACTIVE', label: 'Publish', detail: 'Make the validated immutable version authoritative for matching new operations.', state: published ? 'complete' : validated ? 'current' : 'upcoming' },
      { key: 'ACTIVE', label: 'Verify', detail: draft.sourceSystemId ? 'Probe source-specific runtime readiness after publish.' : 'Confirm Core ACTIVE state; runtime probe requires a Source System scope.', state: verified ? 'complete' : published ? 'current' : 'upcoming' },
    ];
  }, [selectedId, isDirty, draft.lifecycleStatus, draft.sourceSystemId, validation, runtimeReadiness]);

  const refresh = useCallback(async (preferred?: string) => {
    if (!connectionId) { setMappings([]); setSelectedId(''); return; }
    const values = await listProjectMappings(connectionId);
    setMappings(values);
    const scoped = initialSourceSystemId
      ? values.filter((value) => String(value.sourceSystemId ?? '').toUpperCase() === initialSourceSystemId.toUpperCase()
        && (!initialTaskType || String(value.taskType ?? '').toUpperCase() === initialTaskType.toUpperCase()))
      : values;
    const next = preferred
      ?? (initialMappingId && values.some((value) => value.mappingId === initialMappingId) ? initialMappingId : undefined)
      ?? scoped[0]?.mappingId
      ?? '';
    setSelectedId(next);
  }, [connectionId, initialSourceSystemId, initialTaskType, initialMappingId]);

  const refreshDetail = useCallback(async (mappingId: string) => {
    if (!mappingId) { setSnapshots([]); setVersions([]); return; }
    const [metadata, versionHistory] = await Promise.all([listProviderMetadataSnapshots(mappingId), listProjectMappingVersions(mappingId)]);
    setSnapshots(metadata);
    setVersions(versionHistory);
    if (versionHistory.length) {
      setFromVersion(versionHistory.at(-1)?.mappingVersion ?? 1);
      setToVersion(versionHistory[0].mappingVersion);
    }
  }, []);

  async function perform(action: () => Promise<void>, success: string) {
    setBusy(true);
    setMessage('');
    try {
      await action();
      setMessage(success);
    } catch (error) {
      setMessage(errorText(error));
    } finally {
      setBusy(false);
    }
  }

  async function saveDraft() {
    setBusy(true);
    setMessage('');
    setOperationOutcome(null);
    try {
      const saved = await saveProjectMapping({ ...draft, connectionId, customFieldMappings: jsonMap(customText), transitionMappings: jsonMap(transitionText) });
      await refresh(saved.mappingId);
      await onMappingsChanged?.();
      setMessage('Draft saved. Runtime routing is unchanged.');
    } catch (error) {
      setOperationOutcome({
        outcome: 'INFO',
        title: 'Draft was not saved',
        message: errorText(error),
        safetyNote: 'Saving a draft does not activate routing. The current ACTIVE mapping, if any, remains authoritative.',
      });
    } finally {
      setBusy(false);
    }
  }

  async function runValidation() {
    if (!selectedId || isDirty || immutable) return;
    setBusy(true);
    setMessage('');
    setOperationOutcome(null);
    try {
      const result = await validateProjectMapping(selectedId);
      setValidation(result);
      await refresh(selectedId);
      if (result.valid) {
        setOperationOutcome({
          outcome: 'INFO',
          title: 'Draft validated',
          message: 'The saved draft passed provider metadata and required-field validation. Review impact before publishing.',
          safetyNote: 'Validation did not change production routing. Publishing is still a separate explicit action.',
        });
      } else {
        setOperationOutcome({
          outcome: 'VALIDATION_FAILED',
          title: 'Draft is not ready to publish',
          message: result.errors.join(' · ') || 'One or more validation checks failed.',
          safetyNote: 'Production routing was not changed. Correct the draft, save it, and validate again.',
        });
      }
    } catch (error) {
      setOperationOutcome({
        outcome: 'VALIDATION_FAILED',
        title: 'Validation could not complete',
        message: errorText(error),
        safetyNote: 'Production routing was not changed by this validation attempt.',
      });
    } finally {
      setBusy(false);
    }
  }

  async function publishAndVerify() {
    if (!selectedId || isDirty || draft.lifecycleStatus !== 'VALID' || !publishAcknowledged) return;
    setBusy(true);
    setMessage('');
    setOperationOutcome(null);
    setRuntimeReadiness(null);
    let published: IntegrationProjectMapping;
    try {
      published = await publishProjectMapping(selectedId);
    } catch (error) {
      await refresh(selectedId).catch(() => undefined);
      setOperationOutcome({
        outcome: 'APPLY_FAILED',
        title: 'Publish did not complete',
        message: errorText(error),
        safetyNote: 'OpenDispatch did not confirm a new ACTIVE version. The page reloaded current Core state where possible; confirm which mapping is ACTIVE before retrying.',
      });
      setBusy(false);
      return;
    }

    await Promise.allSettled([refresh(published.mappingId), refreshDetail(published.mappingId), Promise.resolve(onMappingsChanged?.())]);
    setPublishAcknowledged(false);

    if (!published.sourceSystemId) {
      setOperationOutcome({
        outcome: 'INFO',
        title: 'Published and ACTIVE in Core',
        message: `Mapping ${published.mappingId} is ACTIVE. A source-specific runtime readiness probe cannot run because this mapping applies to any Source System.`,
        safetyNote: 'The authoritative mapping state is confirmed. Verify runtime behavior from a concrete Source System or connector operation before treating end-to-end delivery as certified.',
      });
      setBusy(false);
      return;
    }

    try {
      const readiness = await probeSourceIssueTrackingReadiness(published.sourceSystemId, published.taskType ?? null);
      setRuntimeReadiness(readiness);
      if (readiness.runtimeReady) {
        setOperationOutcome({
          outcome: 'APPLIED',
          title: 'Published and runtime-ready',
          message: `Mapping ${published.mappingId} is ACTIVE and the ${published.sourceSystemId}${published.taskType ? ` / ${published.taskType}` : ''} readiness probe reports runtime ready.`,
          safetyNote: 'The governed change is active. Continue to observe real connector operations; runtime readiness does not replace live operation evidence.',
        });
      } else {
        setOperationOutcome({
          outcome: 'ACTIVE_NEEDS_ATTENTION',
          title: 'Published, but runtime readiness has blockers',
          message: readiness.blockers.length ? readiness.blockers.join(' · ') : `Readiness status is ${readiness.overallStatus}.`,
          safetyNote: 'The mapping is already ACTIVE. Review the blockers now; production behavior for matching new operations may be affected until readiness is restored.',
        });
      }
    } catch (error) {
      setOperationOutcome({
        outcome: 'ACTIVE_NEEDS_ATTENTION',
        title: 'Published, but post-apply verification could not complete',
        message: errorText(error),
        safetyNote: 'The publish succeeded before this verification error. Treat the mapping as potentially active and inspect current Core readiness / connector operations before making another change.',
      });
    } finally {
      setBusy(false);
    }
  }

  async function verifyActiveState() {
    if (!draft.sourceSystemId || draft.lifecycleStatus !== 'ACTIVE') return;
    setBusy(true);
    setOperationOutcome(null);
    try {
      const readiness = await probeSourceIssueTrackingReadiness(draft.sourceSystemId, draft.taskType ?? null);
      setRuntimeReadiness(readiness);
      setOperationOutcome(readiness.runtimeReady ? {
        outcome: 'APPLIED',
        title: 'Active mapping is runtime-ready',
        message: `${draft.sourceSystemId}${draft.taskType ? ` / ${draft.taskType}` : ''} currently reports runtime ready.`,
        safetyNote: 'This is a current readiness check. Continue to use connector operation evidence for actual provider outcomes.',
      } : {
        outcome: 'ACTIVE_NEEDS_ATTENTION',
        title: 'Active mapping needs attention',
        message: readiness.blockers.join(' · ') || `Readiness status is ${readiness.overallStatus}.`,
        safetyNote: 'This mapping is already ACTIVE. Resolve blockers or prepare a validated replacement / rollback draft before changing authority again.',
      });
    } catch (error) {
      setOperationOutcome({
        outcome: 'ACTIVE_NEEDS_ATTENTION',
        title: 'Runtime verification failed',
        message: errorText(error),
        safetyNote: 'The verification request did not change configuration. The existing ACTIVE mapping remains in place.',
      });
    } finally {
      setBusy(false);
    }
  }

  useEffect(() => {
    setDraft(emptyMapping(connectionId, initialSourceSystemId, initialTaskType));
    setSelectedId('');
    setValidation(null);
    setPreview(null);
    setDiff(null);
    setRuntimeReadiness(null);
    setPersistedFingerprint('');
    refresh(initialMappingId).catch((error) => setMessage(errorText(error)));
  }, [connectionId, initialSourceSystemId, initialTaskType, initialMappingId, refresh]);

  useEffect(() => {
    if (!connectionId) { setPrincipals([]); return; }
    listPrincipals(connectionId).then(setPrincipals).catch((error) => setMessage(errorText(error)));
  }, [connectionId]);

  useEffect(() => {
    if (!activeTenantId) { setSourceSystems([]); setTaskDefinitions([]); return; }
    Promise.all([coreAdminApi.getSourceSystems(activeTenantId), taskContractAdminApi.getDispatchTaskDefinitions('ACTIVE', activeTenantId)])
      .then(([sources, definitions]) => { setSourceSystems(sources); setTaskDefinitions(definitions); })
      .catch((error) => setMessage(errorText(error)));
  }, [activeTenantId]);

  useEffect(() => {
    setValidation(null);
    setRuntimeReadiness(null);
    setOperationOutcome(null);
    setPublishAcknowledged(false);
    setDeprecateAcknowledged(false);
  }, [selectedId]);

  useEffect(() => {
    if (!selectedId) return;
    const value = mappings.find((item) => item.mappingId === selectedId);
    if (value) {
      const nextCustom = JSON.stringify(value.customFieldMappings ?? {}, null, 2);
      const nextTransition = JSON.stringify(value.transitionMappings ?? {}, null, 2);
      setDraft(value);
      setCustomText(nextCustom);
      setTransitionText(nextTransition);
      setPersistedFingerprint(mappingFingerprint(value, nextCustom, nextTransition));
    }
    refreshDetail(selectedId).catch((error) => setMessage(errorText(error)));
  }, [selectedId, mappings, refreshDetail]);

  useEffect(() => { setPublishAcknowledged(false); }, [currentFingerprint]);

  return <section className="rounded-2xl border border-violet-200 bg-violet-50 p-5">
    <div className="flex flex-wrap items-start justify-between gap-3">
      <div>
        <div className="text-xs font-black uppercase tracking-wide text-violet-700">Step 4 · Governed routing decision</div>
        <h3 className="mt-1 text-lg font-black text-violet-950">Decide where each Source / Task creates external issues</h3>
        <p className="mt-1 text-sm leading-6 text-violet-900">Create a working draft that selects this {provider} connection, project and issue type. Saving a draft is safe: runtime routing changes only after a saved draft is validated, impact-reviewed, and explicitly published.</p>
      </div>
      <button type="button" disabled={!connectionId} onClick={() => {
        setSelectedId('');
        setDraft(emptyMapping(connectionId, initialSourceSystemId, initialTaskType));
        setCustomText('{}');
        setTransitionText('{}');
        setPersistedFingerprint('');
        setValidation(null);
        setRuntimeReadiness(null);
        setOperationOutcome(null);
      }} className="rounded-lg border border-violet-300 bg-white px-3 py-2 text-sm font-bold disabled:opacity-50">New Draft</button>
    </div>

    {message ? <div role="status" className="mt-4 rounded-xl border border-violet-200 bg-white px-4 py-3 text-sm font-semibold text-slate-700">{message}</div> : null}
    {operationOutcome ? <div className="mt-4"><ConfigurationOperationOutcome {...operationOutcome} /></div> : null}

    <div className="mt-5">
      <ConfigurationLifecyclePanel
        state={governedState}
        summary="The working copy is separated from runtime authority. Unsaved edits and saved drafts do not change production. Validation is evidence only. Publish is the explicit activation boundary; post-publish verification then checks the applied state."
        steps={lifecycleSteps}
        activeVersion={activeVersion ? `v${activeVersion.mappingVersion}` : undefined}
        workingVersion={draft.mappingId ? `v${draft.mappingVersion ?? 1}` : 'new draft'}
      >
        <div className="grid gap-2 text-xs leading-5 text-slate-600 sm:grid-cols-3">
          <div><b>Unsaved:</b> browser edits only; Core is unchanged.</div>
          <div><b>Validated:</b> publish is allowed only while the saved draft remains unchanged.</div>
          <div><b>Active:</b> immutable authority; recovery creates another draft rather than mutating history.</div>
        </div>
      </ConfigurationLifecyclePanel>
    </div>

    <div className="mt-5 grid gap-5 xl:grid-cols-[minmax(18rem,0.8fr)_minmax(0,1.2fr)]">
      <div className="space-y-4">
        <label className="block text-sm font-bold text-violet-950">Mapping<select value={selectedId} onChange={(event) => setSelectedId(event.target.value)} className="mt-1 w-full rounded-xl border px-3 py-2"><option value="">New mapping</option>{visibleMappings.map((value) => <option key={value.mappingId} value={value.mappingId}>{value.mappingId} · v{value.mappingVersion} · {value.lifecycleStatus}</option>)}</select></label>
        <div className="rounded-xl border border-violet-200 bg-white p-4 text-sm"><div className="grid grid-cols-2 gap-3"><Metric label="Lifecycle" value={governedState}/><Metric label="Mapping version" value={`v${draft.mappingVersion ?? 1}`}/><Metric label="Metadata" value={draft.metadataSnapshotId ? 'Bound' : 'Not bound'}/><Metric label="Schema" value={draft.metadataSchemaHash ? draft.metadataSchemaHash.slice(0, 12) : '—'}/></div></div>
        {activeSnapshot ? <div className="rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm"><div className="font-black text-emerald-950">Latest Metadata Snapshot</div><div className="mt-2 grid grid-cols-2 gap-2 text-emerald-900"><span>v{activeSnapshot.metadataVersion}</span><span>{activeSnapshot.cacheStatus}</span><span>{activeSnapshot.projects.length} projects</span><span>{activeSnapshot.fields.length} fields</span><span>{activeSnapshot.issueTypes.length} issue types</span><span>{activeSnapshot.transitions.length} transitions</span></div><p className="mt-2 text-xs">Expires {activeSnapshot.expiresAt}</p></div> : null}
      </div>

      <div className="grid gap-3 md:grid-cols-2">
        <label className="text-sm font-bold text-slate-700">Source System<select disabled={immutable} value={draft.sourceSystemId ?? ''} onChange={(event) => setDraft({ ...draft, sourceSystemId: event.target.value || null, taskType: null })} className="mt-1 w-full rounded-xl border px-3 py-2 font-normal"><option value="">Any Source System</option>{sourceSystems.map((value) => <option key={value.sourceSystemId} value={value.sourceSystemId}>{value.displayName || value.sourceSystemId}</option>)}</select></label>
        <label className="text-sm font-bold text-slate-700">Task Type<select disabled={immutable} value={draft.taskType ?? ''} onChange={(event) => setDraft({ ...draft, taskType: event.target.value || null })} className="mt-1 w-full rounded-xl border px-3 py-2 font-normal"><option value="">All task types</option>{taskOptions.map((value) => <option key={value.taskType} value={value.taskType}>{value.displayName || value.taskType}</option>)}</select></label>
        <label className="md:col-span-2 text-sm font-bold text-slate-700">Technical Service Account<select disabled={immutable || !connectionId} value={selectedPrincipalId} onChange={(event) => setDraft({ ...draft, readPrincipalId: event.target.value || null, createPrincipalId: null, commentPrincipalId: null, updatePrincipalId: null, relationPrincipalId: null, webhookPrincipalId: null })} className="mt-1 w-full rounded-xl border px-3 py-2 font-normal"><option value="">Resolve the only active Service Account on this connection</option>{serviceAccounts.map((value) => <option key={value.principalId} value={value.principalId}>{value.principalName} · {value.status}</option>)}</select><span className="mt-1 block text-xs font-medium leading-5 text-slate-500">Canonical connector runtime uses one technical Service Account. Legacy per-operation Principal slots are not configured by this UI; the external provider decides READ / CREATE / COMMENT / UPDATE permissions.</span></label>
        <Field label="Mapping ID" value={draft.mappingId} disabled={Boolean(selectedId)} onChange={(value) => setDraft({ ...draft, mappingId: value, connectionId })}/>
        {activeSnapshot?.projects?.length ? <label className="text-sm font-bold text-slate-700">Provider Project<select disabled={immutable} value={draft.externalProjectId} onChange={(event) => { const project = activeSnapshot.projects.find((value) => value.projectId === event.target.value); setDraft({ ...draft, externalProjectId: event.target.value, externalProjectKey: project?.projectKey ?? null }); }} className="mt-1 w-full rounded-xl border px-3 py-2 font-normal"><option value="">Select project</option>{activeSnapshot.projects.filter((value) => value.accessible).map((value) => <option key={value.projectId} value={value.projectId}>{value.displayName || value.projectKey || value.projectId}</option>)}</select></label> : <Field label="Provider Project ID" value={draft.externalProjectId} disabled={immutable} onChange={(value) => setDraft({ ...draft, externalProjectId: value })}/>} 
        <Field label="Provider Project Key" value={draft.externalProjectKey ?? ''} disabled={immutable} onChange={(value) => setDraft({ ...draft, externalProjectKey: value })}/>
        {activeSnapshot?.issueTypes?.length ? <label className="text-sm font-bold text-slate-700">Issue Type / Tracker<select disabled={immutable} value={draft.externalIssueType ?? ''} onChange={(event) => setDraft({ ...draft, externalIssueType: event.target.value, externalTrackerId: event.target.value })} className="mt-1 w-full rounded-xl border px-3 py-2 font-normal"><option value="">Select tracker</option>{activeSnapshot.issueTypes.map((value) => <option key={value.issueTypeId} value={value.issueTypeId}>{value.displayName || value.issueTypeKey || value.issueTypeId}</option>)}</select></label> : <Field label="Issue Type / Tracker" value={draft.externalIssueType ?? ''} disabled={immutable} onChange={(value) => setDraft({ ...draft, externalIssueType: value, externalTrackerId: value })}/>} 
        <Field label="Required fields" value={(draft.requiredFields ?? []).join(', ')} disabled={immutable} onChange={(value) => setDraft({ ...draft, requiredFields: split(value) })}/>
        <label className="md:col-span-2 text-sm font-bold text-slate-700">Summary template<textarea disabled={immutable} value={draft.summaryTemplate ?? ''} onChange={(event) => setDraft({ ...draft, summaryTemplate: event.target.value })} className="mt-1 min-h-20 w-full rounded-xl border px-3 py-2 font-mono text-sm font-normal"/></label>
        <label className="md:col-span-2 text-sm font-bold text-slate-700">Description template<textarea disabled={immutable} value={draft.descriptionTemplate ?? ''} onChange={(event) => setDraft({ ...draft, descriptionTemplate: event.target.value })} className="mt-1 min-h-28 w-full rounded-xl border px-3 py-2 font-mono text-sm font-normal"/></label>
        <details className="md:col-span-2 rounded-xl border border-violet-200 bg-white p-3"><summary className="cursor-pointer text-sm font-black text-violet-900">Advanced mapping behavior</summary><p className="mt-2 text-xs leading-5 text-slate-500">Use these controls only when provider-specific fields, transitions, comments or link behavior require customization.</p><div className="mt-3 grid gap-3 md:grid-cols-2">
          <label className="text-sm font-bold text-slate-700">Custom field mapping JSON<textarea disabled={immutable} value={customText} onChange={(event) => setCustomText(event.target.value)} className="mt-1 min-h-28 w-full rounded-xl border px-3 py-2 font-mono text-xs font-normal"/></label>
          <label className="text-sm font-bold text-slate-700">Transition mapping JSON<textarea disabled={immutable} value={transitionText} onChange={(event) => setTransitionText(event.target.value)} className="mt-1 min-h-28 w-full rounded-xl border px-3 py-2 font-mono text-xs font-normal"/></label>
          <label className="text-sm font-bold text-slate-700">Comment policy<select disabled={immutable} value={draft.commentPolicy ?? 'APPEND_ONLY'} onChange={(event) => setDraft({ ...draft, commentPolicy: event.target.value })} className="mt-1 w-full rounded-xl border px-3 py-2 font-normal"><option>APPEND_ONLY</option><option>DISABLED</option><option>STATUS_CHANGES_ONLY</option></select></label>
          <label className="text-sm font-bold text-slate-700">Link policy<select disabled={immutable} value={draft.linkPolicy ?? 'CANONICAL_ONLY'} onChange={(event) => setDraft({ ...draft, linkPolicy: event.target.value })} className="mt-1 w-full rounded-xl border px-3 py-2 font-normal"><option>CANONICAL_ONLY</option><option>PROVIDER_NATIVE_OPTIONAL</option><option>PROVIDER_NATIVE_REQUIRED</option></select></label>
        </div></details>
      </div>
    </div>

    <div className="mt-5">
      <ConfigurationImpactPreview
        title={draft.lifecycleStatus === 'VALID' && !isDirty ? 'Impact review before publish' : 'Draft and publish safety'}
        risk={draft.lifecycleStatus === 'VALID' && !isDirty ? mappingPublishRisk : 'LOW'}
        summary={draft.lifecycleStatus === 'VALID' && !isDirty ? 'This saved draft has passed validation and is eligible for explicit publication. Publishing can change which provider project receives future issue operations for this scope.' : isDirty ? 'These edits are not yet saved. Production remains on the current ACTIVE mapping while you review and save the working copy.' : 'Saving this mapping as a draft does not activate it. Validate the saved draft against provider metadata before publishing.'}
        items={[
          { label: 'Scope', value: scopeLabel, detail: 'Source System and optional Task Type determine where this mapping can apply.' },
          { label: 'Provider target', value: draft.externalProjectId || 'Not selected', detail: draft.externalIssueType ? `Issue type / tracker: ${draft.externalIssueType}` : 'Select an issue type / tracker before validation.' },
          { label: 'Existing active match', value: String(overlappingActiveMappings.length), detail: overlappingActiveMappings.length ? 'Publishing may change resolution for an already active scope.' : 'No other ACTIVE mapping with the same Source/Task scope was found.', tone: overlappingActiveMappings.length ? 'warning' : 'ready' },
          { label: 'Recovery', value: versions.length ? 'Immutable history + rollback draft' : 'Version history starts after publish', tone: 'info' },
        ]}
        safetyNote={draft.lifecycleStatus === 'VALID' && !isDirty ? 'Publish only after confirming the Source/Task scope, provider project and tracker. The publish boundary changes Core authority; post-publish verification runs immediately when a concrete Source System is available.' : 'Unsaved edits, draft saves and validation do not replace the current ACTIVE routing. Editing a previously validated draft forces this UI back to UNSAVED and requires save + validation again.'}
      />
    </div>

    <div className="mt-5 flex flex-wrap gap-2">
      <Button disabled={busy || immutable || !isDirty || !draft.mappingId || !draft.externalProjectId} onClick={() => void saveDraft()} tone="primary" size="md">Save Draft</Button>
      <Button disabled={busy || !selectedId || immutable || isDirty} onClick={() => perform(async () => { await probeProviderMetadata(selectedId, undefined, true); await refreshDetail(selectedId); }, 'Provider project metadata refreshed using the configured technical Service Account.')} tone="secondary" size="md">Test Project Visibility</Button>
      <Button disabled={busy || !selectedId || immutable || isDirty} onClick={() => void runValidation()} tone="secondary" size="md">Validate saved draft</Button>
      <Button disabled={busy || draft.lifecycleStatus !== 'ACTIVE' || !draft.sourceSystemId} onClick={() => void verifyActiveState()} tone="success" size="md">Verify active runtime</Button>
    </div>

    <div className="mt-4">
      <ConfigurationValidationPanel
        title="Mapping validation"
        status={isDirty ? 'NOT_RUN' : validation ? (validation.valid ? 'PASSED' : 'FAILED') : draft.lifecycleStatus === 'VALID' ? 'PASSED' : 'NOT_RUN'}
        summary={isDirty ? 'Unsaved edits cannot inherit earlier validation. Save the working copy before validating.' : validation ? validation.valid ? 'The saved draft matches current provider metadata and required fields. Review impact and explicitly acknowledge the activation boundary before publishing.' : 'The draft is not ready to publish. Correct the listed validation errors, save, and run validation again.' : 'Validate the saved draft against provider metadata, required fields and mapping rules before publishing.'}
        checks={validation ? [...validation.errors.map((value) => ({ label: 'Error', value, passed: false })), ...validation.warnings.map((value) => ({ label: 'Warning', value }))] : undefined}
        safetyNote="Validation does not publish this mapping and does not replace the current ACTIVE routing. A failed validation is a configuration check failure, not a production routing change."
      />
    </div>

    {draft.lifecycleStatus === 'VALID' && !isDirty ? <section className="mt-4 rounded-xl border border-orange-200 bg-orange-50 p-4">
      <div className="text-xs font-black uppercase tracking-wide text-orange-700">Activation boundary</div>
      <h4 className="mt-1 font-black text-orange-950">Publish the validated routing change</h4>
      <p className="mt-1 text-sm leading-6 text-orange-900">Publish makes this immutable mapping version authoritative for matching future issue operations. It is intentionally separate from Save Draft and Validate.</p>
      <label className="mt-3 flex items-start gap-2 rounded-lg border border-orange-200 bg-white p-3 text-sm font-semibold text-slate-700"><input className="mt-1" type="checkbox" checked={publishAcknowledged} onChange={(event) => setPublishAcknowledged(event.target.checked)}/><span>I reviewed the Source / Task scope, provider target, active overlap and recovery path. I understand that Publish changes runtime authority.</span></label>
      <Button disabled={busy || !publishAcknowledged} onClick={() => void publishAndVerify()} className="mt-3" tone="warning" size="md">Publish and verify applied state</Button>
    </section> : null}

    {runtimeReadiness ? <section className="mt-4 rounded-xl border border-slate-200 bg-white p-4">
      <div className="text-xs font-black uppercase tracking-wide text-slate-500">Post-apply verification</div>
      <div className="mt-2 grid gap-2 sm:grid-cols-2 xl:grid-cols-4">
        <Metric label="Overall" value={runtimeReadiness.overallStatus}/>
        <Metric label="Runtime ready" value={runtimeReadiness.runtimeReady ? 'Yes' : 'No'}/>
        <Metric label="Authenticated" value={runtimeReadiness.providerAuthenticated ? 'Yes' : 'No'}/>
        <Metric label="Live CREATE certified" value={runtimeReadiness.liveCreateCertified ? 'Yes' : 'No'}/>
      </div>
      {runtimeReadiness.blockers.length ? <div className="mt-3 rounded-lg border border-red-200 bg-red-50 p-3 text-xs font-semibold leading-5 text-red-900">Blockers: {runtimeReadiness.blockers.join(' · ')}</div> : <div className="mt-3 rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-xs font-semibold text-emerald-900">No runtime-readiness blocker was reported for this Source / Task scope.</div>}
    </section> : null}

    <div className="mt-5 grid gap-5 xl:grid-cols-2">
      <div className="rounded-xl border border-violet-200 bg-white p-4">
        <h4 className="font-black">Preview rendered issue</h4>
        <p className="mt-1 text-xs leading-5 text-slate-500">Template preview is read-only evidence. It does not save, validate, or publish the mapping.</p>
        <textarea value={contextText} onChange={(event) => setContextText(event.target.value)} className="mt-3 min-h-28 w-full rounded-xl border px-3 py-2 font-mono text-xs"/>
        <Button disabled={busy || !selectedId} onClick={() => perform(async () => setPreview(await previewProjectMapping(selectedId, JSON.parse(contextText))), 'Preview rendered.')} className="mt-3" tone="secondary" size="md">Render Preview</Button>
        {preview ? <div className="mt-3 space-y-2 text-sm"><div><b>Summary:</b> {preview.renderedSummary}</div><div><b>Description:</b> {preview.renderedDescription}</div><div><b>Missing:</b> {preview.missingRequiredFields.join(', ') || 'None'}</div></div> : null}
      </div>

      <div className="rounded-xl border border-violet-200 bg-white p-4">
        <h4 className="font-black">Recovery workspace</h4>
        <p className="mt-1 text-xs leading-5 text-slate-500">Rollback never mutates an immutable active version. It creates a new DRAFT copied from historical configuration; that draft must be saved/validated/published through the same governed lifecycle.</p>
        <div className="mt-3 flex flex-wrap gap-2"><input aria-label="From version" type="number" min={1} value={fromVersion} onChange={(event) => setFromVersion(Number(event.target.value))} className="w-24 rounded-lg border px-2 py-1"/><input aria-label="To version" type="number" min={1} value={toVersion} onChange={(event) => setToVersion(Number(event.target.value))} className="w-24 rounded-lg border px-2 py-1"/><Button disabled={busy || !selectedId || versions.length < 2} onClick={() => perform(async () => setDiff(await diffProjectMapping(selectedId, fromVersion, toVersion)), 'Version diff loaded.')} tone="secondary" size="md">Compare versions</Button></div>
        {diff ? <pre className="mt-3 overflow-auto rounded-lg bg-slate-950 p-3 text-xs text-white">{JSON.stringify(diff.changes, null, 2)}</pre> : null}
        <div className="mt-3"><Field label="New draft Mapping ID" value={newMappingId} onChange={setNewMappingId}/></div>
        <div className="mt-3 flex flex-wrap gap-2"><Button disabled={busy || !selectedId || !newMappingId || draft.lifecycleStatus !== 'ACTIVE'} onClick={() => perform(async () => { const created = await forkProjectMapping(selectedId, newMappingId); await refresh(created.mappingId); await onMappingsChanged?.(); }, 'New mapping version draft created. Production is unchanged.')} tone="secondary" size="md">Create Next Version Draft</Button><Button disabled={busy || !selectedId || !newMappingId || versions.length === 0} onClick={() => perform(async () => { const created = await rollbackProjectMapping(selectedId, fromVersion, newMappingId); await refresh(created.mappingId); await onMappingsChanged?.(); }, 'Rollback draft created from immutable version. Production is unchanged until the draft is validated and published.')} tone="warning" size="md">Create Rollback Draft</Button></div>
      </div>
    </div>

    <section className="mt-5 rounded-xl border border-slate-200 bg-white p-4">
      <div className="flex flex-wrap items-start justify-between gap-3"><div><div className="text-xs font-black uppercase tracking-wide text-slate-500">Audit history</div><h4 className="mt-1 font-black text-slate-950">Immutable mapping versions</h4><p className="mt-1 text-xs leading-5 text-slate-500">Use history to understand what was active and who created each version. Recovery always creates a new working draft; history remains unchanged.</p></div>{draft.lifecycleStatus === 'ACTIVE' ? <details className="rounded-lg border border-amber-300 bg-amber-50 px-3 py-2"><summary className="cursor-pointer text-sm font-bold text-amber-900">Deprecate active mapping</summary><div className="mt-3 max-w-xl text-xs leading-5 text-amber-950">Deprecation changes active authority without creating a replacement. Confirm the replacement / recovery path first.</div><label className="mt-2 flex items-start gap-2 text-xs font-semibold text-amber-950"><input className="mt-0.5" type="checkbox" checked={deprecateAcknowledged} onChange={(event) => setDeprecateAcknowledged(event.target.checked)}/><span>I understand this ACTIVE mapping will be deprecated and may leave this scope without an active mapping.</span></label><Button disabled={busy || !deprecateAcknowledged} onClick={() => perform(async () => { await deprecateProjectMapping(selectedId); await refresh(selectedId); await onMappingsChanged?.(); setDeprecateAcknowledged(false); }, 'Mapping deprecated. Confirm replacement authority before relying on this scope.')} className="mt-2" tone="warning" size="md">Confirm deprecation</Button></details> : null}</div>
      <div className="mt-3 overflow-x-auto">
        <table className="min-w-full border-collapse text-left text-xs">
          <thead><tr className="border-b border-slate-200 text-slate-500"><th className="px-3 py-2">Version</th><th className="px-3 py-2">Lifecycle</th><th className="px-3 py-2">Created by</th><th className="px-3 py-2">Created</th><th className="px-3 py-2">Configuration hash</th></tr></thead>
          <tbody>{versions.length ? versions.map((version) => <tr key={`${version.mappingId}-${version.mappingVersion}`} className="border-b border-slate-100"><td className="px-3 py-2 font-black">v{version.mappingVersion}</td><td className="px-3 py-2">{version.lifecycle}</td><td className="px-3 py-2">{version.createdBy}</td><td className="px-3 py-2">{version.createdAt ? new Date(version.createdAt).toLocaleString() : '—'}</td><td className="px-3 py-2 font-mono">{version.configurationHash?.slice(0, 16) || '—'}</td></tr>) : <tr><td colSpan={5} className="px-3 py-4 text-slate-500">No immutable version history yet. Publish a validated draft to start history.</td></tr>}</tbody>
        </table>
      </div>
    </section>
  </section>;
}

function Field({ label, value, onChange, disabled = false }: { label: string; value: string; onChange: (value: string) => void; disabled?: boolean }) {
  return <label className="text-sm font-bold text-slate-700">{label}<input disabled={disabled} value={value} onChange={(event) => onChange(event.target.value)} className="mt-1 w-full rounded-xl border px-3 py-2 font-normal disabled:bg-slate-100"/></label>;
}
function Metric({ label, value }: { label: string; value: string }) {
  return <div><div className="text-xs font-bold uppercase tracking-wide text-slate-500">{label}</div><div className="mt-1 font-black text-slate-900">{value}</div></div>;
}
