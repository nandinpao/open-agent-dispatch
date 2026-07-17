export function CommandMessage({ message }: { message: string | null }) {
  if (!message) return null;
  return (
    <div className="rounded-2xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-medium text-emerald-800">
      {message}
    </div>
  );
}
