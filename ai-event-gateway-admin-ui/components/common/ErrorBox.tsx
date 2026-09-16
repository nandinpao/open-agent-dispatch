import Link from 'next/link';

function accessProblem(message: string): boolean {
  return /\b403\b|forbidden|permission is denied|permission denied|access denied|scope mismatch|cross[- ]scope|tenant mismatch/i.test(message);
}

function friendlyAccessReason(message: string): string {
  if (/cross[- ]scope/i.test(message)) return 'This action crosses an organizational boundary and needs a broader Responsibility.';
  if (/tenant mismatch/i.test(message)) return 'This item belongs to a different company workspace.';
  if (/scope mismatch/i.test(message)) return 'Your Responsibility does not cover this item’s Department or Group scope.';
  return 'Your current Responsibility does not allow this page, item, or action.';
}

export function ErrorBox({ message }: Readonly<{ message: string }>) {
  if (!accessProblem(message)) {
    return <div className="rounded-2xl border border-rose-200 bg-rose-50 p-6 text-sm font-medium text-rose-700">{message}</div>;
  }
  return (
    <div className="rounded-2xl border border-amber-200 bg-amber-50 p-5 text-sm text-amber-950" role="alert">
      <div className="font-black">You don’t have access to this item or action.</div>
      <p className="mt-2 leading-6">{friendlyAccessReason(message)} Ask an administrator to review your Responsibility and scope instead of changing company workspace or entering an internal ID.</p>
      <div className="mt-3 flex flex-wrap gap-2">
        <Link href="/access-management?workspace=access" className="rounded-lg bg-white px-3 py-2 text-xs font-black text-blue-800 shadow-sm ring-1 ring-blue-100 hover:bg-blue-50">Review access</Link>
        <Link href="/account" className="rounded-lg bg-white px-3 py-2 text-xs font-black text-slate-700 shadow-sm ring-1 ring-slate-200 hover:bg-slate-50">My account</Link>
      </div>
      <details className="mt-4 rounded-xl border border-amber-200 bg-white/70 p-3">
        <summary className="cursor-pointer text-xs font-black text-amber-900">Technical details for support</summary>
        <p className="mt-2 break-words font-mono text-xs leading-5 text-slate-600">{message}</p>
      </details>
    </div>
  );
}
