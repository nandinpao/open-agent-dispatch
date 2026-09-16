export type AuthBackendNamespace = '/api/session';

export function buildAuthBackendPath(
  namespace: AuthBackendNamespace,
  path: readonly string[],
  query = ''
): string {
  const suffix = path.map(encodeURIComponent).join('/');
  const pathname = suffix ? `${namespace}/${suffix}` : namespace;
  return query ? `${pathname}?${query}` : pathname;
}
