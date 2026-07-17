type LoadingBoxProps = Readonly<{
  label?: string;
  /**
   * Backward-compatible alias for older component calls.
   * Prefer `label` in new code.
   */
  title?: string;
}>;

export function LoadingBox({ label, title }: LoadingBoxProps) {
  const displayText = label ?? title ?? 'Loading...';
  return <div className="rounded-2xl border border-slate-200 bg-white p-6 text-sm text-slate-500 shadow-sm">{displayText}</div>;
}
