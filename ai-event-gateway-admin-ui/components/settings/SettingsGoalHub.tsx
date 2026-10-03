'use client';

import Link from 'next/link';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/components/auth/AuthProvider';
import { coreAdminApi } from '@/lib/api/coreAdminApi';
import { listConnections, listProjectMappings } from '@/lib/api/domains/integrationIdentityApi';
import { sourceSystemsAdminApi } from '@/lib/api/domains/sourceSystemsAdminApi';
import { canAccessAdminUiMode, getAdminUiModeOption, type AdminUiMode } from '@/lib/navigation/adminUiMode';
import { actionAllowed, featureDisplayMode, type UiDisplayMode } from '@/lib/navigation/uiEntitlements';
import { useUiEntitlements } from '@/lib/navigation/useUiEntitlements';
import { useAdminUiMode } from '@/hooks/useAdminUiMode';

type GoalState = 'CONFIGURED' | 'ATTENTION' | 'SETUP_REQUIRED' | 'AVAILABLE' | 'READ_ONLY' | 'LIMITED_VISIBILITY' | 'CHECK_UNAVAILABLE';
type GoalTone = 'ready' | 'warning' | 'info' | 'neutral';

type GoalHealth = {
  state: GoalState;
  summary: string;
  detail?: string;
};

type GoalLink = {
  href: string;
  label: string;
  featureId?: string;
};

type SettingsGoal = {
  id: string;
  title: string;
  purpose: string;
  featureIds: string[];
  requiredMode?: AdminUiMode;
  primary: GoalLink;
  related?: GoalLink[];
  whyOpen: string;
  impact: string;
  staticHealth?: GoalHealth;
};

type DispatchHealth = {
  loading: boolean;
  error?: string;
  sourceCount?: number;
  activeFlowCount?: number;
  approvedAgentCount?: number;
  completeVisibility: boolean;
};

type IntegrationHealth = {
  loading: boolean;
  error?: string;
  connectionCount?: number;
  enabledConnectionCount?: number;
  activeMappingCount?: number;
};

