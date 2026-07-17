'use client';

import { useAdminRealtime } from '@/hooks/useAdminRealtime';
import { formatDateTime } from '@/lib/utils/format';

const levelClassName: Record<string, string> = {
  info: 'border-blue-200 bg-blue-50 text-blue-950',
  success: 'border-emerald-200 bg-emerald-50 text-emerald-950',
  warning: 'border-amber-200 bg-amber-50 text-amber-950',
  error: 'border-rose-200 bg-rose-50 text-rose-950'
};

export function ToastViewport() {
  const { toasts, dismissToast } = useAdminRealtime();

  if (toasts.length === 0) return null;

  return (
    <div className="fixed bottom-5 right-5 z-50 w-full max-w-sm space-y-3">
      {toasts.map((toast) => (
        <div key={toast.id} className={`rounded-2xl border p-4 shadow-lg ${levelClassName[toast.level]}`}>
          <div className="flex items-start justify-between gap-3">
            <div>
              <div className="text-sm font-bold">{toast.title}</div>
              {toast.message ? <div className="mt-1 text-sm opacity-85">{toast.message}</div> : null}
              <div className="mt-2 text-xs opacity-70">{formatDateTime(toast.timestamp)}</div>
            </div>
            <button
              type="button"
              onClick={() => dismissToast(toast.id)}
              className="rounded-lg px-2 py-1 text-xs font-bold opacity-70 hover:bg-white/50 hover:opacity-100"
              aria-label="Dismiss notification"
            >
              ×
            </button>
          </div>
        </div>
      ))}
    </div>
  );
}
