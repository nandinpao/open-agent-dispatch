import type { ReactNode } from "react";
import { StatusBadge } from "@/components/common/StatusBadge";

export function DeliveryStep({
  label,
  done,
  detail,
}: Readonly<{ label: string; done: boolean; detail?: ReactNode }>) {
  return (
    <div
      className={`rounded-xl border px-3 py-2 ${done ? "border-emerald-200 bg-emerald-50" : "border-slate-200 bg-slate-50"}`}
    >
      <div className="flex items-center justify-between gap-2">
        <span className="text-xs font-black uppercase tracking-wide text-slate-700">
          {label}
        </span>
        <StatusBadge status={done ? "CONFIRMED" : "PENDING"} />
      </div>
      {detail ? (
        <div className="mt-1 text-xs font-semibold leading-5 text-slate-600">
          {detail}
        </div>
      ) : null}
    </div>
  );
}

export function ChipList({
  values,
  empty = "-",
}: Readonly<{ values: string[]; empty?: string }>) {
  if (!values.length)
    return (
      <span className="text-sm font-semibold text-slate-400">{empty}</span>
    );
  return (
    <div className="flex flex-wrap gap-1.5">
      {values.map((value) => (
        <span
          key={value}
          className="rounded-full border border-slate-200 bg-white px-2.5 py-1 text-xs font-black uppercase tracking-wide text-slate-700"
        >
          {value}
        </span>
      ))}
    </div>
  );
}

export function EvidenceCard({
  label,
  children,
  tone = "neutral",
}: Readonly<{
  label: string;
  children: ReactNode;
  tone?: "neutral" | "good" | "warn" | "bad";
}>) {
  const className = {
    neutral: "border-slate-200 bg-slate-50",
    good: "border-emerald-200 bg-emerald-50",
    warn: "border-amber-200 bg-amber-50",
    bad: "border-rose-200 bg-rose-50",
  }[tone];
  return (
    <div className={`rounded-2xl border px-4 py-3 ${className}`}>
      <div className="text-xs font-black uppercase tracking-wide text-slate-500">
        {label}
      </div>
      <div className="mt-2 min-h-6 text-sm font-bold text-slate-900">
        {children}
      </div>
    </div>
  );
}

