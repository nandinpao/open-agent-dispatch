import type { Department, Group, IdentityAudit } from '@/lib/iam/types';

export function hierarchyDepartmentOptions(items: Department[]) {
  return [...items].sort((a, b) => departmentPath(a, items).localeCompare(departmentPath(b, items))).map((item) => ({ value: item.departmentId, label: item.name, description: departmentPath(item, items), depth: departmentDepth(item, items) }));
}

export function hierarchyGroupOptions(items: Group[]) {
  return [...items].sort((a, b) => groupPath(a, items).localeCompare(groupPath(b, items))).map((item) => ({ value: item.groupId, label: item.name, description: groupPath(item, items), depth: groupDepth(item, items) }));
}

export function departmentPath(department: Department, all: Department[]): string {
  const names = [department.name]; let parentId = department.parentDepartmentId; const seen = new Set<string>();
  while (parentId && !seen.has(parentId)) { seen.add(parentId); const parent = all.find((item) => item.departmentId === parentId); if (!parent) break; names.unshift(parent.name); parentId = parent.parentDepartmentId; }
  return names.join(' / ');
}

export function groupPath(group: Group, all: Group[]): string {
  const names = [group.name]; let parentId = group.parentGroupId; const seen = new Set<string>();
  while (parentId && !seen.has(parentId)) { seen.add(parentId); const parent = all.find((item) => item.groupId === parentId); if (!parent) break; names.unshift(parent.name); parentId = parent.parentGroupId; }
  return names.join(' / ');
}

export function departmentAncestorIds(departmentId: string, all: Department[]): string[] {
  const result: string[] = [];
  let current = all.find((item) => item.departmentId === departmentId);
  const seen = new Set<string>();
  while (current?.parentDepartmentId && !seen.has(current.parentDepartmentId)) {
    seen.add(current.parentDepartmentId);
    result.push(current.parentDepartmentId);
    current = all.find((item) => item.departmentId === current?.parentDepartmentId);
  }
  return result;
}
export function departmentDepth(department: Department, all: Department[]): number { let depth = 0; let parentId = department.parentDepartmentId; const visited = new Set<string>(); while (parentId && depth < 20 && !visited.has(parentId)) { visited.add(parentId); const parent = all.find((item) => item.departmentId === parentId); if (!parent) break; depth += 1; parentId = parent.parentDepartmentId; } return depth; }
export function groupDepth(group: Group, all: Group[]): number { let depth = 0; let parentId = group.parentGroupId; const seen = new Set<string>(); while (parentId && depth < 20 && !seen.has(parentId)) { seen.add(parentId); const parent = all.find((item) => item.groupId === parentId); if (!parent) break; depth += 1; parentId = parent.parentGroupId; } return depth; }
export function countDepartmentDescendants(id: string, departments: Department[]): number { const children = departments.filter((item) => item.parentDepartmentId === id); return children.length + children.reduce((sum, item) => sum + countDepartmentDescendants(item.departmentId, departments), 0); }
export function isDepartmentDescendant(candidateId: string, ancestorId: string, departments: Department[]): boolean { let current = departments.find((item) => item.departmentId === candidateId); const seen = new Set<string>(); while (current?.parentDepartmentId && !seen.has(current.parentDepartmentId)) { if (current.parentDepartmentId === ancestorId) return true; seen.add(current.parentDepartmentId); current = departments.find((item) => item.departmentId === current?.parentDepartmentId); } return false; }
export function isGroupDescendant(candidateId: string, ancestorId: string, groups: Group[]): boolean { let current = groups.find((item) => item.groupId === candidateId); const seen = new Set<string>(); while (current?.parentGroupId && !seen.has(current.parentGroupId)) { if (current.parentGroupId === ancestorId) return true; seen.add(current.parentGroupId); current = groups.find((item) => item.groupId === current?.parentGroupId); } return false; }
export function hasCollapsedDepartmentAncestor(department: Department, all: Department[], collapsed: Set<string>): boolean { let parentId = department.parentDepartmentId; const seen = new Set<string>(); while (parentId && !seen.has(parentId)) { if (collapsed.has(parentId)) return true; seen.add(parentId); parentId = all.find((item) => item.departmentId === parentId)?.parentDepartmentId ?? ''; } return false; }
export function hasCollapsedGroupAncestor(group: Group, all: Group[], collapsed: Set<string>): boolean { let parentId = group.parentGroupId; const seen = new Set<string>(); while (parentId && !seen.has(parentId)) { if (collapsed.has(parentId)) return true; seen.add(parentId); parentId = all.find((item) => item.groupId === parentId)?.parentGroupId ?? ''; } return false; }
export function humanAuditEvent(event: IdentityAudit): string { const action = event.eventType.replaceAll('_', ' ').toLowerCase(); return `${event.actorId} ${action} ${event.targetType.toLowerCase()} ${event.targetId}`; }
export function humanize(value: string): string { return value.toLowerCase().replaceAll('_', ' ').replace(/\b\w/g, (character) => character.toUpperCase()); }
export function groupTypeOptions() { return ['GENERAL', 'SECURITY', 'OPERATIONS', 'DISPATCH', 'AUDIT', 'ON_CALL', 'CROSS_FUNCTIONAL'].map((value) => ({ value, label: humanize(value) })); }
export function generateUniqueCode(name: string, existing: string[], fallback = 'ORG'): string { const base = name.trim().toUpperCase().normalize('NFKD').replace(/[^A-Z0-9]+/g, '_').replace(/^_+|_+$/g, '').slice(0, 40) || fallback; const used = new Set(existing.map((item) => item.toUpperCase())); if (!used.has(base)) return base; for (let index = 2; index < 1000; index += 1) { const candidate = `${base}_${index}`; if (!used.has(candidate)) return candidate; } return `${base}_${Date.now().toString().slice(-4)}`; }
export function toLocalDateTime(value?: string): string { if (!value) return ''; const date = new Date(value); if (Number.isNaN(date.getTime())) return ''; const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000); return local.toISOString().slice(0, 16); }
export function fromLocalDateTime(value: string): string | null { if (!value) return null; const date = new Date(value); return Number.isNaN(date.getTime()) ? null : date.toISOString(); }
