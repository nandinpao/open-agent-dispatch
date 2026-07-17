'use client';

import type { ReactNode } from 'react';
import { HelpTooltip } from '@/components/ui/Tooltip';
import { getActionHelp, type ActionHelpKey } from '@/lib/help/actionHelp';
import { getHelpText, type HelpTextKey } from '@/lib/help/helpText';
import { getStatusHelp } from '@/lib/help/statusHelp';

export interface HelpTextProps {
  term?: HelpTextKey;
  action?: ActionHelpKey;
  status?: string | null;
  label?: ReactNode;
  description?: ReactNode;
  mode?: 'inline' | 'block' | 'icon';
  className?: string;
}

function helpContent({ term, action, status, description }: Readonly<HelpTextProps>): { title: ReactNode; body: ReactNode; extra?: ReactNode } {
  if (description) return { title: 'Help', body: description };
  if (term) {
    const entry = getHelpText(term);
    return {
      title: entry.label,
      body: entry.description,
      extra: entry.example ?? undefined,
    };
  }
  if (action) {
    const entry = getActionHelp(action);
    const flags = [entry.requiresConfirm ? 'Requires confirmation' : undefined, entry.requiresReason ? 'Requires reason' : undefined, entry.danger ? 'Danger operation' : undefined].filter(Boolean).join(' · ');
    return {
      title: entry.label,
      body: entry.description,
      extra: flags || undefined,
    };
  }
  const entry = getStatusHelp(status);
  if (entry) {
    return {
      title: entry.label,
      body: entry.description,
      extra: entry.operatorAction,
    };
  }
  return {
    title: status ?? 'Help',
    body: '目前沒有集中說明。請確認 help dictionary 是否需要新增此名詞或狀態。',
  };
}

function TooltipBody({ title, body, extra }: Readonly<{ title: ReactNode; body: ReactNode; extra?: ReactNode }>) {
  return (
    <span className="block">
      <span className="block font-black text-white">{title}</span>
      <span className="mt-1 block leading-5 text-slate-100">{body}</span>
      {extra ? <span className="mt-2 block rounded-lg bg-white/10 px-2 py-1 text-[11px] leading-4 text-slate-100">{extra}</span> : null}
    </span>
  );
}

export function HelpText(props: Readonly<HelpTextProps>) {
  const { label, mode = 'inline', className = '' } = props;
  const content = helpContent(props);
  const tooltip = <HelpTooltip content={<TooltipBody {...content} />} />;

  if (mode === 'icon') return <span className={className}>{tooltip}</span>;

  if (mode === 'block') {
    return (
      <div className={`rounded-2xl border border-blue-100 bg-blue-50/70 p-4 text-sm leading-6 text-blue-950 ${className}`}>
        <div className="flex items-start gap-2">
          <div className="min-w-0 flex-1">
            <div className="font-black">{label ?? content.title}</div>
            <div className="mt-1 text-blue-900/80">{content.body}</div>
            {content.extra ? <div className="mt-2 text-xs font-semibold text-blue-800">{content.extra}</div> : null}
          </div>
          {tooltip}
        </div>
      </div>
    );
  }

  return (
    <span className={`inline-flex items-center gap-1.5 ${className}`}>
      {label ? <span>{label}</span> : null}
      {tooltip}
    </span>
  );
}
