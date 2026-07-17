export type AdminAuthMode = 'core-session';

export function normalizeAdminAuthMode(value?: string): AdminAuthMode {
  void value;
  return 'core-session';
}

export function serverAdminAuthMode(): AdminAuthMode {
  return 'core-session';
}
