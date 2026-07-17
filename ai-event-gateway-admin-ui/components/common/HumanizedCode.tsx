import { humanizedCodeDescription, humanizedCodeLabel, normalizeCode } from '@/lib/dispatch-readiness/labels';

type HumanizedCodeType = 'skill' | 'capability' | 'status' | 'policy' | 'operation' | 'taskType' | 'generic';

export function HumanizedCode({
  code,
  type = 'generic',
  compact = false,
  className = ''
}: Readonly<{
  code?: string;
  type?: HumanizedCodeType;
  compact?: boolean;
  className?: string;
}>) {
  const normalized = normalizeCode(code);
  const label = humanizedCodeLabel(normalized, type);
  const description = humanizedCodeDescription(normalized, type);

  if (compact) {
    return (
      <span className={`inline-flex items-center gap-1.5 rounded-full bg-slate-100 px-2.5 py-1 text-xs font-semibold text-slate-700 ${className}`} title={`${description} code: ${normalized || '-'}`}>
        <span>{label}</span>
        {normalized && label !== normalized ? <span className="font-mono text-[10px] text-slate-400">{normalized}</span> : null}
      </span>
    );
  }

  return (
    <div className={`rounded-xl border border-slate-200 bg-white px-3 py-2 ${className}`} title={description}>
      <div className="text-sm font-bold text-slate-900">{label}</div>
      <div className="mt-0.5 font-mono text-[11px] text-slate-400">code: {normalized || '-'}</div>
    </div>
  );
}
