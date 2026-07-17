#!/usr/bin/env node
/**
 * Admin UI route-level smoke test.
 *
 * This intentionally does not depend on Playwright. It verifies that the live
 * Next.js app can serve the primary App Router pages as HTML, which catches
 * broken route modules, failed server component rendering, missing runtime env,
 * and accidental route removal before heavier browser E2E is introduced.
 */
const timeoutMs = Number(process.env.SMOKE_TIMEOUT_MS || 5000);
const publicHost = process.env.OPENDISPATCH_PUBLIC_HOST || process.env.CI_PUBLIC_HOST || '';
const publicScheme = process.env.OPENDISPATCH_PUBLIC_SCHEME || 'http';
const defaultAdminOrigin = publicHost ? `${publicScheme}://${publicHost}:${process.env.ADMIN_UI_HTTP_PORT || 3000}` : 'http://localhost:3000';
const adminOrigin = normalizeOrigin(process.env.ADMIN_UI_ORIGIN || defaultAdminOrigin);
const token = process.env.ADMIN_TOKEN || process.env.ACCESS_TOKEN || '';
const defaultPaths = [
  '/',
  '/login',
  '/dashboard',
  '/agents',
  '/agents/runtime',
  '/settings/runtime-resources',
  '/agent-enrollments',
  '/tasks',
  '/tasks/failure-queue',
  '/dispatch-flows',
  '/runtime/rejected-connections',
  '/settings',
  '/dispatch-policies',
  '/testing/dispatch-simulator',
  '/settings/migration-readiness',
  '/settings/release-cutover',
  '/settings/enforce-observability',
  '/settings/dispatch-task-definitions',
  '/supply-profiles',
  '/settings/capabilities'
];
const routePaths = (process.env.ADMIN_ROUTE_SMOKE_PATHS || '')
  .split(',')
  .map((value) => value.trim())
  .filter(Boolean);
const paths = routePaths.length > 0 ? routePaths : defaultPaths;

function normalizeOrigin(value) {
  return String(value || '').replace(/\/+$/, '');
}

function joinUrl(origin, path) {
  return `${origin}${path.startsWith('/') ? path : `/${path}`}`;
}

async function fetchRoute(path) {
  const url = joinUrl(adminOrigin, path);
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  const headers = token ? { Authorization: `Bearer ${token}` } : undefined;
  const started = Date.now();
  try {
    const response = await fetch(url, { headers, signal: controller.signal });
    const contentType = response.headers.get('content-type') || '';
    const body = await response.text();
    const latency = Date.now() - started;
    const looksLikeHtml = contentType.includes('text/html') || /^\s*<!doctype html/i.test(body) || /<html[\s>]/i.test(body);
    if (response.ok && looksLikeHtml) {
      const redirectInfo = response.redirected ? ` redirected-to=${response.url}` : '';
      console.log(`OK   Admin route ${path} -> HTTP ${response.status} text/html (${latency}ms)${redirectInfo}`);
      return true;
    }
    console.error(`FAIL Admin route ${path} -> HTTP ${response.status} content-type=${contentType || '(empty)'} (${latency}ms) ${url}`);
    if (body) {
      console.error(body.slice(0, 300).replace(/\s+/g, ' ').trim());
    }
    return false;
  } catch (error) {
    console.error(`FAIL Admin route ${path} -> ${error instanceof Error ? error.message : String(error)} ${url}`);
    return false;
  } finally {
    clearTimeout(timer);
  }
}

let success = true;
console.log(`Admin UI route smoke against ${adminOrigin}`);
for (const path of paths) {
  success = (await fetchRoute(path)) && success;
}

if (!success) {
  console.error('\nAdmin UI route smoke failed. Set ADMIN_UI_ORIGIN and ADMIN_TOKEN when required.');
  process.exit(1);
}

console.log('\nAdmin UI route smoke completed.');
