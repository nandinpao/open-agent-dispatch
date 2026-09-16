import 'server-only';

import { fetchBackend } from '@/lib/server/backendOrigins';
import type { RuntimeCapabilitySnapshot, RuntimeCapabilityState } from '@/lib/runtime-capability/contracts';

export class RuntimeCapabilityError extends Error {
  constructor(
    public readonly code: string,
    message: string,
    public readonly status = 503,
  ) {
    super(message);
    this.name = 'RuntimeCapabilityError';
  }
}

const SERVER_CACHE_FRESH_MS = 5_000;
const SERVER_CACHE_STALE_MS = 60_000;
let cachedSnapshot: RuntimeCapabilitySnapshot | null = null;
let cachedAt = 0;
let inFlightLoad: Promise<RuntimeCapabilitySnapshot> | null = null;

function publicCapabilityHeaders(): Headers {
  const headers = new Headers({ Accept: 'application/json' });
  headers.set('x-admin-ui-proxy-plane', 'runtime-capability-authority');
  return headers;
}

function hasExpectedSessionContract(snapshot: Partial<RuntimeCapabilitySnapshot>): boolean {
  const authentication = snapshot.authentication;
  return Boolean(authentication
    && authentication.sessionPath === '/api/session'
    && typeof authentication.legacyPasswordAdapterEnabled === 'boolean');
}

function isSurface(value: unknown): value is RuntimeCapabilityState {
  return value === 'DISABLED' || value === 'SHADOW' || value === 'PILOT' || value === 'ENABLED';
}

function validateSnapshot(value: unknown): RuntimeCapabilitySnapshot {
  if (!value || typeof value !== 'object') {
    throw new RuntimeCapabilityError('RUNTIME_CAPABILITY_CONTRACT_INVALID', 'Core returned an invalid runtime capability contract.');
  }
  const snapshot = value as Partial<RuntimeCapabilitySnapshot>;
  if (snapshot.contractVersion !== '4.0'
      || !hasExpectedSessionContract(snapshot)
      || !snapshot.surfaces
      || !Object.values(snapshot.surfaces).every(isSurface)) {
    throw new RuntimeCapabilityError('RUNTIME_CAPABILITY_CONTRACT_INVALID', 'Core returned an unsupported runtime capability contract.');
  }
  return snapshot as RuntimeCapabilitySnapshot;
}

async function loadFromCore(): Promise<RuntimeCapabilitySnapshot> {
  // This endpoint is a non-sensitive, pre-authentication bootstrap authority.
  // Never forward browser credentials: a password-change-only session or stale
  // cookie must not turn a public capability probe into an authenticated request.
  const { response } = await fetchBackend('core', '/api/platform/runtime-capabilities', {
    method: 'GET',
    headers: publicCapabilityHeaders(),
    credentials: 'omit',
    cache: 'no-store',
  });
  if (!response.ok) {
    throw new RuntimeCapabilityError(
      'RUNTIME_CAPABILITY_UNAVAILABLE',
      `Core runtime capability authority returned HTTP ${response.status}.`,
      response.status >= 400 && response.status < 500 ? response.status : 503,
    );
  }
  return validateSnapshot(await response.json());
}

/**
 * Core is the sole authority for optional runtime surfaces.
 *
 * Requests from the browser provider, navigation projection and RSC bootstrap
 * are coalesced inside the Admin UI process. A recent successful snapshot may
 * be used briefly during a transient Core connection failure, but no fallback
 * can enable a surface that Core did not previously authorize.
 */
export async function loadRuntimeCapabilities(): Promise<RuntimeCapabilitySnapshot> {
  const age = Date.now() - cachedAt;
  if (cachedSnapshot && age < SERVER_CACHE_FRESH_MS) return cachedSnapshot;
  if (inFlightLoad) return inFlightLoad;

  inFlightLoad = loadFromCore()
    .then((snapshot) => {
      cachedSnapshot = snapshot;
      cachedAt = Date.now();
      return snapshot;
    })
    .catch((error: unknown) => {
      if (cachedSnapshot && Date.now() - cachedAt < SERVER_CACHE_STALE_MS) return cachedSnapshot;
      throw error;
    })
    .finally(() => {
      inFlightLoad = null;
    });
  return inFlightLoad;
}
