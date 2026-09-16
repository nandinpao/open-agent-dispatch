'use client';

import type { ReactNode } from 'react';
import { VirtualizedResourceList } from '@/components/layout/VirtualizedResourceList';

export function VirtualizedGovernanceList<T>({
  items,
  getItemKey,
  rowHeight = 92,
  height = 620,
  renderRow,
  emptyText = 'No records match the current filter.',
}: Readonly<{
  items: readonly T[];
  getItemKey: (item: T) => string;
  rowHeight?: number;
  height?: number;
  renderRow: (item: T, index: number) => ReactNode;
  emptyText?: string;
}>) {
  return (
    <VirtualizedResourceList
      items={items}
      getItemKey={getItemKey}
      rowHeight={rowHeight}
      height={height}
      renderRow={renderRow}
      empty={<div className="rounded-2xl border border-dashed border-slate-300 bg-slate-50 p-10 text-center text-sm font-semibold text-slate-500">{emptyText}</div>}
    />
  );
}
