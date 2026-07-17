'use client';

import { useMemo, useState } from 'react';
import Link from 'next/link';
import { p3mRuntimeAcceptanceFixture } from '@/lib/fixtures/p3mRuntimeAcceptanceFixture';
import { p3nFullEnforceAutomationFixture } from '@/lib/fixtures/p3nFullEnforceAutomationFixture';
import { p3oEnforceDefaultHardeningFixture } from '@/lib/fixtures/p3oEnforceDefaultHardeningFixture';

const cutoverSteps = [
  {
    title: '1. Keep production in SHADOW until V2 data is visible',
    detail: 'Run Eligibility V2 beside legacy routing. Operators should review Task Detail comparison, Dispatch Simulator, and Migration Readiness without blocking live routing.',
    mode: 'SHADOW',
  },
  {
    title: '2. Move to WARN after readiness export is clean',
    detail: 'WARN surfaces legacy-only candidates and V2 blocking reasons without failing dispatch. This is the final business review window before ENFORCE.',
    mode: 'WARN',
  },
  {
    title: '3. Run P3-M runtime acceptance against a live stack',
    detail: 'Execute the deterministic ENFORCE acceptance harness against Core and Admin UI. Store the JSON output as a release artifact.',
    mode: 'ACCEPTANCE',
  },
  {
    title: '4. Cut over to ENFORCE only after approval',
    detail: 'Set ROUTING_ELIGIBILITY_ENGINE_MODE=ENFORCE only when readiness blocking=0, fixture acceptance passes, and rollback owner is assigned.',
    mode: 'ENFORCE',
  },
];

const rollbackSteps = [
  'Set ROUTING_ELIGIBILITY_ENGINE_MODE=WARN.',
  'Restart Core / routing workers using the standard deployment rollback process.',
  'Export a fresh Migration Readiness report and compare blocked candidates with the P3-M acceptance artifact.',
  'Keep V2 shadow comparison visible in Task Detail while operators repair missing binding / capability / trust / quality data.',
];

const acceptanceCommand = `P3M_RUNTIME_ACCEPTANCE_MODE=live \\
P3M_CORE_BASE_URL=http://127.0.0.1:18080 \\
P3M_ADMIN_BASE_URL=http://127.0.0.1:3000 \\
P3M_TASK_ID=${p3mRuntimeAcceptanceFixture.taskId} \\
node scripts/p3m-enforce-runtime-acceptance.mjs`;

const p3nAutomationCommand = `P3N_RUNTIME_ACCEPTANCE_MODE=live \\
P3N_CORE_BASE_URL=http://127.0.0.1:18080 \\
P3N_ADMIN_BASE_URL=http://127.0.0.1:3000 \\
P3N_ACCEPTANCE_OUTPUT_DIR=../.ci-output/reports/p3n-full-enforce \\
npm run acceptance:p3n-full-enforce`;

