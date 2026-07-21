'use client';

import { useAuth } from '@/components/auth/AuthProvider';

interface WorkspaceTenantSelectorProps {
  compact?: boolean;
  className?: string;
}

export function WorkspaceTenantSelector({ compact = false, className = '' }: Readonly<WorkspaceTenantSelectorProps>) {
  const { tenants, selectedTenantId, selectTenant, tenantChanging, status } = useAuth();
  const disabled = status !== 'AUTHENTICATED' || tenantChanging || tenants.length <= 1;

  return (
    <label className={`flex min-w-0 flex-col gap-1 ${className}`}>
      {!compact ? <span className="text-[10px] font-black uppercase tracking-[0.14em] text-slate-500">工作區</span> : null}
      <select
        aria-label="工作區"
        value={selectedTenantId}
        disabled={disabled}
        onChange={(event) => void selectTenant(event.target.value)}
        className="max-w-[16rem] rounded-xl border border-slate-200 bg-white px-3 py-1.5 text-xs font-bold text-slate-700 shadow-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100 disabled:cursor-not-allowed disabled:bg-slate-50 disabled:text-slate-500"
      >
        {!selectedTenantId ? <option value="">尚未選擇工作區</option> : null}
        {tenants.map((tenant) => (
          <option key={tenant.tenantId} value={tenant.tenantId}>
            {tenant.tenantId}
          </option>
        ))}
      </select>
    </label>
  );
}

export function WorkspaceTenantField({ label = '工作區', className = '' }: Readonly<{ label?: string; className?: string }>) {
  const { selectedTenantId } = useAuth();
  return (
    <label className={`flex flex-col gap-1 text-xs font-semibold uppercase tracking-wide text-slate-500 ${className}`}>
      {label}
      <input
        aria-label={label}
        value={selectedTenantId}
        readOnly
        className="rounded-lg border border-slate-200 bg-slate-100 px-3 py-2 text-sm font-semibold text-slate-700 shadow-sm"
      />
      <span className="normal-case font-normal tracking-normal text-slate-500">請由頁面頂端或行動版導覽中的工作區選擇器切換。</span>
    </label>
  );
}
