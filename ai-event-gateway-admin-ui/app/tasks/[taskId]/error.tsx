'use client';
import Link from 'next/link';
export default function TaskDetailError({ reset }: Readonly<{ error: Error & { digest?: string }; reset: () => void }>) {
  return <section role="alert" className="mx-auto max-w-2xl rounded-2xl border border-amber-200 bg-amber-50 p-8 text-amber-950"><h1 className="text-xl font-black">This protected view is temporarily unavailable</h1><p className="mt-2 text-sm">No protected task content was rendered.</p><div className="mt-5 flex gap-3"><button type="button" onClick={reset} className="rounded-xl bg-amber-950 px-4 py-2 text-sm font-bold text-white">Retry securely</button><Link href="/tasks" className="rounded-xl border border-current px-4 py-2 text-sm font-bold">Return to Tasks</Link></div></section>;
}
