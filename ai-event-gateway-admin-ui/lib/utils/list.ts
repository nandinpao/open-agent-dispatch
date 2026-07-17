export interface PaginationState {
  page: number;
  pageSize: number;
}

export interface PaginatedResult<T> extends PaginationState {
  items: T[];
  totalItems: number;
  totalPages: number;
  startItem: number;
  endItem: number;
}

export function normalizeSearchQuery(value: string): string {
  return value.trim().toLowerCase();
}

export function textIncludesQuery(value: unknown, query: string): boolean {
  if (!query) return true;
  if (value === undefined || value === null) return false;
  return String(value).toLowerCase().includes(query);
}

export function recordIncludesQuery(values: unknown[], query: string): boolean {
  const normalizedQuery = normalizeSearchQuery(query);
  if (!normalizedQuery) return true;
  return values.some((value) => textIncludesQuery(value, normalizedQuery));
}

export function paginateItems<T>(items: T[], state: PaginationState): PaginatedResult<T> {
  const safePageSize = Number.isFinite(state.pageSize) && state.pageSize > 0 ? Math.floor(state.pageSize) : 10;
  const totalItems = items.length;
  const totalPages = Math.max(1, Math.ceil(totalItems / safePageSize));
  const page = Math.min(Math.max(1, Math.floor(state.page || 1)), totalPages);
  const startIndex = (page - 1) * safePageSize;
  const endIndex = Math.min(startIndex + safePageSize, totalItems);

  return {
    items: items.slice(startIndex, endIndex),
    totalItems,
    totalPages,
    page,
    pageSize: safePageSize,
    startItem: totalItems === 0 ? 0 : startIndex + 1,
    endItem: endIndex
  };
}

export function uniqueSortedValues(values: Array<string | undefined | null>): string[] {
  return Array.from(new Set(values.filter((value): value is string => Boolean(value)))).sort((a, b) => a.localeCompare(b));
}
