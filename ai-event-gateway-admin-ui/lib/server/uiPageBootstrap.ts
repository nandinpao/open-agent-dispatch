import 'server-only';

import { randomUUID } from 'node:crypto';
import { headers } from 'next/headers';
import { redirect } from 'next/navigation';
import { fetchBackend } from '@/lib/server/backendOrigins';
import { loadRuntimeCapabilities } from '@/lib/server/runtimeCapabilities';
import type { RuntimeCapabilitySnapshot } from '@/lib/runtime-capability/contracts';
import { UI_CAPABILITY_CONTRACT_VERSION, type UiCapability, type UiPageBootstrap } from '@/lib/ui-capability/contracts';
import { ROOT_ADMIN_TENANT_COOKIE_KEY } from '@/lib/auth/workspaceTenantContext';

interface ServerUiSession { selectedTenantId: string; userId: string; expiresAt: string; roles: readonly string[]; }
export type ReadyUiPageBootstrap = Omit<UiPageBootstrap, 'outcome'> & { outcome: 'PAGE' };
export type SafeUiPageBootstrap = Omit<UiPageBootstrap, 'outcome'> & {
  outcome: Exclude<UiPageBootstrap['outcome'], 'PAGE'>;
};

export type ServerBootstrapDiagnosticCode =
  | 'SESSION_CONTEXT_UNAVAILABLE'
  | 'SERVER_AUTHORIZATION_DENIED'
  | 'SERVER_RESOURCE_NOT_FOUND'
  | 'SERVER_AUTHORIZATION_STALE'
  | 'SERVER_AUTHORIZATION_UNAVAILABLE'
  | 'UI_CAPABILITY_PROJECTION_DISABLED'
  | 'UI_CAPABILITY_AUTHORITY_UNAVAILABLE'
  | 'UI_CAPABILITY_DENIED'
  | 'UI_CAPABILITY_STALE'
  | 'UI_CAPABILITY_BACKEND_ERROR'
  | 'UI_CAPABILITY_CONTRACT_INVALID';

export interface ServerBootstrapDiagnostic {
  code: ServerBootstrapDiagnosticCode;
  message: string;
  correlationId: string;
  status?: number;
  origin?: string;
  retryable: boolean;
  source: 'SESSION' | 'SERVER_GUARD' | 'UI_CAPABILITY';
}

export type ServerBootstrapResult =
  | { kind: 'READY'; bootstrap: ReadyUiPageBootstrap; diagnostic?: ServerBootstrapDiagnostic }
  | { kind: 'SAFE_SHELL'; bootstrap: SafeUiPageBootstrap; diagnostic?: ServerBootstrapDiagnostic }
  | { kind: 'UNAVAILABLE'; diagnostic: ServerBootstrapDiagnostic };

export interface CompatibilityReadBootstrap {
  probePath: string;
  pageCapabilities: readonly UiCapability[];
}

function logBootstrap(marker: string, fields: Record<string, string | number | boolean | undefined>): void {
  const suffix = Object.entries(fields)
    .filter(([, value]) => value !== undefined)
    .map(([key, value]) => `${key}=${String(value).replace(/[\r\n]/g, ' ')}`)
    .join(' ');
  console.info(`[opendispatch-admin-ui] ${marker}${suffix ? ` ${suffix}` : ''}`);
}

function logBootstrapWarning(marker: string, fields: Record<string, string | number | boolean | undefined>): void {
  const suffix = Object.entries(fields)
    .filter(([, value]) => value !== undefined)
    .map(([key, value]) => `${key}=${String(value).replace(/[\r\n]/g, ' ')}`)
    .join(' ');
  console.warn(`[opendispatch-admin-ui] ${marker}${suffix ? ` ${suffix}` : ''}`);
}

function requestCorrelationId(source: Headers): string {
  return source.get('x-correlation-id')?.trim() || `admin-ui-rsc-${randomUUID()}`;
}