const GOALS: SettingsGoal[] = [
  {
    id: 'dispatch-setup',
    title: 'Get work flowing from a Source System',
    purpose: 'Register the business source, define how new work should be dispatched, and make approved Agents available to receive it.',
    featureIds: ['source-systems', 'dispatch', 'agents'],
    primary: { href: '/dispatch-flows', label: 'Review Dispatch setup', featureId: 'dispatch' },
    related: [
      { href: '/source-systems', label: 'Source Systems', featureId: 'source-systems' },
      { href: '/agents', label: 'Agents', featureId: 'agents' },
    ],
    whyOpen: 'Use this when a new system must submit work, a task has no candidate, or a Flow needs to be activated or corrected.',
    impact: 'Activation and routing changes affect new matching work. The target workspace provides simulation and readiness checks before governed live testing.',
  },
  {
    id: 'issue-tracking',
    title: 'Connect Issue Tracking',
    purpose: 'Connect Redmine or Jira and map OpenDispatch Source/Task contexts to the correct external project without storing provider setup in multiple places.',
    featureIds: ['integrations'],
    primary: { href: '/settings/integrations', label: 'Review Issue Tracking', featureId: 'integrations' },
    whyOpen: 'Use this when Tasks cannot create or synchronize external Issues, credentials change, or a Source requires a different project mapping.',
    impact: 'Connection changes can affect every ACTIVE mapping that uses that provider. Mapping publication is governed separately from draft editing and validation.',
  },
  {
    id: 'delegation-routing',
    title: 'Control what Agents may do and who should receive delegated work',
    purpose: 'Keep capability definition, authorization, provider selection, and execution protocol as separate governed decisions.',
    featureIds: ['delegation-governance', 'provider-routing'],
    requiredMode: 'advanced',
    primary: { href: '/settings/delegation-governance', label: 'Review delegation rules', featureId: 'delegation-governance' },
    related: [
      { href: '/settings/capabilities', label: 'Capabilities', featureId: 'administration' },
      { href: '/settings/provider-routing', label: 'Provider Routing', featureId: 'provider-routing' },
      { href: '/settings/execution-adapters', label: 'Execution Adapters', featureId: 'administration' },
    ],
    whyOpen: 'Use this when an Agent is capable of work but is not authorized, the wrong provider is selected, or the selected provider cannot be reached through the expected protocol.',
    impact: 'These controls can change future eligibility and execution selection. Review each target page before applying authority changes.',
    staticHealth: { state: 'AVAILABLE', summary: 'Governance controls are available.', detail: 'OpenDispatch does not infer delegation readiness from menu availability.' },
  },
  {
    id: 'execution-safety',
    title: 'Keep execution safe and recoverable',
    purpose: 'Manage leases, fencing and durable execution controls so retries and delivery recovery do not create unsafe duplicate work.',
    featureIds: ['administration'],
    requiredMode: 'advanced',
    primary: { href: '/settings/execution-safety', label: 'Review execution safety', featureId: 'administration' },
    related: [
      { href: '/agents/runtime', label: 'Agent Runtime Resources', featureId: 'agents' },
    ],
    whyOpen: 'Use this when execution recovery, duplicate-delivery protection, or DispatchIntent safety needs investigation or policy changes.',
    impact: 'Safety controls affect execution behavior rather than business routing intent. Review the page impact and recovery guidance before changing them.',
    staticHealth: { state: 'AVAILABLE', summary: 'Safety controls are available.', detail: 'No runtime safety claim is made until the target surface reports evidence.' },
  },
  {
    id: 'advanced-planning',
    title: 'Design advanced planning, learning and Case convergence',
    purpose: 'Use semantic triage, multi-step plans, learned Fast Paths and canonical Case convergence without bypassing current authorization or provider-routing authority.',
    featureIds: ['administration'],
    requiredMode: 'advanced',
    primary: { href: '/settings/semantic-triage', label: 'Review Semantic Triage', featureId: 'administration' },
    related: [
      { href: '/settings/execution-plans', label: 'Execution Plans', featureId: 'administration' },
      { href: '/settings/plan-executions', label: 'Plan Execution', featureId: 'administration' },
      { href: '/settings/runtime-step-authority', label: 'Runtime Step Authority', featureId: 'administration' },
      { href: '/settings/learning-governance', label: 'Learning Governance', featureId: 'administration' },
      { href: '/settings/learning-fast-path', label: 'Learning Fast Path', featureId: 'administration' },
      { href: '/settings/case-convergence', label: 'Case Convergence', featureId: 'administration' },
    ],
    whyOpen: 'Use this only when the standard single-task dispatch model is not sufficient and you are deliberately designing planning, learning or enterprise Case behavior.',
    impact: 'These controls shape advanced execution semantics. They do not grant capability authorization, provider eligibility or production release authority by themselves.',
    staticHealth: { state: 'AVAILABLE', summary: 'Advanced planning controls are available.', detail: 'Availability is not evidence that a learned or planned path is production-ready.' },
  },
  {
    id: 'runtime-configuration',
    title: 'Tune platform runtime behavior',
    purpose: 'Change governed environment-level runtime parameters through the platform configuration lifecycle instead of editing deployed YAML or environment values.',
    featureIds: ['runtime-configuration'],
    requiredMode: 'advanced',
    primary: { href: '/settings/runtime-configuration', label: 'Review Runtime Configuration', featureId: 'runtime-configuration' },
    whyOpen: 'Use this for environment-level runtime tuning that requires controlled edit, approval, publication, applied-state evidence, or rollback.',
    impact: 'Published runtime configuration can affect platform behavior. Use its approval, impact, publication and rollback controls instead of direct file edits.',
    staticHealth: { state: 'AVAILABLE', summary: 'Platform configuration is available for this workspace.', detail: 'Open the workspace to inspect publication and applied-state evidence.' },
  },
  {
    id: 'integration-recovery',
    title: 'Recover Issue synchronization',
    purpose: 'Inspect projection queues, Webhooks, conflicts and Dead Letters when external Issue synchronization needs operational intervention.',
    featureIds: ['sync-operations'],
    requiredMode: 'advanced',
    primary: { href: '/operations/integration-sync', label: 'Open Integration Recovery', featureId: 'sync-operations' },
    whyOpen: 'Use this after the provider configuration is correct but synchronization is delayed, conflicted, or has entered a Dead Letter path.',
    impact: 'Recovery operations act on synchronization state. They do not replace provider Connection or Project Mapping authority.',
    staticHealth: { state: 'AVAILABLE', summary: 'Recovery workspace is available.', detail: 'Operational queue health is evaluated inside the recovery workspace.' },
  },
  {
    id: 'release-certification',
    title: 'Prepare a production release or controlled cutover',
    purpose: 'Review runtime proof and E2E evidence, certify a release candidate, and promote or roll back explicit Flows from one governed workspace.',
    featureIds: ['release-certification'],
    requiredMode: 'advanced',
    primary: { href: '/settings/release-certification', label: 'Review Release Certification', featureId: 'release-certification' },
    whyOpen: 'Use this only when preparing a release candidate, validating cutover evidence, promoting a Flow, or performing a governed rollback.',
    impact: 'Release and Flow cutover actions can affect production behavior and require dedicated release permissions in the target workspace.',
    staticHealth: { state: 'AVAILABLE', summary: 'Release workspace is available.', detail: 'Certification readiness is evaluated from evidence inside the release workspace.' },
  },
  {
    id: 'engineering-diagnostics',
    title: 'Diagnose migration, compatibility and low-level runtime behavior',
    purpose: 'Inspect internal runtime, migration, enforcement and compatibility evidence without mixing diagnostic controls into everyday administration.',
    featureIds: ['administration', 'engineering-tools'],
    requiredMode: 'developer',
    primary: { href: '/engineering-tools/runtime-diagnostics', label: 'Open Runtime Diagnostics', featureId: 'engineering-tools' },
    related: [
      { href: '/settings/runtime-features', label: 'Runtime Features', featureId: 'administration' },
      { href: '/settings/runtime-resources', label: 'Global Runtime Resources', featureId: 'administration' },
      { href: '/settings/fast-path-runtime', label: 'Fast Path Runtime', featureId: 'administration' },
      { href: '/settings/routing-authority', label: 'Routing Authority Cutover', featureId: 'administration' },
      { href: '/settings/migration-readiness', label: 'Migration Readiness', featureId: 'administration' },
      { href: '/settings/enforce-observability', label: 'Enforcement Observability', featureId: 'administration' },
      { href: '/settings/quality-metrics', label: 'Quality Metrics', featureId: 'administration' },
    ],
    whyOpen: 'Use this only after product-level configuration and operational evidence are insufficient to explain the problem.',
    impact: 'Diagnostic surfaces expose implementation-level evidence. They should not become a parallel configuration authority.',
    staticHealth: { state: 'AVAILABLE', summary: 'Engineering evidence is available.', detail: 'Developer Mode does not imply that a production configuration change is required.' },
  },
];

