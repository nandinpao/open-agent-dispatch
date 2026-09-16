import type { PasswordRequirement } from '@/lib/auth/passwordPolicy';

export function PasswordRequirementList({
  requirements,
  active,
}: {
  requirements: PasswordRequirement[];
  active: boolean;
}) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-slate-50 p-4" aria-live="polite">
      <p className="text-xs font-black uppercase tracking-[.15em] text-slate-600">Password requirements</p>
      <ul className="mt-3 grid gap-2 text-sm">
        {requirements.map((requirement) => {
          const satisfied = active && requirement.satisfied;
          return (
            <li
              key={requirement.key}
              className={`flex items-start gap-2 ${satisfied ? 'text-emerald-700' : 'text-slate-600'}`}
            >
              <span aria-hidden="true" className="mt-0.5 font-black">
                {satisfied ? '✓' : '○'}
              </span>
              <span>{requirement.label}</span>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
