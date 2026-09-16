'use client';

import type { ReactNode } from 'react';
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import type { RuntimeCapabilitySnapshot, RuntimeCapabilityState } from '@/lib/runtime-capability/contracts';

export type RuntimeCapabilityLoadState = 'LOADING' | 'READY' | 'REFRESHING' | 'DEGRADED';

interface RuntimeCapabilityContextValue {
  capabilities: RuntimeCapabilitySnapshot;
  loadState: RuntimeCapabilityLoadState;
  error: string;
  refresh: () => Promise<void>;
}

const RuntimeCapabilityContext = createContext<RuntimeCapabilityContextValue | null>(null);

const CAPABILITY_REQUEST_TIMEOUT_MS = 8_000;
const CAPABILITY_CLIENT_CACHE_TTL_MS = 30_000;
const FALLBACK_TIMESTAMP = '1970-01-01T00:00:00.000Z';

const FAIL_CLOSED_CAPABILITIES: RuntimeCapabilitySnapshot = {
  contractVersion: '4.0',
  generatedAt: FALLBACK_TIMESTAMP,
  authentication: {
    sessionPath: '/api/session',
    legacyPasswordAdapterEnabled: false,
  },
  surfaces: {
    iamAdministration: 'DISABLED',
    resourceAccessAdministration: 'DISABLED',
    uiCapabilityProjection: 'DISABLED',
    enforcementActivation: 'DISABLED',
    a2aOperations: 'DISABLED',
    issueTracking: 'DISABLED',
  },
};

let cachedCapabilities: RuntimeCapabilitySnapshot | null = null;
let cachedAt = 0;
let inFlightCapabilityRequest: Promise<RuntimeCapabilitySnapshot> | null = null;

function isRuntimeState(value: unknown): value is RuntimeCapabilityState {
  return value === 'DISABLED' || value === 'SHADOW' || value === 'PILOT' || value === 'ENABLED';
}

function validateBrowserSnapshot(value: unknown): RuntimeCapabilitySnapshot {
  if (!value || typeof value !== 'object') throw new Error('Core returned an invalid runtime capability contract.');
  const snapshot = value as Partial<RuntimeCapabilitySnapshot>;
  if (snapshot.contractVersion !== '4.0'
      || snapshot.authentication?.sessionPath !== '/api/session'
      || typeof snapshot.authentication.legacyPasswordAdapterEnabled !== 'boolean'
      || !snapshot.surfaces
      || !Object.values(snapshot.surfaces).every((state) => state === undefined || isRuntimeState(state))) {
    throw new Error('Core returned an unsupported runtime capability contract.');
  }
  const surfaces = snapshot.surfaces as Partial<RuntimeCapabilitySnapshot['surfaces']>;
  return {
    contractVersion: '4.0',
    generatedAt: typeof snapshot.generatedAt === 'string' ? snapshot.generatedAt : new Date().toISOString(),
    authentication: snapshot.authentication,
    surfaces: {
      iamAdministration: surfaces.iamAdministration ?? 'DISABLED',
      resourceAccessAdministration: surfaces.resourceAccessAdministration ?? 'DISABLED',
      uiCapabilityProjection: surfaces.uiCapabilityProjection ?? 'DISABLED',
      enforcementActivation: surfaces.enforcementActivation ?? 'DISABLED',
      a2aOperations: surfaces.a2aOperations ?? 'DISABLED',
      issueTracking: surfaces.issueTracking ?? 'DISABLED',
    },
  };
}

async function fetchCapabilitiesFromNetwork(): Promise<RuntimeCapabilitySnapshot> {
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), CAPABILITY_REQUEST_TIMEOUT_MS);
  try {
    const response = await fetch('/api/ui/runtime-capabilities', {
      method: 'GET',
      credentials: 'same-origin',
      cache: 'no-store',
      headers: { Accept: 'application/json' },
      signal: controller.signal,
    });
    const body = await response.json().catch(() => ({})) as { code?: string; message?: string } & Partial<RuntimeCapabilitySnapshot>;
    if (!response.ok) {
      throw new Error(body.message ?? `Runtime capability authority returned HTTP ${response.status}.`);
    }
    return validateBrowserSnapshot(body);
  } catch (cause) {
    if (cause instanceof DOMException && cause.name === 'AbortError') {
      throw new Error('Core runtime capability authority timed out.');
    }
    throw cause;
  } finally {
    window.clearTimeout(timeout);
  }
}