function toneFor(state: GoalState): GoalTone {
  if (state === 'CONFIGURED') return 'ready';
  if (state === 'ATTENTION' || state === 'SETUP_REQUIRED' || state === 'CHECK_UNAVAILABLE') return 'warning';
  if (state === 'AVAILABLE') return 'info';
  return 'neutral';
}

function stateLabel(state: GoalState): string {
  switch (state) {
    case 'CONFIGURED': return 'Configured';
    case 'ATTENTION': return 'Attention required';
    case 'SETUP_REQUIRED': return 'Setup required';
    case 'AVAILABLE': return 'Available';
    case 'READ_ONLY': return 'Read only';
    case 'LIMITED_VISIBILITY': return 'Limited visibility';
    case 'CHECK_UNAVAILABLE': return 'Check unavailable';
  }
}

const toneClasses: Record<GoalTone, string> = {
  ready: 'border-emerald-200 bg-emerald-50 text-emerald-950',
  warning: 'border-amber-200 bg-amber-50 text-amber-950',
  info: 'border-blue-200 bg-blue-50 text-blue-950',
  neutral: 'border-slate-200 bg-slate-50 text-slate-800',
};

function SettingsModeControl() {
  const { mode, setMode } = useAdminUiMode();
  const modes: AdminUiMode[] = ['basic', 'advanced', 'developer'];
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <div className="text-xs font-black uppercase tracking-wide text-slate-500">Console information level</div>
          <div className="mt-1 text-sm font-black text-slate-950">Use the same global UI level shown in the top bar.</div>
          <p className="mt-1 max-w-3xl text-xs leading-5 text-slate-600">Changing this view only changes navigation and information density. It does not grant permissions or change runtime behavior. The selection applies across Dashboard, Settings, Task investigation and other mode-aware surfaces.</p>
        </div>
        <div className="flex flex-wrap gap-2" role="group" aria-label="Global Admin UI mode">
          {modes.map((value) => {
            const option = getAdminUiModeOption(value);
            const selected = value === mode;
            return (
              <button
                key={value}
                type="button"
                onClick={() => setMode(value)}
                aria-pressed={selected}
                title={option.description}
                className={`rounded-full border px-3 py-2 text-xs font-black ${selected ? 'border-blue-700 bg-blue-700 text-white' : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-50'}`}
              >
                {option.shortLabel}
              </button>
            );
          })}
        </div>
      </div>
    </div>
  );
}