function responseCorrelationId(response: Response, fallback: string): string {
  return response.headers.get('x-correlation-id')?.trim() || fallback;
}

function forwardedRequestHeaders(source: Headers, correlationId: string, tenantId?: string): Headers {
  const result = new Headers({
    Accept: 'application/json',
    'X-UI-Capability-Contract': UI_CAPABILITY_CONTRACT_VERSION,
    'X-Correlation-Id': correlationId,
  });
  const cookie = source.get('cookie');
  if (cookie) result.set('cookie', cookie);
  result.set('x-admin-ui-rsc-guard', 'phase7a3');
  if (tenantId) result.set('x-tenant-id', tenantId);
  return result;
}

function unwrapData(value: unknown): unknown {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return value;
  const record = value as Record<string, unknown>;
  if ('data' in record && 'code' in record && 'timestamp' in record) return record.data;
  return value;
}

function parseServerSession(value: unknown): ServerUiSession {
  const data = unwrapData(value);
  if (!data || typeof data !== 'object' || Array.isArray(data)) throw new Error('UI_SESSION_CONTRACT_INVALID');
  const record = data as Record<string, unknown>;
  const selectedTenantId = typeof record.selectedTenantId === 'string' ? record.selectedTenantId.trim() : '';
  const userId = typeof record.userId === 'string' ? record.userId.trim() : '';
  const expiresAt = typeof record.expiresAt === 'string' ? record.expiresAt : '';
  const roles = Array.isArray(record.roles) ? record.roles.filter((value): value is string => typeof value === 'string') : [];
  if (!userId) throw new Error('UI_SESSION_CONTRACT_INVALID');
  return { selectedTenantId, userId, expiresAt, roles };
}

function requestCookieValue(source: Headers, name: string): string {
  const cookie = source.get('cookie') ?? '';
  for (const entry of cookie.split(';')) {
    const separator = entry.indexOf('=');
    if (separator < 0) continue;
    const key = entry.slice(0, separator).trim();
    if (key !== name) continue;
    const value = entry.slice(separator + 1).trim();
    try { return decodeURIComponent(value); } catch { return value; }
  }
  return '';
}

function serverWorkspaceSession(session: ServerUiSession, source: Headers): ServerUiSession {
  if (session.selectedTenantId) return session;
  if (!session.roles.includes('INSTANCE_ROOT')) return session;
  const administrationTenantId = requestCookieValue(source, ROOT_ADMIN_TENANT_COOKIE_KEY).trim();
  if (!administrationTenantId) return session;
  return { ...session, selectedTenantId: administrationTenantId };
}

async function currentSession(source: Headers, correlationId: string): Promise<ServerUiSession | null> {
  const { response } = await fetchBackend('core', '/api/session', {
    method: 'GET', headers: forwardedRequestHeaders(source, correlationId), cache: 'no-store',
  });
  if (response.ok) return parseServerSession(await response.json());
  if (response.status === 401 || response.status === 403) return null;
  throw new Error(`UI_SESSION_${response.status}`);
}

function compatibilityBootstrap(
  input: Readonly<{ routeContext: string; returnPath: string; compatibilityRead: CompatibilityReadBootstrap }>,
  session: ServerUiSession,
): ReadyUiPageBootstrap {
  const now = Date.now();
  return {
    contractVersion: UI_CAPABILITY_CONTRACT_VERSION,
    routeContext: input.routeContext,
    canonicalPath: input.returnPath,
    tenantId: session.selectedTenantId,
    principalEpoch: 0,
    catalogRevision: 0,
    policyVersion: 0,
    outcome: 'PAGE',
    layoutCapabilities: [],
    pageCapabilities: input.compatibilityRead.pageCapabilities,
    expiresAt: new Date(now + 5 * 60 * 1000).toISOString(),
    hydrationNonce: `server-read-${now}-${randomUUID()}`,
  };
}