function loadCapabilities(force: boolean): Promise<RuntimeCapabilitySnapshot> {
  const fresh = cachedCapabilities && Date.now() - cachedAt < CAPABILITY_CLIENT_CACHE_TTL_MS;
  if (!force && fresh) return Promise.resolve(cachedCapabilities as RuntimeCapabilitySnapshot);
  if (inFlightCapabilityRequest) return inFlightCapabilityRequest;

  inFlightCapabilityRequest = fetchCapabilitiesFromNetwork()
    .then((snapshot) => {
      cachedCapabilities = snapshot;
      cachedAt = Date.now();
      return snapshot;
    })
    .finally(() => {
      inFlightCapabilityRequest = null;
    });
  return inFlightCapabilityRequest;
}

function DegradedBanner({ message, retry }: Readonly<{ message: string; retry: () => Promise<void> }>): ReactNode {
  return (
    <aside className="border-b border-amber-300 bg-amber-50 px-4 py-2 text-sm text-amber-950" role="alert">
      <div className="mx-auto flex max-w-screen-2xl flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <span><b>Runtime configuration degraded.</b> {message} Optional surfaces remain fail-closed; authentication is not blocked.</span>
        <button type="button" onClick={() => { void retry(); }} className="shrink-0 rounded-lg border border-amber-400 bg-white px-3 py-1.5 text-xs font-black hover:bg-amber-100">Retry</button>
      </div>
    </aside>
  );
}

export function RuntimeCapabilityProvider({ children }: Readonly<{ children: ReactNode }>) {
  const [capabilities, setCapabilities] = useState<RuntimeCapabilitySnapshot>(() => cachedCapabilities ?? FAIL_CLOSED_CAPABILITIES);
  const [loadState, setLoadState] = useState<RuntimeCapabilityLoadState>(() => cachedCapabilities ? 'READY' : 'LOADING');
  const [error, setError] = useState('');
  const requestSequence = useRef(0);
  const mounted = useRef(true);

  const executeLoad = useCallback(async (force: boolean) => {
    const sequence = ++requestSequence.current;
    setLoadState(cachedCapabilities ? 'REFRESHING' : 'LOADING');
    setError('');
    try {
      const next = await loadCapabilities(force);
      if (!mounted.current || sequence !== requestSequence.current) return;
      setCapabilities(next);
      setLoadState('READY');
    } catch (cause) {
      if (!mounted.current || sequence !== requestSequence.current) return;
      setCapabilities((current) => current ?? FAIL_CLOSED_CAPABILITIES);
      setLoadState('DEGRADED');
      setError(cause instanceof Error ? cause.message : 'Runtime capability authority is unavailable.');
    }
  }, []);

  const refresh = useCallback(() => executeLoad(true), [executeLoad]);

  useEffect(() => {
    mounted.current = true;
    void executeLoad(false);
    return () => {
      mounted.current = false;
      requestSequence.current += 1;
    };
  }, [executeLoad]);

  const value = useMemo<RuntimeCapabilityContextValue>(() => ({
    capabilities,
    loadState,
    error,
    refresh,
  }), [capabilities, error, loadState, refresh]);

  return (
    <RuntimeCapabilityContext.Provider value={value}>
      {loadState === 'DEGRADED' && error ? <DegradedBanner message={error} retry={refresh} /> : null}
      {children}
    </RuntimeCapabilityContext.Provider>
  );
}

export function useRuntimeCapabilities(): RuntimeCapabilityContextValue {
  const context = useContext(RuntimeCapabilityContext);
  if (!context) throw new Error('useRuntimeCapabilities must be used inside RuntimeCapabilityProvider.');
  return context;
}
