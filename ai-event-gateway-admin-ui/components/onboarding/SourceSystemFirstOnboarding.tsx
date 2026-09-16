'use client';

import Link from 'next/link';
import { useI18n } from '@/hooks/useI18n';

export interface OnboardingProgress {
  sourceSystemReady?: boolean;
  agentPoolReady?: boolean;
  poolMembersReady?: boolean;
  defaultPoolReady?: boolean;
  simulationPassed?: boolean;
  testEventSent?: boolean;
}

const steps = [
  ['sourceSystemReady', 'onboarding.step.sourceSystem', '/source-systems'],
  ['agentPoolReady', 'onboarding.step.agentPool', '/dispatch-flows'],
  ['poolMembersReady', 'onboarding.step.members', '/dispatch-flows'],
  ['defaultPoolReady', 'onboarding.step.defaultPool', '/dispatch-flows'],
  ['simulationPassed', 'onboarding.step.simulation', '/dispatch-flows'],
  ['testEventSent', 'onboarding.step.testEvent', '/dispatch-flows'],
] as const;

export function SourceSystemFirstOnboarding({ progress = {} }: Readonly<{ progress?: OnboardingProgress }>) {
  const { t } = useI18n();
  const completed = steps.filter(([field]) => Boolean(progress[field])).length;
  const percent = Math.round((completed / steps.length) * 100);

  return (
    <section className="rounded-3xl border border-blue-200 bg-gradient-to-br from-blue-50 to-cyan-50 p-6 shadow-sm" aria-labelledby="source-system-onboarding-title">
      <div className="flex flex-col gap-5 xl:flex-row xl:items-start xl:justify-between">
        <div className="max-w-3xl">
          <div className="text-xs font-black uppercase tracking-[0.18em] text-blue-700">{t('onboarding.badge')}</div>
          <h2 id="source-system-onboarding-title" className="mt-2 text-xl font-black text-slate-950">{t('onboarding.title')}</h2>
          <p className="mt-2 text-sm leading-6 text-slate-700">{t('onboarding.description')}</p>
        </div>
        <Link href="/source-systems" className="inline-flex shrink-0 items-center justify-center rounded-xl bg-blue-600 px-4 py-2.5 text-sm font-black text-white shadow-sm transition hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-300 focus:ring-offset-2">
          {t('onboarding.cta')}
        </Link>
      </div>

      <div className="mt-5" aria-label={`${completed} of ${steps.length} setup steps completed`}>
        <div className="flex items-center justify-between text-xs font-bold text-slate-600">
          <span>{t('onboarding.progress')}</span>
          <span>{percent}%</span>
        </div>
        <div className="mt-2 h-2 overflow-hidden rounded-full bg-white" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={percent}>
          <div className="h-full rounded-full bg-blue-600 transition-all" style={{ width: `${percent}%` }} />
        </div>
      </div>

      <ol className="mt-5 grid gap-3 md:grid-cols-2 xl:grid-cols-3">
        {steps.map(([field, labelKey, href], index) => {
          const done = Boolean(progress[field]);
          return (
            <li key={field}>
              <Link href={href} className="flex h-full items-start gap-3 rounded-2xl border border-white/80 bg-white/80 p-4 transition hover:border-blue-300 hover:bg-white focus:outline-none focus:ring-2 focus:ring-blue-200">
                <span className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-xs font-black ${done ? 'bg-emerald-600 text-white' : 'bg-slate-100 text-slate-600'}`} aria-hidden="true">
                  {done ? '✓' : index + 1}
                </span>
                <span>
                  <span className="block text-sm font-black text-slate-900">{t(labelKey)}</span>
                  <span className="mt-1 block text-xs text-slate-500">{done ? t('common.completed') : t('common.openSetupStep')}</span>
                </span>
              </Link>
            </li>
          );
        })}
      </ol>
    </section>
  );
}
