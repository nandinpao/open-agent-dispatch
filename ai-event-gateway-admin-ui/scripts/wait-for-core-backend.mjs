const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function normalizeOrigin(value) {
  const normalized = value?.trim().replace(/\/+$/, '');
  if (!normalized) return undefined;
  try {
    const url = new URL(normalized);
    return ['http:', 'https:'].includes(url.protocol) ? url.origin : undefined;
  } catch {
    return undefined;
  }
}

function csv(value) {
  return (value || '').split(',').map(normalizeOrigin).filter(Boolean);
}

function origins() {
  const publicHost = process.env.OPENDISPATCH_PUBLIC_HOST?.trim();
  const publicScheme = process.env.OPENDISPATCH_PUBLIC_SCHEME?.trim() || 'http';
  const publicPort = process.env.CORE_HTTP_PORT?.trim() || '18080';
  return [...new Set([
    normalizeOrigin(process.env.CORE_BACKEND_ORIGIN),
    normalizeOrigin(process.env.AI_EVENT_GATEWAY_CORE_BACKEND_ORIGIN),
    ...csv(process.env.CORE_BACKEND_FALLBACK_ORIGINS),
    publicHost ? normalizeOrigin(`${publicScheme}://${publicHost}:${publicPort}`) : undefined,
    normalizeOrigin('http://localhost:18080')
  ].filter(Boolean))];
}

const timeoutMs = Number(process.env.ADMIN_UI_CORE_STARTUP_TIMEOUT_MS || '120000');
const attemptTimeoutMs = Number(process.env.ADMIN_UI_BACKEND_CONNECT_TIMEOUT_MS || '2500');
const deadline = Date.now() + (Number.isFinite(timeoutMs) ? timeoutMs : 120000);
const candidates = origins();

if (candidates.length === 0) {
  console.error('[admin-ui-runtime][ERROR] No Core backend origin is configured.');
  process.exit(1);
}

console.log(`[admin-ui-runtime] Waiting for Core authentication through: ${candidates.join(', ')}`);
let lastAttempts = [];

while (Date.now() < deadline) {
  lastAttempts = [];
  for (const origin of candidates) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), attemptTimeoutMs);
    try {
      const response = await fetch(`${origin}/api/auth/csrf`, {
        headers: { Accept: 'application/json', 'x-admin-ui-startup-probe': 'true' },
        cache: 'no-store',
        redirect: 'manual',
        signal: controller.signal
      });
      if (response.ok) {
        console.log(`[admin-ui-runtime] Core authentication is reachable through ${origin}.`);
        process.exit(0);
      }
      lastAttempts.push(`${origin}=HTTP ${response.status}`);
    } catch (error) {
      lastAttempts.push(`${origin}=${error instanceof Error ? error.message : String(error)}`);
    } finally {
      clearTimeout(timer);
    }
  }
  await sleep(2000);
}

console.error('[admin-ui-runtime][ERROR] Core authentication remained unreachable.');
console.error(`[admin-ui-runtime][ERROR] Attempts: ${lastAttempts.join(' | ')}`);
console.error('[admin-ui-runtime][ERROR] Verify CORE_BACKEND_ORIGIN, CORE_BACKEND_FALLBACK_ORIGINS, Docker network membership, and /api/auth/csrf.');
process.exit(1);
