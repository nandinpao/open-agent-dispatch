'use client';

import { useMemo, useState, type ReactNode } from 'react';

export function VirtualizedResourceList<T>({
  items,
  getItemKey,
  renderRow,
  rowHeight = 190,
  height = 720,
  overscan = 4,
  empty,
}: Readonly<{
  items: readonly T[];
  getItemKey: (item: T) => string;
  renderRow: (item: T, index: number) => ReactNode;
  rowHeight?: number;
  height?: number;
  overscan?: number;
  empty?: ReactNode;
}>) {
  const [scrollTop, setScrollTop] = useState(0);
  const start = Math.max(0, Math.floor(scrollTop / rowHeight) - overscan);
  const visibleCount = Math.ceil(height / rowHeight) + overscan * 2;
  const end = Math.min(items.length, start + visibleCount);
  const rows = useMemo(() => items.slice(start, end), [items, start, end]);

  if (items.length === 0) return <>{empty}</>;

  return (
    <div
      className="overflow-auto rounded-2xl border border-slate-200 bg-slate-50"
      style={{ height }}
      onScroll={(event) => setScrollTop(event.currentTarget.scrollTop)}
      role="list"
    >
      <div className="relative" style={{ height: items.length * rowHeight }}>
        {rows.map((item, offset) => {
          const index = start + offset;
          return (
            <div
              key={getItemKey(item)}
              role="listitem"
              aria-posinset={index + 1}
              aria-setsize={items.length}
              className="absolute inset-x-0 px-2 py-1"
              style={{ top: index * rowHeight, height: rowHeight }}
            >
              {renderRow(item, index)}
            </div>
          );
        })}
      </div>
    </div>
  );
}
