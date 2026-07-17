export type NumericLike = number | string | null | undefined;

export function toFiniteNumber(value: NumericLike): number | undefined {
  if (typeof value === 'number') {
    return Number.isFinite(value) ? value : undefined;
  }

  if (typeof value === 'string') {
    const normalized = value.trim().replace(/,/g, '').replace(/%$/, '');
    if (!normalized) return undefined;
    const parsed = Number(normalized);
    return Number.isFinite(parsed) ? parsed : undefined;
  }

  return undefined;
}

export function formatNumber(value: NumericLike, emptyText = '-'): string {
  const numberValue = toFiniteNumber(value);
  if (numberValue === undefined) return emptyText;
  return new Intl.NumberFormat('zh-Hant-TW').format(numberValue);
}

export function formatDateTime(value?: string | null): string {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('zh-Hant-TW', {
    dateStyle: 'short',
    timeStyle: 'medium',
    hour12: false
  }).format(date);
}

export function formatDuration(seconds: NumericLike): string {
  const numberValue = toFiniteNumber(seconds);
  if (numberValue === undefined) return '-';

  const safeSeconds = Math.max(0, Math.floor(numberValue));
  const day = Math.floor(safeSeconds / 86400);
  const hour = Math.floor((safeSeconds % 86400) / 3600);
  const minute = Math.floor((safeSeconds % 3600) / 60);
  return `${day}d ${hour}h ${minute}m`;
}

export function formatDurationMs(milliseconds?: NumericLike): string {
  const numberValue = toFiniteNumber(milliseconds);
  if (numberValue === undefined) return '-';
  if (numberValue < 1000) return `${Math.round(numberValue)} ms`;
  return `${(numberValue / 1000).toFixed(1)} s`;
}

export function formatPercent(value: NumericLike, emptyText = '-'): string {
  const numberValue = toFiniteNumber(value);
  if (numberValue === undefined) return emptyText;
  return `${numberValue.toFixed(1)}%`;
}

export function formatMemory(usedMb: NumericLike, maxMb: NumericLike): string {
  return `${formatNumber(usedMb)} / ${formatNumber(maxMb)} MB`;
}
