import Link from "next/link";
import type { OperatorFailureReason } from "@/lib/dispatch-evidence/operatorFailureReasons";

export function OperatorFailureReasonCard({
  reason,
}: Readonly<{ reason: OperatorFailureReason }>) {
  const toneClassName: Record<OperatorFailureReason["tone"], string> = {
    success: "border-emerald-200 bg-emerald-50 text-emerald-950",
    warning: "border-amber-200 bg-amber-50 text-amber-950",
    danger: "border-rose-200 bg-rose-50 text-rose-950",
    info: "border-blue-200 bg-blue-50 text-blue-950",
    neutral: "border-slate-200 bg-slate-50 text-slate-950",
  };
  return (
    <div
      className={`mt-4 rounded-2xl border px-4 py-3 ${toneClassName[reason.tone]}`}
    >
      <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <div className="text-xs font-black uppercase tracking-wide opacity-70">
            Operator-readable failure reason
          </div>
          <div className="mt-1 flex flex-wrap items-center gap-2">
            <span className="rounded-full bg-white/70 px-2.5 py-1 text-xs font-black uppercase tracking-wide">
              {reason.code}
            </span>
            <span className="text-base font-black">{reason.title}</span>
          </div>
          <p className="mt-2 text-sm font-semibold leading-6 opacity-90">
            {reason.message}
          </p>
          <p className="mt-2 text-sm font-bold leading-6">
            <span className="opacity-70">Next action: </span>
            {reason.nextAction}
          </p>
        </div>
        {reason.technicalCodes.length ? (
          <div className="min-w-0 rounded-xl border border-white/70 bg-white/60 px-3 py-2 text-xs font-bold">
            <div className="text-xs font-black uppercase tracking-wide opacity-60">
              Technical codes
            </div>
            <div className="mt-1 flex max-w-lg flex-wrap gap-1">
              {reason.technicalCodes.slice(0, 8).map((code) => (
                <span
                  key={code}
                  className="rounded-full bg-white px-2 py-0.5 font-mono text-xs uppercase tracking-wide text-slate-700"
                >
                  {code}
                </span>
              ))}
            </div>
          </div>
        ) : null}
      </div>
      {reason.actions.length ? (
        <div className="mt-3 grid gap-2 md:grid-cols-2 xl:grid-cols-3">
          {reason.actions.map((action) =>
            action.href ? (
              <Link
                key={`${action.label}:${action.href}`}
                href={action.href}
                className="rounded-xl border border-white/70 bg-white/80 px-3 py-2 text-sm font-black text-blue-700 hover:bg-white"
              >
                {action.label}
                {action.description ? (
                  <div className="mt-1 text-xs font-semibold leading-5 text-slate-600">
                    {action.description}
                  </div>
                ) : null}
              </Link>
            ) : (
              <div
                key={action.label}
                className="rounded-xl border border-white/70 bg-white/80 px-3 py-2 text-sm font-black"
              >
                {action.label}
                {action.description ? (
                  <div className="mt-1 text-xs font-semibold leading-5 text-slate-600">
                    {action.description}
                  </div>
                ) : null}
              </div>
            ),
          )}
        </div>
      ) : null}
    </div>
  );
}

