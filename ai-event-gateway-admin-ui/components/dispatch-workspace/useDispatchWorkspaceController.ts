'use client';

import { useCallback, useEffect, useState } from 'react';
import { dispatchAdminApi } from '@/lib/api/domains/dispatchAdminApi';
import { sourceSystemsAdminApi } from '@/lib/api/domains/sourceSystemsAdminApi';
import { ApiError } from '@/lib/api/client';
import type {
  CoreAgentCapabilityAssignment, CoreAgentCapabilityCatalog, CoreCanonicalCapabilityDefinition, CoreAgentPoolView,
  CoreAgentQualityMetricsWindow, CoreDispatchFlowAgentOptionView, CoreDispatchFlowRuleView, CoreDispatchFlowRuleConflictView,
  CoreFlowDirectAgentCompatibilityBinding, CoreFlowCapabilityLegacyEquivalenceEvidence, CoreFlowCapabilityEquivalenceReadiness,
  CoreDispatchFlowView, CoreDispatchFlowRequiredCapabilityView, CoreDispatchSimulationResponse, CoreEventIntakeDecisionResponse,
  CoreSourceSystem, CoreWorkloadSourceRegistration,
} from '@/lib/types/core';
import {
  activeRules, defaultRule, flowActivationIssues, flowActivationReadinessIssues, flowDisplay, flowHealthIssues, flowSimulationIssues,
  isActiveStatus, simulationMatchesFlowVersion, simulationConfigurationPassed, simulationPassed, sectionState,
} from './dispatchWorkspaceModel';
import {
  type PoolEditorIntent, type PoolEditorState, type RuleEditorState, type SimulationFormState, defaultPoolEditor, emptyRuleEditor,
  generateId, normalizeCode, poolEditorFromPool, poolPayload, ruleEditorFromRule, ruleViewFromEditor,
} from './dispatchWorkspaceEditorModel';

const supportedStrategies = ['LOWEST_LOAD', 'WEIGHTED_SCORE', 'MANUAL_ONLY'] as const;

function defaultSimulationForm(flow?: CoreDispatchFlowView | null): SimulationFormState {
  const firstRule = activeRules(flow).find((rule) => rule.enabled !== false);
  const condition = firstRule?.condition ?? {};
  return {
    eventType: wildcardToBlank(firstRule?.eventType), objectType: wildcardToBlank(firstRule?.objectType), errorCode: wildcardToBlank(firstRule?.errorCode),
    severity: typeof condition.severity === 'string' ? condition.severity : 'CRITICAL', attributesJson: '{\n  "simulation": true\n}',
  };
}
function wildcardToBlank(value?: string | null): string { const text = String(value ?? '').trim(); return !text || text === '*' ? '' : text; }
function parseAttributesJson(value: string): Record<string, unknown> { const text=value.trim(); if(!text) return {}; const parsed=JSON.parse(text) as unknown; if(!parsed||typeof parsed!=='object'||Array.isArray(parsed)) throw new Error('Attributes must be a JSON object.'); return parsed as Record<string, unknown>; }
function apiErrorMessage(error: unknown, fallback: string): string { if (error instanceof ApiError) return error.message || fallback; if (error instanceof Error) return error.message; return fallback; }
function optimisticConflictMessage(error: unknown, resourceName: string): string | null { if (error instanceof ApiError && error.code === 'RESOURCE_VERSION_CONFLICT') return `${resourceName} Another administrator updated this resource. Reload it, review the differences, and save again.`; return null; }

