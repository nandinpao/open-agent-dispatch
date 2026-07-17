import type { ReactNode } from 'react';
import Link from 'next/link';
import { DispatchUserFacingReason } from '@/components/common/DispatchUserFacingReason';
import { HumanizedCode } from '@/components/common/HumanizedCode';
import { StatusBadge } from '@/components/common/StatusBadge';
import { dispatchUserFacingNextAction } from '@/lib/dispatch-readiness/dispatchUserFacingError';
import type { DispatchOperatorActionContext, DispatchOperatorCommand, DispatchOperatorAction } from '@/lib/dispatch-readiness/dispatchOperatorActions';
import type { CoreDispatchUserFacingError } from '@/lib/types/core';

type DecisionTone = 'success' | 'warning' | 'danger' | 'info' | 'neutral';

const toneClasses: Record<DecisionTone, string> = {
  success: 'border-emerald-200 bg-emerald-50 text-emerald-950',
  warning: 'border-amber-200 bg-amber-50 text-amber-950',
  danger: 'border-rose-200 bg-rose-50 text-rose-950',
  info: 'border-blue-200 bg-blue-50 text-blue-950',
  neutral: 'border-slate-200 bg-white text-slate-950'
};

const actionToneClasses: Record<'primary' | 'secondary' | 'danger' | 'safe', string> = {
  primary: 'bg-indigo-600 text-white hover:bg-indigo-700 disabled:bg-slate-300',
  secondary: 'border border-slate-300 bg-white text-slate-700 hover:bg-slate-50 disabled:text-slate-400',
  danger: 'border border-rose-200 bg-white text-rose-700 hover:bg-rose-50 disabled:text-slate-400',
  safe: 'border border-emerald-300 bg-white text-emerald-700 hover:bg-emerald-50 disabled:text-slate-400'
};

export interface DecisionHeaderAction {
  label: string;
  onClick?: () => void;
  href?: string;
  disabled?: boolean;
  tone?: 'primary' | 'secondary' | 'danger' | 'safe';
}

function ActionButton({ action }: Readonly<{ action: DecisionHeaderAction }>) {
  const className = `rounded-xl px-4 py-2 text-sm font-bold shadow-sm disabled:cursor-not-allowed ${actionToneClasses[action.tone ?? 'secondary']}`;
  if (action.href) {
    return <Link href={action.href} className={className}>{action.label}</Link>;
  }
  return <button type="button" onClick={action.onClick} disabled={action.disabled} className={className}>{action.label}</button>;
}


export function DecisionHeader({
  eyebrow = 'Decision summary',
  title,
  subtitle,
  statusCode,
  statusLabel,
  blockingReason,
  userFacingError,
  nextAction,
  tone = 'info',
  facts = [],
  primaryAction,
  secondaryActions = [],
  showOperatorActions = false,
  operatorActionContext,
  onOperatorCommand,
  children
}: Readonly<{
  eyebrow?: string;
  title: string;
  subtitle?: string;
  statusCode?: string;
  statusLabel?: string;
  blockingReason?: string | null;
  userFacingError?: CoreDispatchUserFacingError | null;
  nextAction?: string | null;
  tone?: DecisionTone;
  facts?: Array<{ label: string; value: ReactNode }>;
  primaryAction?: DecisionHeaderAction;
  secondaryActions?: DecisionHeaderAction[];
  showOperatorActions?: boolean;
  operatorActionContext?: DispatchOperatorActionContext;
  onOperatorCommand?: (command: DispatchOperatorCommand, action: DispatchOperatorAction) => void;
  children?: ReactNode;
}>) {
  const displayNextAction = dispatchUserFacingNextAction(blockingReason, userFacingError) ?? nextAction;
  return (
    <section className={`rounded-3xl border p-5 shadow-sm ${toneClasses[tone]}`}>
      <div className="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">
        <div className="min-w-0 flex-1">
          <div className="text-xs font-black uppercase tracking-wide opacity-70">{eyebrow}</div>
          <h2 className="mt-1 text-2xl font-black leading-tight">{title}</h2>
          {subtitle ? <p className="mt-2 max-w-5xl text-sm leading-6 opacity-85">{subtitle}</p> : null}
          <div className="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-4">
            <div className="rounded-2xl border border-white/70 bg-white/70 px-4 py-3">
              <div className="text-xs font-bold uppercase tracking-wide opacity-60">目前狀態</div>
              <div className="mt-2 flex flex-wrap gap-2">
                <StatusBadge status={statusCode ?? statusLabel ?? 'INFO'} label={statusLabel} />
                {statusCode ? <HumanizedCode code={statusCode} type="status" compact /> : null}
              </div>
            </div>
            <div className="rounded-2xl border border-white/70 bg-white/70 px-4 py-3 md:col-span-1 xl:col-span-2">
              <div className="text-xs font-bold uppercase tracking-wide opacity-60">卡住原因 / 判斷</div>
              <div className="mt-2 text-sm font-semibold leading-6"><DispatchUserFacingReason value={blockingReason} error={userFacingError} showOperatorActions={showOperatorActions} actionContext={operatorActionContext} onOperatorCommand={onOperatorCommand} /></div>
            </div>
            <div className="rounded-2xl border border-white/70 bg-white/70 px-4 py-3">
              <div className="text-xs font-bold uppercase tracking-wide opacity-60">下一步</div>
              <div className="mt-2 text-sm font-semibold leading-6">{displayNextAction || '等待下一個系統事件或刷新狀態。'}</div>
            </div>
          </div>
          {facts.length > 0 ? (
            <div className="mt-4 flex flex-wrap gap-2 text-xs">
              {facts.map((fact) => (
                <span key={fact.label} className="rounded-full border border-white/70 bg-white/70 px-3 py-1 font-semibold">
                  <span className="opacity-60">{fact.label}：</span>{fact.value}
                </span>
              ))}
            </div>
          ) : null}
          {children ? <div className="mt-4">{children}</div> : null}
        </div>
        <div className="flex shrink-0 flex-col gap-2 sm:flex-row xl:flex-col">
          {primaryAction ? <ActionButton action={{ ...primaryAction, tone: primaryAction.tone ?? 'primary' }} /> : null}
          {secondaryActions.map((action) => <ActionButton key={action.label} action={action} />)}
        </div>
      </div>
    </section>
  );
}
