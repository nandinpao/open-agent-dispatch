'use client';

import Link from 'next/link';
import type { ButtonHTMLAttributes, ReactNode } from 'react';
import { DecisionReason } from '@/components/ui-capability/DecisionReason';
import type { UiCapability } from '@/lib/ui-capability/contracts';
import { listCapabilityRecordIsFresh, type UiListCapabilityRecord } from '@/lib/ui-capability/listStore';

function available(record: UiListCapabilityRecord | undefined, capability: UiCapability | undefined): boolean {
  return listCapabilityRecordIsFresh(record) && capability?.displayMode === 'ENABLED';
}

function isLoading(record: UiListCapabilityRecord | undefined): boolean {
  return !record || ['IDLE', 'QUEUED', 'HYDRATING'].includes(record.status);
}

function isStale(record: UiListCapabilityRecord | undefined): boolean {
  return record?.status === 'STALE' || (record?.status === 'READY' && !listCapabilityRecordIsFresh(record));
}

function CorrelationReference({ correlationId }: Readonly<{ correlationId?: string }>) {
  if (!correlationId) return null;
  return <span className="mt-1 block break-all font-mono text-xs text-slate-500">Correlation ID: {correlationId}</span>;
}

export function ListCapabilityLink({
  record,
  capability,
  href,
  children,
  className = '',
  serverGuarded = false,
}: Readonly<{
  record?: UiListCapabilityRecord;
  capability?: UiCapability;
  href: string;
  children: ReactNode;
  className?: string;
  /** The target route performs its own server-side authorization guard. */
  serverGuarded?: boolean;
}>) {
  if (capability?.displayMode === 'HIDE') return null;
  if (available(record, capability)) return <Link href={href} className={className}>{children}</Link>;

  const provisional = isLoading(record) || record?.status === 'ERROR' || isStale(record);
  if (serverGuarded && provisional) {
    return (
      <Link
        href={href}
        className={className}
        data-capability-state={isLoading(record) ? 'LOADING' : isStale(record) ? 'STALE' : 'UNAVAILABLE'}
        title="This link is protected by a server-side authorization guard. Current capability state is shown below."
      >
        {children}
      </Link>
    );
  }

  return (
    <span
      className={`cursor-not-allowed opacity-55 ${className}`}
      aria-disabled="true"
      data-capability-state={record?.status ?? 'LOADING'}
      title="Access is not currently authorized. Review the capability reason below."
    >
      {children}
    </span>
  );
}

export function ListCapabilityButton({
  record,
  capability,
  children,
  className = '',
  ...buttonProps
}: Readonly<{
  record?: UiListCapabilityRecord;
  capability?: UiCapability;
  children: ReactNode;
  className?: string;
} & ButtonHTMLAttributes<HTMLButtonElement>>) {
  if (capability?.displayMode === 'HIDE') return null;
  const enabled = available(record, capability) && !buttonProps.disabled;
  return (
    <button
      {...buttonProps}
      type={buttonProps.type ?? 'button'}
      disabled={!enabled}
      aria-disabled={!enabled}
      data-ui-display-mode={capability?.displayMode ?? (isLoading(record) ? 'LOADING' : record?.status ?? 'UNAVAILABLE')}
      className={`${className} disabled:cursor-not-allowed disabled:opacity-45`}
      title={!enabled ? 'This mutation is unavailable until capability authorization is current.' : buttonProps.title}
    >
      {children}
    </button>
  );
}

export function ListCapabilityReason({
  record,
  capability,
  serverGuarded = false,
}: Readonly<{ record?: UiListCapabilityRecord; capability?: UiCapability; serverGuarded?: boolean }>) {
  if (isLoading(record)) {
    return (
      <div className="text-xs text-slate-600" role="status" aria-live="polite">
        <span className="font-semibold">Capability loading.</span>{' '}
        {serverGuarded ? 'You may open the Task console now; the server will independently verify read access.' : 'This action remains disabled until authorization is verified.'}
        <CorrelationReference correlationId={record?.correlationId} />
      </div>
    );
  }
  if (record?.status === 'DENIED') {
    return (
      <div className="text-xs text-rose-800" role="status">
        <span className="font-semibold">Capability denied.</span>{' '}
        Core capability authorization denied this action in the current scope.
        {record.error ? <span className="mt-1 block break-words text-rose-700">{record.error}</span> : null}
        <div className="mt-1"><DecisionReason reason={record.reason ?? 'NOT_ALLOWED'} displayMode="DISABLE_WITH_REASON" compact /></div>
        <CorrelationReference correlationId={record.correlationId} />
      </div>
    );
  }
  if (record?.status === 'ERROR') {
    return (
      <div className="text-xs text-amber-800" role="status">
        <span className="font-semibold">Capability unavailable.</span>{' '}
        {serverGuarded ? 'The Task console can still perform server-side authorization when opened.' : 'This action remains disabled.'}
        {record.error ? <span className="mt-1 block break-words text-amber-700">{record.error}</span> : null}
        <CorrelationReference correlationId={record.correlationId} />
      </div>
    );
  }
  if (isStale(record)) {
    return (
      <div className="text-xs text-amber-800" role="status">
        <span className="font-semibold">Capability stale.</span>{' '}
        {serverGuarded ? 'Opening the Task console will re-run server-side authorization against the current resource.' : 'Reload authorization before using this action.'}
        <div className="mt-1"><DecisionReason reason={record?.reason ?? 'RESOURCE_CHANGED'} displayMode="STALE_RELOAD" compact /></div>
        <CorrelationReference correlationId={record?.correlationId} />
      </div>
    );
  }
  if (record?.status !== 'READY' || !capability || capability.displayMode === 'ENABLED' || capability.displayMode === 'HIDE') return null;
  return (
    <div role="status">
      <DecisionReason reason={capability.reasonCategory} displayMode={capability.displayMode} compact />
      <CorrelationReference correlationId={record.correlationId} />
    </div>
  );
}