function safeBootstrap(
  input: Readonly<{ routeContext: string; returnPath: string }>,
  session: ServerUiSession,
  outcome: SafeUiPageBootstrap['outcome'],
): SafeUiPageBootstrap {
  const now = Date.now();
  return {
    contractVersion: UI_CAPABILITY_CONTRACT_VERSION,
    routeContext: input.routeContext,
    canonicalPath: input.returnPath,
    tenantId: session.selectedTenantId,
    principalEpoch: 0,
    catalogRevision: 0,
    policyVersion: 0,
    outcome,
    layoutCapabilities: [],
    pageCapabilities: [],
    expiresAt: new Date(now + 60_000).toISOString(),
    hydrationNonce: `server-safe-${now}-${randomUUID()}`,
  };
}

async function serverAuthorizationGuard(
  input: Readonly<{ routeContext: string; resourceId: string; returnPath: string; compatibilityRead: CompatibilityReadBootstrap }>,
  requestHeaders: Headers,
  session: ServerUiSession,
  correlationId: string,
): Promise<ServerBootstrapResult | { kind: 'AUTHORIZED'; correlationId: string; origin: string }> {
  logBootstrap('task_detail_server_guard_started', {
    routeContext: input.routeContext,
    resourceId: input.resourceId,
    tenantId: session.selectedTenantId,
    correlationId,
  });

  let backend: Awaited<ReturnType<typeof fetchBackend>>;
  try {
    backend = await fetchBackend('core', input.compatibilityRead.probePath, {
      method: 'GET',
      headers: forwardedRequestHeaders(requestHeaders, correlationId, session.selectedTenantId),
      cache: 'no-store',
    });
  } catch (cause) {
    logBootstrapWarning('task_detail_server_guard_unavailable', {
      routeContext: input.routeContext,
      resourceId: input.resourceId,
      tenantId: session.selectedTenantId,
      correlationId,
      error: cause instanceof Error ? cause.message : String(cause),
    });
    return {
      kind: 'UNAVAILABLE',
      diagnostic: {
        code: 'SERVER_AUTHORIZATION_UNAVAILABLE',
        message: 'Core could not be reached to verify Task read access.',
        correlationId,
        retryable: true,
        source: 'SERVER_GUARD',
      },
    };
  }

  const { response, origin } = backend;
  const effectiveCorrelationId = responseCorrelationId(response, correlationId);
  if (response.status === 401) redirect(`/login?returnTo=${encodeURIComponent(input.returnPath)}`);
  if (response.status === 403) {
    logBootstrapWarning('task_detail_server_guard_denied', {
      routeContext: input.routeContext, resourceId: input.resourceId, tenantId: session.selectedTenantId,
      correlationId: effectiveCorrelationId, status: response.status, origin,
    });
    return {
      kind: 'SAFE_SHELL',
      bootstrap: safeBootstrap(input, session, 'REQUEST_ACCESS_SHELL'),
      diagnostic: {
        code: 'SERVER_AUTHORIZATION_DENIED',
        message: 'Core denied Task read access in the current workspace.',
        correlationId: effectiveCorrelationId,
        status: response.status,
        origin,
        retryable: false,
        source: 'SERVER_GUARD',
      },
    };
  }
  if (response.status === 404) {
    logBootstrapWarning('task_detail_server_guard_not_found', {
      routeContext: input.routeContext, resourceId: input.resourceId, tenantId: session.selectedTenantId,
      correlationId: effectiveCorrelationId, status: response.status, origin,
    });
    return {
      kind: 'SAFE_SHELL',
      bootstrap: safeBootstrap(input, session, 'ANTI_ENUMERATION_NOT_FOUND_SHELL'),
      diagnostic: {
        code: 'SERVER_RESOURCE_NOT_FOUND',
        message: 'Core did not expose this Task in the current authorized workspace.',
        correlationId: effectiveCorrelationId,
        status: response.status,
        origin,
        retryable: false,
        source: 'SERVER_GUARD',
      },
    };
  }
  if (response.status === 409 || response.status === 412) {
    logBootstrapWarning('task_detail_server_guard_stale', {
      routeContext: input.routeContext, resourceId: input.resourceId, tenantId: session.selectedTenantId,
      correlationId: effectiveCorrelationId, status: response.status, origin,
    });
    return {
      kind: 'SAFE_SHELL',
      bootstrap: safeBootstrap(input, session, 'SAFE_RESOURCE_CHANGED_SHELL'),
      diagnostic: {
        code: 'SERVER_AUTHORIZATION_STALE',
        message: 'The Task or authorization context changed while access was being verified.',
        correlationId: effectiveCorrelationId,
        status: response.status,
        origin,
        retryable: true,
        source: 'SERVER_GUARD',
      },
    };
  }
  if (!response.ok) {
    logBootstrapWarning('task_detail_server_guard_unavailable', {
      routeContext: input.routeContext, resourceId: input.resourceId, tenantId: session.selectedTenantId,
      correlationId: effectiveCorrelationId, status: response.status, origin,
    });
    return {
      kind: 'UNAVAILABLE',
      diagnostic: {
        code: 'SERVER_AUTHORIZATION_UNAVAILABLE',
        message: `Core returned HTTP ${response.status} while verifying Task read access.`,
        correlationId: effectiveCorrelationId,
        status: response.status,
        origin,
        retryable: true,
        source: 'SERVER_GUARD',
      },
    };
  }

  logBootstrap('task_detail_server_guard_allowed', {
    routeContext: input.routeContext, resourceId: input.resourceId, tenantId: session.selectedTenantId,
    correlationId: effectiveCorrelationId, origin,
  });
  return { kind: 'AUTHORIZED', correlationId: effectiveCorrelationId, origin };
}