function GoalStatus({ health }: Readonly<{ health: GoalHealth }>) {
  const tone = toneFor(health.state);
  return (
    <div className={`rounded-xl border p-3 ${toneClasses[tone]}`}>
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="text-xs font-black uppercase tracking-wide opacity-70">Current state</div>
        <span className="rounded-full border border-current/20 bg-white/70 px-2.5 py-1 text-[11px] font-black">{stateLabel(health.state)}</span>
      </div>
      <div className="mt-2 text-sm font-black">{health.summary}</div>
      {health.detail ? <p className="mt-1 text-xs leading-5 opacity-80">{health.detail}</p> : null}
    </div>
  );
}

function GoalCard({ goal, health, mode, displayMode, links }: Readonly<{ goal: SettingsGoal; health: GoalHealth; mode: AdminUiMode; displayMode: UiDisplayMode; links: GoalLink[] }>) {
  const primary = links[0] ?? goal.primary;
  const related = links.slice(1);
  const primaryLabel = displayMode === 'READ_ONLY' ? `Review ${primary.label.replace(/^Review\s+/i, '')}` : primary.label;
  return (
    <article className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">
        <div className="max-w-3xl">
          <div className="text-xs font-black uppercase tracking-wide text-blue-700">Management goal</div>
          <h3 className="mt-1 text-xl font-black text-slate-950">{goal.title}</h3>
          <p className="mt-2 text-sm leading-6 text-slate-600">{goal.purpose}</p>
        </div>
        <div className="min-w-[250px] xl:max-w-sm"><GoalStatus health={health} /></div>
      </div>

      <div className="mt-4 grid gap-3 lg:grid-cols-2">
        <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
          <div className="text-xs font-black uppercase tracking-wide text-slate-500">When should I open this?</div>
          <p className="mt-2 text-sm leading-6 text-slate-700">{goal.whyOpen}</p>
        </div>
        <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
          <div className="text-xs font-black uppercase tracking-wide text-slate-500">What could change?</div>
          <p className="mt-2 text-sm leading-6 text-slate-700">{goal.impact}</p>
        </div>
      </div>

      <div className="mt-4 flex flex-wrap items-center gap-2">
        <Link href={primary.href} className="rounded-xl bg-slate-950 px-4 py-2.5 text-sm font-black text-white hover:bg-slate-800">
          {primaryLabel} →
        </Link>
        {related.map((item) => (
          <Link key={`${goal.id}:${item.href}`} href={item.href} className="rounded-xl border border-slate-200 bg-white px-3 py-2 text-xs font-black text-slate-700 hover:bg-slate-50">
            {item.label}
          </Link>
        ))}
        {goal.requiredMode && goal.requiredMode !== 'basic' ? (
          <span className="ml-auto rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-[11px] font-black uppercase tracking-wide text-slate-500">{goal.requiredMode} view</span>
        ) : null}
      </div>
      {mode === 'developer' ? <p className="mt-3 text-[11px] leading-5 text-slate-500">Visibility comes from Core entitlement projection. This card does not authorize the underlying operation.</p> : null}
    </article>
  );
}

