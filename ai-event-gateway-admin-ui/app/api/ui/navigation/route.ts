import { NextRequest, NextResponse } from 'next/server';
import { BackendConnectionError, fetchBackend } from '@/lib/server/backendOrigins';
import { resolveEntitlementRoute, type UiEntitlementResponse, type UiNavigationItem } from '@/lib/navigation/uiEntitlements';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';
export const revalidate = 0;

function proxyHeaders(request: NextRequest): Headers {
  const headers = new Headers();
  const cookie = request.headers.get('cookie');
  if (cookie) headers.set('cookie', cookie);
  const authorization = request.headers.get('authorization');
  if (authorization) headers.set('authorization', authorization);
  headers.set('accept', 'application/json');
  headers.set('x-admin-ui-proxy-plane', 'navigation-from-backend-entitlements');
  return headers;
}
const safeHeaders: HeadersInit = { 'Cache-Control': 'no-store, max-age=0', Pragma: 'no-cache', Vary: 'Cookie', 'X-Content-Type-Options': 'nosniff' };

interface ProjectedNavigationItem {
  navigationId: string;
  featureId: string;
  parentFeatureId: string | null;
  section: string;
  order: number;
  href: string;
  label: string;
  purpose: string;
  displayMode: UiNavigationItem['displayMode'];
  children: ProjectedNavigationItem[];
}

function projectItem(item: UiNavigationItem, tenantId: string): ProjectedNavigationItem {
  return {
    navigationId: `feature.${item.featureId}`,
    featureId: item.featureId,
    parentFeatureId: item.parentFeatureId ?? null,
    section: item.section,
    order: item.order,
    href: resolveEntitlementRoute(item.route, tenantId),
    label: item.label,
    purpose: item.purpose,
    displayMode: item.displayMode,
    children: item.children.map(child => projectItem(child, tenantId)),
  };
}

export async function GET(request: NextRequest): Promise<NextResponse> {
  try {
    const { response } = await fetchBackend('core', '/api/session/entitlements', { method: 'GET', headers: proxyHeaders(request), cache: 'no-store' });
    if (!response.ok) {
      const status = response.status === 401 || response.status === 403 ? response.status : 503;
      return NextResponse.json({ code: 'NAVIGATION_UNAVAILABLE', message: status === 401 ? 'Sign in again to load navigation.' : status === 403 ? 'Your current account cannot open this workspace.' : 'Navigation could not be loaded securely.' }, { status, headers: safeHeaders });
    }
    const value = await response.json() as UiEntitlementResponse;
    return NextResponse.json({
      contractVersion: '3.0', workspaceKind: value.workspaceKind, tenantId: value.tenantId,
      items: value.navigation.map(item => projectItem(item, value.tenantId)),
      generatedAt: value.generatedAt,
    }, { status: 200, headers: safeHeaders });
  } catch (error) {
    const attempts = error instanceof BackendConnectionError ? error.attempts : [];
    console.error('[backend-entitlement-navigation]', error, attempts);
    return NextResponse.json({ code: 'NAVIGATION_UNAVAILABLE', message: 'Navigation could not be loaded securely.' }, { status: 503, headers: safeHeaders });
  }
}
