import Link from 'next/link';
export default function TaskDetailNotFound() {
  return <section className="mx-auto max-w-2xl rounded-2xl border border-slate-200 bg-white p-8 shadow-sm"><h1 className="text-xl font-black">Resource not found or unavailable</h1><p className="mt-2 text-sm text-slate-600">The resource may not exist or may not be available in your current workspace.</p><Link href="/tasks" className="mt-5 inline-flex rounded-xl border border-slate-300 px-4 py-2 text-sm font-bold">Return to Tasks</Link></section>;
}
