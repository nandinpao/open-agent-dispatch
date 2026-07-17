'use client';

import type { ReactNode } from 'react';

export interface SelectFilterOption {
  value: string;
  label: string;
}

export interface SelectFilterConfig {
  id: string;
  label: string;
  value: string;
  options: SelectFilterOption[];
  onChange: (value: string) => void;
}

export interface ListFilterBarProps {
  search: string;
  searchPlaceholder: string;
  onSearchChange: (value: string) => void;
  filters?: SelectFilterConfig[];
  onClear: () => void;
  rightSlot?: ReactNode;
}

export function ListFilterBar({ search, searchPlaceholder, onSearchChange, filters = [], onClear, rightSlot }: Readonly<ListFilterBarProps>) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
      <div className="grid grid-cols-1 gap-3 lg:grid-cols-[minmax(220px,1fr)_auto] lg:items-end">
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-4">
          <label className="space-y-1 md:col-span-2 xl:col-span-1">
            <span className="text-xs font-semibold uppercase tracking-wide text-slate-500">Search</span>
            <input
              value={search}
              onChange={(event) => onSearchChange(event.target.value)}
              placeholder={searchPlaceholder}
              className="w-full rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 outline-none transition focus:border-blue-400 focus:ring-2 focus:ring-blue-100"
            />
          </label>

          {filters.map((filter) => (
            <label key={filter.id} className="space-y-1">
              <span className="text-xs font-semibold uppercase tracking-wide text-slate-500">{filter.label}</span>
              <select
                value={filter.value}
                onChange={(event) => filter.onChange(event.target.value)}
                className="w-full rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 outline-none transition focus:border-blue-400 focus:ring-2 focus:ring-blue-100"
              >
                {filter.options.map((option) => (
                  <option key={option.value} value={option.value}>{option.label}</option>
                ))}
              </select>
            </label>
          ))}
        </div>

        <div className="flex flex-wrap justify-start gap-2 lg:justify-end">
          <button
            type="button"
            onClick={onClear}
            className="rounded-xl border border-slate-200 px-3 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50"
          >
            Clear Filters
          </button>
          {rightSlot}
        </div>
      </div>
    </div>
  );
}
