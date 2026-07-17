export type BackendPlane = 'core' | 'netty' | 'gateway';

export interface BackendAttempt {
  origin: string;
  error: string;
}

export class BackendConnectionError extends Error {
  constructor(
    public readonly plane: BackendPlane,
    public readonly attempts: BackendAttempt[]
  ) {
    super(`${plane} backend is unreachable through all configured origins`);
    this.name = 'BackendConnectionError';
  }
}

function normalizeOrigin(value: string | undefined): string | undefined {
  const normalized = value?.trim().replace(/\/+$/, '');
  if (!normalized) return undefined;
  try {
    const url = new URL(normalized);
    if (!['http:', 'https:'].includes(url.protocol)) return undefined;
    return url.origin;
  } catch {
    return undefined;
  }
}

function commaSeparatedOrigins(value: string | undefined): string[] {
  if (!value) return [];
  return value
    .split(',')
    .map((entry) => normalizeOrigin(entry))
    .filter((entry): entry is string => Boolean(entry));
}

function publicOrigin(plane: BackendPlane): string | undefined {
  const host = process.env.OPENDISPATCH_PUBLIC_HOST?.trim();
  if (!host) return undefined;
  const scheme = process.env.OPENDISPATCH_PUBLIC_SCHEME?.trim() || 'http';
  const port = plane === 'core'
    ? process.env.CORE_HTTP_PORT?.trim() || '18080'
    : process.env.NETTY_ADMIN_HTTP_PORT?.trim() || '18081';
  return normalizeOrigin(`${scheme}://${host}:${port}`);
}

function unique(values: Array<string | undefined>): string[] {
  return [...new Set(values.filter((value): value is string => Boolean(value)))];
}

export function backendOrigins(plane: BackendPlane): string[] {
  if (plane === 'core') {
    return unique([
      normalizeOrigin(process.env.CORE_BACKEND_ORIGIN),
      normalizeOrigin(process.env.AI_EVENT_GATEWAY_CORE_BACKEND_ORIGIN),
      ...commaSeparatedOrigins(process.env.CORE_BACKEND_FALLBACK_ORIGINS),
      publicOrigin('core'),
      normalizeOrigin('http://localhost:18080')
    ]);
  }

  return unique([
    normalizeOrigin(process.env.NETTY_BACKEND_ORIGIN),
    normalizeOrigin(process.env.GATEWAY_BACKEND_ORIGIN),
    normalizeOrigin(process.env.AI_EVENT_GATEWAY_BACKEND_ORIGIN),
    ...commaSeparatedOrigins(process.env.NETTY_BACKEND_FALLBACK_ORIGINS),
    publicOrigin(plane),
    normalizeOrigin('http://localhost:18081')
  ]);
}

export function backendOrigin(plane: BackendPlane): string {
  const origin = backendOrigins(plane)[0];
  if (!origin) {
    throw new Error(`No ${plane} backend origin is configured`);
  }
  return origin;
}

function timeoutMs(): number {
  const parsed = Number(process.env.ADMIN_UI_BACKEND_CONNECT_TIMEOUT_MS ?? '2500');
  return Number.isFinite(parsed) && parsed >= 250 ? parsed : 2500;
}

function errorMessage(error: unknown): string {
  if (error instanceof Error) {
    const cause = (error as Error & { cause?: unknown }).cause;
    if (cause instanceof Error && cause.message) return `${error.message}: ${cause.message}`;
    return error.message;
  }
  return String(error);
}

export async function fetchBackend(
  plane: BackendPlane,
  pathAndQuery: string,
  init: RequestInit
): Promise<{ response: Response; origin: string; attempts: BackendAttempt[] }> {
  const attempts: BackendAttempt[] = [];

  for (const origin of backendOrigins(plane)) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs());
    try {
      const response = await fetch(`${origin}${pathAndQuery}`, {
        ...init,
        signal: controller.signal,
        cache: 'no-store',
        redirect: 'manual'
      });
      return { response, origin, attempts };
    } catch (error) {
      attempts.push({ origin, error: errorMessage(error) });
    } finally {
      clearTimeout(timer);
    }
  }

  throw new BackendConnectionError(plane, attempts);
}
