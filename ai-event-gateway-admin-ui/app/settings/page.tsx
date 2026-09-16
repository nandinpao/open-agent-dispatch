import Link from 'next/link';
import { PageHeader } from '@/components/common/PageHeader';
import { AuthenticationSecurityPanel } from '@/components/settings/AuthenticationSecurityPanel';
import { ReturnToAgentBanner } from '@/components/common/ReturnToAgentBanner';

type PageProps = { searchParams?: Promise<Record<string, string | string[] | undefined>> };
type SettingsItem = { href: string; label: string; description: string };
type SettingsGroup = { title: string; description: string; items: SettingsItem[] };

const configurationGroups: SettingsGroup[] = [
  {
    title: 'Dispatch Configuration',
    description: 'Configure the systems and governed Flow/Pool model that Core uses for normal dispatch. These links do not create a second routing authority.',
    items: [
      { href: '/source-systems', label: 'Source Systems', description: 'Register systems that submit Events and define their governed ownership.' },
      { href: '/dispatch-flows', label: 'Dispatch', description: 'Configure Source Flows, Agent Pools, rules, Draft Simulation, activation and governed real tests.' },
      { href: '/agents', label: 'Agents', description: 'Approve Agent identities, review runtime readiness and bind Agents to governed runtime supply.' },
    ],
  },
  {
    title: 'Delegation & Execution Governance',
    description: 'Configure provider-neutral capability delegation. WHAT, WHO MAY, WHO SHOULD and HOW remain separate governed decisions.',
    items: [
      { href: '/settings/capabilities', label: 'Capability Catalog', description: 'Define enterprise capabilities and register qualified Capability Providers.' },
      { href: '/settings/delegation-governance', label: 'Delegation Governance', description: 'Define WHO MAY use a capability with hard authorization and approval constraints.' },
      { href: '/settings/provider-routing', label: 'Provider Routing', description: 'Define WHO SHOULD among already-authorized providers using runtime eligibility and explainable ranking.' },
      { href: '/settings/execution-adapters', label: 'Execution Adapters', description: 'Resolve HOW a selected provider is reached through Managed Agent, Remote A2A, MCP or Internal Service execution.' },
    ],
  },
  {
    title: 'Integrations',
    description: 'Connect external issue and provider systems without mixing provider credentials with dispatch or delegation authority.',
    items: [
      { href: '/settings/integrations', label: 'Integration Configuration', description: 'Configure provider instances, credentials, project mappings and permission probes.' },
      { href: '/operations/integration-sync', label: 'Integration Recovery', description: 'Operate projection queues, webhooks, conflicts and dead letters when synchronization needs intervention.' },
      { href: '/issues-events', label: 'Issues & Events', description: 'Review business issue projections and operational event evidence.' },
    ],
  },
  {
    title: 'Runtime & Safety',
    description: 'Manage runtime supply and production safety separately from business routing configuration.',
    items: [
      { href: '/agents/runtime', label: 'Agent Runtime Resources', description: 'Register gateway, connector and execution resources while staying inside the Agent setup journey.' },
      { href: '/settings/execution-safety', label: 'Execution Safety Authority', description: 'Control leases, fencing and durable DispatchIntent safety before network delivery.' },
      { href: '/settings/runtime-acceptance', label: 'Runtime Acceptance', description: 'Review live isolation, recovery, migration and scale evidence. Source verification alone cannot certify production.' },
      { href: '/settings/production-foundation', label: 'Production Foundation Release', description: 'Manage release candidates, integrated E2E evidence and explicit per-Flow production cutover or rollback.' },
    ],
  },
];

const advancedWorkflowItems: SettingsItem[] = [
  { href: '/settings/semantic-triage', label: 'Semantic Triage', description: 'Propose Canonical WHAT only for unknown problems; known Service Codes remain deterministic Fast Path.' },
  { href: '/settings/execution-plans', label: 'Execution Plans', description: 'Validate multi-Capability WHAT and dependency plans without selecting Providers or protocols.' },
  { href: '/settings/plan-executions', label: 'Plan Execution', description: 'Execute frozen Plan revisions with dependency-driven fan-out/fan-in, retries and normalized Artifacts.' },
  { href: '/settings/runtime-step-authority', label: 'Runtime Step Authority', description: 'Rebuild WHO CAN → WHO MAY → WHO SHOULD → HOW evidence for READY runtime Steps and retries.' },
  { href: '/settings/learning-governance', label: 'Learning Governance', description: 'Review execution-memory recommendations and certify semantic Fast Paths through governed evidence.' },
  { href: '/settings/learning-fast-path', label: 'Learning Fast Path', description: 'Inspect advanced learned Fast Path controls without bypassing current runtime authority.' },
  { href: '/settings/case-convergence', label: 'Case Convergence', description: 'Aggregate normalized execution Artifacts into a canonical enterprise Case; external Issues remain projections.' },
];