export async function loadUiPageBootstrap(input: Readonly<{
  routeContext: string;
  resourceId: string;
  resourceVersion?: number;
  returnPath: string;
  compatibilityRead?: CompatibilityReadBootstrap;
}>): Promise<ServerBootstrapResult> {
  const requestHeaders = await headers();
  const correlationId = requestCorrelationId(requestHeaders);

  let session: ServerUiSession | null;
  try {
    session = await currentSession(requestHeaders, correlationId);
  } catch (cause) {
    logBootstrapWarning('ui_page_bootstrap_session_unavailable', {
      routeContext: input.routeContext,
      resourceId: input.resourceId,
      correlationId,
      error: cause instanceof Error ? cause.message : String(cause),
    });
    return {
      kind: 'UNAVAILABLE',
      diagnostic: {
        code: 'SESSION_CONTEXT_UNAVAILABLE',
        message: 'OpenDispatch could not read the canonical browser session from Core.',
        correlationId,
        retryable: true,
        source: 'SESSION',
      },
    };
  }
  if (!session) redirect(`/login?returnTo=${encodeURIComponent(input.returnPath)}`);
  session = serverWorkspaceSession(session, requestHeaders);
  if (session.roles.includes('INSTANCE_ROOT')) {
    logBootstrap('task_detail_server_workspace_resolved', {
      routeContext: input.routeContext,
      resourceId: input.resourceId,
      tenantId: session.selectedTenantId,
      source: session.selectedTenantId ? 'ROOT_ADMIN_COOKIE_OR_SESSION' : 'INSTANCE_SCOPE',
      correlationId,
    });
  }

  let guardCorrelationId = correlationId;
  if (input.compatibilityRead) {
    const guard = await serverAuthorizationGuard(
      { routeContext: input.routeContext, resourceId: input.resourceId, returnPath: input.returnPath, compatibilityRead: input.compatibilityRead },
      requestHeaders,
      session,
      correlationId,
    );
    if (guard.kind !== 'AUTHORIZED') return guard;
    guardCorrelationId = guard.correlationId;
  }

  let capabilities: RuntimeCapabilitySnapshot;
  try {
    capabilities = await loadRuntimeCapabilities();
  } catch (cause) {
    logBootstrapWarning('ui_page_bootstrap_capability_authority_unavailable', {
      routeContext: input.routeContext,
      resourceId: input.resourceId,
      tenantId: session.selectedTenantId,
      correlationId: guardCorrelationId,
      error: cause instanceof Error ? cause.message : String(cause),
    });
    if (input.compatibilityRead) {
      return {
        kind: 'READY',
        bootstrap: compatibilityBootstrap({ routeContext: input.routeContext, returnPath: input.returnPath, compatibilityRead: input.compatibilityRead }, session),
        diagnostic: {
          code: 'UI_CAPABILITY_AUTHORITY_UNAVAILABLE',
          message: 'Task read access was verified by Core, but the UI Capability authority is temporarily unavailable. The console is read-only.',
          correlationId: guardCorrelationId,
          retryable: true,
          source: 'UI_CAPABILITY',
        },
      };
    }
    return {
      kind: 'UNAVAILABLE',
      diagnostic: {
        code: 'UI_CAPABILITY_AUTHORITY_UNAVAILABLE',
        message: 'The UI Capability authority is temporarily unavailable.',
        correlationId: guardCorrelationId,
        retryable: true,
        source: 'UI_CAPABILITY',
      },
    };
  }

  if (capabilities.surfaces.uiCapabilityProjection === 'DISABLED') {
    logBootstrap('ui_page_bootstrap_capability_surface_disabled', {
      routeContext: input.routeContext,
      resourceId: input.resourceId,
      tenantId: session.selectedTenantId,
      correlationId: guardCorrelationId,
    });
    if (input.compatibilityRead) {
      return {
        kind: 'READY',
        bootstrap: compatibilityBootstrap({ routeContext: input.routeContext, returnPath: input.returnPath, compatibilityRead: input.compatibilityRead }, session),
        diagnostic: {
          code: 'UI_CAPABILITY_PROJECTION_DISABLED',
          message: 'Task read access was verified server-side. Fine-grained UI Capability projection is disabled, so mutation controls remain read-only.',
          correlationId: guardCorrelationId,
          retryable: false,
          source: 'UI_CAPABILITY',
        },
      };
    }
    return {
      kind: 'UNAVAILABLE',
      diagnostic: {
        code: 'UI_CAPABILITY_PROJECTION_DISABLED',
        message: 'UI Capability projection is disabled for this protected page.',
        correlationId: guardCorrelationId,
        retryable: false,
        source: 'UI_CAPABILITY',
      },
    };
  }

  const query = new URLSearchParams({ resourceId: input.resourceId });
  if (input.resourceVersion !== undefined) query.set('resourceVersion', String(input.resourceVersion));
  const path = `/api/ui/bootstrap/${encodeURIComponent(input.routeContext)}?${query}`;
  let backend: Awaited<ReturnType<typeof fetchBackend>>;
  try {
    backend = await fetchBackend('core', path, {
      method: 'GET', headers: forwardedRequestHeaders(requestHeaders, guardCorrelationId, session.selectedTenantId), cache: 'no-store',
    });
  } catch (cause) {
    logBootstrapWarning('ui_page_bootstrap_failed', {
      routeContext: input.routeContext,
      resourceId: input.resourceId,
      tenantId: session.selectedTenantId,
      correlationId: guardCorrelationId,
      error: cause instanceof Error ? cause.message : String(cause),
    });
    if (input.compatibilityRead) {
      return {
        kind: 'READY',
        bootstrap: compatibilityBootstrap({ routeContext: input.routeContext, returnPath: input.returnPath, compatibilityRead: input.compatibilityRead }, session),
        diagnostic: {
          code: 'UI_CAPABILITY_AUTHORITY_UNAVAILABLE',
          message: 'Task read access was verified by Core, but UI Capability projection could not be reached. The console is read-only.',
          correlationId: guardCorrelationId,
          retryable: true,
          source: 'UI_CAPABILITY',
        },
      };
    }
    return {
      kind: 'UNAVAILABLE',
      diagnostic: {
        code: 'UI_CAPABILITY_AUTHORITY_UNAVAILABLE',
        message: 'UI Capability projection could not be reached.',
        correlationId: guardCorrelationId,
        retryable: true,
        source: 'UI_CAPABILITY',
      },
    };
  }

  const { response, origin } = backend;
  const effectiveCorrelationId = responseCorrelationId(response, guardCorrelationId);
  if (response.status === 401) redirect(`/login?returnTo=${encodeURIComponent(input.returnPath)}`);
  if (!response.ok) {
    logBootstrapWarning('ui_page_bootstrap_backend_error', {
      routeContext: input.routeContext,
      resourceId: input.resourceId,
      tenantId: session.selectedTenantId,
      correlationId: effectiveCorrelationId,
      status: response.status,
      origin,
    });
    if (response.status === 403) {
      return {
        kind: 'SAFE_SHELL',
        bootstrap: safeBootstrap(input, session, 'REQUEST_ACCESS_SHELL'),
        diagnostic: {
          code: 'UI_CAPABILITY_DENIED',
          message: 'The Task is readable, but the UI Capability authority denied opening this protected page in the current authorization context.',
          correlationId: effectiveCorrelationId,
          status: response.status,
          origin,
          retryable: false,
          source: 'UI_CAPABILITY',
        },
      };
    }
    if (response.status === 409 || response.status === 412) {
      return {
        kind: 'SAFE_SHELL',
        bootstrap: safeBootstrap(input, session, 'SAFE_RESOURCE_CHANGED_SHELL'),
        diagnostic: {
          code: 'UI_CAPABILITY_STALE',
          message: 'The UI Capability decision is stale relative to the current Task or security epoch.',
          correlationId: effectiveCorrelationId,
          status: response.status,
          origin,
          retryable: true,
          source: 'UI_CAPABILITY',
        },
      };
    }
    if (input.compatibilityRead) {
      return {
        kind: 'READY',
        bootstrap: compatibilityBootstrap({ routeContext: input.routeContext, returnPath: input.returnPath, compatibilityRead: input.compatibilityRead }, session),
        diagnostic: {
          code: 'UI_CAPABILITY_BACKEND_ERROR',
          message: `Task read access was verified by Core, but UI Capability projection returned HTTP ${response.status}. The console is read-only.`,
          correlationId: effectiveCorrelationId,
          status: response.status,
          origin,
          retryable: response.status >= 500 || response.status === 404,
          source: 'UI_CAPABILITY',
        },
      };
    }
    return {
      kind: 'UNAVAILABLE',
      diagnostic: {
        code: 'UI_CAPABILITY_BACKEND_ERROR',
        message: `UI Capability projection returned HTTP ${response.status}.`,
        correlationId: effectiveCorrelationId,
        status: response.status,
        origin,
        retryable: response.status >= 500,
        source: 'UI_CAPABILITY',
      },
    };
  }

  let bootstrap: UiPageBootstrap;
  try {
    bootstrap = unwrapData(await response.json()) as UiPageBootstrap;
  } catch (cause) {
    logBootstrapWarning('ui_page_bootstrap_contract_mismatch', {
      routeContext: input.routeContext,
      resourceId: input.resourceId,
      tenantId: session.selectedTenantId,
      correlationId: effectiveCorrelationId,
      error: cause instanceof Error ? cause.message : String(cause),
    });
    if (input.compatibilityRead) {
      return {
        kind: 'READY',
        bootstrap: compatibilityBootstrap({ routeContext: input.routeContext, returnPath: input.returnPath, compatibilityRead: input.compatibilityRead }, session),
        diagnostic: {
          code: 'UI_CAPABILITY_CONTRACT_INVALID',
          message: 'Task read access was verified by Core, but the UI Capability response could not be parsed. The console is read-only.',
          correlationId: effectiveCorrelationId,
          origin,
          retryable: true,
          source: 'UI_CAPABILITY',
        },
      };
    }
    return {
      kind: 'UNAVAILABLE',
      diagnostic: {
        code: 'UI_CAPABILITY_CONTRACT_INVALID',
        message: 'The UI Capability response could not be parsed.',
        correlationId: effectiveCorrelationId,
        origin,
        retryable: true,
        source: 'UI_CAPABILITY',
      },
    };
  }

  if (!bootstrap
      || bootstrap.contractVersion !== UI_CAPABILITY_CONTRACT_VERSION
      || bootstrap.tenantId !== session.selectedTenantId
      || bootstrap.routeContext !== input.routeContext
      || !bootstrap.canonicalPath?.startsWith('/')
      || bootstrap.canonicalPath.startsWith('//')) {
    logBootstrapWarning('ui_page_bootstrap_contract_mismatch', {
      routeContext: input.routeContext,
      resourceId: input.resourceId,
      tenantId: session.selectedTenantId,
      correlationId: effectiveCorrelationId,
      contractVersion: bootstrap?.contractVersion,
      bootstrapTenantId: bootstrap?.tenantId,
      bootstrapRouteContext: bootstrap?.routeContext,
    });
    if (input.compatibilityRead) {
      return {
        kind: 'READY',
        bootstrap: compatibilityBootstrap({ routeContext: input.routeContext, returnPath: input.returnPath, compatibilityRead: input.compatibilityRead }, session),
        diagnostic: {
          code: 'UI_CAPABILITY_CONTRACT_INVALID',
          message: 'Task read access was verified by Core, but the UI Capability response failed validation. The console is read-only.',
          correlationId: effectiveCorrelationId,
          origin,
          retryable: true,
          source: 'UI_CAPABILITY',
        },
      };
    }
    return {
      kind: 'UNAVAILABLE',
      diagnostic: {
        code: 'UI_CAPABILITY_CONTRACT_INVALID',
        message: 'The UI Capability response failed validation.',
        correlationId: effectiveCorrelationId,
        origin,
        retryable: true,
        source: 'UI_CAPABILITY',
      },
    };
  }

  if (bootstrap.outcome === 'PAGE') {
    const readyBootstrap: ReadyUiPageBootstrap = { ...bootstrap, outcome: 'PAGE' };
    logBootstrap('ui_page_bootstrap_ready', {
      routeContext: input.routeContext,
      resourceId: input.resourceId,
      tenantId: session.selectedTenantId,
      correlationId: effectiveCorrelationId,
    });
    return { kind: 'READY', bootstrap: readyBootstrap };
  }

  const safeBootstrapResult: SafeUiPageBootstrap = { ...bootstrap, outcome: bootstrap.outcome };
  logBootstrap('ui_page_bootstrap_safe_shell', {
    routeContext: input.routeContext,
    resourceId: input.resourceId,
    tenantId: session.selectedTenantId,
    correlationId: effectiveCorrelationId,
    outcome: bootstrap.outcome,
  });
  return {
    kind: 'SAFE_SHELL',
    bootstrap: safeBootstrapResult,
    diagnostic: {
      code: bootstrap.outcome === 'SAFE_RESOURCE_CHANGED_SHELL' ? 'SERVER_AUTHORIZATION_STALE' : 'SERVER_AUTHORIZATION_DENIED',
      message: `UI Capability authority returned ${bootstrap.outcome}.`,
      correlationId: effectiveCorrelationId,
      origin,
      retryable: bootstrap.outcome === 'SAFE_RESOURCE_CHANGED_SHELL',
      source: 'UI_CAPABILITY',
    },
  };
}
