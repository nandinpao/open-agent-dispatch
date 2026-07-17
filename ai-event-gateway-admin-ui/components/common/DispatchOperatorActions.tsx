'use client';

import Link from 'next/link';
import type { DispatchOperatorAction, DispatchOperatorCommand } from '@/lib/dispatch-readiness/dispatchOperatorActions';

const toneClass: Record<string, string> = {
  primary: 'border-indigo-600 bg-indigo-600 text-white hover:bg-indigo-700',
  secondary: 'border-slate-200 bg-white text-slate-700 hover:bg-slate-50',
  danger: 'border-rose-200 bg-white text-rose-700 hover:bg-rose-50',
  safe: 'border-emerald-200 bg-white text-emerald-700 hover:bg-emerald-50',
  warning: 'border-amber-200 bg-white text-amber-700 hover:bg-amber-50',
};

export function DispatchOperatorActions({
  actions,
  onCommand,
  compact = false,
}: Readonly<{
  actions: DispatchOperatorAction[];
  onCommand?: (command: DispatchOperatorCommand, action: DispatchOperatorAction) => void;
  compact?: boolean;
}>) {
  if (!actions.length) return null;
  return (
    <div className={compact ? 'flex flex-wrap gap-2' : 'space-y-2'}>
      {actions.map((action) => {
        const className = `${compact ? 'rounded-lg px-2.5 py-1 text-[11px]' : 'rounded-xl px-3 py-2 text-xs'} inline-flex items-center border font-black shadow-sm ${toneClass[action.tone ?? 'secondary']}`;
        if (action.href) {
          return <Link key={`${action.id}-${action.href}`} href={action.href} className={className}>{action.label}</Link>;
        }
        if (action.command && onCommand) {
          const command = action.command;
          return <button key={`${action.id}-${command}`} type="button" onClick={() => onCommand(command, action)} className={className}>{action.label}</button>;
        }
        return <span key={action.id} className={`${className} cursor-default opacity-75`}>{action.label}</span>;
      })}
    </div>
  );
}