const engineeringItems: SettingsItem[] = [
  { href: '/settings/runtime-features', label: 'Runtime Features', description: 'Manage advanced runtime feature controls.' },
  { href: '/settings/runtime-resources', label: 'Global Runtime Resources', description: 'Advanced global runtime resource administration outside the normal Agent journey.' },
  { href: '/settings/fast-path-runtime', label: 'Fast Path Runtime', description: 'Inspect deterministic fast-path runtime controls.' },
  { href: '/settings/routing-authority', label: 'Routing Authority Cutover', description: 'Compare routing decisions and execution assignments against the legacy path before controlled authority cutover.' },
  { href: '/settings/migration-readiness', label: 'Migration Readiness', description: 'Review migration compatibility and permission-readiness evidence.' },
  { href: '/settings/release-cutover', label: 'Release Cutover', description: 'Advanced release administration and cutover controls.' },
  { href: '/settings/enforce-observability', label: 'Enforcement Observability', description: 'Inspect advanced enforcement telemetry and production support evidence.' },
  { href: '/settings/quality-metrics', label: 'Quality Metrics', description: 'Inspect quality and acceptance metrics used by governed rules.' },
  { href: '/engineering-tools/runtime-diagnostics', label: 'Runtime Diagnostics', description: 'Engineering-only Core, Gateway and runtime-stream diagnostics.' },
];

function SettingsCards({ items }: Readonly<{ items: SettingsItem[] }>) {
  return <div className="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-3">
    {items.map((item) => (
      <Link key={item.href} href={item.href} className="rounded-2xl border border-slate-200 bg-slate-50 p-4 hover:border-blue-200 hover:bg-blue-50">
        <div className="font-black text-slate-950">{item.label}</div>
        <p className="mt-2 text-sm leading-6 text-slate-600">{item.description}</p>
        <span className="mt-3 inline-block text-xs font-black text-blue-700">Open →</span>
      </Link>
    ))}
  </div>;
}

export default async function SettingsPage({ searchParams }: Readonly<PageProps>) {
  const resolvedSearchParams = await searchParams;
  return (
    <main className="space-y-5">
      <PageHeader title="Configuration & Governance" description="Start from the business purpose: Dispatch Configuration, Delegation & Execution Governance, Integrations, or Runtime & Safety. Planning, migration and engineering controls remain advanced." />
      <ReturnToAgentBanner searchParams={resolvedSearchParams} />

      {configurationGroups.map((group) => (
        <section key={group.title} className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
          <div className="text-xs font-black uppercase tracking-wide text-slate-500">{group.title}</div>
          <p className="mt-2 max-w-4xl text-sm leading-6 text-slate-600">{group.description}</p>
          <SettingsCards items={group.items} />
        </section>
      ))}

      <details className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
        <summary className="cursor-pointer text-base font-black text-slate-950">Advanced planning & learning</summary>
        <p className="mt-2 max-w-4xl text-sm leading-6 text-slate-600">Use these controls only when designing multi-step planning, semantic triage, learning or Case convergence. They do not replace current authorization or provider-routing decisions.</p>
        <SettingsCards items={advancedWorkflowItems} />
      </details>

      <details className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
        <summary className="cursor-pointer text-base font-black text-slate-950">Release & engineering controls</summary>
        <p className="mt-2 max-w-4xl text-sm leading-6 text-slate-600">Migration, cutover and low-level diagnostics are intentionally separated from everyday configuration to reduce accidental authority changes.</p>
        <SettingsCards items={engineeringItems} />
      </details>

      <AuthenticationSecurityPanel />
    </main>
  );
}
