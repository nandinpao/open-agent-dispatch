import type { UiVisibilityLevel } from '@/lib/ui-capability/contracts';

const LABELS: Record<UiVisibilityLevel, string> = {
  NONE: 'No visibility',
  METADATA: 'Metadata',
  SUMMARY: 'Summary',
  STANDARD: 'Standard',
  SENSITIVE: 'Sensitive',
  FULL: 'Full',
  SECRET_METADATA: 'Secret metadata',
};

export function VisibilityBadge({ level }: Readonly<{ level: UiVisibilityLevel }>) {
  return (
    <span className="inline-flex rounded-full border border-slate-300 bg-slate-50 px-2 py-0.5 text-xs font-semibold text-slate-700">
      {LABELS[level]}
    </span>
  );
}
