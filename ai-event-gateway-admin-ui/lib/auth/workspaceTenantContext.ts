export const ROOT_ADMIN_TENANT_STORAGE_KEY = 'opendispatch.root.administrationTenantId';
export const ROOT_ADMIN_TENANT_COOKIE_KEY = 'opendispatch.root.administrationTenantId';

export function readStoredRootAdministrationTenant(): string {
  if (typeof window === 'undefined') return '';
  try {
    return window.sessionStorage.getItem(ROOT_ADMIN_TENANT_STORAGE_KEY)?.trim() ?? '';
  } catch {
    return '';
  }
}

export function writeStoredRootAdministrationTenant(tenantId: string): void {
  if (typeof window === 'undefined') return;
  const normalized = tenantId.trim();
  try {
    if (normalized) window.sessionStorage.setItem(ROOT_ADMIN_TENANT_STORAGE_KEY, normalized);
    else window.sessionStorage.removeItem(ROOT_ADMIN_TENANT_STORAGE_KEY);
  } catch {
    // Browser storage is a convenience for hydration continuity, never authorization authority.
  }
  try {
    const secure = window.location.protocol === 'https:' ? '; Secure' : '';
    if (normalized) {
      document.cookie = `${ROOT_ADMIN_TENANT_COOKIE_KEY}=${encodeURIComponent(normalized)}; Path=/; SameSite=Lax${secure}`;
    } else {
      document.cookie = `${ROOT_ADMIN_TENANT_COOKIE_KEY}=; Max-Age=0; Path=/; SameSite=Lax${secure}`;
    }
  } catch {
    // The cookie is only a server-side workspace hint. Core R3 authorization remains authoritative.
  }
}