export function SettingsGoalHub() {
  const { activeTenantId } = useAuth();
  const entitlements = useUiEntitlements();
  const { mode } = useAdminUiMode();
  const [dispatchHealth, setDispatchHealth] = useState<DispatchHealth>({ loading: true, completeVisibility: false });
  const [integrationHealth, setIntegrationHealth] = useState<IntegrationHealth>({ loading: true });

  const pageMode = useCallback((featureIds: string[]): UiDisplayMode => {
    const modes = featureIds.map((featureId) => featureDisplayMode(entitlements.value, featureId));
    if (modes.includes('ENABLED')) return 'ENABLED';
    if (modes.includes('READ_ONLY')) return 'READ_ONLY';
    return 'HIDDEN';
  }, [entitlements.value]);

  useEffect(() => {
    if (entitlements.loading) return;
    const canSeeSource = featureDisplayMode(entitlements.value, 'source-systems') !== 'HIDDEN';
    const canSeeDispatch = featureDisplayMode(entitlements.value, 'dispatch') !== 'HIDDEN';
    const canSeeAgents = featureDisplayMode(entitlements.value, 'agents') !== 'HIDDEN';
    const completeVisibility = canSeeSource && canSeeDispatch && canSeeAgents;
    if (!canSeeSource && !canSeeDispatch && !canSeeAgents) {
      setDispatchHealth({ loading: false, completeVisibility: false });
      return;
    }
    let cancelled = false;
    setDispatchHealth({ loading: true, completeVisibility });
    Promise.all([
      canSeeSource ? sourceSystemsAdminApi.getSourceSystems(activeTenantId) : Promise.resolve(null),
      canSeeDispatch ? sourceSystemsAdminApi.getDispatchFlows(activeTenantId) : Promise.resolve(null),
      canSeeAgents ? coreAdminApi.getAgents() : Promise.resolve(null),
    ]).then(([sources, flows, agents]) => {
      if (cancelled) return;
      setDispatchHealth({
        loading: false,
        completeVisibility,
        sourceCount: sources?.filter((value) => String(value.status).toUpperCase() === 'ACTIVE').length,
        activeFlowCount: flows?.filter((value) => String(value.status ?? '').toUpperCase() === 'ACTIVE').length,
        approvedAgentCount: agents?.filter((value) => value.enabled && String(value.approvalStatus).toUpperCase() === 'APPROVED').length,
      });
    }).catch((error: unknown) => {
      if (!cancelled) setDispatchHealth({ loading: false, completeVisibility, error: error instanceof Error ? error.message : 'Configuration summary could not be loaded.' });
    });
    return () => { cancelled = true; };
  }, [activeTenantId, entitlements.loading, entitlements.value]);

  useEffect(() => {
    if (entitlements.loading) return;
    const canSee = featureDisplayMode(entitlements.value, 'integrations') !== 'HIDDEN';
    if (!canSee) {
      setIntegrationHealth({ loading: false });
      return;
    }
    let cancelled = false;
    setIntegrationHealth({ loading: true });
    Promise.all([listConnections(), listProjectMappings()]).then(([connections, mappings]) => {
      if (cancelled) return;
      setIntegrationHealth({
        loading: false,
        connectionCount: connections.length,
        enabledConnectionCount: connections.filter((value) => value.enabled === true && String(value.status ?? '').toUpperCase() !== 'DISABLED').length,
        activeMappingCount: mappings.filter((value) => value.lifecycleStatus === 'ACTIVE' && value.enabled !== false).length,
      });
    }).catch((error: unknown) => {
      if (!cancelled) setIntegrationHealth({ loading: false, error: error instanceof Error ? error.message : 'Integration summary could not be loaded.' });
    });
    return () => { cancelled = true; };
  }, [entitlements.loading, entitlements.value]);

  const dispatchSummary = useMemo<GoalHealth>(() => {
    const displayMode = pageMode(['source-systems', 'dispatch', 'agents']);
    if (displayMode === 'READ_ONLY') return { state: 'READ_ONLY', summary: 'You can review dispatch configuration but cannot apply all setup changes.', detail: 'OpenDispatch will continue to enforce action permissions in each target workspace.' };
    if (dispatchHealth.loading) return { state: 'AVAILABLE', summary: 'Checking configuration state…', detail: 'The hub reads existing Source System, Flow and Agent state without changing it.' };
    if (dispatchHealth.error) return { state: 'CHECK_UNAVAILABLE', summary: 'The configuration summary could not be verified.', detail: 'Open the target workspace for authoritative detail. No configuration change was attempted.' };
    if (!dispatchHealth.completeVisibility) return { state: 'LIMITED_VISIBILITY', summary: 'Your access covers only part of the dispatch setup.', detail: 'The hub will not infer missing Source System, Flow or Agent state that you are not authorized to read.' };
    const sources = dispatchHealth.sourceCount ?? 0;
    const flows = dispatchHealth.activeFlowCount ?? 0;
    const agents = dispatchHealth.approvedAgentCount ?? 0;
    if (sources === 0) return { state: 'SETUP_REQUIRED', summary: 'No active Source System is available.', detail: 'Start by registering the business source that will submit work.' };
    if (flows === 0) return { state: 'ATTENTION', summary: `${sources} active Source System${sources === 1 ? '' : 's'}, but no active Dispatch Flow.`, detail: 'Create or activate a Flow before expecting new work to route.' };
    if (agents === 0) return { state: 'ATTENTION', summary: `${flows} active Flow${flows === 1 ? '' : 's'}, but no approved enabled Agent was found.`, detail: 'Review Agent approval and runtime setup before governed live testing.' };
    return { state: 'CONFIGURED', summary: `${sources} active Source System${sources === 1 ? '' : 's'} · ${flows} active Flow${flows === 1 ? '' : 's'} · ${agents} approved enabled Agent${agents === 1 ? '' : 's'}.`, detail: 'Configured does not mean every Flow is runtime-ready. Verify Live Readiness inside the selected Flow before a real test.' };
  }, [dispatchHealth, pageMode]);

  const integrationSummary = useMemo<GoalHealth>(() => {
    const displayMode = pageMode(['integrations']);
    if (displayMode === 'READ_ONLY') return { state: 'READ_ONLY', summary: 'You can review Issue Tracking configuration.', detail: 'Provider changes require Integration management authority.' };
    if (integrationHealth.loading) return { state: 'AVAILABLE', summary: 'Checking provider configuration…', detail: 'The hub reads Connection and Mapping metadata only.' };
    if (integrationHealth.error) return { state: 'CHECK_UNAVAILABLE', summary: 'The provider configuration summary could not be verified.', detail: 'No provider or mapping change was attempted.' };
    const connections = integrationHealth.connectionCount ?? 0;
    const enabled = integrationHealth.enabledConnectionCount ?? 0;
    const mappings = integrationHealth.activeMappingCount ?? 0;
    if (connections === 0) return { state: 'SETUP_REQUIRED', summary: 'No Issue Tracking Connection has been configured.', detail: 'Create a Redmine or Jira Connection, then validate its technical identity before publishing a mapping.' };
    if (enabled === 0) return { state: 'ATTENTION', summary: `${connections} Connection${connections === 1 ? '' : 's'} exist, but none are enabled.`, detail: 'Review provider URL, technical identity and safe validation before enabling a production path.' };
    if (mappings === 0) return { state: 'ATTENTION', summary: `${enabled} enabled Connection${enabled === 1 ? '' : 's'}, but no ACTIVE Source/Task mapping.`, detail: 'Create a mapping draft, validate it, then publish only after reviewing impact.' };
    return { state: 'CONFIGURED', summary: `${enabled} enabled Connection${enabled === 1 ? '' : 's'} · ${mappings} ACTIVE mapping${mappings === 1 ? '' : 's'}.`, detail: 'Configured does not prove provider authentication or live CREATE certification for every Source. Check runtime readiness in context.' };
  }, [integrationHealth, pageMode]);

  const visibleLinksForGoal = useCallback((goal: SettingsGoal): GoalLink[] => {
    return [goal.primary, ...(goal.related ?? [])].filter((link) => !link.featureId || featureDisplayMode(entitlements.value, link.featureId) !== 'HIDDEN');
  }, [entitlements.value]);

  const visibleGoals = useMemo(() => GOALS.filter((goal) => {
    if (!canAccessAdminUiMode(mode, goal.requiredMode)) return false;
    const display = pageMode(goal.featureIds);
    if (display === 'HIDDEN') return false;
    return true;
  }), [mode, pageMode]);

  const goalHealth = useCallback((goal: SettingsGoal): GoalHealth => {
    if (goal.id === 'dispatch-setup') return dispatchSummary;
    if (goal.id === 'issue-tracking') return integrationSummary;
    const display = pageMode(goal.featureIds);
    if (display === 'READ_ONLY') return { state: 'READ_ONLY', summary: 'You can review this area.', detail: 'The target page will hide or disable actions that are not granted by Core.' };
    if (goal.id === 'runtime-configuration' && !actionAllowed(entitlements.value, 'runtime-configuration.edit')) {
      return { state: 'READ_ONLY', summary: 'Runtime configuration is visible, but editing is not granted.', detail: 'Approval, publication and rollback remain separately permissioned.' };
    }
    return goal.staticHealth ?? { state: 'AVAILABLE', summary: 'This administration area is available.' };
  }, [dispatchSummary, entitlements.value, integrationSummary, pageMode]);

  const attentionGoals = visibleGoals.filter((goal) => ['ATTENTION', 'SETUP_REQUIRED', 'CHECK_UNAVAILABLE'].includes(goalHealth(goal).state));
  const configuredGoals = visibleGoals.filter((goal) => goalHealth(goal).state === 'CONFIGURED').length;
  const readOnlyGoals = visibleGoals.filter((goal) => goalHealth(goal).state === 'READ_ONLY').length;

  if (entitlements.loading) {
    return <section className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm"><div className="text-sm font-black text-slate-950">Loading your configuration workspace…</div><p className="mt-2 text-sm text-slate-600">OpenDispatch is checking which administration goals are available for your current workspace.</p></section>;
  }

  if (entitlements.error) {
    return <section className="rounded-3xl border border-amber-200 bg-amber-50 p-6"><div className="text-sm font-black text-amber-950">Configuration navigation could not be verified.</div><p className="mt-2 text-sm leading-6 text-amber-900">{entitlements.error} The hub will not show static fallback links that could bypass Core entitlement projection.</p></section>;
  }

  return (
    <div className="space-y-5">
      <SettingsModeControl />

      <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm" aria-labelledby="settings-overview-title">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <div className="text-xs font-black uppercase tracking-wide text-slate-500">Configuration overview</div>
            <h2 id="settings-overview-title" className="mt-1 text-xl font-black text-slate-950">What needs your attention?</h2>
            <p className="mt-2 max-w-4xl text-sm leading-6 text-slate-600">This overview uses existing Core permissions and read-only configuration evidence. It does not apply changes, and it does not turn partial configuration into a runtime-health claim.</p>
          </div>
          <span className="rounded-full border border-blue-200 bg-blue-50 px-3 py-1 text-xs font-black text-blue-800">{getAdminUiModeOption(mode).shortLabel} view</span>
        </div>
        <div className="mt-4 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <SummaryMetric label="Needs attention" value={String(attentionGoals.length)} detail="Setup, incomplete configuration or an unavailable check" tone={attentionGoals.length ? 'warning' : 'ready'} />
          <SummaryMetric label="Configured" value={String(configuredGoals)} detail="Configuration exists; runtime readiness may still require a contextual check" tone="ready" />
          <SummaryMetric label="Read only" value={String(readOnlyGoals)} detail="Visible for review without all change permissions" tone="neutral" />
          <SummaryMetric label="Visible goals" value={String(visibleGoals.length)} detail="Filtered by Core entitlement and current Settings view" tone="info" />
        </div>
      </section>

      {attentionGoals.length ? (
        <section className="rounded-3xl border border-amber-200 bg-amber-50 p-5" aria-labelledby="settings-attention-title">
          <div className="text-xs font-black uppercase tracking-wide text-amber-800">Start here</div>
          <h2 id="settings-attention-title" className="mt-1 text-lg font-black text-amber-950">Configuration that may need action</h2>
          <div className="mt-3 grid gap-2 lg:grid-cols-2">
            {attentionGoals.map((goal) => {
              const health = goalHealth(goal);
              return (
                <Link key={`attention:${goal.id}`} href={visibleLinksForGoal(goal)[0]?.href ?? goal.primary.href} className="rounded-2xl border border-amber-200 bg-white p-4 hover:border-amber-400">
                  <div className="flex items-start justify-between gap-3">
                    <div><div className="font-black text-slate-950">{goal.title}</div><p className="mt-1 text-xs leading-5 text-slate-600">{health.summary}</p></div>
                    <span className="shrink-0 rounded-full border border-amber-200 bg-amber-50 px-2.5 py-1 text-[11px] font-black text-amber-900">{stateLabel(health.state)}</span>
                  </div>
                </Link>
              );
            })}
          </div>
        </section>
      ) : (
        <section className="rounded-3xl border border-emerald-200 bg-emerald-50 p-5">
          <div className="text-sm font-black text-emerald-950">No configuration item in the current view is asking for immediate setup attention.</div>
          <p className="mt-1 text-xs leading-5 text-emerald-900">This does not certify runtime readiness. Use the target workflow&apos;s validation and live-readiness evidence before production changes.</p>
        </section>
      )}

      <section className="space-y-3" aria-labelledby="settings-goals-title">
        <div>
          <div className="text-xs font-black uppercase tracking-wide text-slate-500">Choose by purpose</div>
          <h2 id="settings-goals-title" className="mt-1 text-xl font-black text-slate-950">What do you want to accomplish?</h2>
          <p className="mt-1 max-w-4xl text-sm leading-6 text-slate-600">Open the goal that matches the change you intend to make. Each target workspace remains responsible for validation, impact review and authorization.</p>
        </div>
        {visibleGoals.map((goal) => <GoalCard key={goal.id} goal={goal} health={goalHealth(goal)} mode={mode} displayMode={pageMode(goal.featureIds)} links={visibleLinksForGoal(goal)} />)}
        {!visibleGoals.length ? <div className="rounded-3xl border border-slate-200 bg-white p-6 text-sm text-slate-600">No configuration goals are available for the current workspace and Settings view.</div> : null}
      </section>
    </div>
  );
}

function SummaryMetric({ label, value, detail, tone }: Readonly<{ label: string; value: string; detail: string; tone: GoalTone }>) {
  return (
    <div className={`rounded-2xl border p-4 ${toneClasses[tone]}`}>
      <div className="text-xs font-black uppercase tracking-wide opacity-70">{label}</div>
      <div className="mt-1 text-2xl font-black">{value}</div>
      <p className="mt-1 text-xs leading-5 opacity-80">{detail}</p>
    </div>
  );
}