export function EnforceReleaseCutoverPanel() {
  const [copyState, setCopyState] = useState<'idle' | 'copied' | 'failed'>('idle');
  const checklist = useMemo(
    () => [
      '# P3-M ENFORCE Release Cutover Checklist',
      `Fixture: ${p3mRuntimeAcceptanceFixture.id}`,
      `Task: ${p3mRuntimeAcceptanceFixture.taskId}`,
      '',
      'Required approvals:',
      '- Migration Readiness export has blocking=0.',
      '- Dispatch Simulator P3-K fixture matches expected eligible / blocked candidates.',
      '- P3-M runtime acceptance JSON is attached to the release review.',
      '- Rollback owner and rollback window are confirmed.',
      '',
      'Cutover sequence:',
      ...cutoverSteps.map((step) => `- ${step.mode}: ${step.title}`),
      '',
      'Rollback:',
      ...rollbackSteps.map((step) => `- ${step}`),
    ].join('\n'),
    []
  );

  async function copyChecklist() {
    try {
      await navigator.clipboard.writeText(checklist);
      setCopyState('copied');
    } catch {
      setCopyState('failed');
    }
  }

  return (
    <div className="space-y-6">
      <section className="rounded-3xl border border-blue-200 bg-blue-50 p-5 shadow-sm">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <div className="text-[11px] font-black uppercase tracking-wide text-blue-700">P3-M RELEASE_CUTOVER_RUNBOOK</div>
            <h2 className="mt-2 text-xl font-black text-slate-950">ENFORCE runtime acceptance and release cutover</h2>
            <p className="mt-2 max-w-4xl text-sm leading-6 text-slate-700">
              P3-O hardens the release profile: production defaults to WARN, controlled ENFORCE uses the enforce profile, live P3-N artifacts are required for release, and legacy profile fallback is disabled in ENFORCE.
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <button type="button" onClick={copyChecklist} className="rounded-xl border border-blue-200 bg-white px-3 py-2 text-xs font-black text-blue-700 hover:bg-blue-100">
              Copy cutover checklist
            </button>
            <Link href="/settings/migration-readiness" className="rounded-xl border border-slate-300 bg-white px-3 py-2 text-xs font-black text-slate-700 hover:bg-slate-50">
              Open readiness →
            </Link>
            <Link href="/settings/enforce-observability" className="rounded-xl border border-indigo-200 bg-white px-3 py-2 text-xs font-black text-indigo-700 hover:bg-indigo-100">
              ENFORCE observability →
            </Link>
          </div>
        </div>
        {copyState === 'copied' ? <p className="mt-3 text-xs font-bold text-emerald-700">Cutover checklist copied.</p> : null}
        {copyState === 'failed' ? <p className="mt-3 text-xs font-bold text-red-700">Clipboard unavailable. Use the command and runbook below.</p> : null}
      </section>

      <section className="rounded-3xl border border-emerald-200 bg-emerald-50 p-5 shadow-sm">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <div className="text-[11px] font-black uppercase tracking-wide text-emerald-700">P3-O OPERATOR_CUTOVER_DASHBOARD</div>
            <h3 className="mt-2 text-lg font-black text-slate-950">ENFORCE default hardening status</h3>
            <p className="mt-2 max-w-4xl text-sm leading-6 text-slate-700">
              Use this dashboard as the operator entry point before release cutover. It shows the intended mode defaults, mandatory live artifact gate, archive contract, legacy fallback removal, and rollback rehearsal path.
            </p>
          </div>
          <div className="rounded-full bg-white px-3 py-1 text-[11px] font-black uppercase tracking-wide text-emerald-700">{p3oEnforceDefaultHardeningFixture.id}</div>
        </div>
        <div className="mt-4 grid gap-3 md:grid-cols-4">
          <MetricCard label="Dev / Local" value={p3oEnforceDefaultHardeningFixture.releaseProfile.devDefault} />
          <MetricCard label="Production default" value={p3oEnforceDefaultHardeningFixture.releaseProfile.prodDefault} />
          <MetricCard label="Controlled profile" value={p3oEnforceDefaultHardeningFixture.releaseProfile.controlledEnforceProfile} />
          <MetricCard label="Release target" value={p3oEnforceDefaultHardeningFixture.releaseProfile.enforceMode} />
        </div>
        <div className="mt-4 grid gap-3 md:grid-cols-3">
          <ArtifactCard title="Mandatory live gate" items={[p3oEnforceDefaultHardeningFixture.mandatoryLiveGate.npmScript, p3oEnforceDefaultHardeningFixture.mandatoryLiveGate.requiredMode, p3oEnforceDefaultHardeningFixture.mandatoryLiveGate.acceptanceArtifact]} />
          <ArtifactCard title="Archive contract" items={[p3oEnforceDefaultHardeningFixture.archive.command, p3oEnforceDefaultHardeningFixture.archive.manifest, p3oEnforceDefaultHardeningFixture.archive.defaultOutputDir]} />
          <ArtifactCard title="Legacy fallback removal" items={[p3oEnforceDefaultHardeningFixture.legacyRemoval.disabledInEnforceProperty, p3oEnforceDefaultHardeningFixture.legacyRemoval.scoreBreakdownRequiredProperty, p3oEnforceDefaultHardeningFixture.legacyRemoval.readonlyReportView]} />
        </div>
      </section>

      <section className="grid gap-4 lg:grid-cols-4">
        {cutoverSteps.map((step) => (
          <div key={step.mode} className="rounded-3xl border border-slate-200 bg-white p-4 shadow-sm">
            <div className="rounded-full bg-slate-100 px-3 py-1 text-[11px] font-black uppercase tracking-wide text-slate-700">{step.mode}</div>
            <h3 className="mt-3 text-sm font-black text-slate-950">{step.title}</h3>
            <p className="mt-2 text-xs leading-5 text-slate-600">{step.detail}</p>
          </div>
        ))}
      </section>

      <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
        <h3 className="text-sm font-black uppercase tracking-wide text-slate-600">Runtime acceptance command</h3>
        <p className="mt-1 text-sm leading-6 text-slate-600">Run this against a live Core / Admin UI stack after loading the P3-K deterministic fixture data.</p>
        <pre className="mt-4 overflow-x-auto rounded-2xl bg-slate-950 p-4 text-xs font-bold leading-6 text-slate-100"><code>{acceptanceCommand}</code></pre>
        <div className="mt-4 grid gap-3 md:grid-cols-3">
          <Link href="/testing/dispatch-simulator" className="rounded-2xl border border-blue-200 bg-blue-50 p-4 text-sm font-black text-blue-700 hover:bg-blue-100">Run Dispatch Simulator →</Link>
          <Link href="/settings/migration-readiness" className="rounded-2xl border border-emerald-200 bg-emerald-50 p-4 text-sm font-black text-emerald-700 hover:bg-emerald-100">Export Migration Readiness JSON →</Link>
          <Link href="/settings/enforce-observability" className="rounded-2xl border border-indigo-200 bg-indigo-50 p-4 text-sm font-black text-indigo-700 hover:bg-indigo-100">Open ENFORCE Observability →</Link>
        </div>
      </section>



      <section className="rounded-3xl border border-indigo-200 bg-indigo-50 p-5 shadow-sm">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
          <div>
            <h3 className="text-sm font-black uppercase tracking-wide text-indigo-800">P3-N one-command automation</h3>
            <p className="mt-2 max-w-4xl text-sm leading-6 text-indigo-950">
              P3-N automates seed → acceptance → readiness → rollback → teardown, so ENFORCE acceptance no longer depends on manually prepared fixture data.
            </p>
          </div>
          <div className="rounded-full bg-white px-3 py-1 text-[11px] font-black uppercase tracking-wide text-indigo-700">{p3nFullEnforceAutomationFixture.id}</div>
        </div>
        <pre className="mt-4 overflow-x-auto rounded-2xl bg-slate-950 p-4 text-xs font-bold leading-6 text-slate-100"><code>{p3nAutomationCommand}</code></pre>
        <div className="mt-4 grid gap-3 md:grid-cols-3">
          <ArtifactCard title="P3-N artifacts" items={Object.values(p3nFullEnforceAutomationFixture.artifacts)} />
          <ArtifactCard title="Required blocked codes" items={p3nFullEnforceAutomationFixture.expected.blockedCodes} />
          <ArtifactCard title="Automation sequence" items={p3nFullEnforceAutomationFixture.oneCommandSequence} />
        </div>
      </section>

      <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
        <h3 className="text-sm font-black uppercase tracking-wide text-slate-600">P3-O artifact archive and release gate</h3>
        <p className="mt-2 text-sm leading-6 text-slate-600">Archive the P3-N reports and require a live artifact for release unless explicitly running a dry-run.</p>
        <pre className="mt-4 overflow-x-auto rounded-2xl bg-slate-950 p-4 text-xs font-bold leading-6 text-slate-100"><code>{`${p3oEnforceDefaultHardeningFixture.archive.command}
npm run ${p3oEnforceDefaultHardeningFixture.mandatoryLiveGate.npmScript}`}</code></pre>
      </section>

      <section className="rounded-3xl border border-amber-200 bg-amber-50 p-5 shadow-sm">
        <h3 className="text-sm font-black uppercase tracking-wide text-amber-800">Rollback runbook</h3>
        <ol className="mt-3 space-y-2 text-sm leading-6 text-amber-900">
          {rollbackSteps.map((step, index) => (
            <li key={step} className="font-bold">{index + 1}. {step}</li>
          ))}
        </ol>
      </section>

      <section className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
        <h3 className="text-sm font-black uppercase tracking-wide text-slate-600">P3-M acceptance artifact contract</h3>
        <div className="mt-3 grid gap-3 md:grid-cols-3">
          <ArtifactCard title="Required checks" items={p3mRuntimeAcceptanceFixture.releaseArtifact.requiredChecks} />
          <ArtifactCard title="Expected blocked codes" items={p3mRuntimeAcceptanceFixture.expected.blockedCodes} />
          <ArtifactCard title="Routing score keys" items={p3mRuntimeAcceptanceFixture.expected.scoreBreakdownKeys} />
        </div>
      </section>
    </div>
  );
}

function MetricCard({ label, value }: Readonly<{ label: string; value: string }>) {
  return (
    <div className="rounded-2xl border border-emerald-200 bg-white p-4">
      <div className="text-[11px] font-black uppercase tracking-wide text-emerald-600">{label}</div>
      <div className="mt-2 text-lg font-black text-slate-950">{value}</div>
    </div>
  );
}

function ArtifactCard({ title, items }: Readonly<{ title: string; items: readonly string[] }>) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
      <div className="text-xs font-black uppercase tracking-wide text-slate-500">{title}</div>
      <ul className="mt-3 space-y-2">
        {items.map((item) => (
          <li key={item} className="rounded-xl bg-white px-3 py-2 text-xs font-bold text-slate-700">{item}</li>
        ))}
      </ul>
    </div>
  );
}
