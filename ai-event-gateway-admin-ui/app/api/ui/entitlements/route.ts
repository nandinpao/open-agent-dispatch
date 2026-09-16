import { NextRequest, NextResponse } from 'next/server';
import { BackendConnectionError, fetchBackend } from '@/lib/server/backendOrigins';

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
  headers.set('x-admin-ui-proxy-plane', 'backend-ui-entitlements');
  return headers;
}

const safeHeaders: HeadersInit = {
  'Cache-Control': 'no-store, max-age=0',
  Pragma: 'no-cache',
  Vary: 'Cookie',
  'X-Content-Type-Options': 'nosniff',
};

export async function GET(request: NextRequest): Promise<NextResponse> {
  try {
    const { response } = await fetchBackend('core', '/api/session/entitlements', {
      method: 'GET', headers: proxyHeaders(request), cache: 'no-store',
    });
    if (!response.ok) {
      const status = response.status === 401 || response.status === 403 ? response.status : 503;
      return NextResponse.json(
        { code: status === 401 ? 'AUTHENTICATION_REQUIRED' : status === 403 ? 'ENTITLEMENT_FORBIDDEN' : 'ENTITLEMENT_UNAVAILABLE', message: status === 401 ? 'Sign in again to load your workspace access.' : status === 403 ? 'Your current account cannot open this workspace.' : 'Workspace access could not be loaded securely.' },
        { status, headers: safeHeaders },
      );
    }
    return new NextResponse(await response.text(), { status: 200, headers: { ...safeHeaders, 'Content-Type': 'application/json' } });
  } catch (error) {
    const attempts = error instanceof BackendConnectionError ? error.attempts : [];
    console.error('[backend-ui-entitlements]', error, attempts);
    return NextResponse.json({ code: 'ENTITLEMENT_UNAVAILABLE', message: 'Workspace access could not be loaded securely.' }, { status: 503, headers: safeHeaders });
  }
}