export function useDispatchWorkspaceController({
  tenantId,
  flow,
  sourceSystems,
  pools,
  sourceLoading,
  sourceError,
  poolLoading,
  poolError,
  onReload,
  onSimulationResultChange,
  onRealTestResultChange,
}: Readonly<{
  tenantId: string;
  flow: CoreDispatchFlowView | null;
  sourceSystems: CoreSourceSystem[];
  pools: CoreAgentPoolView[];
  sourceLoading: boolean;
  sourceError?: string | null;
  poolLoading: boolean;
  poolError?: string | null;
  onReload: () => void;
  onSimulationResultChange?: (result: CoreDispatchSimulationResponse | null) => void;
  onRealTestResultChange?: (result: CoreEventIntakeDecisionResponse | null) => void;
}>) {
  const selectedSource = sourceSystems.find((source) => source.sourceSystemId === flow?.sourceSystem);
  const defaultPool = pools.find((pool) => pool.poolId === flow?.defaultPoolId);
  const rules = activeRules(flow);
  const primaryRule = defaultRule(flow);
  const sourceState = sectionState(sourceLoading, sourceError ?? null, !flow?.sourceSystem);
  const poolState = sectionState(poolLoading, poolError ?? null, !flow?.defaultPoolId && rules.every((rule) => !rule.targetPoolId));

  const [agents, setAgents] = useState<CoreDispatchFlowAgentOptionView[]>([]);
  const [agentsLoaded, setAgentsLoaded] = useState(false);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [poolIntent, setPoolIntent] = useState<PoolEditorIntent>('defaultPool');
  const [poolEditorOpen, setPoolEditorOpen] = useState(false);
  const [poolEditor, setPoolEditor] = useState<PoolEditorState>(() => defaultPoolEditor());
  const [ruleEditorOpen, setRuleEditorOpen] = useState(false);
  const [ruleEditor, setRuleEditor] = useState<RuleEditorState>(() => ({ ruleCode: '', ruleName: '', serviceCode: '', priority: 100, eventType: '', objectType: '', errorCode: '', severity: '', targetPoolId: '', issueSyncPolicy: '', enabled: true }));
  const [simulationForm, setSimulationForm] = useState<SimulationFormState>(() => defaultSimulationForm(flow));
  const [simulationResult, setSimulationResult] = useState<CoreDispatchSimulationResponse | null>(null);
  const [simulationBusy, setSimulationBusy] = useState(false);
  const [simulationError, setSimulationError] = useState<string | null>(null);
  const [runtimeReadinessResult, setRuntimeReadinessResult] = useState<CoreDispatchSimulationResponse | null>(null);
  const [runtimeReadinessBusy, setRuntimeReadinessBusy] = useState(false);
  const [runtimeReadinessError, setRuntimeReadinessError] = useState<string | null>(null);
  const [realTestResult, setRealTestResult] = useState<CoreEventIntakeDecisionResponse | null>(null);
  const [realTestBusy, setRealTestBusy] = useState(false);
  const [realTestError, setRealTestError] = useState<string | null>(null);
  const [capabilityCatalog, setCapabilityCatalog] = useState<CoreAgentCapabilityCatalog[]>([]);
  const [canonicalCapabilities, setCanonicalCapabilities] = useState<CoreCanonicalCapabilityDefinition[]>([]);
  const [pendingCapabilityCode, setPendingCapabilityCode] = useState('');
  const [capabilityAssignmentsByAgent, setCapabilityAssignmentsByAgent] = useState<Record<string, CoreAgentCapabilityAssignment[]>>({});
  const [qualityByAgent, setQualityByAgent] = useState<Record<string, CoreAgentQualityMetricsWindow[]>>({});
  const [capabilityLookupLoading, setCapabilityLookupLoading] = useState(false);
  const [capabilityLookupError, setCapabilityLookupError] = useState<string | null>(null);
  const [ruleConflicts, setRuleConflicts] = useState<CoreDispatchFlowRuleConflictView[]>([]);
  const [ruleConflictError, setRuleConflictError] = useState<string | null>(null);
  const [compatibilityBridge, setCompatibilityBridge] = useState<CoreFlowDirectAgentCompatibilityBinding[]>([]);
  const [equivalenceEvidence, setEquivalenceEvidence] = useState<CoreFlowCapabilityLegacyEquivalenceEvidence[]>([]);
  const [equivalenceReadiness, setEquivalenceReadiness] = useState<CoreFlowCapabilityEquivalenceReadiness | null>(null);
  const [migrationLoading, setMigrationLoading] = useState(false);
  const [migrationError, setMigrationError] = useState<string | null>(null);
  const [advancedDiagnosticsOpen, setAdvancedDiagnosticsOpen] = useState(false);
  const [sourceRegistrations, setSourceRegistrations] = useState<CoreWorkloadSourceRegistration[]>([]);

  const scopedTenantId = tenantId.trim();

  useEffect(() => {
    let active = true;
    if (!scopedTenantId || !flow?.sourceSystem) { setSourceRegistrations([]); return () => { active = false; }; }
    void sourceSystemsAdminApi.getSourceRegistrations(scopedTenantId, flow.sourceSystem)
      .then((rows) => { if (active) setSourceRegistrations(rows.filter((row) => String(row.status ?? 'ACTIVE').toUpperCase() === 'ACTIVE')); })
      .catch(() => { if (active) setSourceRegistrations([]); });
    return () => { active = false; };
  }, [flow?.sourceSystem, scopedTenantId]);

  const loadAgents = useCallback(async () => {
    if (!scopedTenantId || agentsLoaded) return;
    try {
      setAgents(await dispatchAdminApi.getDispatchFlowAgentOptions(scopedTenantId));
      setAgentsLoaded(true);
    } catch (caught) {
      setActionError(apiErrorMessage(caught, 'Failed to load Agents.'));
    }
  }, [agentsLoaded, scopedTenantId]);


  const loadCanonicalCapabilityDefinitions = useCallback(async () => {
    if (!scopedTenantId) { setCanonicalCapabilities([]); return; }
    setCapabilityLookupError(null);
    try {
      setCanonicalCapabilities(await dispatchAdminApi.getCanonicalCapabilities('ACTIVE', undefined, undefined, scopedTenantId));
    } catch (caught) {
      setCapabilityLookupError(apiErrorMessage(caught, 'Capability Catalog failed to load.'));
      setCanonicalCapabilities([]);
    }
  }, [scopedTenantId]);

  const loadAdvancedDispatchEvidence = useCallback(async () => {
    if (!scopedTenantId || !advancedDiagnosticsOpen) return;
    setCapabilityLookupLoading(true);
    setCapabilityLookupError(null);
    try {
      const [catalog, currentAgents] = await Promise.all([
        dispatchAdminApi.getCapabilities('ACTIVE', undefined, scopedTenantId),
        agentsLoaded ? Promise.resolve(agents) : dispatchAdminApi.getDispatchFlowAgentOptions(scopedTenantId),
      ]);
      if (!agentsLoaded) { setAgents(currentAgents); setAgentsLoaded(true); }
      const [assignmentEntries, qualityEntries] = await Promise.all([
        Promise.allSettled(currentAgents.slice(0, 100).map(async (agent) => [agent.agentId, await dispatchAdminApi.getAgentCapabilities(agent.agentId)] as const)),
        Promise.allSettled(currentAgents.slice(0, 100).map(async (agent) => [agent.agentId, await dispatchAdminApi.getAgentQualityWindows(agent.agentId, '24h', scopedTenantId, 4)] as const)),
      ]);
      const nextAssignments: Record<string, CoreAgentCapabilityAssignment[]> = {};
      for (const entry of assignmentEntries) if (entry.status === 'fulfilled') nextAssignments[entry.value[0]] = entry.value[1];
      const nextQuality: Record<string, CoreAgentQualityMetricsWindow[]> = {};
      for (const entry of qualityEntries) if (entry.status === 'fulfilled') nextQuality[entry.value[0]] = entry.value[1];
      setCapabilityCatalog(catalog);
      setCapabilityAssignmentsByAgent(nextAssignments);
      setQualityByAgent(nextQuality);
    } catch (caught) {
      setCapabilityLookupError(apiErrorMessage(caught, 'Advanced dispatch evidence failed to load.'));
      setCapabilityCatalog([]);
      setCapabilityAssignmentsByAgent({});
      setQualityByAgent({});
    } finally { setCapabilityLookupLoading(false); }
  }, [advancedDiagnosticsOpen, agents, agentsLoaded, scopedTenantId]);

  useEffect(() => {
    if (flow?.flowId) void loadCanonicalCapabilityDefinitions();
  }, [flow?.flowId, loadCanonicalCapabilityDefinitions]);

  useEffect(() => {
    if (flow?.flowId && advancedDiagnosticsOpen) void loadAdvancedDispatchEvidence();
  }, [advancedDiagnosticsOpen, flow?.flowId, loadAdvancedDispatchEvidence]);

  useEffect(() => {
    let active = true;
    if (!flow?.flowId || !scopedTenantId) { setRuleConflicts([]); setRuleConflictError(null); return () => { active = false; }; }
    void dispatchAdminApi.getDispatchFlowRuleConflicts(flow.flowId, scopedTenantId)
      .then((items) => { if (active) { setRuleConflicts(items); setRuleConflictError(null); } })
      .catch((caught) => { if (active) { setRuleConflicts([]); setRuleConflictError(apiErrorMessage(caught, 'Rule conflict check failed.')); } });
    return () => { active = false; };
  }, [flow?.flowId, scopedTenantId]);

  const loadLegacyEquivalence = useCallback(async () => {
    if (!flow?.flowId || !scopedTenantId) { setCompatibilityBridge([]); setEquivalenceEvidence([]); setEquivalenceReadiness(null); return; }
    setMigrationLoading(true); setMigrationError(null);
    try {
      const [bridge, evidence, readiness] = await Promise.all([
        dispatchAdminApi.getDispatchFlowCapabilityBridge(flow.flowId, scopedTenantId),
        dispatchAdminApi.getDispatchFlowLegacyEquivalence(flow.flowId, scopedTenantId, 50),
        dispatchAdminApi.getDispatchFlowLegacyEquivalenceReadiness(flow.flowId, scopedTenantId),
      ]);
      setCompatibilityBridge(bridge); setEquivalenceEvidence(evidence); setEquivalenceReadiness(readiness);
    } catch (caught) {
      setMigrationError(apiErrorMessage(caught, 'Legacy equivalence evidence failed to load.'));
    } finally { setMigrationLoading(false); }
  }, [flow?.flowId, scopedTenantId]);

  useEffect(() => { if (advancedDiagnosticsOpen) void loadLegacyEquivalence(); }, [advancedDiagnosticsOpen, loadLegacyEquivalence]);

  useEffect(() => {
    if (poolEditorOpen) void loadAgents();
  }, [loadAgents, poolEditorOpen]);

  useEffect(() => {
    setSimulationForm(defaultSimulationForm(flow));
    setSimulationResult(null);
    setSimulationError(null);
    setRuntimeReadinessResult(null);
    setRuntimeReadinessError(null);
    setRealTestResult(null);
    setRealTestError(null);
    onSimulationResultChange?.(null);
    onRealTestResultChange?.(null);
  }, [flow, onRealTestResultChange, onSimulationResultChange]);

  if (!flow) return { hasFlow: false as const };


  const currentFlow = flow;
  const configurationIssues = flowHealthIssues(currentFlow, pools);
  const simulationIssues = flowSimulationIssues(currentFlow, pools);
  const lifecycleActivationIssues = flowActivationIssues(currentFlow);
  const activationReadinessIssues = flowActivationReadinessIssues(currentFlow, pools, simulationResult);
  const currentSimulation = simulationMatchesFlowVersion(currentFlow, simulationResult);
  const simulationReady = currentSimulation && simulationConfigurationPassed(simulationResult);
  const runtimeEvaluationCurrent = Boolean(
    runtimeReadinessResult
      && runtimeReadinessResult.evaluationMode === 'RUNTIME_READINESS'
      && (runtimeReadinessResult.flowVersion == null || currentFlow.version == null || String(runtimeReadinessResult.flowVersion) === String(currentFlow.version)),
  );
  const runtimeReady = runtimeEvaluationCurrent && simulationPassed(runtimeReadinessResult);
  const runtimeStatus = !isActiveStatus(currentFlow.status)
    ? 'NOT_ACTIVE'
    : !runtimeReadinessResult || !runtimeEvaluationCurrent
      ? 'UNKNOWN'
      : runtimeReady
        ? 'READY'
        : 'BLOCKED';

  const configuredEventTypes = [...new Set([...rules.map((rule) => wildcardToBlank(rule.eventType)), ...sourceRegistrations.flatMap((row) => row.allowedEventTypes ?? [])].map((value) => String(value ?? '').trim()).filter(Boolean))].sort();
  const configuredObjectTypes = [...new Set([...rules.map((rule) => wildcardToBlank(rule.objectType)), ...sourceRegistrations.flatMap((row) => row.allowedObjectTypes ?? [])].map((value) => String(value ?? '').trim()).filter(Boolean))].sort();
  const configuredErrorCodes = [...new Set(rules.map((rule) => wildcardToBlank(rule.errorCode)).filter(Boolean))].sort();
  const severityOptions = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'];

  async function refreshCompatibilityBridge() {
    if (!scopedTenantId || !currentFlow.flowId) return;
    setMigrationLoading(true); setMigrationError(null);
    try { await dispatchAdminApi.refreshDispatchFlowCapabilityBridge(currentFlow.flowId, scopedTenantId); await loadLegacyEquivalence(); setMessage('Historical compatibility evidence refreshed. It has no production dispatch authority.'); }
    catch (caught) { setMigrationError(apiErrorMessage(caught, 'Compatibility bridge refresh failed.')); }
    finally { setMigrationLoading(false); }
  }

  async function backfillLegacyEquivalence() {
    if (!scopedTenantId || !currentFlow.flowId) return;
    setMigrationLoading(true); setMigrationError(null);
    try { await dispatchAdminApi.backfillDispatchFlowLegacyEquivalence(currentFlow.flowId, scopedTenantId, 250); await loadLegacyEquivalence(); setMessage('Historical migration evidence queued. It is diagnostics-only and cannot dispatch work.'); }
    catch (caught) { setMigrationError(apiErrorMessage(caught, 'Legacy-equivalence backfill failed.')); }
    finally { setMigrationLoading(false); }
  }

  async function saveFlowPatch(patch: Partial<CoreDispatchFlowView>, successText: string, evidenceMutationSource = 'ADMIN_UI_DISPATCH_WORKSPACE_PATCH') {
    if (!scopedTenantId) { setActionError('Select a Workspace first.'); return; }
    setBusy(true); setActionError(null); setMessage(null);
    try {
      await dispatchAdminApi.updateDispatchFlow(currentFlow.flowId, { ...currentFlow, ...patch, tenantId: scopedTenantId, evidenceMutationSource }, scopedTenantId);
      setMessage(successText);
      onReload();
    } catch (caught) {
      setActionError(optimisticConflictMessage(caught, 'Source Flow') ?? apiErrorMessage(caught, 'Source Flow Save failed.'));
    } finally {
      setBusy(false);
    }
  }

  function currentRequiredCapabilityCodes(): string[] {
    const rows = currentFlow.requiredCapabilities ?? currentFlow.requiredSkills ?? [];
    return [...new Set(rows
      .filter((row) => row.required !== false)
      .map((row) => String(row.capabilityCode ?? row.skillCode ?? '').trim())
      .filter(Boolean))];
  }

  function capabilityRequirementRows(codes: string[]): CoreDispatchFlowRequiredCapabilityView[] {
    const existing = currentFlow.requiredCapabilities ?? currentFlow.requiredSkills ?? [];
    return codes.map((code) => {
      const prior = existing.find((row) => String(row.capabilityCode ?? row.skillCode ?? '').toLowerCase() === code.toLowerCase());
      const definition = canonicalCapabilities.find((item) => item.capabilityCode.toLowerCase() === code.toLowerCase());
      return {
        ...prior,
        tenantId: scopedTenantId,
        flowId: currentFlow.flowId,
        eventStage: prior?.eventStage ?? 'EXTERNAL',
        agentRole: prior?.agentRole ?? 'LEAD',
        capabilityCode: code,
        capabilityName: definition?.displayName ?? prior?.capabilityName ?? prior?.skillName ?? code,
        capabilityKind: definition?.capabilityType ?? prior?.capabilityKind ?? prior?.skillKind ?? 'SERVICE',
        required: true,
        description: definition?.description ?? prior?.description,
        skillCode: code,
        skillName: definition?.displayName ?? prior?.skillName ?? code,
        skillKind: definition?.capabilityType ?? prior?.skillKind ?? 'SERVICE',
      };
    });
  }

  async function saveRequiredCapabilityCodes(codes: string[], successText: string) {
    const normalized = [...new Set(codes.map((code) => code.trim()).filter(Boolean))];
    const rows = capabilityRequirementRows(normalized);
    await saveFlowPatch({
      requiredCapabilities: rows,
      requiredSkills: rows,
      metadata: { ...(currentFlow.metadata ?? {}), legacyChildrenPreserved: false, canonicalCapabilityAuthority: true },
    }, successText);
  }

  async function addRequiredCapability() {
    const code = pendingCapabilityCode.trim();
    if (!code) return;
    const next = [...currentRequiredCapabilityCodes(), code];
    setPendingCapabilityCode('');
    await saveRequiredCapabilityCodes(next, `Required Capability ${code} added to this Source Flow.`);
  }

  async function removeRequiredCapability(code: string) {
    const next = currentRequiredCapabilityCodes().filter((item) => item.toLowerCase() !== code.toLowerCase());
    await saveRequiredCapabilityCodes(next, `Required Capability ${code} removed from this Source Flow.`);
  }

  async function refreshCanonicalCapabilities(created?: CoreCanonicalCapabilityDefinition) {
    if (!scopedTenantId) return;
    const latest = await dispatchAdminApi.getCanonicalCapabilities('ACTIVE', undefined, undefined, scopedTenantId);
    setCanonicalCapabilities(latest);
    if (created) setPendingCapabilityCode(created.capabilityCode);
  }

  async function handleDefaultPoolChange(poolId: string) {
    await saveFlowPatch({ defaultPoolId: poolId || undefined }, poolId ? 'Default Pool update.' : 'Default Pool ');
  }

  async function handleIssueSyncPolicyChange(issueSyncPolicy: string) {
    const normalized = ['NONE', 'OPTIONAL', 'REQUIRED', 'MANUAL'].includes(issueSyncPolicy) ? issueSyncPolicy : 'OPTIONAL';
    await saveFlowPatch({ defaultIssueSyncPolicy: normalized }, `External Issue behavior updated to ${normalized}.`, 'ADMIN_UI_ISSUE_POLICY_CHANGE');
  }

  function openCreatePool(intent: PoolEditorIntent) {
    setPoolIntent(intent);
    setPoolEditor(defaultPoolEditor(currentFlow.sourceSystem));
    setPoolEditorOpen(true);
    setActionError(null);
  }

  function openEditPool(pool: CoreAgentPoolView, intent: PoolEditorIntent) {
    setPoolIntent(intent);
    setPoolEditor(poolEditorFromPool(pool));
    setPoolEditorOpen(true);
    setActionError(null);
  }

  async function savePool() {
    if (!scopedTenantId) { setActionError('Select a Workspace first.'); return; }
    const normalizedPoolCode = normalizeCode(poolEditor.poolCode);
    if (!normalizedPoolCode) { setActionError('Pool Code is required.'); return; }
    const duplicatePool = pools.find((pool) => normalizeCode(pool.poolCode) === normalizedPoolCode && pool.poolId !== poolEditor.poolId);
    if (duplicatePool) {
      setActionError(`Agent Pool code ${normalizedPoolCode} already exists. Open the existing Agent Pool and edit it instead of creating another pool with the same code.`);
      return;
    }
    if (!poolEditor.poolName.trim()) { setActionError('Pool name is required.'); return; }
    if (!supportedStrategies.some((strategy) => strategy === poolEditor.selectionStrategy)) { setActionError('Select a supported Pool selection strategy.'); return; }
    setBusy(true); setActionError(null); setMessage(null);
    try {
      const body = poolPayload(poolEditor, scopedTenantId, agents);
      const saved = poolEditor.poolId
        ? await dispatchAdminApi.updateAgentPool(poolEditor.poolId, body, scopedTenantId)
        : await dispatchAdminApi.createAgentPool(body, scopedTenantId);
      if (poolIntent === 'defaultPool' && currentFlow.defaultPoolId !== saved.poolId) {
        await dispatchAdminApi.updateDispatchFlow(currentFlow.flowId, { ...currentFlow, tenantId: scopedTenantId, defaultPoolId: saved.poolId }, scopedTenantId);
      }
      setPoolEditorOpen(false);
      setMessage(poolIntent === 'defaultPool'
        ? 'Agent Pool saved and assigned as the Default Pool. Save the Flow, then run Dispatch Simulation.'
        : 'Agent Pool saved.');
      onReload();
    } catch (caught) {
      setActionError(optimisticConflictMessage(caught, 'Agent Pool') ?? apiErrorMessage(caught, 'Agent Pool Save failed.'));
    } finally {
      setBusy(false);
    }
  }

  function openCreateRule() {
    setRuleEditor(emptyRuleEditor(currentFlow, pools));
    setRuleEditorOpen(true);
    setActionError(null);
  }

  function openEditRule(rule: CoreDispatchFlowRuleView) {
    setRuleEditor(ruleEditorFromRule(rule));
    setRuleEditorOpen(true);
    setActionError(null);
  }

  async function saveRule() {
    if (!ruleEditor.targetPoolId) { setActionError('Select a target Agent Pool for this rule.'); return; }
    const nextRule = ruleViewFromEditor(ruleEditor, currentFlow);
    const currentRules = currentFlow.rules ?? [];
    const nextRules = currentRules.some((rule) => (rule.ruleId && rule.ruleId === nextRule.ruleId) || (rule.ruleCode && rule.ruleCode === nextRule.ruleCode))
      ? currentRules.map((rule) => ((rule.ruleId && rule.ruleId === nextRule.ruleId) || (rule.ruleCode && rule.ruleCode === nextRule.ruleCode)) ? { ...rule, ...nextRule } : rule)
      : [...currentRules, nextRule];
    await saveFlowPatch({ rules: nextRules }, 'Classification rule saved.', 'ADMIN_UI_RULE_SAVE');
    setRuleEditorOpen(false);
  }

  async function runDispatchSimulation() {
    if (!scopedTenantId) { setSimulationError('Select a Workspace first.'); return; }
    if (!currentFlow.sourceSystem) { setSimulationError('The Source Flow does not specify a Source System.'); return; }
    if (simulationIssues.length > 0) { setSimulationError(`Complete the Draft Simulation prerequisites: ${simulationIssues.join(';')}`); return; }
    setSimulationBusy(true);
    setSimulationError(null);
    setSimulationResult(null);
    setRuntimeReadinessResult(null);
    setRuntimeReadinessError(null);
    try {
      const attributes = parseAttributesJson(simulationForm.attributesJson);
      const result = await dispatchAdminApi.simulateDispatch({
        tenantId: scopedTenantId,
        flowId: currentFlow.flowId,
        sourceSystem: currentFlow.sourceSystem,
        eventStage: 'EXTERNAL',
        eventType: simulationForm.eventType.trim() || undefined,
        objectType: simulationForm.objectType.trim() || undefined,
        errorCode: simulationForm.errorCode.trim() || undefined,
        severity: simulationForm.severity.trim() || undefined,
        message: `OpenDispatch no-side-effect Draft Simulation for ${flowDisplay(currentFlow)}`,
        attributes,
        includeRuntimeSnapshot: true,
        evaluationMode: 'DRAFT_SIMULATION',
      }, scopedTenantId);
      setSimulationResult(result);
      onSimulationResultChange?.(result);
    } catch (caught) {
      setSimulationError(apiErrorMessage(caught, 'Draft Simulation failed.'));
    } finally {
      setSimulationBusy(false);
    }
  }

  async function refreshRuntimeReadiness() {
    if (!scopedTenantId) { setRuntimeReadinessError('Select a Workspace first.'); return; }
    if (!isActiveStatus(currentFlow.status)) { setRuntimeReadinessError('Activate the Source Flow before checking runtime executability.'); return; }
    if (!currentFlow.sourceSystem) { setRuntimeReadinessError('The Source Flow does not specify a Source System.'); return; }
    setRuntimeReadinessBusy(true);
    setRuntimeReadinessError(null);
    setRuntimeReadinessResult(null);
    try {
      const attributes = parseAttributesJson(simulationForm.attributesJson);
      const result = await dispatchAdminApi.simulateDispatch({
        tenantId: scopedTenantId,
        flowId: currentFlow.flowId,
        sourceSystem: currentFlow.sourceSystem,
        eventStage: 'EXTERNAL',
        eventType: simulationForm.eventType.trim() || undefined,
        objectType: simulationForm.objectType.trim() || undefined,
        errorCode: simulationForm.errorCode.trim() || undefined,
        severity: simulationForm.severity.trim() || undefined,
        message: `OpenDispatch authoritative runtime readiness for ${flowDisplay(currentFlow)}`,
        attributes,
        includeRuntimeSnapshot: true,
        evaluationMode: 'RUNTIME_READINESS',
      }, scopedTenantId);
      setRuntimeReadinessResult(result);
    } catch (caught) {
      setRuntimeReadinessError(apiErrorMessage(caught, 'Core runtime readiness check failed.'));
    } finally {
      setRuntimeReadinessBusy(false);
    }
  }

  async function runRealTestEvent() {
    if (!scopedTenantId) { setRealTestError('Select a Workspace first.'); return; }
    if (!currentFlow.sourceSystem) { setRealTestError('The Source Flow does not specify a Source System.'); return; }
    if (!isActiveStatus(currentFlow.status)) { setRealTestError('Activate the Source Flow before sending a governed real test event.'); return; }
    if (!runtimeReadinessResult || !runtimeEvaluationCurrent) { setRealTestError('Refresh Core Runtime Readiness for the current Flow version before the governed real test.'); return; }
    if (!runtimeReady) { setRealTestError(`Runtime readiness is blocked: ${runtimeReadinessResult.blockerReason ?? runtimeReadinessResult.blockerCode ?? 'no executable path'}`); return; }
    setRealTestBusy(true);
    setRealTestError(null);
    setRealTestResult(null);
    onRealTestResultChange?.(null);
    try {
      const attributes = parseAttributesJson(simulationForm.attributesJson);
      const effectiveIssueSyncPolicy = String(primaryRule?.issueSyncPolicy || currentFlow.defaultIssueSyncPolicy || 'OPTIONAL').trim().toUpperCase();
      const result = await dispatchAdminApi.createDispatchFlowRealTestEvent(currentFlow.flowId, {
        message: `OpenDispatch real test event for ${flowDisplay(currentFlow)}`,
        severity: simulationForm.severity.trim() || 'INFO',
        eventType: simulationForm.eventType.trim() || undefined,
        objectType: simulationForm.objectType.trim() || undefined,
        errorCode: simulationForm.errorCode.trim() || undefined,
        objectId: `REAL-TEST-${Date.now()}`,
        correlationId: generateId('corr'),
        expectedIssueSyncPolicy: effectiveIssueSyncPolicy,
        expectExternalIssueOnSuccess: effectiveIssueSyncPolicy === 'REQUIRED',
        attributes: {
          ...attributes,
          adminUiTest: true,
          setupJourney: 'BEGINNER_JOURNEY_REAL_TEST',
        },
      }, scopedTenantId);
      setRealTestResult(result);
      onRealTestResultChange?.(result);
    } catch (caught) {
      setRealTestError(apiErrorMessage(caught, 'Failed to send the test event.'));
    } finally {
      setRealTestBusy(false);
    }
  }

  async function setFlowStatus(status: string) {
    if (isActiveStatus(status) && activationReadinessIssues.length > 0) {
      setActionError(`Activation is blocked: ${activationReadinessIssues.join(';')}`);
      return;
    }
    setRuntimeReadinessResult(null);
    setRuntimeReadinessError(null);
    await saveFlowPatch({ status }, isActiveStatus(status) ? 'Source Flow activated. Core revalidated durable routing configuration before commit; runtime readiness remains operational.' : 'Source Flow saved as draft.');
  }


  return {
    hasFlow: true as const,
    selectedSource, defaultPool, rules, primaryRule, sourceState, poolState, scopedTenantId,
    agents, busy, message, actionError, poolIntent, poolEditorOpen, poolEditor, ruleEditorOpen, ruleEditor,
    simulationForm, simulationResult, simulationBusy, simulationError, runtimeReadinessResult, runtimeReadinessBusy, runtimeReadinessError,
    realTestResult, realTestBusy, realTestError, capabilityCatalog, canonicalCapabilities, pendingCapabilityCode,
    capabilityAssignmentsByAgent, qualityByAgent, capabilityLookupLoading, capabilityLookupError, ruleConflicts, ruleConflictError,
    compatibilityBridge, equivalenceEvidence, equivalenceReadiness, migrationLoading, migrationError, advancedDiagnosticsOpen,
    currentFlow, configurationIssues, simulationIssues, lifecycleActivationIssues, activationReadinessIssues, currentSimulation, simulationReady,
    runtimeEvaluationCurrent, runtimeReady, runtimeStatus, configuredEventTypes, configuredObjectTypes, configuredErrorCodes, severityOptions,
    setPendingCapabilityCode, setSimulationForm, setAdvancedDiagnosticsOpen, setPoolEditor, setPoolEditorOpen, setRuleEditor, setRuleEditorOpen,
    refreshCompatibilityBridge, backfillLegacyEquivalence, currentRequiredCapabilityCodes, addRequiredCapability, removeRequiredCapability,
    refreshCanonicalCapabilities, handleDefaultPoolChange, handleIssueSyncPolicyChange, openCreatePool, openEditPool, savePool, openCreateRule, openEditRule, saveRule,
    runDispatchSimulation, refreshRuntimeReadiness, runRealTestEvent, setFlowStatus,
  };
}
